/**
 * The activation pipeline: the fixed Cosmic Enchants-style gate sequence (docs/architecture.md §3.3) as a
 * pure, kernel-internal stage. {@link engine.pipeline.ActivationPipeline} runs an
 * {@link compile.model.Ability} through gates 1–11 against an {@link engine.pipeline.Activation},
 * returning a {@link engine.pipeline.GateOutcome}. The cross-version/Bukkit gates (protection,
 * {@code PreActivate}) are injected guards so the core stays unit-testable with no server.
 */
package engine.pipeline;
