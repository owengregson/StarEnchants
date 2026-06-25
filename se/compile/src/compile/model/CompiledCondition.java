package compile.model;

import compile.model.cond.Cond;
import schema.diag.Source;
import schema.grammar.expr.FlowKind;

/**
 * A compiled activation condition: the typed, slot-resolved {@link Cond} tree plus the control-flow
 * outcome its result carries (docs/architecture.md §3.4). A {@code null} {@code CompiledCondition} on an
 * {@link Ability} means "always true". A bare condition is a gate ({@code whenTrue=CONTINUE},
 * {@code whenFalse=STOP}); a clause {@code <test> : <sentinel>} authors a different {@code whenTrue}
 * (e.g. {@code : %stop%} &rarr; {@code whenTrue=STOP}/{@code whenFalse=CONTINUE}). Flow is the schema-layer
 * {@link FlowKind} because this record sits below se-engine, which maps it to its own {@code Flow}.
 *
 * @param chanceDelta percentage points added to the base chance when {@code root} passes
 *                    (non-zero only for a {@code ±N %chance%} clause)
 */
public record CompiledCondition(Cond root, FlowKind whenTrue, FlowKind whenFalse,
                                double chanceDelta, Source source) {

    public static CompiledCondition gate(Cond root, Source source) {
        return new CompiledCondition(root, FlowKind.CONTINUE, FlowKind.STOP, 0.0, source);
    }
}
