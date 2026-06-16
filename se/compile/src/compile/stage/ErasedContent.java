package compile.stage;

import compile.model.Ability;
import compile.model.Interners;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;

/**
 * The output of {@link EraseStage}: the dense {@link Ability} array together with the
 * {@link Interners} populated while building it, the {@link StableKeyIndex} that maps
 * each ability's stable key to its dense id (docs/architecture.md §4.1, §5.3), and
 * the {@link SourceMap} recording where each def was authored. Erasure builds the
 * source map because it is the last stage that still holds each ability's authored
 * {@code Source} (the runtime {@link Ability} deliberately does not carry one).
 * {@link SnapshotStage} assembles all of this (plus diagnostics and a generation)
 * into the published {@link compile.model.Snapshot}.
 *
 * <p>Invariant: {@code abilities[i].id() == i} and
 * {@code stableKeys.keyOf(i)} is {@code abilities[i]}'s stable key.
 *
 * @param abilities  the dense ability array
 * @param interners  the populated world/trigger/suppress/cooldown-scope tables
 * @param stableKeys the stable-key &harr; dense-id index
 * @param sourceMap  defId &rarr; authored origin, for op-visible diagnostics (§10)
 */
public record ErasedContent(
        Ability[] abilities,
        Interners interners,
        StableKeyIndex stableKeys,
        SourceMap sourceMap) {
}
