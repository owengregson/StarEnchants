package compile.model.cond;

/**
 * A numeric-valued operand of a compiled condition (docs/architecture.md §3.4): a
 * resolved {@code FactBuffer} slot, a constant, or a PlaceholderAPI token parsed to a
 * number at evaluation time. Pure data — the runtime walks it over a primitive fact
 * buffer with no parsing on the hot path.
 */
public sealed interface NumExpr permits NumExpr.Var, NumExpr.Lit, NumExpr.Papi {

    /** A numeric variable resolved to its dense {@code FactBuffer} number slot. */
    record Var(int slot) implements NumExpr {}

    /** A numeric literal, pre-parsed at compile time. */
    record Lit(double value) implements NumExpr {}

    /**
     * A PlaceholderAPI token used in a numeric comparison; the engine resolves the
     * placeholder and parses it to a double only when this node is reached, and only
     * if PlaceholderAPI is present (§3.4). The {@code raw} token is the original
     * {@code %...%} text (without the surrounding percents).
     */
    record Papi(String raw) implements NumExpr {}
}
