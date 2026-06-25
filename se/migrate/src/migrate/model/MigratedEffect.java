package migrate.model;

/**
 * One legacy effect token after translation. {@code se} is a valid SE token or {@code null} (unmapped →
 * {@code # TODO}); {@code legacy} is the original and {@code note} the rationale — both become review
 * comments in the emitted YAML.
 */
public record MigratedEffect(String legacy, String se, String note) {

    /** A faithfully translated effect carrying a valid StarEnchants token. */
    public static MigratedEffect mapped(String legacy, String se, String note) {
        return new MigratedEffect(legacy, se, note);
    }

    /** An effect with no StarEnchants equivalent (or an unsafe translation) — flagged for manual porting. */
    public static MigratedEffect todo(String legacy, String note) {
        return new MigratedEffect(legacy, null, note);
    }

    public boolean mapped() {
        return se != null;
    }
}
