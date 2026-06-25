package compile.stage;

import compile.model.Snapshot;
import schema.diag.Diagnostics;
import java.util.Objects;

/**
 * The default {@link SnapshotStage}: freezes erased content, diagnostics, and a generation counter into
 * the immutable {@link Snapshot} the runtime swaps in by reference (docs/architecture.md §4.5, §10).
 * The publish boundary — pure assembly (no new diagnostics); the caller discards the result if
 * {@code diags.hasErrors()}, leaving the previous snapshot live.
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
