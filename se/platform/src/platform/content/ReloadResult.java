package platform.content;

import java.util.List;
import schema.diag.Diagnostic;
import schema.diag.Source;

/**
 * The outcome of one {@link ContentReloader} attempt, reported to the caller on the global thread so
 * a {@code /se reload} command can message the operator (docs/architecture.md §10). Immutable.
 *
 * <p>{@link #generation} and {@link #abilityCount} describe the <em>candidate</em> build; they are the
 * live values only when {@link #published} is {@code true}. On a rejected build (or {@link #busy}/
 * {@link #failure}) the live content is unchanged and these describe the build that was thrown away.
 *
 * @param published    whether the new library was swapped in (clean, non-dry-run)
 * @param dryRun       whether this was a {@code --dry-run} (never publishes, only reports)
 * @param generation   the generation stamped on the candidate snapshot ({@code -1} for busy/failure)
 * @param abilityCount how many abilities the candidate build produced ({@code 0} for busy/failure)
 * @param diagnostics  every diagnostic from the build, in order
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

    /** A reload rejected because one is already in flight (single-flight). */
    public static ReloadResult busy() {
        return new ReloadResult(false, false, -1, 0,
                List.of(Diagnostic.error("reload.busy", "a content reload is already in progress", Source.UNKNOWN)));
    }

    /** A reload whose off-thread build threw (e.g. an I/O fault), so nothing was published. */
    public static ReloadResult failure(Throwable cause) {
        return new ReloadResult(false, false, -1, 0,
                List.of(Diagnostic.error("reload.failed", "reload build failed: " + cause, Source.UNKNOWN)));
    }
}
