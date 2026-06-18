package feature.combat;

import engine.stores.KnockbackControlStore;
import java.lang.reflect.Method;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Registers the KNOCKBACK_CONTROL applier for whichever knockback event THIS server fires — the
 * version-specific edge of the {@code KNOCKBACK_CONTROL} effect (docs/v3-directives.md §C combat-flags,
 * {@code paper-cross-version}). The choice is a capability probe (class presence), never a version-string
 * {@code if}:
 *
 * <ul>
 *   <li><strong>Modern</strong> ({@code org.bukkit.event.entity.EntityKnockbackEvent}, 1.20.6+): NOT on
 *       the floor compile classpath, so applied reflectively ({@code getFinalKnockback}/
 *       {@code setFinalKnockback}, {@link Cancellable}). Registered via a dynamic
 *       {@link EventExecutor}.</li>
 *   <li><strong>Legacy</strong> ({@code com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent},
 *       floor &rarr; pre-1.20.6): on the floor classpath, a plain {@link LegacyKnockbackListener}.</li>
 * </ul>
 *
 * <p>The modern event is preferred when present (it is the one Paper actually fires post-1.20.6, with the
 * legacy event deprecated), so exactly one applier runs per server — no double-scale. {@link #resolve} is
 * the pure decision (unit-tested); {@link #register} performs the side-effecting registration. The legacy
 * branch is the only place {@link LegacyKnockbackListener} is referenced, so on a modern server that class
 * is never loaded even though its event type happens to still exist there.
 */
public final class KnockbackListener {

    /** Which knockback applier a server uses. */
    public enum Path { MODERN, LEGACY, NONE }

    static final String MODERN_EVENT = "org.bukkit.event.entity.EntityKnockbackEvent";
    static final String LEGACY_EVENT = "com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent";

    private KnockbackListener() {
    }

    /** Prefer the modern event when present; else the legacy one; else neither (KNOCKBACK_CONTROL inert). */
    static Path resolve(boolean modernPresent, boolean legacyPresent) {
        if (modernPresent) {
            return Path.MODERN;
        }
        return legacyPresent ? Path.LEGACY : Path.NONE;
    }

    /**
     * Register the applier this server fires its knockback through, reading {@code store} for a victim's
     * active {@code KNOCKBACK_CONTROL} flag. A no-op if neither event class is present (KNOCKBACK_CONTROL
     * simply has no effect on such a server). Returns the chosen {@link Path} (for logging/verification).
     */
    public static Path register(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks) {
        Path path = resolve(classPresent(MODERN_EVENT), classPresent(LEGACY_EVENT));
        switch (path) {
            case MODERN -> registerModern(plugin, store, nowTicks);
            case LEGACY -> plugin.getServer().getPluginManager().registerEvents(
                    new LegacyKnockbackListener(store, nowTicks), plugin);
            case NONE -> { /* no knockback event on this server — nothing to hook */ }
        }
        return path;
    }

    /** Reflectively hook the modern Bukkit {@code EntityKnockbackEvent} (absent from the floor classpath). */
    private static void registerModern(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks) {
        try {
            Class<? extends Event> eventClass = Class.forName(MODERN_EVENT).asSubclass(Event.class);
            Method getFinal = eventClass.getMethod("getFinalKnockback");
            Method setFinal = eventClass.getMethod("setFinalKnockback", Vector.class);
            EventExecutor executor = (ignored, event) -> applyModern(event, store, nowTicks, getFinal, setFinal);
            // ignoreCancelled = true: if another plugin already cancelled the knockback, there is nothing to scale.
            plugin.getServer().getPluginManager().registerEvent(
                    eventClass, new Listener() { }, EventPriority.HIGH, executor, plugin, true);
        } catch (ReflectiveOperationException unexpected) {
            // classPresent already confirmed the class; a missing accessor would be a Paper API change.
            plugin.getLogger().warning("KNOCKBACK_CONTROL: modern knockback event present but unhookable ("
                    + unexpected.getMessage() + "); the effect will be inert on this server.");
        }
    }

    /** Apply the stored multiplier to a modern {@code EntityKnockbackEvent}, reading its victim + final vector. */
    private static void applyModern(Event event, KnockbackControlStore store, LongSupplier nowTicks,
                                    Method getFinal, Method setFinal) {
        if (!(event instanceof EntityEvent entityEvent)) {
            return;
        }
        Entity entity = entityEvent.getEntity();
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        double multiplier = store.multiplier(entity.getUniqueId(), nowTicks.getAsLong());
        if (Double.isNaN(multiplier)) {
            return; // no active KNOCKBACK_CONTROL flag for this entity
        }
        if (multiplier <= 0.0) {
            if (event instanceof Cancellable cancellable) {
                cancellable.setCancelled(true); // a full cancel — no knockback at all
            }
            return;
        }
        try {
            Object current = getFinal.invoke(event);
            if (current instanceof Vector vector) {
                setFinal.invoke(event, vector.clone().multiply(multiplier));
            }
        } catch (ReflectiveOperationException ignored) {
            // Best-effort scale; a cancel (above) is the exact, always-available path.
        }
    }

    /** Whether {@code className} resolves on this server's classpath. */
    private static boolean classPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
