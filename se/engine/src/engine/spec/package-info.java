/**
 * The effect SPI's declarative surface (docs/architecture.md §7). {@link engine.spec.EffectSpec}
 * wraps the schema {@code ParamSpec} and adds the engine-specific facts — the declared
 * {@link compile.model.Affinity} and the {@link engine.spec.TargetSpec} slots — so one
 * declaration drives validation, completion, docs, migration, the affinity fold, and
 * selector resolution. {@link engine.spec.T} names the built-in selector kinds.
 */
package engine.spec;
