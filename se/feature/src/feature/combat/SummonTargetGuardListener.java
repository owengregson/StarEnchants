package feature.combat;

import engine.sink.GuardianCasts;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * Keeps a {@link GuardianCasts}-owned summon off its own summoner: vanilla AI freely retargets the nearest
 * player, which is frequently the owner standing beside their cast. Fires on the summon's own region thread
 * (Folia-safe); shared-era code (the {@code MobTargetGuard} event, present on 1.8 too). Converted-summon
 * holds are handled elsewhere — this cancels ONLY the summon-targets-owner acquisition.
 */
public final class SummonTargetGuardListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target == null) {
            return; // an un-target (forget) never needs guarding
        }
        UUID owner = GuardianCasts.owner(event.getEntity().getUniqueId());
        if (owner != null && owner.equals(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
