/**
 * The effect SPI (docs/architecture.md §3.1, §3.5, §7): the stateless
 * {@link engine.effect.EffectKind} an author implements, the read-only
 * {@link engine.effect.EffectCtx} it runs against, and the explicit
 * {@link engine.effect.EffectRegistry} that wires kinds and bridges the compiler
 * (handing it a {@code SpecRegistry} + affinity lookup so {@code se-compile} stays
 * pure). Concrete kinds live in {@code engine.effect.kind}.
 */
package engine.effect;
