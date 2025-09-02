package ru.refontstudio.refontcrafts.storage;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.IllegalPluginAccessException;
import ru.refontstudio.refontcrafts.RefontCrafts;
import ru.refontstudio.refontcrafts.db.Database;
import ru.refontstudio.refontcrafts.util.BackupUtil;
import ru.refontstudio.refontcrafts.util.ItemCodec;
import ru.refontstudio.refontcrafts.util.ItemUtil;
import ru.refontstudio.refontcrafts.util.ChatLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

public class RecipeStorage {
    private final RefontCrafts plugin;
    private final Database db;
    private final Map<String, NamespacedKey> shapelessKeys = new LinkedHashMap<>();
    private final Map<String, AnvilRecipe> anvil = new LinkedHashMap<>();
    private volatile boolean closed = false;

    public RecipeStorage(RefontCrafts plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    public void shutdown() { closed = true; }
    private boolean alive() { return !closed && plugin.isEnabled(); }

    public int shapelessCount() { return shapelessKeys.size(); }
    public int anvilCount() { return anvil.size(); }
    public Collection<AnvilRecipe> getAnvilRecipes() { return anvil.values(); }

    public void loadAllAsync(Runnable onDone) {
        if (!alive()) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!alive()) return;

            boolean ready = db.ensureReadyWithRetry(3, 1000);
            if (!ready) db.activateFailoverSqlite();
            try { db.init(); } catch (Throwable ignored) {}

            autoMigrateIfDbTypeChanged();
            bootstrapFromConfigIfNeeded();

            List<LoadedShapeless> shapelessList = new ArrayList<>();
            List<AnvilRecipe> anvilList = new ArrayList<>();

            try (Connection cn = db.getConnection();
                 PreparedStatement ps = cn.prepareStatement("SELECT id,result FROM shapeless_recipes ORDER BY created_at ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    String resStr = rs.getString(2);
                    List<ItemStack> ings = new ArrayList<>();
                    try (PreparedStatement pi = cn.prepareStatement("SELECT ord,item FROM shapeless_ingredients WHERE recipe_id=? ORDER BY ord ASC")) {
                        pi.setString(1, id);
                        try (ResultSet ri = pi.executeQuery()) {
                            while (ri.next()) {
                                ItemStack is = ItemCodec.parseString(ri.getString(2));
                                if (is != null && is.getType() != Material.AIR) ings.add(ItemUtil.cloneWithAmount(is, 1));
                            }
                        }
                    }
                    ItemStack res = ItemCodec.parseString(resStr);
                    if (!ings.isEmpty() && res != null && res.getType() != Material.AIR) {
                        shapelessList.add(new LoadedShapeless(id, ings, ItemUtil.cloneWithAmount(res, Math.max(1, res.getAmount()))));
                    }
                }
            } catch (Throwable t) {
                runSync(() -> ChatLog.send(plugin.prefix() + "&cDB error: load shapeless: &f" + t.getMessage()));
            }

            try (Connection cn = db.getConnection();
                 PreparedStatement ps = cn.prepareStatement("SELECT id,left_item,right_item,result,cost FROM anvil_recipes ORDER BY created_at ASC");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString(1);
                    ItemStack left = ItemCodec.parseString(rs.getString(2));
                    ItemStack right = ItemCodec.parseString(rs.getString(3));
                    ItemStack result = ItemCodec.parseString(rs.getString(4));
                    int cost = rs.getInt(5);
                    if (left != null && right != null && result != null && !left.getType().isAir() && !right.getType().isAir() && !result.getType().isAir()) {
                        anvilList.add(new AnvilRecipe(id, left, right, result, cost));
                    }
                }
            } catch (Throwable t) {
                runSync(() -> ChatLog.send(plugin.prefix() + "&cDB error: load anvil: &f" + t.getMessage()));
            }

            List<String> snapS = new ArrayList<>();
            for (LoadedShapeless s : shapelessList) {
                StringBuilder sb = new StringBuilder();
                sb.append("S;").append(s.id).append(";").append(ItemCodec.formatString(s.result)).append(";");
                List<String> items = new ArrayList<>();
                for (ItemStack it : s.ingredients) items.add(ItemCodec.formatString(it));
                sb.append(String.join(",", items));
                snapS.add(sb.toString());
            }
            List<String> snapA = new ArrayList<>();
            for (AnvilRecipe a : anvilList) {
                String line = "A;" + a.id + ";" + ItemCodec.formatString(a.left) + ";" + ItemCodec.formatString(a.right) + ";" + ItemCodec.formatString(a.result) + ";" + a.cost;
                snapA.add(line);
            }
            BackupUtil.writeSnapshot(plugin, snapS, snapA, db.getActiveType());

            if (!alive()) return;
            try {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!alive()) return;
                    unregisterAllShapeless();
                    anvil.clear();
                    for (LoadedShapeless s : shapelessList) registerShapeless(s.id, s.ingredients, s.result);
                    for (AnvilRecipe a : anvilList) anvil.put(a.id, a);
                    if (onDone != null) onDone.run();
                });
            } catch (IllegalPluginAccessException ignored) {}
        });
    }

    public void autoMigrateIfDbTypeChanged() {
        FileConfiguration conf = plugin.getConfig();
        String curr = db.getActiveType();
        String prev = conf.getString("database.last_type", null);

        if (prev == null || prev.isEmpty()) {
            conf.set("database.last_type", curr);
            plugin.saveConfig();
            return;
        }
        if (prev.equalsIgnoreCase(curr)) return;

        Database src = Database.ofType(plugin, prev);
        if (isDbEmpty(db)) {
            try {
                migrateAll(src, db);
                runSync(() -> ChatLog.send(plugin.prefix() + "&aMigrated recipes from &f" + prev + " &7→ &f" + curr + "&a."));
            } catch (Throwable t) {
                runSync(() -> ChatLog.send(plugin.prefix() + "&cMigration error from &f" + prev + " &cto &f" + curr + "&c: &f" + t.getMessage()));
            }
        }
        conf.set("database.last_type", curr);
        plugin.saveConfig();
    }

    private boolean isDbEmpty(Database target) {
        boolean empty = true;
        try (Connection cn = target.getConnection();
             PreparedStatement ps = cn.prepareStatement("SELECT 1 FROM shapeless_recipes LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) empty = false;
        } catch (Throwable ignored) {}
        if (empty) {
            try (Connection cn = target.getConnection();
                 PreparedStatement ps = cn.prepareStatement("SELECT 1 FROM anvil_recipes LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) empty = false;
            } catch (Throwable ignored) {}
        }
        return empty;
    }

    private void migrateAll(Database src, Database dst) throws Exception {
        try (Connection scn = src.getConnection()) {
            try (PreparedStatement ps = scn.prepareStatement("SELECT id,result,created_at FROM shapeless_recipes");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String result = rs.getString("result");
                    long created = rs.getLong("created_at");
                    try (Connection dcn = dst.getConnection();
                         PreparedStatement ins = dcn.prepareStatement("INSERT INTO shapeless_recipes(id,result,created_at) VALUES(?,?,?)")) {
                        ins.setString(1, id);
                        ins.setString(2, result);
                        ins.setLong(3, created);
                        ins.executeUpdate();
                    }
                    try (PreparedStatement pi = scn.prepareStatement("SELECT ord,item FROM shapeless_ingredients WHERE recipe_id=? ORDER BY ord ASC")) {
                        pi.setString(1, id);
                        try (ResultSet ri = pi.executeQuery()) {
                            int ord = 0;
                            while (ri.next()) {
                                String item = ri.getString("item");
                                try (Connection dcn = dst.getConnection();
                                     PreparedStatement insI = dcn.prepareStatement("INSERT INTO shapeless_ingredients(recipe_id,ord,item) VALUES(?,?,?)")) {
                                    insI.setString(1, id);
                                    insI.setInt(2, ord++);
                                    insI.setString(3, item);
                                    insI.executeUpdate();
                                }
                            }
                        }
                    }
                }
            }

            try (PreparedStatement ps = scn.prepareStatement("SELECT id,left_item,right_item,result,cost,created_at FROM anvil_recipes");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try (Connection dcn = dst.getConnection();
                         PreparedStatement ins = dcn.prepareStatement("INSERT INTO anvil_recipes(id,left_item,right_item,result,cost,created_at) VALUES(?,?,?,?,?,?)")) {
                        ins.setString(1, rs.getString("id"));
                        ins.setString(2, rs.getString("left_item"));
                        ins.setString(3, rs.getString("right_item"));
                        ins.setString(4, rs.getString("result"));
                        ins.setInt(5, rs.getInt("cost"));
                        ins.setLong(6, rs.getLong("created_at"));
                        ins.executeUpdate();
                    }
                }
            }
        }
    }

    public void bootstrapFromConfigIfNeeded() {
        FileConfiguration c = plugin.getConfig();
        if (!isDbEmpty(db)) return;

        ConfigurationSection s = c.getConfigurationSection("recipes.shapeless");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                String base = "recipes.shapeless." + id;
                List<String> list = c.getStringList(base + ".ingredients");
                String resStr = c.getString(base + ".result");
                if (list == null || list.isEmpty() || resStr == null) continue;
                String rid = "s_" + System.currentTimeMillis() + "_" + id;
                long now = System.currentTimeMillis();
                try (Connection cn = db.getConnection();
                     PreparedStatement ins = cn.prepareStatement("INSERT INTO shapeless_recipes(id,result,created_at) VALUES(?,?,?)")) {
                    ins.setString(1, rid);
                    ins.setString(2, resStr);
                    ins.setLong(3, now);
                    ins.executeUpdate();
                    int ord = 0;
                    for (String it : list) {
                        try (PreparedStatement insI = cn.prepareStatement("INSERT INTO shapeless_ingredients(recipe_id,ord,item) VALUES(?,?,?)")) {
                            insI.setString(1, rid);
                            insI.setInt(2, ord++);
                            insI.setString(3, it);
                            insI.executeUpdate();
                        }
                    }
                } catch (Throwable t) {
                    runSync(() -> ChatLog.send(plugin.prefix() + "&cDB error: bootstrap shapeless &f" + id + "&c: &f" + t.getMessage()));
                }
            }
        }
        ConfigurationSection a = c.getConfigurationSection("recipes.anvil");
        if (a != null) {
            for (String id : a.getKeys(false)) {
                String base = "recipes.anvil." + id;
                String left = c.getString(base + ".left");
                String right = c.getString(base + ".right");
                String result = c.getString(base + ".result");
                int cost = c.getInt(base + ".cost", plugin.defaultAnvilCost());
                if (left == null || right == null || result == null) continue;
                String aid = "a_" + System.currentTimeMillis() + "_" + id;
                long now = System.currentTimeMillis();
                try (Connection cn = db.getConnection();
                     PreparedStatement ins = cn.prepareStatement("INSERT INTO anvil_recipes(id,left_item,right_item,result,cost,created_at) VALUES(?,?,?,?,?,?)")) {
                    ins.setString(1, aid);
                    ins.setString(2, left);
                    ins.setString(3, right);
                    ins.setString(4, result);
                    ins.setInt(5, cost);
                    ins.setLong(6, now);
                    ins.executeUpdate();
                } catch (Throwable t) {
                    runSync(() -> ChatLog.send(plugin.prefix() + "&cDB error: bootstrap anvil &f" + id + "&c: &f" + t.getMessage()));
                }
            }
        }
    }

    public String saveShapelessRecipe(List<ItemStack> ingredients, ItemStack result) {
        String id = "s_" + System.currentTimeMillis();
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack it : ingredients) copy.add(ItemUtil.cloneWithAmount(it, 1));
        registerShapeless(id, copy, ItemUtil.cloneWithAmount(result, Math.max(1, result.getAmount())));
        boolean async = plugin.getConfig().getBoolean("database.async_save", true);
        Runnable task = () -> {
            boolean ok = tryInsertShapeless(id, copy, result);
            if (!ok) {
                StringBuilder sb = new StringBuilder();
                List<String> items = new ArrayList<>();
                for (ItemStack it : copy) items.add(ItemCodec.formatString(it));
                sb.append("S;").append(id).append(";").append(ItemCodec.formatString(result)).append(";").append(String.join(",", items));
                BackupUtil.appendPending(plugin, sb.toString());
            }
        };
        if (async && alive()) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task); else task.run();
        return id;
    }

    private boolean tryInsertShapeless(String id, List<ItemStack> copy, ItemStack result) {
        try (Connection cn = db.getConnection();
             PreparedStatement ins = cn.prepareStatement("INSERT INTO shapeless_recipes(id,result,created_at) VALUES(?,?,?)")) {
            ins.setString(1, id);
            ins.setString(2, ItemCodec.formatString(ItemUtil.cloneWithAmount(result, Math.max(1, result.getAmount()))));
            ins.setLong(3, System.currentTimeMillis());
            ins.executeUpdate();
            int ord = 0;
            for (ItemStack it : copy) {
                try (PreparedStatement insI = cn.prepareStatement("INSERT INTO shapeless_ingredients(recipe_id,ord,item) VALUES(?,?,?)")) {
                    insI.setString(1, id);
                    insI.setInt(2, ord++);
                    insI.setString(3, ItemCodec.formatString(ItemUtil.cloneWithAmount(it, 1)));
                    insI.executeUpdate();
                }
            }
            return true;
        } catch (Throwable first) {
            if (!"sqlite".equalsIgnoreCase(db.getActiveType())) {
                db.activateFailoverSqlite();
                try { db.init(); } catch (Throwable ignored) {}
                try (Connection cn = db.getConnection();
                     PreparedStatement ins = cn.prepareStatement("INSERT INTO shapeless_recipes(id,result,created_at) VALUES(?,?,?)")) {
                    ins.setString(1, id);
                    ins.setString(2, ItemCodec.formatString(ItemUtil.cloneWithAmount(result, Math.max(1, result.getAmount()))));
                    ins.setLong(3, System.currentTimeMillis());
                    ins.executeUpdate();
                    int ord = 0;
                    for (ItemStack it : copy) {
                        try (PreparedStatement insI = cn.prepareStatement("INSERT INTO shapeless_ingredients(recipe_id,ord,item) VALUES(?,?,?)")) {
                            insI.setString(1, id);
                            insI.setInt(2, ord++);
                            insI.setString(3, ItemCodec.formatString(ItemUtil.cloneWithAmount(it, 1)));
                            insI.executeUpdate();
                        }
                    }
                    return true;
                } catch (Throwable ignored) {}
            }
            return false;
        }
    }

    public String saveAnvilRecipe(ItemStack left, ItemStack right, ItemStack result, int cost) {
        String id = "a_" + System.currentTimeMillis();
        anvil.put(id, new AnvilRecipe(id, left.clone(), right.clone(), result.clone(), cost));
        boolean async = plugin.getConfig().getBoolean("database.async_save", true);
        Runnable task = () -> {
            boolean ok = tryInsertAnvil(id, left, right, result, cost);
            if (!ok) {
                String line = "A;" + id + ";" + ItemCodec.formatString(left) + ";" + ItemCodec.formatString(right) + ";" + ItemCodec.formatString(result) + ";" + cost;
                BackupUtil.appendPending(plugin, line);
            }
        };
        if (async && alive()) plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task); else task.run();
        return id;
    }

    private boolean tryInsertAnvil(String id, ItemStack left, ItemStack right, ItemStack result, int cost) {
        try (Connection cn = db.getConnection();
             PreparedStatement ins = cn.prepareStatement("INSERT INTO anvil_recipes(id,left_item,right_item,result,cost,created_at) VALUES(?,?,?,?,?,?)")) {
            ins.setString(1, id);
            ins.setString(2, ItemCodec.formatString(left));
            ins.setString(3, ItemCodec.formatString(right));
            ins.setString(4, ItemCodec.formatString(result));
            ins.setInt(5, cost);
            ins.setLong(6, System.currentTimeMillis());
            ins.executeUpdate();
            return true;
        } catch (Throwable first) {
            if (!"sqlite".equalsIgnoreCase(db.getActiveType())) {
                db.activateFailoverSqlite();
                try { db.init(); } catch (Throwable ignored) {}
                try (Connection cn = db.getConnection();
                     PreparedStatement ins = cn.prepareStatement("INSERT INTO anvil_recipes(id,left_item,right_item,result,cost,created_at) VALUES(?,?,?,?,?,?)")) {
                    ins.setString(1, id);
                    ins.setString(2, ItemCodec.formatString(left));
                    ins.setString(3, ItemCodec.formatString(right));
                    ins.setString(4, ItemCodec.formatString(result));
                    ins.setInt(5, cost);
                    ins.setLong(6, System.currentTimeMillis());
                    ins.executeUpdate();
                    return true;
                } catch (Throwable ignored) {}
            }
            return false;
        }
    }

    private void registerShapeless(String id, List<ItemStack> ingredients, ItemStack result) {
        if (!alive()) return;
        NamespacedKey key = new NamespacedKey(plugin, "shapeless_" + id);
        try { Bukkit.removeRecipe(key); } catch (Throwable ignored) {}
        ShapelessRecipe r = new ShapelessRecipe(key, result.clone());
        boolean exact = plugin.exactMeta();
        int added = 0;
        for (ItemStack it : ingredients) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (added >= 9) break;
            if (exact) {
                ItemStack one = it.clone();
                one.setAmount(1);
                r.addIngredient(new RecipeChoice.ExactChoice(one));
            } else {
                r.addIngredient(it.getType());
            }
            added++;
        }
        try { Bukkit.addRecipe(r); } catch (IllegalStateException ignored) {}
        shapelessKeys.put(id, key);
    }

    public void unregisterAllShapeless() {
        for (NamespacedKey k : shapelessKeys.values()) {
            try { Bukkit.removeRecipe(k); } catch (Throwable ignored) {}
        }
        shapelessKeys.clear();
    }

    private void runSync(Runnable r) {
        if (!alive()) return;
        try { plugin.getServer().getScheduler().runTask(plugin, r); } catch (IllegalPluginAccessException ignored) {}
    }

    private static class LoadedShapeless {
        final String id;
        final List<ItemStack> ingredients;
        final ItemStack result;
        LoadedShapeless(String id, List<ItemStack> ingredients, ItemStack result) {
            this.id = id;
            this.ingredients = ingredients;
            this.result = result;
        }
    }

    public static class AnvilRecipe {
        public final String id;
        public final ItemStack left;
        public final ItemStack right;
        public final ItemStack result;
        public final int cost;
        public AnvilRecipe(String id, ItemStack left, ItemStack right, ItemStack result, int cost) {
            this.id = id;
            this.left = left;
            this.right = right;
            this.result = result;
            this.cost = cost;
        }
    }
}