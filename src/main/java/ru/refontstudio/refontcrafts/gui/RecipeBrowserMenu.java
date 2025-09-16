package ru.refontstudio.refontcrafts.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.refontstudio.refontcrafts.RefontCrafts;
import ru.refontstudio.refontcrafts.storage.RecipeStorage;
import ru.refontstudio.refontcrafts.storage.RecipeStorage.AnvilRecipe;
import ru.refontstudio.refontcrafts.storage.RecipeStorage.WorkbenchRecipe;
import ru.refontstudio.refontcrafts.util.ItemUtil;
import ru.refontstudio.refontcrafts.util.Text;

import java.util.*;

public class RecipeBrowserMenu implements Listener {
    private final RefontCrafts plugin;
    private final RecipeStorage storage;

    private final NamespacedKey NAV;
    private final NamespacedKey RID;
    private final NamespacedKey TYP;

    private final Map<UUID, Integer> pages = new HashMap<>();

    private static final int[] VIEW_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };
    private static final int SLOT_PREV = 45;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_NEXT = 53;

    public RecipeBrowserMenu(RefontCrafts plugin, RecipeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.NAV = new NamespacedKey(plugin, "rc_nav");
        this.RID = new NamespacedKey(plugin, "rc_rid");
        this.TYP = new NamespacedKey(plugin, "rc_typ");
    }

    public void openWorkbench(Player p, int page) {
        pages.put(p.getUniqueId(), Math.max(1, page));
        String title = plugin.titleBrowseWorkbench();
        Inventory inv = Bukkit.createInventory(p, 54, title);
        fillFrame(inv);
        List<WorkbenchRecipe> list = new ArrayList<>(storage.getWorkbenchRecipes());
        list.sort(Comparator.comparing(w -> w.id));
        int per = VIEW_SLOTS.length;
        int pg = Math.max(1, page);
        int from = (pg - 1) * per;
        int to = Math.min(from + per, list.size());
        int idx = 0;
        for (int i = from; i < to; i++) {
            WorkbenchRecipe r = list.get(i);
            ItemStack it = r.result.clone();
            ItemMeta im = it.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&8Тип: &fВерстак"));
            lore.add(Text.color("&8Форма: " + (r.shaped ? "&aСтрогая" : "&eБез формы")));
            lore.add(Text.color("&8ID: &7" + r.id));
            lore.add(" ");
            lore.add(Text.color("§x§f§f§e§2§4§7&lЛКМ &7— &aРедактировать"));
            lore.add(Text.color("§x§f§0§5§0§5§0&lПКМ &7— &cУдалить"));
            im.setLore(lore);
            im.getPersistentDataContainer().set(RID, PersistentDataType.STRING, r.id);
            im.getPersistentDataContainer().set(TYP, PersistentDataType.STRING, "wb");
            it.setItemMeta(im);
            inv.setItem(VIEW_SLOTS[idx++], it);
        }
        inv.setItem(SLOT_PREV, navItem("prev"));
        inv.setItem(SLOT_CLOSE, closeItem());
        inv.setItem(SLOT_NEXT, navItem("next"));
        p.openInventory(inv);
    }

    public void openAnvil(Player p, int page) {
        pages.put(p.getUniqueId(), Math.max(1, page));
        String title = plugin.titleBrowseAnvil();
        Inventory inv = Bukkit.createInventory(p, 54, title);
        fillFrame(inv);
        List<AnvilRecipe> list = new ArrayList<>(storage.getAnvilRecipes());
        list.sort(Comparator.comparing(a -> a.id));
        int per = VIEW_SLOTS.length;
        int pg = Math.max(1, page);
        int from = (pg - 1) * per;
        int to = Math.min(from + per, list.size());
        int idx = 0;
        for (int i = from; i < to; i++) {
            AnvilRecipe r = list.get(i);
            ItemStack it = r.result.clone();
            ItemMeta im = it.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(Text.color("&8Тип: &fНаковальня"));
            lore.add(Text.color("&8ID: &7" + r.id));
            lore.add(Text.color("&8Стоимость: &f" + r.cost));
            lore.add(" ");
            lore.add(Text.color("&7Левый вход: &f" + r.left.getType().name()));
            lore.add(Text.color("&7Правый вход: &f" + r.right.getType().name()));
            lore.add(" ");
            lore.add(Text.color("§x§f§f§e§2§4§7&lЛКМ &7— &aРедактировать"));
            lore.add(Text.color("§x§f§0§5§0§5§0&lПКМ &7— &cУдалить"));
            im.setLore(lore);
            im.getPersistentDataContainer().set(RID, PersistentDataType.STRING, r.id);
            im.getPersistentDataContainer().set(TYP, PersistentDataType.STRING, "anv");
            it.setItemMeta(im);
            inv.setItem(VIEW_SLOTS[idx++], it);
        }
        inv.setItem(SLOT_PREV, navItem("prev"));
        inv.setItem(SLOT_CLOSE, closeItem());
        inv.setItem(SLOT_NEXT, navItem("next"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        boolean isWB = Text.plain(title).equals(Text.plain(plugin.titleBrowseWorkbench()));
        boolean isAN = Text.plain(title).equals(Text.plain(plugin.titleBrowseAnvil()));
        if (!isWB && !isAN) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        Inventory top = e.getView().getTopInventory();
        if (e.getRawSlot() >= top.getSize()) return;
        e.setCancelled(true);

        int raw = e.getRawSlot();
        ItemStack it = e.getCurrentItem();
        String nav = null;
        if (it != null) {
            ItemMeta im = it.getItemMeta();
            if (im != null) nav = im.getPersistentDataContainer().get(NAV, PersistentDataType.STRING);
        }
        if (nav == null) {
            if (raw == SLOT_PREV) nav = "prev";
            else if (raw == SLOT_NEXT) nav = "next";
            else if (raw == SLOT_CLOSE) nav = "close";
        }
        if (nav != null) {
            int cur = pages.getOrDefault(p.getUniqueId(), 1);
            if (nav.equals("prev")) {
                cur = Math.max(1, cur - 1);
                if (isWB) openWorkbench(p, cur); else openAnvil(p, cur);
                return;
            }
            if (nav.equals("next")) {
                cur = cur + 1;
                if (isWB) openWorkbench(p, cur); else openAnvil(p, cur);
                return;
            }
            if (nav.equals("close")) {
                p.closeInventory();
                return;
            }
        }

        if (it == null || it.getType() == Material.AIR) return;
        ItemMeta im = it.getItemMeta();
        if (im == null) return;
        String id = im.getPersistentDataContainer().get(RID, PersistentDataType.STRING);
        String tp = im.getPersistentDataContainer().get(TYP, PersistentDataType.STRING);
        if (id == null || tp == null) return;

        if (tp.equals("wb")) {
            if (e.isLeftClick()) {
                WorkbenchRecipe r = storage.getWorkbenchRecipe(id);
                if (r == null) return;
                plugin.recipeMenu().openEditorForEdit(p, id, r.ingredients, r.result, r.shaped);
            } else if (e.isRightClick()) {
                storage.deleteWorkbenchRecipe(id);
                openWorkbench(p, pages.getOrDefault(p.getUniqueId(), 1));
            }
            return;
        }
        if (tp.equals("anv")) {
            if (e.isLeftClick()) {
                AnvilRecipe r = storage.getAnvilRecipe(id);
                if (r == null) return;
                plugin.anvilMenu().openEditorForEdit(p, r.left, r.right, r.result, r.cost, id);
            } else if (e.isRightClick()) {
                storage.deleteAnvilRecipe(id);
                openAnvil(p, pages.getOrDefault(p.getUniqueId(), 1));
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        String title = e.getView().getTitle();
        boolean isWB = Text.plain(title).equals(Text.plain(plugin.titleBrowseWorkbench()));
        boolean isAN = Text.plain(title).equals(Text.plain(plugin.titleBrowseAnvil()));
        if (!isWB && !isAN) return;
        pages.remove(e.getPlayer().getUniqueId());
    }

    private void fillFrame(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ItemUtil.named(Material.BLACK_STAINED_GLASS_PANE, " "));
    }

    private ItemStack navItem(String action) {
        ItemStack it = action.equals("prev") ? ItemUtil.named(Material.ARROW, Text.color("&e← Предыдущая")) : ItemUtil.named(Material.ARROW, Text.color("&eСледующая →"));
        ItemMeta im = it.getItemMeta();
        im.getPersistentDataContainer().set(NAV, PersistentDataType.STRING, action);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack closeItem() {
        ItemStack it = ItemUtil.named(Material.BARRIER, Text.color("&cЗакрыть"));
        ItemMeta im = it.getItemMeta();
        im.getPersistentDataContainer().set(NAV, PersistentDataType.STRING, "close");
        it.setItemMeta(im);
        return it;
    }
}