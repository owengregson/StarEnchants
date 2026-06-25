package schema.grammar.expr;

/** The six relational comparators, pure syntax used by {@link Expr.Compare} (docs/architecture.md §3.2). */
public enum Cmp {
    EQ("=="),
    NE("!="),
    LT("<"),
    LE("<="),
    GT(">"),
    GE(">=");

    private final String symbol;

    Cmp(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
