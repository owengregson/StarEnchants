package feature.combat;

import compile.load.ContentHolder;
import feature.trigger.LifecycleDriver;
import feature.trigger.PassiveEffectDriver;
import feature.trigger.RepeatingDriver;
import feature.trigger.SetMessageDriver;
import item.worn.WornStateStore;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import platform.sched.Scheduling;

/**
 * Legacy (1.8.9) {@link WornStateStore} refresher — the era leaf over {@link EquipListenerBase}. 1.8 has no
 * Paper {@code PlayerArmorChangeEvent}, so armour changes are caught by a per-tick poll of each player's armour
 * signature (Item 3, docs/legacy-1.8.9-codeshare-design.md §6) — catching right-click / dispenser equips the
 * instant they happen — backed up by {@link InventoryCloseEvent} (covers a same-material swap the signature
 * can't tell apart). The join / held-item / respawn / quit lifecycle and the refresh pipeline are shared.
 * Polling is main-thread only (1.8 is never Folia). Same-FQN counterpart to the {@code overlay/modern} listener.
 */
public final class EquipListener extends EquipListenerBase {

    /** Per-player armour signature (material + count of the 4 pieces), last seen by {@link #pollArmour}. */
    private final Map<UUID, String> lastArmour = new ConcurrentHashMap<>();

    public EquipListener(WornStateStore worn, ContentHolder content, RepeatingDriver repeating,
                         LifecycleDriver lifecycle, PassiveEffectDriver passiveEffects, SetMessageDriver setMessages) {
        super(worn, content, repeating, lifecycle, passiveEffects, setMessages);
        Scheduling.repeatingGlobal(1L, 1L, this::pollArmour);
    }

    private void pollArmour() {
        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            online.add(id);
            String signature = armourSignature(player);
            String previous = lastArmour.put(id, signature);
            if (previous != null && !previous.equals(signature)) {
                refresh(player); // armour changed without an inventory close (right-click / dispenser equip)
            }
        }
        lastArmour.keySet().retainAll(online); // forget players who logged off
    }

    /** Material + amount of the four armour pieces — changes on equip/unequip/type-swap, not on durability loss. */
    private static String armourSignature(Player player) {
        StringBuilder sb = new StringBuilder(48);
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            sb.append(piece == null ? "-" : piece.getType().name() + "x" + piece.getAmount()).append('|');
        }
        return sb.toString();
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // armour may have changed while the inventory was open; refresh on the player's thread
            Scheduling.onEntityLater(player, 1L, () -> refresh(player));
        }
    }
}
