package platform.content;

import java.util.List;
import java.util.Objects;
import schema.diag.Diagnostic;

/**
 * One parallel config source reloaded in the SAME transaction as the content {@link compile.load.Library}
 * (docs/v3-directives.md §L). {@link #build()} runs off the main thread (pure parse); its blocking diagnostic
 * aborts the entire reload, so a broken config never half-swaps (content + every source keep their value).
 */
public interface ReloadStep {

    /** Parse off the main thread; returns diagnostics + a global-thread publish action. */
    Built build();

    /**
     * The off-thread build result for one source: diagnostics plus a {@code publish} action run on the
     * global thread iff the transaction commits.
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
