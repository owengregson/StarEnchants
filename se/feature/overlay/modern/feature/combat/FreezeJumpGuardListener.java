package feature.combat;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import engine.sink.FrozenTargets;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Modern no-jump guard ({@code FREEZE no-jump}, R-QC57) — the era-exclusive {@code overlay/modern} source:
 * a victim inside a live window that authored the flag cannot jump out of the root.
 *
 * <p>Paper's {@code PlayerJumpEvent} is cancellable and predates the 1.17.1 floor, so the whole feature is
 * one cancel on an event that already knows a jump was attempted — no per-tick work, no velocity write, no
 * movement-path cost. That mattered to the mechanism choice: the two substitutes are a {@code PlayerMoveEvent}
 * velocity test, which rubber-bands the victim and pays for itself on the hottest event the server has, and a
 * per-tick velocity write, which jitters. The 1.8.9 overlay builds against CraftBukkit and has no such event,
 * so its binding is inert ({@code NoopListener}) and a legacy freeze keeps its DoT and slow while the victim
 * can still hop — the same recorded degrade the powder-snow visual takes there.
 *
 * <p>The amplifier-128 Jump Boost trick the source used is ruled out pack-wide: it is a 1.8-only integer
 * overflow, and on the modern lane that amplifier is a real and enormous jump that would fling the very
 * targets the freeze exists to pin.
 *
 * <p>Folia-correct: the event fires on the player's own region thread, and the read is one concurrent map
 * lookup.
 */
public final class FreezeJumpGuardListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (FrozenTargets.blocksJump(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
