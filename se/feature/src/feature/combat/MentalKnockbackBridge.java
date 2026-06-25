package feature.combat;

import engine.stores.KnockbackControlStore;
import java.lang.reflect.Method;
import java.util.function.LongSupplier;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * Coordinates {@code KNOCKBACK_CONTROL} with the {@code me.vexmc.mental} knockback plugin when installed
 * (docs/v3-directives.md §N; docs/decisions/0026). That plugin OWNS player knockback — it {@code setVelocity}s
 * its own vector over the vanilla {@code EntityKnockbackEvent} {@link KnockbackListener} scales — so the
 * effect would die for player victims; the bridge rides its {@code KnockbackApplyEvent} seam instead.
 *
 * <p>No double-scale: exactly one write reaches the client per hit (a plugin-owned hit lands here and
 * discards SE's harmless vanilla scaling; a yielded hit fires no apply event and lands via
 * {@link KnockbackListener}; mobs always take the vanilla path). {@code multiplier <= 0} writes a ZERO
 * velocity, not a cancel — cancelling would tell that plugin to keep vanilla velocity, the opposite of
 * KNOCKBACK_CONTROL:0. Reflective + optional ({@code integrations.named.mental: false} disables it);
 * Folia-correct (apply event on the victim's region thread, store concurrent + UUID-keyed).
 */
public final class MentalKnockbackBridge {

    /** Outcome of {@link #register} — for the boot log / verification. */
    public enum Path {
        /** Hooked that plugin's apply event; KNOCKBACK_CONTROL composes with its knockback. */
        BOUND,
        /** That plugin's API event class is not on the classpath — it is not installed; nothing to coordinate. */
        ABSENT,
        /** {@code integrations.named.mental: false} — coordination switched off by config. */
        DISABLED
    }

    /** That plugin's public apply-event class (its {@code api} module); present iff the plugin is installed. */
    public static final String APPLY_EVENT = "me.vexmc.mental.api.event.KnockbackApplyEvent";

    private MentalKnockbackBridge() {
    }

    /**
     * The pure KNOCKBACK_CONTROL decision for that plugin's apply event — mirrors {@link KnockbackControlStore}
     * semantics so SE behaves the same whether the knockback came from that plugin or vanilla.
     *
     * @param multiplier the active control for the victim ({@link KnockbackControlStore#NONE} / {@code NaN}
     *     when there is no flag), as returned by {@link KnockbackControlStore#multiplier}
     * @param current    the knockback vector that plugin is about to apply (already a defensive copy from the API)
     * @return the vector SE should write back, or {@code null} to leave that plugin's vector untouched (the
     *     common no-flag case); a zero vector cancels the knockback, otherwise the scaled vector
     */
    static Vector controlled(double multiplier, Vector current) {
        if (Double.isNaN(multiplier)) {
            return null; // no active KNOCKBACK_CONTROL flag for this victim — that plugin's vector stands
        }
        if (multiplier <= 0.0) {
            return new Vector(0, 0, 0); // full cancel — zero knockback (NOT a cancel-the-event "vanilla stands")
        }
        return current.clone().multiply(multiplier);
    }

    /**
     * Hook that plugin's {@code KnockbackApplyEvent} so a victim's active {@code KNOCKBACK_CONTROL} flag (read
     * from the shared {@code store}) scales or cancels the knockback that plugin is about to deliver. A no-op
     * that returns {@link Path#ABSENT} when that plugin is not installed, or {@link Path#DISABLED} when the
     * integration is switched off. Returns the chosen {@link Path} (for logging/verification).
     *
     * @param enabled {@code integrations.named.mental} — {@code false} skips the hook entirely
     */
    public static Path register(Plugin plugin, KnockbackControlStore store, LongSupplier nowTicks, boolean enabled) {
        if (!enabled) {
            return Path.DISABLED;
        }
        final Class<? extends Event> eventClass;
        final Method getVictim;
        final Method getVelocity;
        final Method setVelocity;
        try {
            eventClass = Class.forName(APPLY_EVENT).asSubclass(Event.class);
            getVictim = eventClass.getMethod("getVictim");
            getVelocity = eventClass.getMethod("velocity");
            setVelocity = eventClass.getMethod("velocity", Vector.class);
        } catch (ClassNotFoundException absent) {
            return Path.ABSENT; // that plugin not installed — nothing to coordinate with
        } catch (NoSuchMethodException apiChanged) {
            // The class is present but its accessors moved — an API change in that plugin. Decline rather than risk
            // a per-hit reflective failure; KNOCKBACK_CONTROL stays on the vanilla path (degraded, not broken).
            plugin.getLogger().warning("Mental is present but its KnockbackApplyEvent API is unrecognised ("
                    + apiChanged.getMessage() + "); skipping knockback coordination.");
            return Path.ABSENT;
        }
        EventExecutor executor = (ignored, event) -> apply(event, store, nowTicks, getVictim, getVelocity, setVelocity);
        // ignoreCancelled: a cancelled apply event means no override, so nothing to scale. NORMAL: that plugin
        // reads the final velocity after every handler.
        plugin.getServer().getPluginManager().registerEvent(
                eventClass, new Listener() { }, EventPriority.NORMAL, executor, plugin, true);
        return Path.BOUND;
    }

    /** Reflectively read the apply event's victim + velocity, apply {@link #controlled}, write any change back. */
    private static void apply(Event event, KnockbackControlStore store, LongSupplier nowTicks,
                              Method getVictim, Method getVelocity, Method setVelocity) {
        try {
            if (!(getVictim.invoke(event) instanceof Entity victim)) {
                return; // KnockbackApplyEvent's victim is a Player (an Entity); be defensive regardless
            }
            double multiplier = store.multiplier(victim.getUniqueId(), nowTicks.getAsLong());
            if (Double.isNaN(multiplier)) {
                return; // no flag — skip the velocity read entirely (the overwhelmingly common path)
            }
            if (!(getVelocity.invoke(event) instanceof Vector current)) {
                return;
            }
            Vector result = controlled(multiplier, current);
            if (result != null) {
                setVelocity.invoke(event, result);
            }
        } catch (ReflectiveOperationException unexpected) {
            // Accessors were verified at registration; swallow regardless so a reflective slip never breaks a hit.
        }
    }
}
