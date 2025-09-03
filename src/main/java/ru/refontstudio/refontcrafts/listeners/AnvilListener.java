package ru.refontstudio.refontcrafts.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import ru.refontstudio.refontcrafts.RefontCrafts;
import ru.refontstudio.refontcrafts.storage.RecipeStorage;
import ru.refontstudio.refontcrafts.storage.RecipeStorage.AnvilRecipe;
import ru.refontstudio.refontcrafts.util.ItemUtil;

import java.util.*;

public class AnvilListener implements Listener {
    private final RefontCrafts plugin;
    private final RecipeStorage storage;

    private final Map<Long, List<AnvilRecipe>> index = new HashMap<>();
    private int indexedCount = -1;

    public AnvilListener(RefontCrafts plugin, RecipeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        ItemStack a = e.getInventory().getItem(0);
        ItemStack b = e.getInventory().getItem(1);
        if (a == null || b == null || a.getType() == Material.AIR || b.getType() == Material.AIR) return;

        ensureIndex();
        List<AnvilRecipe> candidates = index.getOrDefault(key(a.getType(), b.getType()), Collections.emptyList());
        if (candidates.isEmpty()) return;

        for (AnvilRecipe r : candidates) {
            boolean ok = plugin.exactMeta()
                    ? ItemUtil.similarExact(a, ItemUtil.cloneWithAmount(r.left, a.getAmount()))
                    && ItemUtil.similarExact(b, ItemUtil.cloneWithAmount(r.right, b.getAmount()))
                    : ItemUtil.similarType(a, r.left) && ItemUtil.similarType(b, r.right);
            if (!ok) continue;

            int needA = Math.max(1, r.left.getAmount());
            int needB = Math.max(1, r.right.getAmount());
            if (a.getAmount() < needA || b.getAmount() < needB) continue;

            int setsPossible = Math.min(a.getAmount() / needA, b.getAmount() / needB);
            if (setsPossible <= 0) continue;

            ItemStack base = r.result.clone();
            int perSet = Math.max(1, base.getAmount());
            int maxStack = Math.max(1, base.getMaxStackSize());
            int previewSets = Math.max(1, Math.min(setsPossible, Math.max(1, maxStack / perSet)));

            ItemStack preview = base.clone();
            preview.setAmount(perSet * previewSets);
            e.setResult(preview);
            e.getInventory().setRepairCost(Math.max(0, r.cost * previewSets));
            return;
        }
    }

    @EventHandler
    public void onTake(InventoryClickEvent e) {
        if (e.getInventory().getType() != InventoryType.ANVIL) return;
        if (e.getRawSlot() != 2) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        AnvilInventory inv = (AnvilInventory) e.getInventory();
        ItemStack resultSlot = inv.getItem(2);
        if (resultSlot == null || resultSlot.getType() == Material.AIR) return;

        ItemStack a = inv.getItem(0);
        ItemStack b = inv.getItem(1);
        if (a == null || b == null || a.getType() == Material.AIR || b.getType() == Material.AIR) return;

        ensureIndex();
        List<AnvilRecipe> candidates = index.getOrDefault(key(a.getType(), b.getType()), Collections.emptyList());
        if (candidates.isEmpty()) return;

        AnvilRecipe match = null;
        for (AnvilRecipe r : candidates) {
            boolean ok = plugin.exactMeta()
                    ? ItemUtil.similarExact(a, ItemUtil.cloneWithAmount(r.left, a.getAmount()))
                    && ItemUtil.similarExact(b, ItemUtil.cloneWithAmount(r.right, b.getAmount()))
                    : ItemUtil.similarType(a, r.left) && ItemUtil.similarType(b, r.right);
            if (!ok) continue;
            if (!resultSlot.isSimilar(r.result)) continue;
            match = r;
            break;
        }
        if (match == null) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();

        int needA = Math.max(1, match.left.getAmount());
        int needB = Math.max(1, match.right.getAmount());
        int haveA = a.getAmount();
        int haveB = b.getAmount();
        int setsByItems = Math.min(haveA / needA, haveB / needB);
        if (setsByItems <= 0) return;

        int perSet = Math.max(1, match.result.getAmount());
        int setsByXP = match.cost > 0 ? (p.getLevel() / match.cost) : setsByItems;

        int setsWanted;
        if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
            setsWanted = Math.min(setsByItems, setsByXP);
        } else {
            int previewSets = Math.max(1, resultSlot.getAmount() / perSet);
            setsWanted = Math.min(previewSets, Math.min(setsByItems, setsByXP));
        }
        if (setsWanted <= 0) return;

        int desiredItems = perSet * setsWanted;
        ItemStack giveAll = match.result.clone();
        giveAll.setAmount(desiredItems);

        int acceptedItems = desiredItems;
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(giveAll);
        if (!leftover.isEmpty()) {
            int leftAmount = 0;
            for (ItemStack it : leftover.values()) if (it != null) leftAmount += it.getAmount();
            acceptedItems = Math.max(0, desiredItems - leftAmount);
        }
        int setsCrafted = acceptedItems / perSet;
        if (setsCrafted <= 0) {
            p.sendMessage(plugin.msg("no_inventory_space"));
            return;
        }

        int spendA = setsCrafted * needA;
        int spendB = setsCrafted * needB;
        int spendXP = match.cost * setsCrafted;

        ItemStack a2 = a.clone();
        ItemStack b2 = b.clone();
        a2.setAmount(a2.getAmount() - spendA);
        b2.setAmount(b2.getAmount() - spendB);
        inv.setItem(0, a2.getAmount() <= 0 ? null : a2);
        inv.setItem(1, b2.getAmount() <= 0 ? null : b2);

        if (spendXP > 0) p.setLevel(Math.max(0, p.getLevel() - spendXP));

        inv.setItem(2, null);
        inv.setRepairCost(0);
        p.updateInventory();
    }

    private void ensureIndex() {
        int cur = storage.getAnvilRecipes().size();
        if (cur == indexedCount) return;
        index.clear();
        for (AnvilRecipe r : storage.getAnvilRecipes()) {
            long k = key(r.left.getType(), r.right.getType());
            index.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        }
        indexedCount = cur;
    }

    private long key(Material left, Material right) {
        return (((long) left.ordinal()) << 32) | (right.ordinal() & 0xffffffffL);
    }
}