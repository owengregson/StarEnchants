package feature.combat;

import engine.sink.TimedRevert;
import java.util.Objects;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Drains a quitting player's outstanding timed-buff reverts (temporary FLY / INVINCIBLE / MOVEMENT_SPEED and the
 * max-health drain give-back) so a logout mid-window can no longer strand the flag on disk (F07/F08).
 *
 * <p>The timed revert in the Sink covers the normal case; this covers the logout edge. Like
 * {@link TempEquipListener}, it relies on {@code PlayerQuitEvent} firing BEFORE the quit-time playerdata save on
 * both eras and, on Folia, on the player's owning region thread — so the reverted mayfly / walkSpeed /
 * Invulnerable / max-health base is exactly what gets persisted. The claimed token in {@link TimedRevert} makes
 * the later expiry timer a no-op instead of a mutation on the disconnected player.
 */
public final class TimedRevertListener implements Listener {

    private final TimedRevert reverts;

    public TimedRevertListener(TimedRevert reverts) {
        this.reverts = Objects.requireNonNull(reverts, "reverts");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        reverts.revertAll(event.getPlayer().getUniqueId());
    }
}
