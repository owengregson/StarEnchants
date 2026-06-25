package engine.condition;

/**
 * The control-flow outcome of a compiled condition at the pipeline's condition gate (docs/architecture.md
 * §3.3). One condition both gates and tunes the chance roll, so its result pairs a {@code Flow} with a
 * chance delta (see {@link ConditionResult}).
 */
public enum Flow {

    /** The condition passed; proceed to the chance roll as normal. */
    CONTINUE,

    /** The condition failed; stop — this ability does not activate. */
    STOP,

    /** Force activation, skipping the chance roll (treated as 100% chance). */
    FORCE,

    /** Allow activation regardless of the chance roll, without forcing other gates. */
    ALLOW
}
