package engine.spec;

/**
 * One declared target slot of an effect: a {@code name} the effect reads via
 * {@code EffectCtx.targets(name)} and the {@code selectorType} (a {@link T}
 * constant or a custom selector head) that resolves it (docs/architecture.md §7).
 * The compiler resolves the selector once; the hot path never parses it.
 *
 * @param name         the slot name the effect uses to read its targets
 * @param selectorType the selector kind that fills the slot (e.g. {@link T#AOE})
 */
public record TargetSpec(String name, String selectorType) {
}
