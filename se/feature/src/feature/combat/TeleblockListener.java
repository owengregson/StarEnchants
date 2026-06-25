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
 * The {@code TELEBLOCK} applier (§ combat-flags): cancels a teleport-blocked player's ender-pearl launch
 * and chorus/pearl teleport. The effect arms it through the per-event sink; this reads it back on the
 * SEPARATE launch/teleport events, on the player's own region thread (concurrent store, UUID-keyed —
 * Folia-safe). A faithful port of a Cosmic Enchants-style Teleblock, extended to chorus-fruit teleport.
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
