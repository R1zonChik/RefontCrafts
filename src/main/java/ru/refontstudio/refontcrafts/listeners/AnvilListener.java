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
import ru.refontstudio.refontcrafts.util.Text;

public class AnvilListener implements Listener {
    private final RefontCrafts plugin;
    private final RecipeStorage storage;

    public AnvilListener(RefontCrafts plugin, RecipeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        ItemStack a = e.getInventory().getItem(0);
        ItemStack b = e.getInventory().getItem(1);
        if (a == null || b == null || a.getType() == Material.AIR || b.getType() == Material.AIR) return;
        for (AnvilRecipe r : storage.getAnvilRecipes()) {
            boolean ok = plugin.exactMeta()
                    ? ItemUtil.similarExact(a, ItemUtil.cloneWithAmount(r.left, a.getAmount())) && ItemUtil.similarExact(b, ItemUtil.cloneWithAmount(r.right, b.getAmount()))
                    : ItemUtil.similarType(a, r.left) && ItemUtil.similarType(b, r.right);
            if (!ok) continue;
            if (a.getAmount() < r.left.getAmount() || b.getAmount() < r.right.getAmount()) continue;
            e.setResult(r.result.clone());
            e.getInventory().setRepairCost(r.cost);
            return;
        }
    }

    @EventHandler
    public void onTake(InventoryClickEvent e) {
        if (e.getInventory().getType() != InventoryType.ANVIL) return;
        if (e.getRawSlot() != 2) return;
        if (!(e.getWhoClicked() instanceof Player)) return;
        AnvilInventory inv = (AnvilInventory) e.getInventory();
        ItemStack result = inv.getItem(2);
        if (result == null || result.getType() == Material.AIR) return;

        ItemStack a = inv.getItem(0);
        ItemStack b = inv.getItem(1);
        AnvilRecipe match = null;

        for (AnvilRecipe r : storage.getAnvilRecipes()) {
            boolean ok = plugin.exactMeta()
                    ? ItemUtil.similarExact(a, ItemUtil.cloneWithAmount(r.left, a == null ? 1 : a.getAmount())) && ItemUtil.similarExact(b, ItemUtil.cloneWithAmount(r.right, b == null ? 1 : b.getAmount()))
                    : ItemUtil.similarType(a, r.left) && ItemUtil.similarType(b, r.right);
            if (!ok) continue;
            if (a == null || b == null) continue;
            if (a.getAmount() < r.left.getAmount() || b.getAmount() < r.right.getAmount()) continue;
            if (!result.isSimilar(r.result)) continue;
            match = r;
            break;
        }

        if (match == null) return;

        e.setCancelled(true);
        Player p = (Player) e.getWhoClicked();
        int cost = inv.getRepairCost();
        if (cost > 0 && p.getLevel() < cost) {
            p.sendMessage(Text.color(plugin.prefix() + "&cНедостаточно уровней: нужно &f" + cost));
            return;
        }

        ItemStack give = result.clone();
        if (e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT) {
            int can = p.getInventory().firstEmpty() == -1 ? 0 : 1;
            if (can == 0) {
                p.sendMessage(Text.color(plugin.prefix() + "&cНет места в инвентаре."));
                return;
            }
        }
        p.getInventory().addItem(give);

        ItemStack a2 = a.clone();
        ItemStack b2 = b.clone();
        a2.setAmount(a2.getAmount() - match.left.getAmount());
        b2.setAmount(b2.getAmount() - match.right.getAmount());
        inv.setItem(0, a2.getAmount() <= 0 ? null : a2);
        inv.setItem(1, b2.getAmount() <= 0 ? null : b2);
        inv.setRepairCost(match.cost);
        inv.setItem(2, null);

        if (cost > 0) p.setLevel(p.getLevel() - cost);
        p.updateInventory();
    }
}