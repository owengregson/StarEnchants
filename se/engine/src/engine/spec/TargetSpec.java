package engine.spec;

/**
 * One declared target slot of an effect: a {@code name} the effect reads via {@code EffectCtx.targets(name)}
 * and the {@code selectorType} ({@link T} constant or custom head) that resolves it (§7). The compiler
 * resolves the selector once; the hot path never parses it.
 */
public record TargetSpec(String name, String selectorType) {
}
