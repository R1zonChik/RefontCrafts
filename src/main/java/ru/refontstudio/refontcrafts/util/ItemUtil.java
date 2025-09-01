package ru.refontstudio.refontcrafts.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class ItemUtil {
    public static ItemStack named(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(Text.color(name));
        if (lore != null && lore.length > 0) im.setLore(colorLines(Arrays.asList(lore)));
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        it.setItemMeta(im);
        return it;
    }
    public static List<String> colorLines(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) lines.set(i, Text.color(lines.get(i)));
        return lines;
    }
    public static boolean similarType(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() == Material.AIR || b.getType() == Material.AIR) return false;
        return a.getType() == b.getType();
    }
    public static boolean similarExact(ItemStack a, ItemStack b) {
        if (!similarType(a, b)) return false;
        return a.isSimilar(b);
    }
    public static ItemStack cloneWithAmount(ItemStack it, int amount) {
        ItemStack c = it.clone();
        c.setAmount(amount);
        return c;
    }
}