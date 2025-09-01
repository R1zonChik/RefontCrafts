package ru.refontstudio.refontcrafts.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.refontstudio.refontcrafts.RefontCrafts;
import ru.refontstudio.refontcrafts.util.Text;

import java.util.ArrayList;
import java.util.List;

public class RefontCraftsCommand implements CommandExecutor, TabCompleter {
    private final RefontCrafts plugin;

    public RefontCraftsCommand(RefontCrafts plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            s.sendMessage(Text.color(plugin.prefix() + "&fКоманды: &a/rc recipe&7, &a/rc anvil&7, &a/rc reload"));
            return true;
        }
        String sub = args[0].toLowerCase();
        if (sub.equals("recipe")) {
            if (!(s instanceof Player)) {
                s.sendMessage(Text.color(plugin.prefix() + "&cТолько игрок."));
                return true;
            }
            if (!s.hasPermission("refontcrafts.admin")) {
                s.sendMessage(Text.color(plugin.prefix() + "&cНедостаточно прав."));
                return true;
            }
            plugin.recipeMenu().openEditor((Player) s);
            return true;
        }
        if (sub.equals("anvil")) {
            if (!(s instanceof Player)) {
                s.sendMessage(Text.color(plugin.prefix() + "&cТолько игрок."));
                return true;
            }
            if (!s.hasPermission("refontcrafts.admin")) {
                s.sendMessage(Text.color(plugin.prefix() + "&cНедостаточно прав."));
                return true;
            }
            plugin.anvilMenu().openEditor((Player) s);
            return true;
        }
        if (sub.equals("reload")) {
            if (!s.hasPermission("refontcrafts.admin")) {
                s.sendMessage(Text.color(plugin.prefix() + "&cНедостаточно прав."));
                return true;
            }
            plugin.reloadAll();
            s.sendMessage(Text.color(plugin.prefix() + "&aКонфиг и рецепты перезагружены."));
            return true;
        }
        s.sendMessage(Text.color(plugin.prefix() + "&7Неизвестно. &f/rc recipe &7| &f/rc anvil &7| &f/rc reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("recipe");
            out.add("anvil");
            out.add("reload");
        }
        return out;
    }
}