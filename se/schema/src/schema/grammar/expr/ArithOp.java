package schema.grammar.expr;

/**
 * A binary arithmetic operator in the expression sublanguage ({@code + - * /}), used by
 * {@link Expr.Arith}. Like {@link Cmp}, this is pure syntax produced by the parser; typing and
 * lowering to the runtime numeric AST are se-compile's job (docs/architecture.md §2, §3.4).
 *
 * <p>Arithmetic operands appear wherever a numeric value is expected — both inside a condition
 * comparison (e.g. {@code %actor.health% < %actor.maxhealth% / 2}) and as an
 * <em>expression-valued effect argument</em> (e.g. {@code DAMAGE_MOD:attack:add:%combo% * 10}).
 */
public enum ArithOp {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/");

    private final String symbol;

    ArithOp(String symbol) {
        this.symbol = symbol;
    }

    /** The source symbol of this operator, for diagnostics and round-tripping. */
    public String symbol() {
        return symbol;
    }
}
