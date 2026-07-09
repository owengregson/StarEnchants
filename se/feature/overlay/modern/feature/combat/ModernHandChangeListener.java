package feature.combat;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import platform.sched.Scheduling;

/**
 * Modern (1.9+) hand-change feeder for {@link EquipListener} — the era-exclusive {@code overlay/modern} source
 * (ADR-0044; G02/F09) for the two off-hand mutations that fire no inventory-close: the F-key hand SWAP outside the
 * screen ({@code PlayerSwapHandItemsEvent}, absent on 1.8.9) and a drop+repickup into an empty main hand
 * ({@code EntityPickupItemEvent}). 1.8 has no off-hand, so its binding is inert.
 */
public final class ModernHandChangeListener implements Listener {

    private final EquipListener equip;

    public ModernHandChangeListener(EquipListener equip) {
        this.equip = Objects.requireNonNull(equip, "equip");
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        // The swap applies only after this event returns — refresh next tick, same pattern as the held-slot change.
        Player player = event.getPlayer();
        Scheduling.onEntityLater(player, 1L, () -> equip.refresh(player));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        // Only a pickup INTO an empty main hand restores held-item abilities that no hotbar scroll would; the guard
        // keeps mob-farm pickups (main hand already full) from churning refreshes.
        if (event.getEntity() instanceof Player player
                && player.getInventory().getItemInMainHand().getType().isAir()) {
            Scheduling.onEntityLater(player, 1L, () -> equip.refresh(player));
        }
    }
}
