package schema.grammar.expr;

/**
 * String-domain binary operators used by {@link Expr.StringMatch} (docs/architecture.md §3.4).
 * Kept separate from {@link Cmp} so the numeric comparison path never carries string-only operators.
 */
public enum StrOp {
    /** {@code contains} — true if left contains any {@code |}-separated alternative on the right. */
    CONTAINS("contains"),
    /** {@code matchesregex} — true if left fully matches the (literal) regex on the right. */
    MATCHES_REGEX("matchesregex");

    private final String symbol;

    StrOp(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
