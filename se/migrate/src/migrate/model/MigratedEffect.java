package migrate.model;

/**
 * One legacy effect token after translation (docs/architecture.md §10). Either it MAPPED — {@code se}
 * holds a valid StarEnchants effect token (e.g. {@code DAMAGE:6:@Victim}) — or it did not, in which
 * case {@code se} is {@code null} and {@link #mapped()} is false (the writer emits a {@code # TODO}
 * line instead of a list entry, and the {@link migrate.Migrator} records a diagnostic). {@code legacy}
 * is always the original token, and {@code note} explains the translation or why it was skipped, both
 * surfaced as review comments in the emitted YAML.
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

    /** Whether this effect translated to a usable StarEnchants token. */
    public boolean mapped() {
        return se != null;
    }
}
