package ru.refontstudio.refontcrafts.storage;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.Material;
import ru.refontstudio.refontcrafts.RefontCrafts;
import ru.refontstudio.refontcrafts.util.ItemCodec;
import ru.refontstudio.refontcrafts.util.ItemUtil;

import java.util.*;

public class RecipeStorage {
    private final RefontCrafts plugin;
    private final Map<String, NamespacedKey> shapelessKeys = new LinkedHashMap<>();
    private final Map<String, AnvilRecipe> anvil = new LinkedHashMap<>();

    public RecipeStorage(RefontCrafts plugin) {
        this.plugin = plugin;
    }

    public int shapelessCount() { return shapelessKeys.size(); }
    public int anvilCount() { return anvil.size(); }
    public Collection<AnvilRecipe> getAnvilRecipes() { return anvil.values(); }

    public void migrateLegacy() {
        FileConfiguration c = plugin.getConfig();
        ConfigurationSection s = c.getConfigurationSection("recipes.shapeless");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                String base = "recipes.shapeless." + id;
                List<?> rawIngs = c.getList(base + ".ingredients");
                if (rawIngs != null && !rawIngs.isEmpty() && !(rawIngs.get(0) instanceof String)) {
                    List<Map<?, ?>> maps = c.getMapList(base + ".ingredients");
                    List<String> out = new ArrayList<>();
                    for (Map<?, ?> m : maps) {
                        Object typeObj = m.containsKey("type") ? m.get("type") : "AIR";
                        Object amtObj = m.containsKey("amount") ? m.get("amount") : null;
                        String type = String.valueOf(typeObj);
                        int amount = 1;
                        if (amtObj != null) {
                            try { amount = Math.max(1, Integer.parseInt(String.valueOf(amtObj))); } catch (Throwable ignored) {}
                        }
                        out.add(type.toUpperCase(Locale.ROOT) + ":" + amount);
                    }
                    c.set(base + ".ingredients", out);
                }
                Object resObj = c.get(base + ".result");
                if (!(resObj instanceof String)) {
                    if (resObj instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) resObj;
                        Object typeObj = m.containsKey("type") ? m.get("type") : "AIR";
                        Object amtObj = m.containsKey("amount") ? m.get("amount") : null;
                        String type = String.valueOf(typeObj);
                        int amount = 1;
                        if (amtObj != null) {
                            try { amount = Math.max(1, Integer.parseInt(String.valueOf(amtObj))); } catch (Throwable ignored) {}
                        }
                        c.set(base + ".result", type.toUpperCase(Locale.ROOT) + ":" + amount);
                    }
                }
            }
        }
        ConfigurationSection a = c.getConfigurationSection("recipes.anvil");
        if (a != null) {
            for (String id : a.getKeys(false)) {
                String base = "recipes.anvil." + id;
                Object l = c.get(base + ".left");
                if (!(l instanceof String)) {
                    if (l instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) l;
                        Object typeObj = m.containsKey("type") ? m.get("type") : "AIR";
                        Object amtObj = m.containsKey("amount") ? m.get("amount") : null;
                        String type = String.valueOf(typeObj);
                        int amount = 1;
                        if (amtObj != null) {
                            try { amount = Math.max(1, Integer.parseInt(String.valueOf(amtObj))); } catch (Throwable ignored) {}
                        }
                        c.set(base + ".left", type.toUpperCase(Locale.ROOT) + ":" + amount);
                    }
                }
                Object r = c.get(base + ".right");
                if (!(r instanceof String)) {
                    if (r instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) r;
                        Object typeObj = m.containsKey("type") ? m.get("type") : "AIR";
                        Object amtObj = m.containsKey("amount") ? m.get("amount") : null;
                        String type = String.valueOf(typeObj);
                        int amount = 1;
                        if (amtObj != null) {
                            try { amount = Math.max(1, Integer.parseInt(String.valueOf(amtObj))); } catch (Throwable ignored) {}
                        }
                        c.set(base + ".right", type.toUpperCase(Locale.ROOT) + ":" + amount);
                    }
                }
                Object res = c.get(base + ".result");
                if (!(res instanceof String)) {
                    if (res instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) res;
                        Object typeObj = m.containsKey("type") ? m.get("type") : "AIR";
                        Object amtObj = m.containsKey("amount") ? m.get("amount") : null;
                        String type = String.valueOf(typeObj);
                        int amount = 1;
                        if (amtObj != null) {
                            try { amount = Math.max(1, Integer.parseInt(String.valueOf(amtObj))); } catch (Throwable ignored) {}
                        }
                        c.set(base + ".result", type.toUpperCase(Locale.ROOT) + ":" + amount);
                    }
                }
            }
        }
        plugin.saveConfig();
    }

    public void loadAndRegisterAll() {
        shapelessKeys.clear();
        anvil.clear();
        FileConfiguration c = plugin.getConfig();

        ConfigurationSection s = c.getConfigurationSection("recipes.shapeless");
        if (s != null) {
            for (String id : s.getKeys(false)) {
                String base = "recipes.shapeless." + id;
                List<String> list = c.getStringList(base + ".ingredients");
                String resStr = c.getString(base + ".result");
                if (list == null || list.isEmpty() || resStr == null) continue;
                List<ItemStack> ing = new ArrayList<>();
                for (String x : list) {
                    ItemStack it = ItemCodec.parseString(x);
                    if (it == null || it.getType() == Material.AIR) continue;
                    ing.add(ItemUtil.cloneWithAmount(it, 1));
                }
                ItemStack res = ItemCodec.parseString(resStr);
                if (ing.isEmpty() || res == null || res.getType() == Material.AIR) continue;
                registerShapeless(id, ing, ItemUtil.cloneWithAmount(res, Math.max(1, res.getAmount())));
            }
        }

        ConfigurationSection a = c.getConfigurationSection("recipes.anvil");
        if (a != null) {
            for (String id : a.getKeys(false)) {
                String base = "recipes.anvil." + id;
                ItemStack left = ItemCodec.parseString(c.getString(base + ".left"));
                ItemStack right = ItemCodec.parseString(c.getString(base + ".right"));
                ItemStack result = ItemCodec.parseString(c.getString(base + ".result"));
                int cost = c.getInt(base + ".cost", plugin.defaultAnvilCost());
                if (left == null || right == null || result == null) continue;
                if (left.getType().isAir() || right.getType().isAir() || result.getType().isAir()) continue;
                anvil.put(id, new AnvilRecipe(id, left, right, result, cost));
            }
        }
    }

    public String saveShapelessRecipe(List<ItemStack> ingredients, ItemStack result) {
        String id = "s_" + System.currentTimeMillis();
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack it : ingredients) copy.add(ItemUtil.cloneWithAmount(it, 1));
        registerShapeless(id, copy, ItemUtil.cloneWithAmount(result, Math.max(1, result.getAmount())));
        FileConfiguration c = plugin.getConfig();
        List<String> flat = new ArrayList<>();
        for (ItemStack it : copy) flat.add(ItemCodec.formatString(ItemUtil.cloneWithAmount(it, 1)));
        c.set("recipes.shapeless." + id + ".ingredients", flat);
        c.set("recipes.shapeless." + id + ".result", ItemCodec.formatString(ItemUtil.cloneWithAmount(result, Math.max(1, result.getAmount()))));
        plugin.saveConfig();
        return id;
    }

    public String saveAnvilRecipe(ItemStack left, ItemStack right, ItemStack result, int cost) {
        String id = "a_" + System.currentTimeMillis();
        anvil.put(id, new AnvilRecipe(id, left.clone(), right.clone(), result.clone(), cost));
        FileConfiguration c = plugin.getConfig();
        c.set("recipes.anvil." + id + ".left", ItemCodec.formatString(left));
        c.set("recipes.anvil." + id + ".right", ItemCodec.formatString(right));
        c.set("recipes.anvil." + id + ".result", ItemCodec.formatString(result));
        c.set("recipes.anvil." + id + ".cost", cost);
        plugin.saveConfig();
        return id;
    }

    private void registerShapeless(String id, List<ItemStack> ingredients, ItemStack result) {
        NamespacedKey key = new NamespacedKey(plugin, "shapeless_" + id);
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
        Bukkit.addRecipe(r);
        shapelessKeys.put(id, key);
    }

    public void unregisterAllShapeless() {
        for (NamespacedKey k : shapelessKeys.values()) {
            try { Bukkit.removeRecipe(k); } catch (Throwable ignored) {}
        }
        shapelessKeys.clear();
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