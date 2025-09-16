package ru.refontstudio.refontcrafts.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.refontstudio.refontcrafts.RefontCrafts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RefontCraftsCommand implements CommandExecutor, TabCompleter {
    private final RefontCrafts plugin;

    public RefontCraftsCommand(RefontCrafts plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player)) {
            s.sendMessage(plugin.msg("only_player"));
            return true;
        }
        Player p = (Player) s;
        if (args.length == 0) {
            plugin.browserMenu().openWorkbench(p, 1);
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("recipe")) {
            plugin.recipeMenu().openEditor(p);
            return true;
        }
        if (sub.equals("anvil")) {
            plugin.anvilMenu().openEditor(p);
            return true;
        }
        if (sub.equals("view") || sub.equals("browse") || sub.equals("list")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("anvil")) {
                int page = 1;
                if (args.length >= 3) try { page = Math.max(1, Integer.parseInt(args[2])); } catch (Throwable ignored) {}
                plugin.browserMenu().openAnvil(p, page);
            } else {
                int page = 1;
                if (args.length >= 3) try { page = Math.max(1, Integer.parseInt(args[2])); } catch (Throwable ignored) {}
                plugin.browserMenu().openWorkbench(p, page);
            }
            return true;
        }
        if (sub.equals("reload")) {
            plugin.reloadAll();
            p.sendMessage(plugin.msg("reloaded"));
            return true;
        }
        plugin.browserMenu().openWorkbench(p, 1);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("view","browse","list","recipe","anvil","reload");
        if (args.length == 2 && (args[0].equalsIgnoreCase("view") || args[0].equalsIgnoreCase("browse") || args[0].equalsIgnoreCase("list"))) return Arrays.asList("workbench","anvil");
        return new ArrayList<>();
    }
}