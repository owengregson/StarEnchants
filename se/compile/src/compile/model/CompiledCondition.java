package compile.model;

import compile.model.cond.Cond;
import schema.diag.Source;
import schema.grammar.expr.FlowKind;

/**
 * A compiled activation condition: the typed, slot-resolved {@link Cond} tree the
 * runtime evaluates over a thread-local primitive {@code FactBuffer}, together with the
 * authored control-flow outcome the result carries (docs/architecture.md §3.4). A {@code null}
 * {@code CompiledCondition} on an {@link Ability} means "always true" — no gate.
 *
 * <p>The untyped {@link schema.grammar.expr.Expr} the parser produces has been lowered
 * here by {@code se-compile}: every variable is a dense {@code FactBuffer} slot, every
 * literal is pre-parsed, and every operand is type-checked, so the hot path does no
 * string work. The {@link Source} is retained so a runtime fault can still be reported
 * where the condition was authored.
 *
 * <p>{@code root} is evaluated to a boolean; {@link #whenTrue} is the flow applied when it
 * passes and {@link #whenFalse} when it fails. A bare condition expression is a gate
 * ({@code whenTrue=CONTINUE}, {@code whenFalse=STOP}); a clause {@code <test> : <sentinel>}
 * authors a different {@code whenTrue} (and {@code whenFalse=CONTINUE}) — e.g. {@code : %stop%}
 * is {@code whenTrue=STOP}/{@code whenFalse=CONTINUE}. {@link #chanceDelta} is the percentage-point
 * adjustment added to the base chance when {@code root} passes (non-zero only for a
 * {@code ±N %chance%} clause). The flow is carried as the schema-layer {@link FlowKind} because
 * this record sits below se-engine in the module graph; se-engine maps it to its own {@code Flow}.
 *
 * @param root        the boolean-valued root of the lowered condition
 * @param whenTrue    the flow when {@code root} evaluates true
 * @param whenFalse   the flow when {@code root} evaluates false
 * @param chanceDelta percentage points added to the base chance when {@code root} passes
 * @param source      the authored origin, for diagnostics
 */
public record CompiledCondition(Cond root, FlowKind whenTrue, FlowKind whenFalse,
                                double chanceDelta, Source source) {

    /**
     * A bare boolean gate: pass &rarr; {@link FlowKind#CONTINUE}, fail &rarr; {@link FlowKind#STOP},
     * no chance adjustment. The common form for a plain {@code condition: "%x% < 5"}.
     */
    public static CompiledCondition gate(Cond root, Source source) {
        return new CompiledCondition(root, FlowKind.CONTINUE, FlowKind.STOP, 0.0, source);
    }
}
