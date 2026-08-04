package feature.summon;

import engine.sink.TurretCasts;
import feature.trigger.TriggerDispatch;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * The two things a {@code TURRET_RING} needs the event bus for: a shot that STRIKES a body runs the turret
 * owner's {@code IMPACT} abilities on it (the {@code FallingBlockListener} contract — the payload is entirely
 * author-defined), and neither an emplacement's blast nor a shot's ever damages terrain.
 *
 * <p>The strike is claimed ONCE per shot ({@link TurretCasts#claimImpact}): an explosive projectile damages its
 * victim and then its own blast damages them again, and only the first is the hit the ability paid for. The
 * claim runs at {@link EventPriority#MONITOR} on {@code ignoreCancelled}, so IMPACT is never spent on a hit a
 * protection plugin already blocked and the carried damage is the one that would really have landed.
 *
 * <p>Cancelling {@link EntityExplodeEvent} removes the blast's BLOCK damage; the shot's own strike damage has
 * already been dealt by then, so the cancel costs the ability nothing. A shot's registry row is dropped at its
 * blast — that is the end of its life — and the sink's TTL sweep covers the shots that never explode at all.
 * Both handlers run on the exploding/striking entity's own region thread.
 */
public final class TurretListener implements Listener {

    private final TriggerDispatch dispatch;

    public TurretListener(TriggerDispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (!TurretCasts.neverGriefs(id)) {
            return;
        }
        event.setCancelled(true);
        TurretCasts.forgetShot(id); // a shot's blast is the end of it; an emplacement's row lives to its TTL
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity shot = event.getDamager();
        TurretCasts.Impact impact = TurretCasts.claimImpact(shot.getUniqueId());
        if (impact == null) {
            return; // not one of ours, already spent, or fired by nobody — nothing to run IMPACT against
        }
        Player owner = Bukkit.getPlayer(impact.owner());
        if (owner != null) {
            // Scoped to the group that armed the RING (ADR-0074), which the shot carried from its emplacement.
            dispatch.fireImpact(owner, victim, event.getDamage(), impact.sourceGroup());
        }
    }
}
