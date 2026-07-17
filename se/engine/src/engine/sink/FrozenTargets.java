package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;

/**
 * Per-victim FROZEN windows (Ice Aspect, ADR-0065): deadline + attribution + the window's idempotent
 * teardown. Written by the {@code FREEZE} intent, read by the window's own tasks, the modern
 * {@code FreezeDamageGuardListener} (cancel vanilla freeze self-damage; join reconcile) and the
 * disable stop. The {@link LockedPotions} shape: static, concurrent, wall-clock, self-evicting.
 * A re-proc REFRESHES (same generation, new deadline/attacker) — never a second window (owner rule).
 *
 * <p>The attacker handle is stored only to hand to vanilla as the damage source of a DoT tick
 * (never dereferenced — the ADR-0054 {@code hurt()} contract), so a cross-region attacker is safe.
 */
public final class FrozenTargets {

    /** One live window. {@code teardown} is set right after arm (the sink owns the closure). */
    public static final class Window {
        final long generation;
        volatile long deadlineMs;
        volatile UUID attackerId;
        volatile LivingEntity attacker;
        volatile Runnable teardown;

        Window(long generation, long deadlineMs, UUID attackerId, LivingEntity attacker) {
            this.generation = generation;
            this.deadlineMs = deadlineMs;
            this.attackerId = attackerId;
            this.attacker = attacker;
        }

        public LivingEntity attacker() {
            return attacker;
        }
    }

    private static final Map<UUID, Window> WINDOWS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();

    private FrozenTargets() {
    }

    /** Refresh a LIVE window (extend deadline, retarget attribution). False when none is live. */
    static boolean refresh(UUID victim, long deadlineMs, UUID attackerId, LivingEntity attacker) {
        Window w = WINDOWS.get(victim);
        if (w == null || System.currentTimeMillis() >= w.deadlineMs) {
            return false;
        }
        w.deadlineMs = Math.max(w.deadlineMs, deadlineMs);
        w.attackerId = attackerId;
        w.attacker = attacker;
        return true;
    }

    /** Arm a fresh window; returns its generation (the teardown guard). */
    static long arm(UUID victim, long deadlineMs, UUID attackerId, LivingEntity attacker) {
        long gen = GENERATIONS.incrementAndGet();
        WINDOWS.put(victim, new Window(gen, deadlineMs, attackerId, attacker));
        return gen;
    }

    /** Attach the window's idempotent teardown (generation-guarded by the sink's closure). */
    static void onTeardown(UUID victim, long generation, Runnable teardown) {
        Window w = WINDOWS.get(victim);
        if (w != null && w.generation == generation) {
            w.teardown = teardown;
        }
    }

    static Window get(UUID victim) {
        return WINDOWS.get(victim);
    }

    /** Whether {@code victim} is inside a live window at {@code nowMs} (self-evicts a lapsed entry). */
    public static boolean isFrozen(UUID victim, long nowMs) {
        Window w = WINDOWS.get(victim);
        if (w == null) {
            return false;
        }
        if (nowMs >= w.deadlineMs) {
            // The entry lapses here only when the owning tasks died with their entity/region; the
            // normal path removes it via disarm() in the teardown.
            WINDOWS.remove(victim, w);
            return false;
        }
        return true;
    }

    /** Drop the window iff still the {@code generation} one (a newer window survives a stale teardown). */
    static void disarm(UUID victim, long generation) {
        Window w = WINDOWS.get(victim);
        if (w != null && w.generation == generation) {
            WINDOWS.remove(victim, w);
        }
    }

    /** Forget one entity's window (quit drains run the teardown separately via TimedRevert). */
    public static void clear(UUID victim) {
        WINDOWS.remove(victim);
    }

    /** Disable stop: best-effort run of every live window's teardown (the SwarmSpawns.removeAll shape). */
    public static void teardownAll() {
        for (Window w : WINDOWS.values()) {
            Runnable teardown = w.teardown;
            if (teardown != null) {
                try {
                    teardown.run();
                } catch (RuntimeException unreachable) {
                    Regions.swallowed("FrozenTargets.teardownAll", unreachable);
                }
            }
        }
        WINDOWS.clear();
    }
}
