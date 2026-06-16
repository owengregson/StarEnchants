package com.starenchants.schema.grammar.expr;

/**
 * The six relational comparators of the condition-expression language, used by
 * {@link Expr.Compare} (docs/architecture.md §3.2).
 *
 * <p>This is pure syntax: a {@code Cmp} records <em>which</em> comparison was
 * written, not how to evaluate it (numbers, strings, and booleans each get their
 * own semantics in se-compile/se-engine, not here). Each constant carries the
 * exact operator lexeme so a diagnostic or {@code /se docs} can echo what the
 * author typed.
 */
public enum Cmp {
    /** {@code ==} — equality. */
    EQ("=="),
    /** {@code !=} — inequality. */
    NE("!="),
    /** {@code <} — strictly less than. */
    LT("<"),
    /** {@code <=} — less than or equal. */
    LE("<="),
    /** {@code >} — strictly greater than. */
    GT(">"),
    /** {@code >=} — greater than or equal. */
    GE(">=");

    private final String symbol;

    Cmp(String symbol) {
        this.symbol = symbol;
    }

    /** The operator as written in source (e.g. {@code "<="}). */
    public String symbol() {
        return symbol;
    }
}
