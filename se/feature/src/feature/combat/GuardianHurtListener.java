package feature.combat;

import engine.sink.GuardianCasts;
import feature.trigger.TriggerDispatch;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Fires the {@code GUARDIAN_HURT} trigger when a summoned guardian tracked in {@link GuardianCasts} takes damage
 * (ADR-0049 Blood Link): resolves the owner and runs their GUARDIAN_HURT abilities on the guardian via
 * {@link TriggerDispatch#fireGuardianHurt}. The event fires on the guardian's own region thread, co-region with
 * the guardian, so the guardian read is Folia-safe; the owner's worn abilities resolve from the immutable
 * WornState (a safe cross-region read) and any owner mutation goes through the sink intents — the same
 * cross-entity pattern as {@link FallingBlockListener}.
 *
 * <p>{@code ignoreCancelled = true}: a cancelled hit (a plugin voided the damage) fires no Blood Link heal.
 */
public final class GuardianHurtListener implements Listener {

    private final TriggerDispatch dispatch;

    public GuardianHurtListener(TriggerDispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGuardianHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity guardian)) {
            return;
        }
        UUID ownerId = GuardianCasts.owner(guardian.getUniqueId());
        if (ownerId == null) {
            return; // not a tracked guardian
        }
        Player owner = Bukkit.getPlayer(ownerId);
        if (owner != null) {
            dispatch.fireGuardianHurt(owner, guardian, event.getFinalDamage());
        }
    }
}
