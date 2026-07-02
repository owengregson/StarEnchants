package platform.content;

import java.util.List;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import schema.diag.Source;

/**
 * The outcome of one {@link ContentReloader} attempt (docs/architecture.md §10). Immutable.
 * {@link #generation}/{@link #abilityCount} describe the <em>candidate</em> build; they are live values
 * only when {@link #published}, else they describe the build that was thrown away.
 *
 * @param published    whether the new library was swapped in (clean, non-dry-run)
 * @param dryRun       whether this was a {@code --dry-run} (never publishes, only reports)
 * @param generation   the generation stamped on the candidate snapshot ({@code -1} for busy/failure)
 * @param abilityCount how many abilities the candidate build produced ({@code 0} for busy/failure)
 * @param diagnostics  every diagnostic from the build, in order
 * @param failure      the off-thread build's throwable, {@code null} unless this is a failure result (ADR-0042)
 */
public record ReloadResult(boolean published, boolean dryRun, int generation, int abilityCount,
                           List<Diagnostic> diagnostics, Throwable failure) {

    public ReloadResult {
        diagnostics = List.copyOf(diagnostics);
    }

    /** The common case: a result that is not a build crash. */
    public ReloadResult(boolean published, boolean dryRun, int generation, int abilityCount,
                        List<Diagnostic> diagnostics) {
        this(published, dryRun, generation, abilityCount, diagnostics, null);
    }

    /** The number of blocking (error) diagnostics — non-zero means the build was kept out. */
    public long errorCount() {
        return diagnostics.stream().filter(Diagnostic::blocking).count();
    }

    /** True when rejected by single-flight, not by content faults (auto-reload logs this at FINE). */
    public boolean isBusy() {
        return diagnostics.stream().anyMatch(d -> d.is(DiagCode.E_RELOAD_BUSY));
    }

    /** A reload rejected because one is already in flight (single-flight). */
    public static ReloadResult busy() {
        return new ReloadResult(false, false, -1, 0,
                List.of(Diagnostic.error(DiagCode.E_RELOAD_BUSY, "a content reload is already in progress",
                        Source.UNKNOWN)), null);
    }

    /** A reload whose off-thread build threw (e.g. an I/O fault), so nothing was published. */
    public static ReloadResult failure(Throwable cause) {
        return new ReloadResult(false, false, -1, 0,
                List.of(Diagnostic.error(DiagCode.E_RELOAD_FAILED, "reload build failed: " + cause,
                        Source.UNKNOWN)), cause);
    }
}
