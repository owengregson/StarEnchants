package feature.combat;

import engine.sink.DotParkLedger;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;

/**
 * Reflective, optional bridge to the {@code me.vexmc.mental} combat engine's balanced ComboStart/ComboEnd
 * events (ADR-0069), shaped exactly like {@link MentalKnockbackBridge}: no compile-time Mental dependency,
 * accessors verified at registration, observation-only MONITOR listeners. ComboStart marks the victim's combo
 * active in the park ledger (SE stops issuing its own hurts on them); ComboEnd clears it and paces out anything
 * still owed. Both events fire on the victim's owning region thread, so the ledger writes and the release arm
 * are region-correct.
 */
public final class MentalComboBridge {

    /** Outcome of {@link #register} — for the boot log / verification. */
    public enum Path {
        /** Hooked Mental's combo events; parking engages while a combo is held. */
        BOUND,
        /** Mental's combo-event API is not on the classpath — it is not installed; combo state is never written. */
        ABSENT,
        /** {@code integrations.named.mental(-combo-sync): false} — combo-DoT sync switched off by config. */
        DISABLED
    }

    public static final String START_EVENT = "me.vexmc.mental.api.event.ComboStartEvent";
    public static final String END_EVENT = "me.vexmc.mental.api.event.ComboEndEvent";

    private MentalComboBridge() {
    }

    /**
     * Hook Mental's combo events so parking engages while a combo is held and pays out when it ends. A no-op
     * returning {@link Path#DISABLED} when {@code enabled} is false, or {@link Path#ABSENT} when Mental (or its
     * expected accessors) is not present. Returns the chosen {@link Path} (for logging/verification).
     */
    public static Path register(Plugin plugin, DotParkLedger ledger, ComboDotRelease release,
                                LongSupplier nowTicks, boolean enabled) {
        if (!enabled) {
            return Path.DISABLED;
        }
        final Class<? extends Event> startClass;
        final Class<? extends Event> endClass;
        final Method startVictim;
        final Method startAttacker;
        final Method endVictim;
        try {
            startClass = Class.forName(START_EVENT).asSubclass(Event.class);
            endClass = Class.forName(END_EVENT).asSubclass(Event.class);
            startVictim = startClass.getMethod("getVictim");
            startAttacker = startClass.getMethod("getAttacker");
            endVictim = endClass.getMethod("getVictim");
        } catch (ClassNotFoundException absent) {
            return Path.ABSENT; // Mental not installed — combo state is never written, so tryPark always declines
        } catch (NoSuchMethodException apiChanged) {
            // The events are present but their accessors moved — an API change in Mental. Decline rather than risk
            // a per-event reflective failure; the combo-DoT mechanism stays off (no combo state ever recorded).
            plugin.getLogger().log(Level.WARNING, "Mental is present but its combo-event API is unrecognised; "
                    + "skipping combo-DoT sync.", apiChanged);
            return Path.ABSENT;
        }
        EventExecutor startExec = (ignored, event) -> onStart(event, ledger, nowTicks, startVictim, startAttacker);
        EventExecutor endExec = (ignored, event) -> onEnd(event, ledger, release, endVictim);
        // MONITOR, observation-only: the combo events are not cancellable; we only read their outcome.
        var manager = plugin.getServer().getPluginManager();
        manager.registerEvent(startClass, new Listener() { }, EventPriority.MONITOR, startExec, plugin, false);
        manager.registerEvent(endClass, new Listener() { }, EventPriority.MONITOR, endExec, plugin, false);
        return Path.BOUND;
    }

    /** ComboStart: mark the victim's combo active so {@code hurtOrPark} banks SE DoT ticks on them. */
    private static void onStart(Event event, DotParkLedger ledger, LongSupplier nowTicks,
                                Method getVictim, Method getAttacker) {
        try {
            if (!(getVictim.invoke(event) instanceof Player victim)) {
                return; // ComboStartEvent's victim is always a Player; be defensive regardless
            }
            Object attacker = getAttacker.invoke(event);
            UUID attackerId = attacker instanceof LivingEntity living ? living.getUniqueId() : null;
            ledger.comboStarted(victim.getUniqueId(), attackerId, nowTicks.getAsLong());
        } catch (ReflectiveOperationException verifiedAtRegistration) {
            // Accessors verified at registration; swallow so a reflective slip never breaks Mental's dispatch.
        }
    }

    /** ComboEnd: clear the combo state and pace out anything still owed on the victim. */
    private static void onEnd(Event event, DotParkLedger ledger, ComboDotRelease release, Method getVictim) {
        try {
            if (!(getVictim.invoke(event) instanceof Player victim)) {
                return;
            }
            ledger.comboEnded(victim.getUniqueId());
            if (ledger.hasParked(victim.getUniqueId())) {
                release.begin(victim);
            }
        } catch (ReflectiveOperationException verifiedAtRegistration) {
            // Same contract as onStart.
        }
    }
}
