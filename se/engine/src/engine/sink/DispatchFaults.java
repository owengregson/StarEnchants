package engine.sink;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

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
 * something" into a check that fails.
 */
public final class DispatchFaults {

    private static final LongAdder COUNT = new LongAdder();
    private static final AtomicReference<Throwable> LAST = new AtomicReference<>();

    private DispatchFaults() {
    }

    /** Record one flush-time fault. Called only from the warn-and-skip arm; any thread (Folia). */
    static void record(Throwable failed) {
        COUNT.increment();
        LAST.set(failed);
    }

    /** Flush-time faults since the last {@link #reset()}. */
    public static long count() {
        return COUNT.sum();
    }

    /** The most recent flush-time fault, or {@code null} — the detail a bare count cannot give a failure message. */
    public static Throwable last() {
        return LAST.get();
    }

    /** Zero the channel. A suite calls this before the action it wants to hold to account. */
    public static void reset() {
        COUNT.reset();
        LAST.set(null);
    }
}
