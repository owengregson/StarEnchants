package compile.stage;

import compile.model.Snapshot;
import schema.diag.Diagnostics;

/**
 * Stage 5: assembles the immutable {@link Snapshot} from erased content (docs/architecture.md §4.5).
 * Pure assembly; adds no faults beyond what is already in {@code diags}.
 */
public interface SnapshotStage {

    Snapshot assemble(ErasedContent erased, Diagnostics diags, int generation);
}
