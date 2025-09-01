package ru.refontstudio.refontcrafts.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class ItemCodec {
    public static ItemStack parseString(String s) {
        if (s == null || s.trim().isEmpty()) return new ItemStack(Material.AIR);
        String t = s.trim();
        String[] p = t.split("[: ]");
        String name = p[0].trim().toUpperCase(Locale.ROOT);
        int amount = 1;
        if (p.length > 1) {
            try { amount = Math.max(1, Integer.parseInt(p[1])); } catch (Throwable ignored) {}
        }
        Material m;
        try { m = Material.valueOf(name); } catch (IllegalArgumentException ex) { m = null; }
        if (m == null || m.isAir()) return new ItemStack(Material.AIR);
        return new ItemStack(m, amount);
    }
    public static String formatString(ItemStack it) {
        if (it == null || it.getType().isAir()) return "AIR:1";
        return it.getType().name() + ":" + Math.max(1, it.getAmount());
    }
    public static ItemStack parseSection(ConfigurationSection s) {
        if (s == null) return new ItemStack(Material.AIR);
        String type = s.getString("type", "AIR");
        int amount = Math.max(1, s.getInt("amount", 1));
        Material m;
        try { m = Material.valueOf(type.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ex) { m = null; }
        if (m == null || m.isAir()) return new ItemStack(Material.AIR);
        return new ItemStack(m, amount);
    }
}