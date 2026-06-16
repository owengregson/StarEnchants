package engine.condition;

/**
 * The result of evaluating a compiled condition: a {@link Flow} and a chance delta
 * (docs/architecture.md §3.4 — "one compiled condition both gates and tunes chance").
 * The pipeline adds {@link #chanceDelta()} to the ability's base chance before the roll
 * at gate 8.
 *
 * <p>A plain boolean condition yields {@link #CONTINUE} (passed) or {@link #STOP}
 * (failed) with a zero delta; richer DSL forms that adjust chance produce a non-zero
 * delta or a {@link Flow#FORCE}/{@link Flow#ALLOW} flow.
 *
 * @param flow        the control-flow outcome
 * @param chanceDelta percentage points to add to the base chance (may be negative)
 */
public record ConditionResult(Flow flow, double chanceDelta) {

    /** Passed, no chance adjustment. */
    public static final ConditionResult CONTINUE = new ConditionResult(Flow.CONTINUE, 0.0);

    /** Failed — the ability does not activate. */
    public static final ConditionResult STOP = new ConditionResult(Flow.STOP, 0.0);

    /** @return {@code true} unless the flow is {@link Flow#STOP}. */
    public boolean passes() {
        return flow != Flow.STOP;
    }
}
