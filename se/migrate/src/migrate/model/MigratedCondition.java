package migrate.model;

/**
 * One AdvancedEnchantments condition line after translation (docs/architecture.md §10). AE conditions
 * are {@code LEFT : RESULT} where RESULT is flow control. A line maps to either a StarEnchants boolean
 * <em>gate</em> (the {@code %allow%}/{@code %continue%} form, or the negated {@code %stop%} form) or a
 * top-level flow/chance <em>clause</em> (the {@code %force%} or {@code ±N %chance%} form, written
 * {@code <expr> : %force%} / {@code <expr> : +N %chance%}). A line that cannot map faithfully (a
 * {@code contains}/{@code matchesregex} operator or a variable with no StarEnchants fact) carries a
 * {@code todo} reason instead. Exactly one of {@code expr}/{@code todo} is non-null.
 *
 * <p>{@code clauseForm} distinguishes the two mapped shapes: a clause carries a top-level
 * {@code : <outcome>} tail and therefore cannot be parenthesised and {@code &&}-joined with other lines
 * (a condition expression admits at most one clause), whereas plain gates combine freely. The reader
 * uses this to decide how to fold a level's multiple condition lines.
 *
 * @param expr       the StarEnchants condition expression, or {@code null} if unmapped
 * @param todo       why the line could not be mapped (for a {@code # TODO} comment), or {@code null} if mapped
 * @param clauseForm {@code true} if {@code expr} is a top-level flow/chance clause (not joinable)
 */
public record MigratedCondition(String expr, String todo, boolean clauseForm) {

    /** A plain boolean gate (joinable with {@code &&}). */
    public static MigratedCondition mapped(String expr) {
        return new MigratedCondition(expr, null, false);
    }

    /** A top-level flow/chance clause ({@code <expr> : %force%} / {@code <expr> : ±N %chance%}); not joinable. */
    public static MigratedCondition clause(String expr) {
        return new MigratedCondition(expr, null, true);
    }

    public static MigratedCondition todo(String reason) {
        return new MigratedCondition(null, reason, false);
    }

    public boolean mapped() {
        return expr != null;
    }
}
