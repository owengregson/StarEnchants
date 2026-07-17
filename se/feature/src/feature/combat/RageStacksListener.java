package feature.combat;

import engine.sink.EngineDamage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Feeds each direct melee hit to {@link RageStacksService} (§3). {@code MONITOR} + {@code ignoreCancelled} so it
 * runs AFTER {@link CombatDispatch} has advanced the combo streak (HIGH) and only for a hit that landed. Two sides
 * of one event: a DIRECT player damager BUILDS that attacker's run ({@link RageStacksService#onHit} — the
 * attacker is co-located with the victim, so the attacker-directed fx are in-region); a player VICTIM BREAKS its
 * own run ({@link RageStacksService#onHitTaken} — from any damager, since rage is a combo kept by not being hit,
 * ADR-0058). A projectile hit carries no rage on the attack side but still breaks the victim's run.
 */
public final class RageStacksListener implements Listener {

    private final RageStacksService service;

    public RageStacksListener(RageStacksService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (EngineDamage.active()) {
            return; // engine-issued damage (DoT ticks, reflects — ADR-0054): rage is for real melee swings only
        }
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity)) {
            return;
        }
        boolean selfInflicted = event.getDamager() == victim;
        // The dispatch's §3.7 duplicate skip (a same-swing "damage the difference" re-hit) keeps rage
        // single-advance too; the break side stays unguarded — re-breaking a broken run is a no-op.
        if (event.getDamager() instanceof Player attacker && !selfInflicted && !ReHitGuard.skipped(event)) {
            service.onHit(attacker); // attack side: build/advance the attacker's run
        }
        if (victim instanceof Player defender && !selfInflicted) {
            service.onHitTaken(defender); // defense side: taking a hit breaks the victim's own run
        }
    }
}
