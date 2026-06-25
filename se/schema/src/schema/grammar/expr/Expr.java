package schema.grammar.expr;

import schema.diag.Source;

/**
 * The untyped condition-expression AST: an immutable, data-only tree of flyweight
 * nodes (docs/architecture.md §3.2, §3.4). Shape only — typing, name resolution,
 * and lowering are se-compile's job. Sealed so the compiler can switch exhaustively;
 * every node carries the {@link Source} of its first character for diagnostics.
 */
public sealed interface Expr
        permits Expr.Or, Expr.And, Expr.Not, Expr.Compare, Expr.StringMatch,
                Expr.Arith, Expr.Neg,
                Expr.VarRef, Expr.NumberLit, Expr.BoolLit, Expr.StringLit, Expr.Clause {

    Source source();

    /**
     * A top-level condition clause pairing a boolean {@code test} with a control-flow outcome
     * (docs/architecture.md §3.4; v3.1 §A). Only ever at a condition's root; {@code test} is never
     * another {@code Clause}. For a {@code ±N %chance%} clause, {@code flow} is {@link FlowKind#CONTINUE}
     * and {@code chanceDelta} is the signed percentage-point adjustment; for flow sentinels it is 0.
     */
    record Clause(Expr test, FlowKind flow, double chanceDelta, Source source) implements Expr {}

    /** Logical OR ({@code ||}), the lowest-precedence binary operator. */
    record Or(Expr left, Expr right, Source source) implements Expr {}

    /** Logical AND ({@code &&}), binding tighter than {@link Or}. */
    record And(Expr left, Expr right, Source source) implements Expr {}

    /** Logical negation ({@code !}), unary, binding tighter than the comparators. */
    record Not(Expr operand, Source source) implements Expr {}

    /** A relational comparison; non-associative ({@code a < b < c} is a parse error). */
    record Compare(Expr left, Cmp op, Expr right, Source source) implements Expr {}

    /** A string-domain match ({@code contains}/{@code matchesregex}); non-associative, like {@link Compare}. */
    record StringMatch(Expr left, StrOp op, Expr right, Source source) implements Expr {}

    /** Binary arithmetic; {@code * /} bind tighter than {@code + -}, both left-associative. */
    record Arith(Expr left, ArithOp op, Expr right, Source source) implements Expr {}

    /** Numeric negation ({@code -operand}), unary, binding tighter than binary arithmetic. */
    record Neg(Expr operand, Source source) implements Expr {}

    /**
     * A {@code %scope.name%} variable; bare {@code %name%} has a {@code null} scope. Only the first dot
     * splits scope from name ({@code %a.b.c%} → scope {@code a}, name {@code b.c}), so PlaceholderAPI
     * tokens survive intact. Never decides whether a variable is known (docs/architecture.md §3.4).
     */
    record VarRef(String scope, String name, Source source) implements Expr {}

    /** A numeric literal kept as its raw lexeme. */
    record NumberLit(String raw, Source source) implements Expr {}

    record BoolLit(boolean value, Source source) implements Expr {}

    /** A quoted string literal, already unescaped by the tokenizer. */
    record StringLit(String value, Source source) implements Expr {}
}
