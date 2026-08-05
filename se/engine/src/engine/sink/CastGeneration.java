package engine.sink;

/**
 * The live snapshot generation, for the four IMPACT-scope carriers that outlive the activation that armed them
 * (R-QC58): a falling-block cast, a tracked summon, a turret shot, and the volley chain that binds them.
 *
 * <p>Each of those carries an INTERNED group id, and interned ids are snapshot-relative — a reload re-interns
 * the table, so a cast armed before the swap names a different group after it. Stamping the generation beside
 * the group lets the consume site tell "this row is from the current table" from "this row's int now means
 * something else".
 *
 * <p>A stale cast is DROPPED, never unscoped to {@code -1}. Unscoping fails OPEN — an unscoped payload fires
 * the owner's whole IMPACT roster, which is precisely the over-firing the scoping exists to stop (a ~142-block
 * field firing a second feature's payload on every block). Dropping loses at most the tail of one field that
 * was already mid-flight when the operator reloaded.
 *
 * <p>ONE holder for the whole family on purpose: four per-registry counters would grow the adjudicated static
 * set without adding a single consideration to adjudicate. Bound by the composition root at boot and on every
 * reload publish, beside {@code WhyStore#generation} — the same moment, for the same reason.
 */
public final class CastGeneration {

    private static volatile int current;

    private CastGeneration() {
    }

    /** Bind the live snapshot generation; the composition root calls this at boot and on every reload publish. */
    public static void generation(int generation) {
        current = generation;
    }

    /** The generation to stamp into a carrier being armed now. */
    public static int current() {
        return current;
    }

    /** Whether a stamped generation is from an older snapshot — the consume-site drop test. */
    public static boolean stale(int stamped) {
        return stamped != current;
    }
}
