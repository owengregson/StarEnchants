package platform.content;

import java.util.List;
import schema.diag.Diagnostic;

/**
 * The outcome of one {@link ContentReloader} build, reported to the caller on the global thread so a
 * {@code /se reload} command can message the operator (docs/architecture.md §10). Immutable.
 *
 * @param published      whether the new library was swapped in (clean, non-dry-run); a fatal build keeps the old one
 * @param dryRun         whether this was a {@code --dry-run} (never publishes, only reports)
 * @param generation     the generation stamped on the built snapshot
 * @param abilityCount   how many abilities the build produced
 * @param diagnostics    every diagnostic from the build, in order
 */
public record ReloadResult(boolean published, boolean dryRun, int generation, int abilityCount,
                           List<Diagnostic> diagnostics) {

    public ReloadResult {
        diagnostics = List.copyOf(diagnostics);
    }

    /** The number of blocking (error) diagnostics — non-zero means the build was kept out. */
    public long errorCount() {
        return diagnostics.stream().filter(Diagnostic::blocking).count();
    }
}
