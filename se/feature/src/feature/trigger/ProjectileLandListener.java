package feature.trigger;

import feature.compat.Projectiles;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Fires {@code PROJECTILE_LAND} where a player's projectile comes down. Nothing else observes ground impact —
 * BOW fires at the entity a shot HITS — so a landing-AoE bow ability or a web field had no hook.
 *
 * <p>The landing point (and whether this event is a landing at all) comes from the {@link Projectiles} era
 * seam, which is also where the no-double-dispatch rule lives: an entity hit calls nothing back because
 * BOW/ATTACK already dispatched it. The seam also owns WHEN it answers — modern inline, 1.8 a tick later off
 * the arrow's own entity scheduler, which is why this is a callback. Either way the dispatch runs on the
 * landing region's thread, so the projectile read is Folia-safe; the shooter's worn abilities resolve from the
 * immutable WornState and effects route through the sink.
 */
public final class ProjectileLandListener implements Listener {

    private final TriggerDispatch dispatch;
    private final Projectiles projectiles;

    public ProjectileLandListener(TriggerDispatch dispatch, Projectiles projectiles) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.projectiles = Objects.requireNonNull(projectiles, "projectiles");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLand(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player shooter)) {
            return; // a skeleton's arrow carries nobody's enchants
        }
        projectiles.landing(event, at -> dispatch.fireProjectileLand(shooter, at));
    }
}
