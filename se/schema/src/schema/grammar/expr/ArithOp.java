package schema.grammar.expr;

/** A binary arithmetic operator ({@code + - * /}), pure syntax produced by the parser (docs/architecture.md §3.4). */
public enum ArithOp {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/");

    private final String symbol;

    ArithOp(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }
}
