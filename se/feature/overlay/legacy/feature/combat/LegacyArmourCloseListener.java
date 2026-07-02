package feature.combat;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import platform.sched.Scheduling;

/**
 * Legacy (1.8.9) armour-change backup for {@link EquipListener} — the era-exclusive {@code overlay/legacy}
 * {@code InventoryCloseEvent} feeder (ADR-0044; §6). The {@code LegacyGearPoll} armour-signature delta catches
 * right-click / dispenser equips, but cannot tell a same-material swap apart; a close event refreshes to cover
 * that. Main-thread only — the 1.8 lane is never Folia; the refresh is still deferred a tick so the inventory
 * has settled.
 */
public final class LegacyArmourCloseListener implements Listener {

    private final EquipListener equip;

    public LegacyArmourCloseListener(EquipListener equip) {
        this.equip = Objects.requireNonNull(equip, "equip");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            Scheduling.onEntityLater(player, 1L, () -> equip.refresh(player));
        }
    }
}
