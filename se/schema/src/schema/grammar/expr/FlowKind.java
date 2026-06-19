package schema.grammar.expr;

/**
 * The control-flow outcome a condition clause authors (docs/architecture.md §3.4; v3.1 §A).
 * A bare condition expression is a gate (pass &rarr; continue, fail &rarr; stop); a clause
 * {@code <expr> : <sentinel>} names a richer outcome to apply when the expression is true:
 *
 * <ul>
 *   <li>{@link #CONTINUE} — {@code %continue%}: proceed to the chance roll as normal.</li>
 *   <li>{@link #STOP} — {@code %stop%}: block this activation.</li>
 *   <li>{@link #FORCE} — {@code %force%}: force activation, skipping the chance roll.</li>
 *   <li>{@link #ALLOW} — {@code %allow%}: allow activation regardless of the chance roll.</li>
 * </ul>
 *
 * <p>This is the schema-layer mirror of the engine's {@code engine.condition.Flow}: the grammar and
 * the lowered {@code CompiledCondition} live below the engine in the module graph (engine depends on
 * compile depends on schema), so they cannot name the engine enum. se-compile threads a
 * {@code FlowKind} through {@code CompiledCondition} and se-engine maps it to its own {@code Flow} at
 * evaluation time. The {@code ±N %chance%} clause carries no {@code FlowKind} of its own — it is a
 * {@link #CONTINUE} outcome with a non-zero chance delta.
 */
public enum FlowKind {

    /** {@code %continue%} — passed; proceed to the chance roll as normal (the bare-gate pass outcome). */
    CONTINUE,

    /** {@code %stop%} — block this activation. */
    STOP,

    /** {@code %force%} — force activation, skipping the chance roll (treated as 100%). */
    FORCE,

    /** {@code %allow%} — allow activation regardless of the chance roll, without forcing other gates. */
    ALLOW
}
