package feature.combat;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import engine.stores.KnockbackControlStore;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * The KNOCKBACK_CONTROL applier for servers that fire Paper's legacy {@link EntityKnockbackByEntityEvent}
 * (floor &rarr; pre-1.20.6). {@link KnockbackListener} registers it only when the modern event is absent,
 * so this class never loads on a modern server and exactly one applier fires (no double-scale). The event
 * runs on the victim's region thread; the store is concurrent and UUID-keyed — Folia-safe.
 */
final class LegacyKnockbackListener implements Listener {

    private final KnockbackControlStore store;
    private final LongSupplier nowTicks;

    LegacyKnockbackListener(KnockbackControlStore store, LongSupplier nowTicks) {
        this.store = Objects.requireNonNull(store, "store");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event) {
        LivingEntity victim = event.getEntity();
        double multiplier = store.multiplier(victim.getUniqueId(), nowTicks.getAsLong());
        if (Double.isNaN(multiplier)) {
            return; // no active KNOCKBACK_CONTROL flag for this entity
        }
        if (multiplier <= 0.0) {
            event.setCancelled(true); // a full cancel — no knockback at all
            return;
        }
        // Best-effort in-place scale of the applied knockback; degrades to a no-op if a server returns a
        // defensive copy here (the full-cancel path above is exact on every version).
        event.getAcceleration().multiply(multiplier);
    }
}
