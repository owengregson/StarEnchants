package compile.model;

import schema.diag.Source;
import schema.grammar.expr.Expr;

/**
 * A compiled activation condition: the pre-built, validated expression AST that the
 * runtime evaluates over a thread-local primitive {@code FactBuffer} to produce a
 * flow + chance-delta result (docs/architecture.md §3.4). A {@code null}
 * {@code CompiledCondition} on an {@link Ability} means "always true" — no gate.
 *
 * <p>This wraps the untyped {@link Expr} produced by {@code se-schema}. Lowering of
 * variable references to primitive {@code FactBuffer} slot indices (and the
 * STOP/FORCE/CONTINUE/ALLOW flow semantics) is layered on here as the variable
 * vocabulary lands; for now it carries the validated AST and its {@link Source} so
 * a runtime fault can still be reported where it was authored.
 */
public record CompiledCondition(Expr ast, Source source) {
}
