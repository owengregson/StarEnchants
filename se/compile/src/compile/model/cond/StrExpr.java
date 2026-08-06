package compile.model.cond;

/**
 * A string-valued operand of a compiled condition (docs/architecture.md §3.4): a
 * resolved {@code FactBuffer} string slot, a constant, or a PlaceholderAPI token.
 * String operands may only be compared for (in)equality, never ordered.
 */
public sealed interface StrExpr permits StrExpr.Var, StrExpr.Lit, StrExpr.Papi, StrExpr.SubjectStr {

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

    /**
     * A string fact of the SUBJECT CURSOR — {@code %target.type%} / {@code %target.relation%} (ADR-0076).
     * Slot-less for the same reason {@link NumExpr.SubjectNum} is: the cursor re-points per body inside one
     * activation, so the value cannot live in a populated slot.
     */
    record SubjectStr(SubjectText fact) implements StrExpr {}

    /** The slot-less string facts {@link SubjectStr} can name. */
    enum SubjectText {
        /** The bound body's {@code EntityType} name — a read the selector's own filter already made. */
        TYPE,
        /** {@code ALLY} | {@code ENEMY} | {@code NEUTRAL} (non-player body), through the ONE alliance hook. */
        RELATION
    }
}
