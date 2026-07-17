package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.entity.LivingEntity;
import platform.caps.Regions;

/**
 * Per-victim FROZEN windows (Ice Aspect, ADR-0065): tick budget + wall-clock deadline + attribution +
 * the window's idempotent teardown. Written by the {@code FREEZE} intent, read by the window's own tasks,
 * the modern {@code FreezeDamageGuardListener} (cancel vanilla freeze self-damage; join/load reconcile) and the
 * disable stop. The {@link LockedPotions} shape: static, concurrent.
 * A re-proc REFRESHES (same generation, extended budget/deadline, new attacker) — never a second window
 * (owner rule).
 *
 * <p>Two clocks, one authority: the DoT chain claims its period slots against the TICK budget
 * ({@link #chainTick}) and disarms the entry itself, so wall/tick drift (catch-up bursts, sustained lag)
 * never adds or drops a DoT tick; the wall-clock deadline serves only {@link #isFrozen} — the per-tick
 * pin and the damage guard, whose call sites have no tick anchor.
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
        /** Lattice ticks the chain may consume, boundary-inclusive (the t+duration slot lands). */
        volatile long budgetTicks;
        /** Lattice ticks the chain has consumed; single writer = the owning chain's thread. */
        volatile long elapsedTicks;

        Window(long generation, long budgetTicks, long deadlineMs, UUID attackerId, LivingEntity attacker) {
            this.generation = generation;
            this.budgetTicks = budgetTicks;
            this.deadlineMs = deadlineMs;
            this.attackerId = attackerId;
            this.attacker = attacker;
        }

        public LivingEntity attacker() {
            return attacker;
        }

        /** Whether another lattice slot fits the budget after this run (false = this run was the final slot). */
        boolean hasNextSlot(int periodTicks) {
            return elapsedTicks + periodTicks <= budgetTicks;
        }
    }

    private static final Map<UUID, Window> WINDOWS = new ConcurrentHashMap<>();
    private static final AtomicLong GENERATIONS = new AtomicLong();

    private FrozenTargets() {
    }

    /**
     * Refresh a LIVE window: extend the tick budget from the chain's last completed slot (a re-proc buys
     * exactly {@code durationTicks} more lattice — its anchor quantizes down by up to one period), extend
     * the wall deadline, retarget attribution. False when none is live — a wall-lapsed entry is presumed
     * a dead chain (its tasks died with the entity/region), so the caller arms fresh and {@link #arm}
     * supersedes it.
     */
    static boolean refresh(UUID victim, int durationTicks, long deadlineMs, UUID attackerId, LivingEntity attacker) {
        Window w = WINDOWS.get(victim);
        if (w == null || System.currentTimeMillis() >= w.deadlineMs) {
            return false;
        }
        w.budgetTicks = Math.max(w.budgetTicks, w.elapsedTicks + durationTicks);
        w.deadlineMs = Math.max(w.deadlineMs, deadlineMs);
        w.attackerId = attackerId;
        w.attacker = attacker;
        return true;
    }

    /**
     * Arm a fresh window; returns its generation (the teardown guard). A surviving entry (wall-lapsed
     * under lag, or its chain died with the entity) is torn down FIRST, so its stale teardown can never
     * fire later and clobber this window's visual/slow, and two DoT chains never coexist.
     */
    static long arm(UUID victim, int durationTicks, long deadlineMs, UUID attackerId, LivingEntity attacker) {
        Window prior = WINDOWS.get(victim);
        if (prior != null) {
            Runnable priorTeardown = prior.teardown;
            if (priorTeardown != null) {
                try {
                    priorTeardown.run(); // idempotent; disarms the prior entry via its own generation
                } catch (RuntimeException unreachable) {
                    Regions.swallowed("FrozenTargets.arm", unreachable);
                }
            }
        }
        long gen = GENERATIONS.incrementAndGet();
        WINDOWS.put(victim, new Window(gen, durationTicks, deadlineMs, attackerId, attacker));
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

    /**
     * The owning DoT chain's per-run claim: advance {@code periodTicks} along the window's lattice and
     * return the window while this run lands inside the budget (boundary-inclusive — the t+duration slot
     * is the final hurt); {@code null} once the budget is spent, the generation superseded, or the window
     * gone (the caller tears down). Tick space only: wall-clock drift must never add or drop a slot.
     */
    static Window chainTick(UUID victim, long generation, int periodTicks) {
        Window w = WINDOWS.get(victim);
        if (w == null || w.generation != generation) {
            return null;
        }
        w.elapsedTicks += periodTicks;
        return w.elapsedTicks <= w.budgetTicks ? w : null;
    }

    /**
     * Whether {@code victim} is inside a live window at {@code nowMs} — the wall-clock read for the
     * per-tick pin and the damage guard. Never evicts: the owning chain disarms in tick space, so a
     * wall-lapsed-but-still-ticking window (sustained lag) only drops the pin/guard early — it never
     * costs a DoT slot. An entry whose chain died with its entity lingers until the next {@link #arm}
     * for that victim or the disable sweep reclaims it.
     */
    public static boolean isFrozen(UUID victim, long nowMs) {
        Window w = WINDOWS.get(victim);
        return w != null && nowMs < w.deadlineMs;
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
