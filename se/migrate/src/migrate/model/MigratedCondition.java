package migrate.model;

/**
 * One AdvancedEnchantments condition line ({@code LEFT : RESULT}) after translation (docs/architecture.md
 * §10). It maps to either a boolean <em>gate</em> ({@code %allow%}/{@code %continue%}, or negated
 * {@code %stop%}) or a top-level flow/chance <em>clause</em> ({@code %force%} / {@code ±N %chance%}); an
 * unmappable line carries a {@code todo} reason instead. Exactly one of {@code expr}/{@code todo} is non-null.
 *
 * <p>{@code clauseForm} marks a clause: it carries a top-level {@code : <outcome>} tail and so cannot be
 * {@code &&}-joined (a condition admits at most one), whereas plain gates combine freely — the reader uses
 * this to fold a level's multiple condition lines.
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
