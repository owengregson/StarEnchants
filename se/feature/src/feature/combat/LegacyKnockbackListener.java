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
 * The KNOCKBACK_CONTROL applier for servers that fire Paper's legacy
 * {@link EntityKnockbackByEntityEvent} (floor &rarr; pre-1.20.6, before the Bukkit
 * {@code EntityKnockbackEvent} existed). This event class is on the floor compile classpath, so the
 * handler is a plain {@code @EventHandler}; {@link KnockbackListener} only ever constructs and registers
 * this class when the modern Bukkit event is <em>absent</em>, so exactly one knockback listener fires per
 * server (no double-scale) and this class is never loaded on a modern server.
 *
 * <p>Folia: the knockback event fires on the victim's region thread; reading the concurrent
 * {@link KnockbackControlStore} and cancelling/scaling the event is in-thread.
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
        // Best-effort scale: the acceleration vector is the applied knockback on this event; multiplying
        // it in place scales the launch. (A pure cancel above is exact on every version; partial scaling
        // degrades to a no-op only if a server returns a defensive copy here.)
        event.getAcceleration().multiply(multiplier);
    }
}
