package compile.model.cond;

/**
 * A string-valued operand of a compiled condition (docs/architecture.md §3.4): a
 * resolved {@code FactBuffer} string slot, a constant, or a PlaceholderAPI token.
 * String operands may only be compared for (in)equality, never ordered.
 */
public sealed interface StrExpr permits StrExpr.Var, StrExpr.Lit, StrExpr.Papi {

    /** A string variable resolved to its dense {@code FactBuffer} string slot. */
    record Var(int slot) implements StrExpr {}

    /** A string literal (already unquoted/unescaped by the lexer). */
    record Lit(String value) implements StrExpr {}

    /**
     * A PlaceholderAPI token used in a string comparison; the engine resolves it only
     * when reached and only if PlaceholderAPI is present (§3.4). The {@code raw} token
     * is the original {@code %...%} text (without the surrounding percents).
     */
    record Papi(String raw) implements StrExpr {}
}
