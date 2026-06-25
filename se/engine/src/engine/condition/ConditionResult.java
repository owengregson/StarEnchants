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
 * @param chanceDelta percentage points to add to the base chance (may be negative)
 */
public record ConditionResult(Flow flow, double chanceDelta) {

    /** Passed, no chance adjustment. */
    public static final ConditionResult CONTINUE = new ConditionResult(Flow.CONTINUE, 0.0);

    /** Failed — the ability does not activate. */
    public static final ConditionResult STOP = new ConditionResult(Flow.STOP, 0.0);

    /** Forced — activate, skipping the chance roll. */
    public static final ConditionResult FORCE = new ConditionResult(Flow.FORCE, 0.0);

    /** Allowed — activate regardless of the chance roll. */
    public static final ConditionResult ALLOW = new ConditionResult(Flow.ALLOW, 0.0);

    /** Returns the flyweight constant for the common zero-delta case (gate-7 hot path allocates nothing); only a non-zero delta allocates. */
    public static ConditionResult of(Flow flow, double chanceDelta) {
        if (chanceDelta == 0.0) {
            return switch (flow) {
                case CONTINUE -> CONTINUE;
                case STOP -> STOP;
                case FORCE -> FORCE;
                case ALLOW -> ALLOW;
            };
        }
        return new ConditionResult(flow, chanceDelta);
    }

    public boolean passes() {
        return flow != Flow.STOP;
    }
}
