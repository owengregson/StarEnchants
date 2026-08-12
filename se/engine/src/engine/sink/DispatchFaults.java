package engine.sink;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The fault-surfacing channel for the DEFERRED half of the runtime (§3.5–3.6).
 *
 * <p>{@link DispatchPlan} flushes world mutations under a warn-and-skip guard, so one bad intent cannot sink a
 * batch. That is the right runtime policy and the wrong test policy: a flushed fault reaches no
 * {@link engine.run.AbilityQuarantine} — the executor already returned — so an intent that throws on every
 * single proc costs nothing but a log line nothing asserts on. The gate walk has a fault channel; before this,
 * everything the engine does to the world had none.
 *
 * <p>Process-wide and diagnostic-only: the happy path never touches it, and only the {@code catch} arm in
 * {@link DispatchPlan#runAll} writes. Live suites read it around a staged action to turn "the server logged
 * something" into a check that fails. The count and the throwable live in ONE reference so a reported count
 * can never belong to a different fault than the throwable printed beside it, and the plugin's disable hook
 * clears it so the last fault's cause graph is not retained for the life of the JVM.
 *
 * <p>The channel is server-wide, not per-suite: a reader owns what it counts only if nothing else on the
 * server is dispatching between its {@link #reset()} and its read.
 */
public final class DispatchFaults {

    /** The channel's whole state: how many faults since the last reset, and the most recent one. */
    private record Faults(long count, Throwable last) {
    }

    private static final Faults NONE = new Faults(0L, null);
    private static final AtomicReference<Faults> STATE = new AtomicReference<>(NONE);

    private DispatchFaults() {
    }

    /** Record one flush-time fault. Called only from the warn-and-skip arm; any thread (Folia). */
    static void record(Throwable failed) {
        STATE.updateAndGet(seen -> new Faults(seen.count() + 1, failed));
    }

    /** Flush-time faults since the last {@link #reset()}. */
    public static long count() {
        return STATE.get().count();
    }

    /** The most recent flush-time fault, or {@code null} — the detail a bare count cannot give a failure message. */
    public static Throwable last() {
        return STATE.get().last();
    }

    /** Zero the channel. A suite calls this before the action it wants to hold to account; disable calls it too. */
    public static void reset() {
        STATE.set(NONE);
    }
}
