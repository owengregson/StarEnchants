package feature.summon;

import engine.sink.GuardianCasts;
import engine.sink.PetSummons;
import engine.sink.SummonFlags;
import feature.trigger.TriggerDispatch;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * The {@link SummonFlags#PHASE_STRIKE} rung: an owned summon that lands a MELEE hit on a player spends the hit
 * on its owner's {@code IMPACT} abilities instead of its own damage — the one-shot courier. The rung fires
 * IMPACT, not {@code SUMMON_PAYLOAD}, so this sits beside {@link SummonPayloadListener} rather than inside it:
 * it needs the trigger dispatcher, not the payload seam.
 *
 * <p>Melee-only falls out of the lookup: a projectile the summon fired arrives as the damager, and no arrow is
 * in the registry. {@code payload-consume} despawns the summon on that hit, and the registries are forgotten
 * BEFORE the removal — the {@code PetSummonListener} fuse rule ("one fuse per summon"), which here is the
 * once-only guard: a second delivery of the same hit finds nothing armed and cannot pay the payload twice.
 *
 * <p>Runs at {@link EventPriority#HIGHEST} because it must be able to CANCEL ({@code PetSummonListener}'s
 * MONITOR cannot), and last so that every damage-modifying plugin has already had the event — the damage
 * carried into IMPACT is then the one that would really have landed. {@code ignoreCancelled} keeps a courier
 * from being spent on a hit a protection plugin already blocked. The event fires on the summon's own region
 * thread, co-region with what it hit, so the removal and the dispatch are both region-correct inline.
 */
public final class SummonStrikeListener implements Listener {

    private final TriggerDispatch dispatch;
    private final BooleanSupplier enabled;

    public SummonStrikeListener(TriggerDispatch dispatch, BooleanSupplier enabled) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
        this.enabled = Objects.requireNonNull(enabled, "enabled");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStrike(EntityDamageByEntityEvent event) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        Entity summon = event.getDamager();
        UUID id = summon.getUniqueId();
        SummonFlags flags = PetSummons.flags(id);
        if (flags == null || !flags.payloadOn(SummonFlags.PHASE_STRIKE)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        UUID ownerId = GuardianCasts.owner(id);
        Player owner = ownerId == null ? null : Bukkit.getPlayer(ownerId);
        if (owner == null) {
            return; // no actor to run IMPACT: the summon keeps its own hit and stays armed for the owner's return
        }
        double carried = event.getDamage(); // read before the cancel, so IMPACT sees what would have landed
        if (flags.payloadCancel()) {
            event.setCancelled(true);
        }
        if (flags.payloadConsume()) {
            PetSummons.forget(id);
            GuardianCasts.forget(id); // registries before the removal, the summon-path invariant
            summon.remove();
        }
        dispatch.fireImpact(owner, victim, carried);
    }
}
