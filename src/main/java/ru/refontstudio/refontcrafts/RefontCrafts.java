package ru.refontstudio.refontcrafts;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.refontstudio.refontcrafts.commands.RefontCraftsCommand;
import ru.refontstudio.refontcrafts.db.Database;
import ru.refontstudio.refontcrafts.gui.AnvilEditorMenu;
import ru.refontstudio.refontcrafts.gui.RecipeBrowserMenu;
import ru.refontstudio.refontcrafts.gui.RecipeEditorMenu;
import ru.refontstudio.refontcrafts.listeners.AnvilClickListener;
import ru.refontstudio.refontcrafts.listeners.AnvilListener;
import ru.refontstudio.refontcrafts.listeners.WorkbenchListener;
import ru.refontstudio.refontcrafts.storage.RecipeStorage;
import ru.refontstudio.refontcrafts.util.ChatLog;
import ru.refontstudio.refontcrafts.util.ChatLogger;
import ru.refontstudio.refontcrafts.util.Text;

import java.util.logging.Handler;

public final class RefontCrafts extends JavaPlugin {
    private static RefontCrafts instance;
    private Database database;
    private RecipeStorage storage;
    private RecipeEditorMenu recipeMenu;
    private AnvilEditorMenu anvilMenu;
    private RecipeBrowserMenu browserMenu;
    private boolean clickAnvilMode;

    public static RefontCrafts getInstance() { return instance; }
    public Database database() { return database; }
    public RecipeStorage storage() { return storage; }
    public RecipeEditorMenu recipeMenu() { return recipeMenu; }
    public AnvilEditorMenu anvilMenu() { return anvilMenu; }
    public RecipeBrowserMenu browserMenu() { return browserMenu; }
    public boolean clickAnvilMode() { return clickAnvilMode; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        new ru.refontstudio.refontcrafts.util.ConfigUpdater(this).writePretty();
        reloadConfig();
        getLogger().setUseParentHandlers(false);
        for (Handler h : getLogger().getHandlers()) getLogger().removeHandler(h);
        getLogger().addHandler(new ChatLogger());
        database = new Database(this);
        storage = new RecipeStorage(this, database);
        recipeMenu = new RecipeEditorMenu(this, storage);
        anvilMenu = new AnvilEditorMenu(this, storage);
        browserMenu = new RecipeBrowserMenu(this, storage);
        String mode = getConfig().getString("settings.anvil_mode", "auto").toLowerCase();
        boolean aePresent = Bukkit.getPluginManager().getPlugin("AdvancedEnchantments") != null;
        clickAnvilMode = mode.equals("click") || (mode.equals("auto") && aePresent);
        Bukkit.getPluginManager().registerEvents(recipeMenu, this);
        Bukkit.getPluginManager().registerEvents(anvilMenu, this);
        Bukkit.getPluginManager().registerEvents(browserMenu, this);
        if (clickAnvilMode) {
            Bukkit.getPluginManager().registerEvents(new AnvilClickListener(this, storage), this);
        } else {
            Bukkit.getPluginManager().registerEvents(new AnvilListener(this, storage), this);
        }
        Bukkit.getPluginManager().registerEvents(new WorkbenchListener(this), this);
        RefontCraftsCommand cmd = new RefontCraftsCommand(this);
        if (getCommand("rcrafts") != null) {
            getCommand("rcrafts").setExecutor(cmd);
            getCommand("rcrafts").setTabCompleter(cmd);
        }
        storage.loadAllAsync(() -> ChatLog.send(prefix() + "&aЗагружено рецептов: &f" + storage.shapelessCount() + " &7| Наковальня: &f" + storage.anvilCount()));
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.shutdown();
            storage.unregisterAllShapeless();
        }
    }

    public String prefix() { return Text.color(getConfig().getString("settings.prefix", "&7[&aRefontCrafts&7] ")); }
    public String titleRecipe() { return Text.color(getConfig().getString("settings.titles.recipe", "Создание рецепта")); }
    public String titleAnvil() { return Text.color(getConfig().getString("settings.titles.anvil", "Наковальня рецепта")); }
    public String titleBrowseWorkbench() { return Text.color(getConfig().getString("settings.titles.browser_workbench", "Рецепты Верстака")); }
    public String titleBrowseAnvil() { return Text.color(getConfig().getString("settings.titles.browser_anvil", "Рецепты Наковальни")); }
    public boolean exactMeta() { return getConfig().getBoolean("settings.exact_meta_match", true); }
    public int defaultAnvilCost() { return getConfig().getInt("settings.default_anvil_cost", 0); }
    public boolean workbenchStrictShape() { return getConfig().getBoolean("settings.workbench_strict_shape", true); }
    public boolean workbenchAllowMirror() { return getConfig().getBoolean("settings.workbench_allow_mirror", false); }
    public String msg(String key) { return Text.color(getConfig().getString("messages." + key, "")); }
    public String msg(String key, String... ph) {
        String s = getConfig().getString("messages." + key, "");
        for (int i = 0; i + 1 < ph.length; i += 2) s = s.replace("%" + ph[i] + "%", ph[i + 1]);
        return Text.color(s);
    }
    public void reloadAll() {
        reloadConfig();
        storage.unregisterAllShapeless();
        storage.loadAllAsync(() -> {});
    }
}