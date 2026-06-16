package compile.model.cond;

import schema.grammar.expr.Cmp;

/**
 * The boolean-valued root of a compiled condition: a typed, slot-resolved tree the
 * runtime evaluates over a thread-local primitive {@code FactBuffer} to gate an
 * activation (docs/architecture.md §3.2 "compile, never interpret", §3.4). This is the
 * lowered form of the untyped {@link schema.grammar.expr.Expr} the parser produces —
 * every variable is a dense slot, every literal is pre-parsed, and every operand has a
 * checked type, so the hot path does no string work and no boxing.
 *
 * <p>The hierarchy is sealed so the evaluator switches exhaustively. Comparisons are
 * split by operand type because the legal operators differ: numbers admit all six
 * comparators, while strings and booleans admit only equality.
 */
public sealed interface Cond
        permits Cond.And, Cond.Or, Cond.Not,
                Cond.NumCmp, Cond.StrCmp, Cond.BoolCmp,
                Cond.BoolVar, Cond.BoolLit, Cond.BoolPapi {

    /** Short-circuiting logical AND. */
    record And(Cond left, Cond right) implements Cond {}

    /** Short-circuiting logical OR. */
    record Or(Cond left, Cond right) implements Cond {}

    /** Logical negation. */
    record Not(Cond operand) implements Cond {}

    /** A numeric comparison {@code left op right} (any of the six comparators). */
    record NumCmp(NumExpr left, Cmp op, NumExpr right) implements Cond {}

    /** A string (in)equality test; {@code equal} is {@code true} for {@code ==}, {@code false} for {@code !=}. */
    record StrCmp(StrExpr left, boolean equal, StrExpr right) implements Cond {}

    /** A boolean (in)equality test; {@code equal} is {@code true} for {@code ==}, {@code false} for {@code !=}. */
    record BoolCmp(Cond left, boolean equal, Cond right) implements Cond {}

    /** A boolean variable resolved to its dense {@code FactBuffer} flag slot. */
    record BoolVar(int slot) implements Cond {}

    /** A boolean literal. */
    record BoolLit(boolean value) implements Cond {}

    /**
     * A PlaceholderAPI token used in a boolean context — produced when a placeholder
     * is compared to a boolean (e.g. {@code %essentials_afk% == true}). The engine
     * resolves the placeholder at evaluation time and reads its result as truthy
     * ({@code true/yes/on/1}); an absent placeholder is {@code false}. The {@code raw}
     * token is the {@code %...%} text without the surrounding percents.
     */
    record BoolPapi(String raw) implements Cond {}
}
