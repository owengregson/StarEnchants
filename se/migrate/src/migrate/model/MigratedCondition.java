package migrate.model;

/**
 * One AdvancedEnchantments condition line ({@code LEFT : RESULT}) after translation. {@code clauseForm}
 * marks a flow/chance clause ({@code : <outcome>} tail): it cannot be {@code &&}-joined (a condition admits
 * one), so the reader keeps it separate from joinable gates. Exactly one of {@code expr}/{@code todo} is set.
 */
public record MigratedCondition(String expr, String todo, boolean clauseForm) {

    /** A plain boolean gate (joinable with {@code &&}). */
    public static MigratedCondition mapped(String expr) {
        return new MigratedCondition(expr, null, false);
    }

    /** A flow/chance clause ({@code <expr> : %force%} / {@code <expr> : ±N %chance%}); not joinable. */
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
