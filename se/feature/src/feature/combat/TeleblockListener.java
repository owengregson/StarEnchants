package feature.combat;

import engine.stores.TeleblockStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * The {@code TELEBLOCK} applier (§ combat-flags): while a player is teleport-blocked in the
 * {@link TeleblockStore}, their ender-pearl launch and chorus/pearl teleport are cancelled. The block is
 * armed by the effect through the per-event sink on the hit; this reads it back on the SEPARATE
 * launch/teleport events. Both handlers run on the player's own region thread (the event's), reading only
 * the concurrent store by UUID — Folia-safe with no cross-region access. A faithful port of EE's Teleblock
 * (which cancelled the ender-pearl {@code ProjectileLaunchEvent}), extended to also stop chorus-fruit teleport.
 */
public final class TeleblockListener implements Listener {

    private final TeleblockStore store;
    private final LongSupplier nowTicks;

    public TeleblockListener(TeleblockStore store, LongSupplier nowTicks) {
        this.store = Objects.requireNonNull(store, "store");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof EnderPearl pearl
                && pearl.getShooter() instanceof Player shooter
                && store.isBlocked(shooter.getUniqueId(), nowTicks.getAsLong())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        boolean teleportMove = cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL;
        if (teleportMove && store.isBlocked(event.getPlayer().getUniqueId(), nowTicks.getAsLong())) {
            event.setCancelled(true);
        }
    }
}
