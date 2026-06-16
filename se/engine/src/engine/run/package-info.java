/**
 * The runtime execution path (docs/architecture.md §3.3, §3.5): the glue that turns an
 * {@code ACTIVATED} gate outcome into world mutations. {@link engine.run.AbilityExecutor} walks the
 * candidate abilities for an event, runs each through the {@link engine.pipeline.ActivationPipeline},
 * and for every ability that passes resolves its effects' target selectors, builds the read-only
 * {@link engine.effect.EffectCtx}, and runs each {@code EffectKind} into the {@link engine.sink.DispatchSink}
 * with that effect's declared affinity — emitting intents, never touching the world. The caller flushes
 * the sink once after the gate walk (§3.6).
 *
 * <p>This layer is pure orchestration over already-verified pieces (the gate pipeline, the pure
 * selector/effect kinds, and the matrix-verified dispatcher); the only Bukkit objects it handles are
 * the activation's actors, passed straight through to the contexts. Area scans for {@code AOE}/
 * {@code NEAREST} are an injected {@link engine.run.AreaScan} so the Folia-correct, region-scoped scan
 * stays a dependency rather than baked into the pure executor.
 */
package engine.run;
