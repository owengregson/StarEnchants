package compile.stage;

import compile.model.Snapshot;
import schema.diag.Diagnostics;

/**
 * Stage 5 of the compiler: assembles the immutable {@link Snapshot} from the erased
 * content (docs/architecture.md §4.5). It builds the {@link compile.model.SourceMap}
 * (defId &rarr; authored origin) from the abilities, stamps the generation counter,
 * and freezes the collected diagnostics in — producing the single artifact the
 * runtime swaps in by reference (§10).
 *
 * <p>Pure assembly: it adds no new faults of its own beyond what is already in
 * {@code diags}; it folds that collector into the published snapshot so callers can
 * inspect it after the swap.
 */
public interface SnapshotStage {

    /**
     * Assemble the published snapshot.
     *
     * @param erased     the dense abilities + interners + stable-key index
     * @param diags      every diagnostic collected over the whole compile
     * @param generation the build counter to stamp into the snapshot
     * @return the immutable snapshot
     */
    Snapshot assemble(ErasedContent erased, Diagnostics diags, int generation);
}
