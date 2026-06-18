package platform.content;

import java.util.List;
import java.util.Objects;
import schema.diag.Diagnostic;

/**
 * One parallel config source ({@code items/}, {@code config.yml}, {@code lang.yml}, {@code menus/}) reloaded
 * in the SAME transaction as the content {@link compile.load.Library} (docs/v3-directives.md §L). The plugin
 * registers one step per source with the {@link ContentReloader}.
 *
 * <p>{@link #build()} runs OFF the main thread (pure file I/O + parse, touching no regionized state, like
 * {@link compile.load.LibraryLoader}); it returns the parsed source's diagnostics plus a {@code publish}
 * action the reloader runs on the GLOBAL thread — and only when the whole transaction commits. So a broken
 * config never half-swaps: its blocking diagnostic aborts the entire reload (content + every source keep
 * their previous value) and surfaces in {@code /se reload [--dry-run]} alongside content faults.
 */
public interface ReloadStep {

    /** Parse the source off the main thread; returns its diagnostics + a global-thread publish action. */
    Built build();

    /**
     * The off-thread build result for one source: the parsed snapshot's diagnostics and the action that
     * swaps it into its published holder (run on the global thread iff the transaction commits).
     *
     * @param diagnostics every diagnostic raised parsing this source
     * @param publish     swaps the parsed snapshot into its holder (global thread, commit only)
     */
    record Built(List<Diagnostic> diagnostics, Runnable publish) {
        public Built {
            diagnostics = List.copyOf(diagnostics);
            Objects.requireNonNull(publish, "publish");
        }

        /** Whether this source produced a blocking diagnostic (which aborts the whole transaction). */
        public boolean hasErrors() {
            return diagnostics.stream().anyMatch(Diagnostic::blocking);
        }
    }
}
