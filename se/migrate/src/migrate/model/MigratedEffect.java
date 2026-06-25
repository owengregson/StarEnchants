package migrate.model;

/**
 * One legacy effect token after translation (docs/architecture.md §10). Either {@code se} holds a valid
 * SE effect token (e.g. {@code DAMAGE:6:@Victim}) or it is {@code null} (the writer emits a {@code # TODO}
 * and {@link migrate.Migrator} records a diagnostic). {@code legacy} is the original token; {@code note}
 * explains the translation or why it was skipped — both surface as review comments in the emitted YAML.
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
