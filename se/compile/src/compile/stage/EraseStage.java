package compile.stage;

import schema.diag.Diagnostics;
import java.util.List;

/**
 * Stage 4 — source erasure (docs/architecture.md §4.1). Lowers all five sources into one dense
 * {@link compile.model.Ability} array, assigning per-snapshot dense ids and interning names into
 * bit-packed runtime fields ({@code worldBlacklist} long, {@code triggerMask} int, scope ids). Also
 * builds the {@link compile.model.StableKeyIndex} (§5.3) and {@link compile.model.SourceMap}.
 *
 * <p>Never throws: a duplicate stable key (dropped) or a world/trigger past its bitset width
 * (64 worlds / 32 triggers, that name skipped) is reported into {@code diags}.
 */
public interface EraseStage {

    /** @param lowered the lowered abilities, in the order ids should be assigned */
    ErasedContent erase(List<LoweredAbility> lowered, Diagnostics diags);
}
