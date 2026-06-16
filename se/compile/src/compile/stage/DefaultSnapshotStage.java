package compile.stage;

import compile.model.Snapshot;
import schema.diag.Diagnostics;
import java.util.Objects;

/**
 * The default {@link SnapshotStage}: freezes the erased content, the collected
 * diagnostics, and a generation counter into the immutable {@link Snapshot} the
 * runtime swaps in by reference (docs/architecture.md §4.5, §10).
 *
 * <p>Pure assembly — it introduces no new diagnostics of its own. It is the publish
 * boundary: after this returns, the snapshot is immutable and safe to hand to the
 * engine (or to discard, if {@code diags.hasErrors()}, leaving the previous snapshot
 * live).
 */
public final class DefaultSnapshotStage implements SnapshotStage {

    @Override
    public Snapshot assemble(ErasedContent erased, Diagnostics diags, int generation) {
        Objects.requireNonNull(erased, "erased");
        Objects.requireNonNull(diags, "diags");
        return new Snapshot(
                generation,
                erased.abilities(),
                erased.stableKeys(),
                erased.interners(),
                erased.sourceMap(),
                diags.all());
    }
}
