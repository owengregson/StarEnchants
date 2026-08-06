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
 * <p>One authority — TICK space: the DoT chain claims its period slots against the budget
 * ({@link #chainTick}) and disarms the entry itself, and {@link #isFrozen} reads that same budget, so
 * wall/tick drift (catch-up bursts, sustained lag) never adds or drops a DoT tick and never blinds the
 * pin/guard mid-window. The wall-clock deadline only reaps an entry whose chain died with its entity.
 *
 * <p>The attacker handle is stored only to hand to vanilla as the damage source of a DoT tick
 * (never dereferenced — the ADR-0054 {@code hurt()} contract), so a cross-region attacker is safe.
 */
public final class FrozenTargets {

    /** One live window. {@code teardown} is set right after arm (the sink owns the closure). */
    public static final class Window {
        final long generation;
        /** Reaper only: a lapsed entry is presumed a dead chain, so {@link #refresh} lets the next arm supersede it. */
        volatile long deadlineMs;
        volatile UUID attackerId;
        volatile LivingEntity attacker;
        volatile Runnable teardown;
        /** Lattice ticks the chain may consume, boundary-inclusive (the t+duration slot lands). */
        volatile long budgetTicks;
        /** Lattice ticks the chain has consumed; single writer = the owning chain's thread. */
        volatile long elapsedTicks;
        /** {@code FREEZE no-jump} (R-QC57): this window also pins the victim to the ground. */
        volatile boolean noJump;

        Window(long generation, long budgetTicks, long deadlineMs, UUID attackerId, LivingEntity attacker,
               boolean noJump) {
            this.generation = generation;
            this.budgetTicks = budgetTicks;
            this.deadlineMs = deadlineMs;
            this.attackerId = attackerId;
            this.attacker = attacker;
            this.noJump = noJump;
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
    static boolean refresh(UUID victim, int durationTicks, long deadlineMs, UUID attackerId,
                           LivingEntity attacker, boolean noJump) {
        Window w = WINDOWS.get(victim);
        if (w == null || System.currentTimeMillis() >= w.deadlineMs) {
            return false;
        }
        w.budgetTicks = Math.max(w.budgetTicks, w.elapsedTicks + durationTicks);
        w.deadlineMs = Math.max(w.deadlineMs, deadlineMs);
        w.attackerId = attackerId;
        w.attacker = attacker;
        // The no-jump flag LATCHES on refresh, like the budget and the deadline: a re-proc must never be able
        // to release a ground-pin the window already had, which is what a plain overwrite from a second
        // consumer's ordinary FREEZE would do mid-window.
        w.noJump |= noJump;
        return true;
    }

    /**
     * Arm a fresh window; returns its generation (the teardown guard). A surviving entry (wall-lapsed
     * under lag, or its chain died with the entity) is torn down FIRST, so its stale teardown can never
     * fire later and clobber this window's visual/slow, and two DoT chains never coexist.
     */
    static long arm(UUID victim, int durationTicks, long deadlineMs, UUID attackerId, LivingEntity attacker,
                    boolean noJump) {
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
        WINDOWS.put(victim, new Window(gen, durationTicks, deadlineMs, attackerId, attacker, noJump));
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
     * Whether {@code victim} is inside a live window — the liveness read for the per-tick pin and the damage
     * guard, in TICK space: unspent budget is exactly the span Paper's freeze-tick lock keeps the victim
     * pinned. Reading the wall deadline instead went blind in a laggy tail (60 game ticks outlast 3000 ms
     * below 20 TPS) while the victim stayed pinned, letting vanilla's fully-frozen self-hurt through — its
     * i-frames then partial the next DoT slot on ≤1.20.6. Never evicts; the owning chain disarms itself.
     */
    public static boolean isFrozen(UUID victim) {
        Window w = WINDOWS.get(victim);
        return w != null && w.elapsedTicks < w.budgetTicks;
    }

    /**
     * Whether {@code victim} is inside a live window that also pins them to the GROUND ({@code FREEZE
     * no-jump}, R-QC57). The modern lane's jump listener is the only reader; the legacy overlay has no
     * cancellable jump event, so a legacy freeze keeps its DoT and slow and the victim can still hop — the
     * same recorded degrade the powder-snow visual takes there.
     *
     * <p>Deliberately a SECOND read rather than a widening of {@link #isFrozen}: frozen and ground-pinned are
     * different states, and every consumer authored before the flag existed must keep the feel it was tuned
     * at. One map read on a jump — an event that fires far less often than the hit path this class usually
     * serves — so the guard costs nothing anyone can measure.
     */
    public static boolean blocksJump(UUID victim) {
        Window w = WINDOWS.get(victim);
        return w != null && w.noJump && w.elapsedTicks < w.budgetTicks;
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

    /**
     * TRAP_BREAK (ADR-0071): run {@code victim}'s live window teardown NOW — a freeze is confinement, so
     * Turnkey thaws it like any registered trap. Call on the victim's own thread (the teardown touches the
     * entity); idempotent + generation-guarded by the owning chain's contract. False = nothing to thaw.
     */
    public static boolean breakNow(UUID victim) {
        Window w = WINDOWS.get(victim);
        if (w == null) {
            return false;
        }
        Runnable teardown = w.teardown;
        if (teardown != null) {
            teardown.run();
        }
        return true;
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
