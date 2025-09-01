package ru.refontstudio.refontcrafts;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.refontstudio.refontcrafts.commands.RefontCraftsCommand;
import ru.refontstudio.refontcrafts.gui.AnvilEditorMenu;
import ru.refontstudio.refontcrafts.gui.RecipeEditorMenu;
import ru.refontstudio.refontcrafts.listeners.AnvilListener;
import ru.refontstudio.refontcrafts.storage.RecipeStorage;
import ru.refontstudio.refontcrafts.util.ChatLog;
import ru.refontstudio.refontcrafts.util.ChatLogger;
import ru.refontstudio.refontcrafts.util.Text;

import java.util.logging.Handler;

public final class RefontCrafts extends JavaPlugin {
    private static RefontCrafts instance;
    private RecipeStorage storage;
    private RecipeEditorMenu recipeMenu;
    private AnvilEditorMenu anvilMenu;

    public static RefontCrafts getInstance() { return instance; }
    public RecipeStorage storage() { return storage; }
    public RecipeEditorMenu recipeMenu() { return recipeMenu; }
    public AnvilEditorMenu anvilMenu() { return anvilMenu; }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        getLogger().setUseParentHandlers(false);
        for (Handler h : getLogger().getHandlers()) getLogger().removeHandler(h);
        getLogger().addHandler(new ChatLogger());

        storage = new RecipeStorage(this);
        storage.migrateLegacy();
        storage.loadAndRegisterAll();

        recipeMenu = new RecipeEditorMenu(this, storage);
        anvilMenu = new AnvilEditorMenu(this, storage);
        Bukkit.getPluginManager().registerEvents(recipeMenu, this);
        Bukkit.getPluginManager().registerEvents(anvilMenu, this);
        Bukkit.getPluginManager().registerEvents(new AnvilListener(this, storage), this);

        RefontCraftsCommand cmd = new RefontCraftsCommand(this);
        if (getCommand("rcrafts") != null) {
            getCommand("rcrafts").setExecutor(cmd);
            getCommand("rcrafts").setTabCompleter(cmd);
        }

        ChatLog.send(prefix() + "&aЗагружено рецептов: &f" + storage.shapelessCount() + " &7| Наковальня: &f" + storage.anvilCount());
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.unregisterAllShapeless();
    }

    public String prefix() {
        return Text.color(getConfig().getString("settings.prefix", "&7[&aRefontCrafts&7] "));
    }
    public String titleRecipe() {
        return Text.color(getConfig().getString("settings.titles.recipe", "Создание рецепта"));
    }
    public String titleAnvil() {
        return Text.color(getConfig().getString("settings.titles.anvil", "Наковальня рецепта"));
    }
    public boolean exactMeta() {
        return getConfig().getBoolean("settings.exact_meta_match", false);
    }
    public int defaultAnvilCost() {
        return getConfig().getInt("settings.default_anvil_cost", 0);
    }
    public boolean takeBackOnClose() {
        return getConfig().getBoolean("settings.take_back_on_close", true);
    }
    public void reloadAll() {
        reloadConfig();
        storage.unregisterAllShapeless();
        storage.migrateLegacy();
        storage.loadAndRegisterAll();
        ChatLog.send(prefix() + "&aКонфиг и рецепты перезагружены.");
    }
}