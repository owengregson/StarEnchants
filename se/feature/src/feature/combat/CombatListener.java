package feature.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Bridges Bukkit's {@link EntityDamageByEntityEvent} to {@link CombatDispatch} (§3.7). Registered at
 * {@code HIGH} priority with {@code ignoreCancelled} so StarEnchants folds its damage after the base
 * (and other plugins') calculation and skips an already-cancelled hit. The handler runs on the firing
 * region thread; the dispatch keeps every entity mutation on its correct thread via the Sink.
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
