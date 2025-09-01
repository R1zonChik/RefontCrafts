package ru.refontstudio.refontcrafts.util;

import net.md_5.bungee.api.ChatColor;

public class Text {
    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
    public static String plain(String s) {
        return ChatColor.stripColor(color(s));
    }
}