package feature.combat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Feeds each direct melee hit to {@link RageStacksService} (§3). {@code MONITOR} + {@code ignoreCancelled} so it
 * runs AFTER {@link CombatDispatch} has advanced the combo streak (HIGH) and only for a hit that landed. Only a
 * DIRECT player damager is handled: rage is a melee ATTACK enchant, so the attacker is co-located with the victim
 * and the attacker-directed fx are in-region on the firing thread (Folia) — a projectile hit carries no rage.
 */
public final class RageStacksListener implements Listener {

    private final RageStacksService service;

    public RageStacksListener(RageStacksService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        if (!(victim instanceof LivingEntity)) {
            return;
        }
        if (!(event.getDamager() instanceof Player attacker) || attacker == victim) {
            return; // not a direct player melee (or self-inflicted) — no rage stacks
        }
        service.onHit(attacker);
    }
}
