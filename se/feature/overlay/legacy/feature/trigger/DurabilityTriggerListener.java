package feature.trigger;

import engine.run.ActivationContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import platform.sched.Scheduling;

/**
 * Legacy (1.8.9) ITEM_DAMAGE trigger (docs/legacy-1.8.9-codeshare-design.md §6, Item 3). 1.8 has no
 * {@code PlayerItemDamageEvent}, so durability loss is observed by a per-tick poll of each player's held +
 * worn DAMAGEABLE items: 1.8 stores durability as a damage value that rises toward the item's max as it wears,
 * so a rise (same item, no swap) is a durability hit → fire ITEM_DAMAGE. Same-FQN counterpart to the modern
 * event listener; the NMS-free degrade restored to parity. Polling is main-thread only — the 1.8 lane is never
 * Folia, so no scheduling hops are needed (the maps are concurrent purely for defensiveness).
 *
 * <p>Registered before {@link feature.heroic.HeroicDurabilityListener} (StarEnchantsPlugin order), so its poll
 * runs first each tick and ITEM_DAMAGE fires on the loss BEFORE a heroic save restores it — matching the
 * modern order (ITEM_DAMAGE at HIGH fires regardless of the heroic cancel).
 */
public final class DurabilityTriggerListener implements Listener {

    /** Slots polled per player: index 0 = held, 1..4 = the four armour pieces. */
    private static final int SLOTS = 5;

    private final TriggerDispatch dispatch;
    private final Map<UUID, int[]> lastType = new ConcurrentHashMap<>();   // Material ordinal per slot, −1 = empty
    private final Map<UUID, short[]> lastDamage = new ConcurrentHashMap<>(); // durability (damage value) per slot

    public DurabilityTriggerListener(TriggerDispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        Scheduling.repeatingGlobal(1L, 1L, this::poll);
    }

    private void poll() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
            scan(player);
        }
        lastType.keySet().retainAll(online); // forget players who logged off
        lastDamage.keySet().retainAll(online);
    }

    private void scan(Player player) {
        UUID id = player.getUniqueId();
        PlayerInventory inv = player.getInventory();
        ItemStack[] slots = slots(inv);
        int[] types = lastType.computeIfAbsent(id, k -> emptyTypes());
        short[] dmg = lastDamage.computeIfAbsent(id, k -> new short[SLOTS]);
        for (int i = 0; i < SLOTS; i++) {
            ItemStack item = slots[i];
            short max = item == null ? 0 : item.getType().getMaxDurability();
            int type = item == null ? -1 : item.getType().ordinal();
            short dur = (item == null || max <= 0) ? 0 : item.getDurability();
            // Same item still in the slot AND its damage value rose → a durability hit. A type change is a
            // swap (not a hit); a fall is a repair/replace. Only damageable items (max > 0) carry durability.
            if (type == types[i] && max > 0 && dur > dmg[i]) {
                dispatch.fire(player, dispatch.itemDamage,
                        new ActivationContext(player, null, null, player.getLocation()), null);
            }
            types[i] = type;
            dmg[i] = dur;
        }
    }

    private static ItemStack[] slots(PlayerInventory inv) {
        ItemStack[] armour = inv.getArmorContents();
        ItemStack[] all = new ItemStack[SLOTS];
        all[0] = inv.getItemInHand();
        System.arraycopy(armour, 0, all, 1, Math.min(armour.length, SLOTS - 1));
        return all;
    }

    private static int[] emptyTypes() {
        int[] a = new int[SLOTS];
        Arrays.fill(a, -1);
        return a;
    }
}
