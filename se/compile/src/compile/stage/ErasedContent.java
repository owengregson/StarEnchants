package compile.stage;

import compile.model.Ability;
import compile.model.Interners;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;

/**
 * The output of {@link EraseStage}: the dense {@link Ability} array plus the {@link Interners},
 * {@link StableKeyIndex}, and {@link SourceMap} built alongside it (docs/architecture.md §4.1, §5.3).
 * Erasure builds the source map because it is the last stage still holding each ability's authored
 * {@code Source} (the runtime {@link Ability} carries none).
 *
 * <p>Invariant: {@code abilities[i].id() == i} and {@code stableKeys.keyOf(i)} is {@code abilities[i]}'s key.
 *
 * @param sourceMap defId &rarr; authored origin, for op-visible diagnostics (§10)
 */
public record ErasedContent(
        Ability[] abilities,
        Interners interners,
        StableKeyIndex stableKeys,
        SourceMap sourceMap) {
}
