package ru.refontstudio.refontcrafts.listeners;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import ru.refontstudio.refontcrafts.RefontCrafts;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorkbenchListener implements Listener {
    private final RefontCrafts plugin;

    public WorkbenchListener(RefontCrafts plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() == null) return;
        if (!(e.getRecipe() instanceof ShapelessRecipe)) return;
        ShapelessRecipe sr = (ShapelessRecipe) e.getRecipe();

        NamespacedKey key = sr.getKey();
        if (key == null || !key.getNamespace().equalsIgnoreCase(plugin.getName())) return;

        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null || matrix.length == 0) return;

        List<ItemStack> req = extractRequirements(sr);
        if (req.isEmpty()) return;

        int possible = computeSetsPossible(req, matrix, plugin.exactMeta());
        if (possible <= 0) {
            inv.setResult(null);
            return;
        }

        ItemStack base = sr.getResult().clone();
        int perSet = Math.max(1, base.getAmount());
        int maxStack = Math.max(1, base.getMaxStackSize());
        int previewSets = Math.min(possible, Math.max(1, maxStack / perSet));
        base.setAmount(perSet * previewSets);
        inv.setResult(base);
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (e.getRecipe() == null) return;
        if (!(e.getRecipe() instanceof ShapelessRecipe)) return;
        ShapelessRecipe sr = (ShapelessRecipe) e.getRecipe();

        NamespacedKey key = sr.getKey();
        if (key == null || !key.getNamespace().equalsIgnoreCase(plugin.getName())) return;
        if (!(e.getWhoClicked() instanceof Player)) return;

        Player p = (Player) e.getWhoClicked();
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null || matrix.length == 0) return;

        List<ItemStack> req = extractRequirements(sr);
        if (req.isEmpty()) return;

        int possible = computeSetsPossible(req, matrix, plugin.exactMeta());
        if (possible <= 0) {
            e.setCancelled(true);
            inv.setResult(null);
            p.updateInventory();
            return;
        }

        ItemStack base = sr.getResult().clone();
        int perSet = Math.max(1, base.getAmount());
        int maxStack = Math.max(1, base.getMaxStackSize());

        boolean shift = e.isShiftClick();
        int setsWanted;
        if (shift) {
            int cap = capacityForItem(p.getInventory(), base);
            int byInv = Math.max(0, cap / perSet);
            setsWanted = Math.min(possible, byInv);
            if (setsWanted <= 0) {
                e.setCancelled(true);
                p.sendMessage(plugin.msg("no_inventory_space"));
                return;
            }
        } else {
            int previewSets = Math.min(possible, Math.max(1, maxStack / perSet));
            setsWanted = previewSets;
        }

        int[] left = simulateConsume(req, matrix, plugin.exactMeta(), setsWanted);
        if (left == null) {
            e.setCancelled(true);
            inv.setResult(null);
            p.updateInventory();
            return;
        }

        int totalItems = perSet * setsWanted;
        ItemStack out = base.clone();
        out.setAmount(totalItems);

        e.setCancelled(true);

        if (shift) {
            Map<Integer, ItemStack> rem = p.getInventory().addItem(out);
            if (!rem.isEmpty()) {
                int given = totalItems;
                int back = 0;
                for (ItemStack r : rem.values()) if (r != null) back += r.getAmount();
                int accepted = Math.max(0, given - back);
                int setsAccepted = accepted / perSet;
                left = simulateConsume(req, matrix, plugin.exactMeta(), setsAccepted);
                if (left == null || setsAccepted <= 0) {
                    p.sendMessage(plugin.msg("no_inventory_space"));
                    return;
                }
                totalItems = accepted;
            }
        } else {
            ItemStack cursor = p.getItemOnCursor();
            int canOnCursor = 0;
            if (cursor == null || cursor.getType() == Material.AIR) {
                canOnCursor = maxStack;
            } else if (cursor.isSimilar(base)) {
                canOnCursor = Math.max(0, maxStack - cursor.getAmount());
            } else {
                canOnCursor = 0;
            }
            int putOnCursor = Math.min(totalItems, canOnCursor);
            if (putOnCursor > 0) {
                ItemStack nc = base.clone();
                nc.setAmount((cursor == null || cursor.getType() == Material.AIR) ? putOnCursor : cursor.getAmount() + putOnCursor);
                if (cursor == null || cursor.getType() == Material.AIR) {
                    ItemStack toSet = base.clone();
                    toSet.setAmount(putOnCursor);
                    p.setItemOnCursor(toSet);
                } else {
                    cursor.setAmount(cursor.getAmount() + putOnCursor);
                    p.setItemOnCursor(cursor);
                }
            }
            int rest = totalItems - putOnCursor;
            if (rest > 0) {
                Map<Integer, ItemStack> rem = p.getInventory().addItem(cloneWithAmount(base, rest));
                if (!rem.isEmpty()) {
                    int back = 0;
                    for (ItemStack r : rem.values()) if (r != null) back += r.getAmount();
                    int accepted = Math.max(0, rest - back) + putOnCursor;
                    int setsAccepted = accepted / perSet;
                    left = simulateConsume(req, matrix, plugin.exactMeta(), setsAccepted);
                    if (left == null || setsAccepted <= 0) {
                        p.sendMessage(plugin.msg("no_inventory_space"));
                        return;
                    }
                    totalItems = accepted;
                }
            }
        }

        ItemStack[] newMatrix = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            if (left[i] <= 0) {
                newMatrix[i] = null;
            } else {
                ItemStack c = matrix[i].clone();
                c.setAmount(left[i]);
                newMatrix[i] = c;
            }
        }
        inv.setMatrix(newMatrix);
        inv.setResult(null);
        p.updateInventory();
    }

    private List<ItemStack> extractRequirements(ShapelessRecipe sr) {
        List<ItemStack> req = new ArrayList<>();
        try {
            List<ItemStack> ing = sr.getIngredientList();
            if (ing != null) {
                for (ItemStack it : ing) {
                    if (it == null || it.getType() == Material.AIR) continue;
                    ItemStack one = it.clone();
                    one.setAmount(1);
                    req.add(one);
                }
                if (!req.isEmpty()) return req;
            }
        } catch (Throwable ignored) {}
        try {
            List<RecipeChoice> choices = sr.getChoiceList();
            if (choices != null) {
                for (RecipeChoice ch : choices) {
                    if (ch instanceof RecipeChoice.ExactChoice) {
                        List<ItemStack> list = ((RecipeChoice.ExactChoice) ch).getChoices();
                        if (!list.isEmpty()) {
                            ItemStack one = list.get(0).clone();
                            one.setAmount(1);
                            req.add(one);
                        }
                    } else if (ch instanceof RecipeChoice.MaterialChoice) {
                        List<Material> mats = ((RecipeChoice.MaterialChoice) ch).getChoices();
                        if (!mats.isEmpty()) req.add(new ItemStack(mats.get(0), 1));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return req;
    }

    private int computeSetsPossible(List<ItemStack> req, ItemStack[] matrix, boolean exact) {
        int[] left = new int[matrix.length];
        ItemStack[] items = new ItemStack[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            items[i] = it;
            left[i] = (it == null || it.getType() == Material.AIR) ? 0 : it.getAmount();
        }
        int sets = 0;
        while (true) {
            boolean ok = true;
            for (ItemStack need : req) {
                int idx = findMatchIndex(need, items, left, exact);
                if (idx == -1) { ok = false; break; }
                left[idx]--;
            }
            if (!ok) break;
            sets++;
        }
        return sets;
    }

    private int[] simulateConsume(List<ItemStack> req, ItemStack[] matrix, boolean exact, int sets) {
        int[] left = new int[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            ItemStack it = matrix[i];
            left[i] = (it == null || it.getType() == Material.AIR) ? 0 : it.getAmount();
        }
        for (int s = 0; s < sets; s++) {
            for (ItemStack need : req) {
                int idx = findMatchIndex(need, matrix, left, exact);
                if (idx == -1) return null;
                left[idx]--;
            }
        }
        return left;
    }

    private int findMatchIndex(ItemStack need, ItemStack[] items, int[] left, boolean exact) {
        for (int i = 0; i < items.length; i++) {
            if (left[i] <= 0) continue;
            ItemStack have = items[i];
            if (have == null || have.getType() == Material.AIR) continue;
            if (exact) {
                if (!have.isSimilar(need)) continue;
            } else {
                if (have.getType() != need.getType()) continue;
            }
            return i;
        }
        return -1;
    }

    private int capacityForItem(PlayerInventory inv, ItemStack sample) {
        int cap = 0;
        ItemStack[] cont = inv.getStorageContents();
        int max = sample.getMaxStackSize();
        for (ItemStack it : cont) {
            if (it == null || it.getType() == Material.AIR) {
                cap += max;
            } else if (it.isSimilar(sample)) {
                cap += Math.max(0, max - it.getAmount());
            }
        }
        return cap;
    }

    private ItemStack cloneWithAmount(ItemStack it, int amount) {
        ItemStack c = it.clone();
        c.setAmount(amount);
        return c;
    }
}