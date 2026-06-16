package compile.model;

import compile.model.cond.Cond;
import schema.diag.Source;

/**
 * A compiled activation condition: the typed, slot-resolved {@link Cond} tree the
 * runtime evaluates over a thread-local primitive {@code FactBuffer} to produce a
 * flow + chance-delta result (docs/architecture.md §3.4). A {@code null}
 * {@code CompiledCondition} on an {@link Ability} means "always true" — no gate.
 *
 * <p>The untyped {@link schema.grammar.expr.Expr} the parser produces has been lowered
 * here by {@code se-compile}: every variable is a dense {@code FactBuffer} slot, every
 * literal is pre-parsed, and every operand is type-checked, so the hot path does no
 * string work. The {@link Source} is retained so a runtime fault can still be reported
 * where the condition was authored.
 *
 * @param root   the boolean-valued root of the lowered condition
 * @param source the authored origin, for diagnostics
 */
public record CompiledCondition(Cond root, Source source) {
}
