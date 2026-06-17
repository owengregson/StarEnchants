package migrate.model;

/**
 * One AdvancedEnchantments condition line after translation (docs/architecture.md §10). AE conditions
 * are {@code LEFT : RESULT} where RESULT is flow control. A line that becomes a StarEnchants allow-gate
 * carries its compiled {@code expr} (the {@code %stop%} form already negated); a line that cannot map
 * faithfully (a {@code %force%}/{@code %chance%} result, a {@code contains}/{@code matchesregex} operator,
 * or a variable with no StarEnchants fact) carries a {@code todo} reason instead. Exactly one is non-null.
 *
 * @param expr the StarEnchants condition expression (an allow-gate), or {@code null} if unmapped
 * @param todo why the line could not be mapped (for a {@code # TODO} comment), or {@code null} if mapped
 */
public record MigratedCondition(String expr, String todo) {

    public static MigratedCondition mapped(String expr) {
        return new MigratedCondition(expr, null);
    }

    public static MigratedCondition todo(String reason) {
        return new MigratedCondition(null, reason);
    }

    public boolean mapped() {
        return expr != null;
    }
}
