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
 * Coordinates {@code KNOCKBACK_CONTROL} with a <strong>packet/anticheat reference</strong> knockback plugin
 * ({@code me.vexmc.mental}) when it is installed (docs/v3-directives.md §N; docs/decisions/0026). This is
 * the integration edge for the one place StarEnchants and that plugin both touch the same thing: a player's
 * incoming knockback.
 *
 * <p><b>Why a bridge is needed.</b> That plugin <em>owns</em> player knockback: it {@code setVelocity}s its
 * own vector, overwriting whatever vanilla (and thus {@link KnockbackListener}'s scaling of the vanilla
 * {@code EntityKnockbackEvent}) produced — so KNOCKBACK_CONTROL would silently die for player victims. It
 * publishes a seam: a cancellable {@code KnockbackApplyEvent} on the victim's owning thread with a mutable
 * {@code velocity()}, where SE applies its multiplier so the effect rides on that plugin's vector.
 *
 * <p><b>No double-scale.</b> Both this hook and {@link KnockbackListener} read the same short-TTL
 * {@link KnockbackControlStore}, but exactly one write reaches the client per hit: a plugin-owned hit
 * overwrites the vanilla event (SE's vanilla scaling discarded harmlessly) and lands here; a yielded hit
 * (OCM ownership, full block, module off) fires no apply event and lands via {@link KnockbackListener}; mob
 * victims always take the vanilla path. The store read is idempotent, so no skip logic is needed.
 *
 * <p><b>Cancel means zero, not "let vanilla stand".</b> {@code multiplier <= 0} writes a zero velocity
 * rather than cancelling the apply event — cancelling tells that plugin to keep vanilla velocity, the exact
 * opposite of KNOCKBACK_CONTROL:0 on a plugin-owned hit.
 *
 * <p><b>Reflective, soft, optional.</b> SE compiles against no class from that plugin; the event is hooked
 * only when present, and {@code integrations.named.mental: false} disables the bridge. Folia-correct: the
 * apply event fires on the victim's region thread, the store is concurrent and UUID-keyed.
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
        // ignoreCancelled = true: a cancelled apply event means that plugin will not override the velocity, so
        // there is nothing for SE to scale. NORMAL priority: SE is the only expected consumer, and that plugin
        // reads the final velocity after every handler runs.
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
            // Best-effort coordination; a reflective failure here must never break a hit. The class +
            // accessors were verified at registration, so this should not occur.
        }
    }
}
