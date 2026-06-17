package schema.grammar.expr;

/**
 * The string-domain binary operators of the condition-expression language, used by
 * {@link Expr.StringMatch} (docs/architecture.md §3.4; v3.1 §A). Kept separate from the
 * relational {@link Cmp} comparators because they are string-only and lower to distinct
 * runtime nodes — mixing them into {@code Cmp} would force the numeric comparison path to
 * carry operators it can never evaluate.
 *
 * <p>Pure syntax: a {@code StrOp} records <em>which</em> string operator was written, not
 * how to evaluate it (the {@code contains} pipe-OR membership and the {@code matchesregex}
 * full-match semantics live in se-engine). Each constant carries its source lexeme so a
 * diagnostic or {@code /se} reference can echo what the author typed.
 */
public enum StrOp {
    /** {@code contains} — true if the left string contains any {@code |}-separated alternative on the right. */
    CONTAINS("contains"),
    /** {@code matchesregex} — true if the left string fully matches the (literal) regular expression on the right. */
    MATCHES_REGEX("matchesregex");

    private final String symbol;

    StrOp(String symbol) {
        this.symbol = symbol;
    }

    /** The operator as written in source (e.g. {@code "contains"}). */
    public String symbol() {
        return symbol;
    }
}
