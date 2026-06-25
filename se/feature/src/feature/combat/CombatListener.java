package feature.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Bridges {@link EntityDamageByEntityEvent} to {@link CombatDispatch} (§3.7). {@code HIGH} +
 * {@code ignoreCancelled} so SE folds its damage after the base calculation and skips a cancelled hit.
 */
public final class CombatListener implements Listener {

    private final CombatDispatch dispatch;

    public CombatListener(CombatDispatch dispatch) {
        this.dispatch = dispatch;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        dispatch.onDamage(event);
    }
}
