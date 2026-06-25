package compile.stage;

import compile.model.Ability;
import compile.model.Interners;
import compile.model.SourceMap;
import compile.model.StableKeyIndex;

/**
 * The output of {@link EraseStage} (docs/architecture.md §4.1). Erasure builds the {@link SourceMap}
 * because it is the last stage still holding each ability's authored {@code Source} (the runtime
 * {@link Ability} carries none).
 *
 * <p>Invariant: {@code abilities[i].id() == i} and {@code stableKeys.keyOf(i)} is {@code abilities[i]}'s key.
 */
public record ErasedContent(
        Ability[] abilities,
        Interners interners,
        StableKeyIndex stableKeys,
        SourceMap sourceMap) {
}
