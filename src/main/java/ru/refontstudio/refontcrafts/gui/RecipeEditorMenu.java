package ru.refontstudio.refontcrafts.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.refontstudio.refontcrafts.RefontCrafts;
import ru.refontstudio.refontcrafts.storage.RecipeStorage;
import ru.refontstudio.refontcrafts.util.ItemUtil;
import ru.refontstudio.refontcrafts.util.Text;

import java.util.*;

public class RecipeEditorMenu implements Listener {
    private final RefontCrafts plugin;
    private final RecipeStorage storage;
    private final Set<UUID> open = new HashSet<>();
    private final Map<UUID, String> editId = new HashMap<>();
    private final Map<UUID, Boolean> editShaped = new HashMap<>();
    private final NamespacedKey GHOST;

    private static final int[] ING = {10,11,12,19,20,21,28,29,30};
    private static final int RES = 25;
    private static final int SAVE = 49;
    private static final int CLEAR = 50;
    private static final int EXIT = 53;

    public RecipeEditorMenu(RefontCrafts plugin, RecipeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.GHOST = new NamespacedKey(plugin, "rc_ghost");
    }

    public void openEditor(Player p) {
        Inventory inv = Bukkit.createInventory(p, 54, plugin.titleRecipe());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ItemUtil.named(Material.GRAY_STAINED_GLASS_PANE, " "));
        for (int s : ING) inv.setItem(s, null);
        inv.setItem(RES, null);
        inv.setItem(SAVE, ItemUtil.named(Material.LIME_WOOL, "§aСохранить", "§7Сохранить рецепт"));
        inv.setItem(CLEAR, ItemUtil.named(Material.YELLOW_WOOL, "§eОчистить", "§7Убрать все предметы"));
        inv.setItem(EXIT, ItemUtil.named(Material.BARRIER, "§cВыход", "§7Вернуть вещи и закрыть"));
        p.openInventory(inv);
        open.add(p.getUniqueId());
        editId.remove(p.getUniqueId());
        editShaped.remove(p.getUniqueId());
    }

    public void openEditorForEdit(Player p, String id, List<ItemStack> grid9, ItemStack result, boolean shaped) {
        Inventory inv = Bukkit.createInventory(p, 54, plugin.titleRecipe());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ItemUtil.named(Material.GRAY_STAINED_GLASS_PANE, " "));
        for (int i = 0; i < ING.length; i++) {
            ItemStack it = i < grid9.size() ? grid9.get(i) : null;
            ItemStack put = it == null ? null : it.clone();
            if (put != null && put.getType() != Material.AIR) markGhost(put);
            inv.setItem(ING[i], put);
        }
        ItemStack out = result == null ? null : result.clone();
        if (out != null && out.getType() != Material.AIR) markGhost(out);
        inv.setItem(RES, out);
        inv.setItem(SAVE, ItemUtil.named(Material.LIME_WOOL, "§aСохранить", "§7Пересохранить рецепт"));
        inv.setItem(CLEAR, ItemUtil.named(Material.YELLOW_WOOL, "§eОчистить", "§7Убрать все предметы"));
        inv.setItem(EXIT, ItemUtil.named(Material.BARRIER, "§cВыход", "§7Вернуть вещи и закрыть"));
        p.openInventory(inv);
        open.add(p.getUniqueId());
        editId.put(p.getUniqueId(), id);
        editShaped.put(p.getUniqueId(), shaped);
    }

    private boolean isEditorTitle(String title) {
        return Text.plain(title).equals(Text.plain(plugin.titleRecipe()));
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!isEditorTitle(e.getView().getTitle())) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Inventory top = e.getView().getTopInventory();
        int slot = e.getRawSlot();
        if (slot >= top.getSize()) return;

        if (slot == SAVE) {
            e.setCancelled(true);
            Player p = (Player) e.getWhoClicked();
            List<ItemStack> grid = new ArrayList<>(9);
            for (int s1 : ING) {
                ItemStack it = top.getItem(s1);
                if (it == null || it.getType() == Material.AIR || isGhost(it)) {
                    grid.add(new ItemStack(Material.AIR));
                } else {
                    grid.add(ItemUtil.cloneWithAmount(it, 1));
                }
            }
            ItemStack res = top.getItem(RES);
            if (res == null || res.getType() == Material.AIR || isGhost(res)) {
                p.sendMessage(Text.color(plugin.prefix() + plugin.msg("recipe_fill_both")));
                return;
            }
            String old = editId.remove(p.getUniqueId());
            editShaped.remove(p.getUniqueId());
            if (old != null) storage.deleteWorkbenchRecipe(old);
            String id = storage.saveShapedRecipe(grid, res.clone());
            p.sendMessage(Text.color(plugin.prefix() + plugin.msg("saved_recipe", "id", id)));
            for (int s2 : ING) {
                dropBack(p, top.getItem(s2));
                top.setItem(s2, null);
            }
            dropBack(p, top.getItem(RES));
            top.setItem(RES, null);
            openEditor(p);
            return;
        }

        if (slot == CLEAR) {
            e.setCancelled(true);
            for (int s : ING) dropBack(e.getWhoClicked(), top.getItem(s));
            dropBack(e.getWhoClicked(), top.getItem(RES));
            for (int s : ING) top.setItem(s, null);
            top.setItem(RES, null);
            return;
        }

        if (slot == EXIT) {
            e.setCancelled(true);
            e.getWhoClicked().closeInventory();
            return;
        }

        boolean allowed = false;
        for (int s : ING) if (slot == s) allowed = true;
        if (slot == RES) allowed = true;
        if (!allowed) {
            e.setCancelled(true);
            return;
        }

        ItemStack cur = top.getItem(slot);
        if (cur != null && cur.getType() != Material.AIR && isGhost(cur)) {
            e.setCancelled(true);
            Player pl = (Player) e.getWhoClicked();
            ItemStack cursor = pl.getItemOnCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                pl.sendMessage(plugin.msg("editor_preview_hint"));
            } else {
                if (slot != RES) {
                    int place = Math.min(1, cursor.getAmount());
                    ItemStack put = cursor.clone();
                    put.setAmount(place);
                    top.setItem(slot, put);
                    int rest = cursor.getAmount() - place;
                    if (rest > 0) {
                        cursor.setAmount(rest);
                        pl.setItemOnCursor(cursor);
                    } else {
                        pl.setItemOnCursor(null);
                    }
                } else {
                    ItemStack put = cursor.clone();
                    top.setItem(slot, put);
                    pl.setItemOnCursor(null);
                }
            }
            return;
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (!isEditorTitle(e.getView().getTitle())) return;
        if (e.getRawSlots().isEmpty()) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (Integer s : e.getRawSlots()) {
            if (s < topSize) {
                ItemStack it = e.getView().getTopInventory().getItem(s);
                if (it != null && it.getType() != Material.AIR && isGhost(it)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        if (!isEditorTitle(e.getView().getTitle())) return;
        Inventory inv = e.getInventory();
        for (int s : ING) dropBack(e.getPlayer(), inv.getItem(s));
        dropBack(e.getPlayer(), inv.getItem(RES));
        open.remove(e.getPlayer().getUniqueId());
        editId.remove(e.getPlayer().getUniqueId());
        editShaped.remove(e.getPlayer().getUniqueId());
    }

    private void dropBack(HumanEntity p, ItemStack it) {
        if (it == null || it.getType() == Material.AIR) return;
        if (isGhost(it)) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
        if (!left.isEmpty() && p instanceof Player) ((Player) p).sendMessage(plugin.msg("no_inventory_space"));
        for (ItemStack r : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }

    private void markGhost(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        m.getPersistentDataContainer().set(GHOST, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
    }

    private boolean isGhost(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m == null) return false;
        Byte b = m.getPersistentDataContainer().get(GHOST, PersistentDataType.BYTE);
        return b != null && b == (byte) 1;
    }
}