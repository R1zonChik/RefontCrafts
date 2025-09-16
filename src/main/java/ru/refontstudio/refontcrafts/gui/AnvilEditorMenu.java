package ru.refontstudio.refontcrafts.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.refontstudio.refontcrafts.RefontCrafts;
import ru.refontstudio.refontcrafts.storage.RecipeStorage;
import ru.refontstudio.refontcrafts.util.ItemUtil;
import ru.refontstudio.refontcrafts.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnvilEditorMenu implements Listener {
    private final RefontCrafts plugin;
    private final RecipeStorage storage;
    private final Map<Player, Integer> costs = new HashMap<>();
    private final Map<UUID, String> editId = new HashMap<>();

    private static final int LEFT = 10;
    private static final int RIGHT = 12;
    private static final int OUT = 16;
    private static final int MINUS = 20;
    private static final int COST = 22;
    private static final int PLUS = 24;
    private static final int SAVE = 39;
    private static final int CLEAR = 40;
    private static final int EXIT = 44;

    public AnvilEditorMenu(RefontCrafts plugin, RecipeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public void openEditor(Player p) {
        Inventory inv = Bukkit.createInventory(p, 54, plugin.titleAnvil());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ItemUtil.named(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(LEFT, null);
        inv.setItem(RIGHT, null);
        inv.setItem(OUT, null);
        inv.setItem(MINUS, ItemUtil.named(Material.REDSTONE, "&c- Стоимость", "&7Уменьшить на 1"));
        inv.setItem(PLUS, ItemUtil.named(Material.EMERALD, "&a+ Стоимость", "&7Увеличить на 1"));
        inv.setItem(COST, ItemUtil.named(Material.EXPERIENCE_BOTTLE, "&eСтоимость: &f" + plugin.defaultAnvilCost(), "&7Уровни при крафте"));
        inv.setItem(SAVE, ItemUtil.named(Material.LIME_WOOL, "&aСохранить", "&7Сохранить рецепт наковальни"));
        inv.setItem(CLEAR, ItemUtil.named(Material.YELLOW_WOOL, "&eОчистить", "&7Убрать все предметы"));
        inv.setItem(EXIT, ItemUtil.named(Material.BARRIER, "&cВыход", "&7Вернуть вещи и закрыть"));
        costs.put(p, plugin.defaultAnvilCost());
        editId.remove(p.getUniqueId());
        p.openInventory(inv);
    }

    public void openEditorForEdit(Player p, ItemStack left, ItemStack right, ItemStack out, int cost, String id) {
        Inventory inv = Bukkit.createInventory(p, 54, plugin.titleAnvil());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ItemUtil.named(Material.GRAY_STAINED_GLASS_PANE, " "));
        inv.setItem(LEFT, left == null ? null : left.clone());
        inv.setItem(RIGHT, right == null ? null : right.clone());
        inv.setItem(OUT, out == null ? null : out.clone());
        inv.setItem(MINUS, ItemUtil.named(Material.REDSTONE, "&c- Стоимость", "&7Уменьшить на 1"));
        inv.setItem(PLUS, ItemUtil.named(Material.EMERALD, "&a+ Стоимость", "&7Увеличить на 1"));
        inv.setItem(COST, ItemUtil.named(Material.EXPERIENCE_BOTTLE, "&eСтоимость: &f" + cost, "&7Уровни при крафте"));
        inv.setItem(SAVE, ItemUtil.named(Material.LIME_WOOL, "&aСохранить", "&7Пересохранить рецепт"));
        inv.setItem(CLEAR, ItemUtil.named(Material.YELLOW_WOOL, "&eОчистить", "&7Убрать все предметы"));
        inv.setItem(EXIT, ItemUtil.named(Material.BARRIER, "&cВыход", "&7Вернуть вещи и закрыть"));
        costs.put(p, cost);
        editId.put(p.getUniqueId(), id);
        p.openInventory(inv);
    }

    private boolean isEditorTitle(String title) {
        return Text.plain(title).equals(Text.plain(plugin.titleAnvil()));
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!isEditorTitle(e.getView().getTitle())) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        int slot = e.getRawSlot();
        if (slot >= top.getSize()) return;

        if (slot == MINUS || slot == PLUS) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            int cur = costs.getOrDefault(p, plugin.defaultAnvilCost());
            if (slot == MINUS) cur = Math.max(0, cur - 1); else cur = Math.min(99, cur + 1);
            costs.put(p, cur);
            top.setItem(COST, ItemUtil.named(Material.EXPERIENCE_BOTTLE, "&eСтоимость: &f" + cur, "&7Уровни при крафте"));
            return;
        }

        if (slot == SAVE) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            ItemStack left = top.getItem(LEFT);
            ItemStack right = top.getItem(RIGHT);
            ItemStack out = top.getItem(OUT);
            if (left == null || right == null || out == null || left.getType() == Material.AIR || right.getType() == Material.AIR || out.getType() == Material.AIR) {
                p.sendMessage(Text.color(plugin.prefix() + plugin.msg("anvil_fill_both")));
                return;
            }
            int cost = costs.getOrDefault(p, plugin.defaultAnvilCost());
            String old = editId.remove(p.getUniqueId());
            if (old != null) storage.deleteAnvilRecipe(old);
            String id = storage.saveAnvilRecipe(left.clone(), right.clone(), out.clone(), cost);
            p.sendMessage(Text.color(plugin.prefix() + plugin.msg("saved_anvil", "id", id, "cost", String.valueOf(cost))));
            dropBack(p, left);
            dropBack(p, right);
            dropBack(p, out);
            top.setItem(LEFT, null);
            top.setItem(RIGHT, null);
            top.setItem(OUT, null);
            openEditor(p);
            return;
        }

        if (slot == CLEAR) {
            e.setCancelled(true);
            dropBack(e.getWhoClicked(), top.getItem(LEFT));
            dropBack(e.getWhoClicked(), top.getItem(RIGHT));
            dropBack(e.getWhoClicked(), top.getItem(OUT));
            top.setItem(LEFT, null);
            top.setItem(RIGHT, null);
            top.setItem(OUT, null);
            return;
        }

        if (slot == EXIT) {
            e.setCancelled(true);
            e.getWhoClicked().closeInventory();
            return;
        }

        boolean allowed = slot == LEFT || slot == RIGHT || slot == OUT;
        if (!allowed) e.setCancelled(true);
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!isEditorTitle(e.getView().getTitle())) return;
        dropBack(e.getPlayer(), e.getInventory().getItem(LEFT));
        dropBack(e.getPlayer(), e.getInventory().getItem(RIGHT));
        dropBack(e.getPlayer(), e.getInventory().getItem(OUT));
        costs.remove((Player) e.getPlayer());
        editId.remove(e.getPlayer().getUniqueId());
    }

    private void dropBack(HumanEntity p, ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
        for (ItemStack r : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }
}