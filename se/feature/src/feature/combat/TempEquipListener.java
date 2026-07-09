package feature.combat;

import engine.sink.TempEquip;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import platform.item.Inventories;

/**
 * Keeps a temporary {@code EQUIP_SWAP} (spooky's pumpkin helmet) from letting the victim keep BOTH the real
 * piece and a minted copy (F06/F22) or lose the real piece outright (F21). It is keepInventory-aware: it deletes
 * the one minted placeholder exactly once (from the drops snapshot AND the live inventory) and returns the real
 * piece exactly once, choosing the drops or the kept inventory by the death's keep state.
 *
 * <ul>
 *   <li><b>death</b> {@code @LOWEST} — before the scroll/keep listeners read: reclaim the placeholder, place the
 *       real piece (to drops when it drops, to the kept inventory when it is kept);</li>
 *   <li><b>death</b> {@code @MONITOR} — reconcile if a later handler flipped keepInventory on after we staged the
 *       real piece into the drops (it would otherwise both drop and be kept — a dupe);</li>
 *   <li><b>quit</b> — restore the worn slot / reclaim the placeholder so the saved inventory keeps the real
 *       piece and never a minted pumpkin.</li>
 * </ul>
 *
 * <p>The timed revert in the Sink covers the normal case; this covers the death/logout edges. Both death
 * handlers run on the dying player's own region thread (same event dispatch), so the reconcile map needs no
 * cross-thread coordination and is always drained at MONITOR.
 */
public final class TempEquipListener implements Listener {

    /** A real piece staged into the drops at LOWEST, and whether it was also restored into the worn slot. */
    private record Staged(ItemStack real, boolean inArmor) {
    }

    /** Per-death staging, drained at MONITOR; only populated when a real piece was added to the drops. */
    private final Map<UUID, List<Staged>> staged = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID id = player.getUniqueId();
        Map<Integer, TempEquip.Swap> active = TempEquip.active(id);
        if (active.isEmpty()) {
            return;
        }
        boolean keep = event.getKeepInventory();
        ItemStack[] armor = player.getInventory().getArmorContents();
        List<Staged> pending = new ArrayList<>();
        for (Integer slot : new ArrayList<>(active.keySet())) {
            TempEquip.Swap swap = TempEquip.end(id, slot);
            if (swap == null || slot < 0 || slot >= armor.length) {
                continue;
            }
            Material placeholder = swap.placeholder();
            // Delete the minted placeholder exactly once: from the drops snapshot (matters when it drops) and
            // from the live inventory (matters when it is kept). Both are safe — drops are detached copies.
            decrementOne(event.getDrops(), placeholder);
            player.getInventory().removeItem(new ItemStack(placeholder, 1));

            ItemStack real = TempEquip.isAir(swap.original()) ? null : swap.original();
            if (real == null) {
                continue;
            }
            ItemStack inSlot = armor[slot];
            boolean slotFree = inSlot == null || inSlot.getType() == placeholder || TempEquip.isAir(inSlot);
            if (keep) {
                // Kept inventory persists: give the real piece back, never to the drops (it would dupe).
                if (slotFree) {
                    armor[slot] = real;
                } else {
                    Inventories.giveOrDrop(player, real); // re-equipped with something else — don't clobber it
                }
            } else {
                // Drops: the real piece drops as usual so the scroll/keep listeners at HIGH see it there.
                if (slotFree) {
                    armor[slot] = real;
                }
                event.getDrops().add(real);
                pending.add(new Staged(real, slotFree));
            }
        }
        player.getInventory().setArmorContents(armor);
        if (!pending.isEmpty()) {
            staged.put(id, pending);
        }
    }

    /**
     * If a later handler (a grave / region / keep plugin at HIGHEST) flipped keepInventory on AFTER we staged the
     * real piece into the drops, the server keeps the whole live inventory (which holds the piece we put in the
     * worn slot) and would ALSO spawn the drops copy — a dupe. Pull our staged instances back out of the drops by
     * identity, and hand back any that were not restored into the worn slot so the kept player still receives them.
     *
     * <p>The reverse flip (keep true at LOWEST, flipped false later) is deliberately not auto-repaired: an
     * identity-blind re-add to the drops could duplicate an item a scroll stashed at HIGH, so the worn-slot copy
     * is simply lost to the normal inventory clear in that obscure double-flip case.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeathReconcile(PlayerDeathEvent event) {
        List<Staged> pending = staged.remove(event.getEntity().getUniqueId());
        if (pending == null || !event.getKeepInventory()) {
            return; // nothing staged, or keep stayed false — the drops are correct as staged
        }
        List<ItemStack> drops = event.getDrops();
        for (Staged item : pending) {
            drops.removeIf(drop -> drop == item.real()); // identity: only our staged instance, not a look-alike
            if (!item.inArmor()) {
                Inventories.giveOrDrop(event.getEntity(), item.real());
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Map<Integer, TempEquip.Swap> active = TempEquip.active(id);
        if (active.isEmpty()) {
            return;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (Integer slot : new ArrayList<>(active.keySet())) {
            TempEquip.Swap swap = TempEquip.end(id, slot);
            if (swap == null || slot < 0 || slot >= armor.length) {
                continue;
            }
            Material placeholder = swap.placeholder();
            ItemStack real = TempEquip.isAir(swap.original()) ? null : swap.original();
            ItemStack inSlot = armor[slot];
            if (inSlot != null && inSlot.getType() == placeholder) {
                armor[slot] = real; // still our placeholder — swap it straight back
                continue;
            }
            // The victim relocated the pumpkin before quitting: reclaim it, then return the real piece.
            player.getInventory().removeItem(new ItemStack(placeholder, 1));
            if (real == null) {
                continue;
            }
            if (inSlot == null || TempEquip.isAir(inSlot)) {
                armor[slot] = real; // the slot is free again — restore it to its natural home
            } else {
                Inventories.giveOrDrop(player, real); // re-equipped with something else — don't clobber it
            }
        }
        player.getInventory().setArmorContents(armor);
    }

    /** Remove one item of {@code placeholder} material from {@code drops} (the single minted pumpkin, no more). */
    private static void decrementOne(List<ItemStack> drops, Material placeholder) {
        for (Iterator<ItemStack> it = drops.iterator(); it.hasNext();) {
            ItemStack drop = it.next();
            if (drop != null && drop.getType() == placeholder) {
                if (drop.getAmount() <= 1) {
                    it.remove();
                } else {
                    drop.setAmount(drop.getAmount() - 1);
                }
                return;
            }
        }
    }
}
