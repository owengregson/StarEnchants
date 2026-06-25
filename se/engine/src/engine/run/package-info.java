/**
 * The runtime execution path (docs/architecture.md §3.3, §3.5): glue that turns an {@code ACTIVATED} gate
 * outcome into world mutations. {@link engine.run.AbilityExecutor} runs each candidate ability through the
 * {@link engine.pipeline.ActivationPipeline} and emits the activated ones' effects into the
 * {@link engine.sink.DispatchSink} without touching the world; the caller flushes once (§3.6).
 *
 * <p>Pure orchestration over already-verified pieces. World-touching scans are an injected
 * {@link engine.run.AreaScan} so the Folia-correct, region-scoped scan stays a dependency rather than baked
 * into the executor.
 */
package engine.run;
