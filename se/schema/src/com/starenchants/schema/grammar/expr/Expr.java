package com.starenchants.schema.grammar.expr;

import com.starenchants.schema.diag.Source;

/**
 * The untyped condition-expression AST: a small, immutable, data-only tree of
 * flyweight nodes (docs/architecture.md §3.2, §3.4).
 *
 * <p>A StarEnchants condition is an <em>expression</em> in a tiny boolean/relational
 * language — {@code && || ! ( )}, the six comparators, {@code %scope.name%}
 * variables (PlaceholderAPI passthrough included), and number/boolean/string
 * literals. This package produces only the <em>shape</em> of that expression; it
 * carries no types and performs no evaluation. Typing (against the variable
 * vocabulary), name resolution, and lowering to the runtime's pre-built flyweight
 * condition AST are se-compile's job, never the parser's (docs/architecture.md §2,
 * "produces untyped AST (data only)").
 *
 * <p>The hierarchy is sealed so the compiler can exhaustively switch over it:
 * <ul>
 *   <li>{@link Or}, {@link And} — short-circuiting boolean combinators (binary).
 *   <li>{@link Not} — boolean negation (unary).
 *   <li>{@link Compare} — a relational test {@code left op right} with a {@link Cmp}.
 *   <li>{@link VarRef} — a {@code %scope.name%} variable; an unrecognised or
 *       PlaceholderAPI token is still a {@code VarRef}, resolved later, never
 *       rejected here.
 *   <li>{@link NumberLit}, {@link BoolLit}, {@link StringLit} — literals.
 * </ul>
 *
 * <p>Every node carries the {@link Source} of its first character so se-compile can
 * attach an argument-precise {@link com.starenchants.schema.diag.Diagnostic} when a
 * later stage rejects, e.g., an unknown variable or a type mismatch.
 */
public sealed interface Expr
        permits Expr.Or, Expr.And, Expr.Not, Expr.Compare,
                Expr.VarRef, Expr.NumberLit, Expr.BoolLit, Expr.StringLit {

    /** The source position of this node's first character, for diagnostics. */
    Source source();

    /**
     * Logical OR ({@code ||}) — the lowest-precedence binary operator.
     *
     * <p>{@code source} is the position of the left operand (the start of the whole
     * sub-expression), matching how the parser threads spans.
     */
    record Or(Expr left, Expr right, Source source) implements Expr {}

    /** Logical AND ({@code &&}), binding tighter than {@link Or}. */
    record And(Expr left, Expr right, Source source) implements Expr {}

    /**
     * Logical negation ({@code !}), the unary operator binding tighter than the
     * comparators. {@code source} points at the {@code !} token.
     */
    record Not(Expr operand, Source source) implements Expr {}

    /**
     * A relational comparison {@code left op right} (e.g. {@code %victim.health% < 5}).
     *
     * <p>Comparators are non-associative in this grammar: {@code a < b < c} is a
     * parse error rather than a silently-chained expression. {@code source} points
     * at the start of the left operand.
     */
    record Compare(Expr left, Cmp op, Expr right, Source source) implements Expr {}

    /**
     * A {@code %scope.name%} variable reference. The {@code scope} is optional: a
     * bare {@code %name%} parses with a {@code null} scope. Only the <em>first</em>
     * dot splits scope from name, so {@code %a.b.c%} yields {@code scope="a"},
     * {@code name="b.c"} — PlaceholderAPI tokens (which routinely contain dots and
     * underscores) survive intact for later passthrough resolution.
     *
     * <p>This stage never decides whether a variable is known; an unrecognised or
     * PAPI token is a perfectly valid {@code VarRef} (docs/architecture.md §3.4).
     */
    record VarRef(String scope, String name, Source source) implements Expr {}

    /** A numeric literal, kept as its raw lexeme (e.g. {@code "3"}, {@code "1.5"}). */
    record NumberLit(String raw, Source source) implements Expr {}

    /** A boolean literal: {@code true} or {@code false}. */
    record BoolLit(boolean value, Source source) implements Expr {}

    /**
     * A quoted string literal with its already-unescaped {@code value} (the
     * surrounding quotes and {@code \} escapes have been processed by the
     * tokenizer).
     */
    record StringLit(String value, Source source) implements Expr {}
}
