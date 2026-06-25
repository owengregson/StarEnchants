package schema.grammar.expr;

/**
 * The control-flow outcome a condition clause authors (docs/architecture.md §3.4; v3.1 §A).
 * Schema-layer mirror of the engine's {@code Flow}: schema sits below engine in the module graph
 * and cannot name the engine enum, so se-engine maps {@code FlowKind} to {@code Flow} at evaluation.
 */
public enum FlowKind {

    /** {@code %continue%} — proceed to the chance roll as normal (the bare-gate pass outcome). */
    CONTINUE,

    /** {@code %stop%} — block this activation. */
    STOP,

    /** {@code %force%} — force activation, skipping the chance roll (treated as 100%). */
    FORCE,

    /** {@code %allow%} — allow regardless of the chance roll, without forcing other gates. */
    ALLOW
}
