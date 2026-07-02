package feature.combat;

import engine.stores.KnockbackControlStore;
import feature.compat.KnockbackSeam;
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
 * Registers the KNOCKBACK_CONTROL applier for whichever knockback event THIS server fires (§C combat-flags,
 * {@code paper-cross-version}). The applier {@link Path} is chosen by the compile-time era seam
 * {@link KnockbackSeam}: on the modern tree it probes the events (the 1.20.6+ {@code EntityKnockbackEvent},
 * hooked reflectively, else Paper's legacy event, else none); on the 1.8 tree it is always the NMS applier —
 * so no 1.8-only runtime class probe lives in shared code. Exactly one applier runs per server (no
 * double-scale). {@link #resolve} is the pure (unit-tested) modern decision; {@link #register} the side effect.
 */
public final class KnockbackListener {

    /** Which knockback applier a server uses. */
    public enum Path { MODERN, LEGACY, NONE }

    /** The modern Bukkit knockback event (1.20.6+); the live suite asserts its reflective accessors exist. */
    public static final String MODERN_EVENT = "org.bukkit.event.entity.EntityKnockbackEvent";
    /** The legacy destroystokyo knockback event (floor &rarr; pre-1.20.6). */
    public static final String LEGACY_EVENT = "com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent";

    private KnockbackListener() {
    }

    /** Prefer the modern event when present; else the legacy one; else neither (KNOCKBACK_CONTROL inert). */
    public static Path resolve(boolean modernPresent, boolean legacyPresent) {
        if (modernPresent) {
            return Path.MODERN;
        }
        return legacyPresent ? Path.LEGACY : Path.NONE;
    }

    /**
     * Register the applier this server fires its knockback through, reading {@code store} for a victim's
     * active {@code KNOCKBACK_CONTROL} flag. A no-op when {@link KnockbackSeam} resolves {@link Path#NONE}
     * (KNOCKBACK_CONTROL simply has no effect on such a server). Returns the chosen {@link Path} (for
     * logging/verification).
     */
    public static Path register(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks) {
        Path path = KnockbackSeam.resolve();
        switch (path) {
            case MODERN -> registerModern(plugin, store, nowTicks);
            // On the modern tree LegacyKnockbackListener wraps Paper's legacy event; on the 1.8 tree its
            // same-FQN overlay is the NMS knockback-resistance applier — the era seam picks which is compiled.
            case LEGACY -> plugin.getServer().getPluginManager().registerEvents(
                    new LegacyKnockbackListener(store, nowTicks), plugin);
            case NONE -> {
                // No knockback applier on this server — KNOCKBACK_CONTROL stays inert.
            }
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
}
