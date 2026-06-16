package compile.stage;

import schema.diag.Diagnostics;
import java.util.List;

/**
 * Stage 4 of the compiler — source erasure (docs/architecture.md §4.1). Takes the
 * lowered abilities from all five sources and produces one dense
 * {@link compile.model.Ability} array, assigning each a per-snapshot dense id and
 * interning its names into bit-packed runtime fields: world names &rarr; the
 * {@code worldBlacklist} long, trigger names &rarr; the {@code triggerMask} int,
 * the suppression key and cooldown scopes &rarr; interned ids. It also builds the
 * {@link compile.model.StableKeyIndex} so items resolve by stable key (§5.3) and the
 * {@link compile.model.SourceMap} of authored origins (it is the last stage holding
 * each ability's {@code Source}).
 *
 * <p>Never throws. A duplicate stable key (the duplicate is dropped), or a
 * world/trigger overflowing its bitset width (64 worlds / 32 triggers, that name
 * skipped), is reported into {@code diags}.
 */
public interface EraseStage {

    /**
     * Erase the five sources into one ability array.
     *
     * @param lowered the lowered abilities, in the order ids should be assigned
     * @param diags   the collector for any faults
     * @return the dense ability array plus the interners and stable-key index
     */
    ErasedContent erase(List<LoweredAbility> lowered, Diagnostics diags);
}
