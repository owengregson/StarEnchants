package compile.stage;

import compile.model.Snapshot;
import schema.diag.Diagnostics;

/**
 * Stage 5: assembles the immutable {@link Snapshot} from erased content — stamps the generation, freezes
 * in the collected diagnostics, and produces the single artifact the runtime swaps in by reference
 * (docs/architecture.md §4.5, §10). Pure assembly; adds no faults beyond what is already in {@code diags}.
 */
public interface SnapshotStage {

    Snapshot assemble(ErasedContent erased, Diagnostics diags, int generation);
}
