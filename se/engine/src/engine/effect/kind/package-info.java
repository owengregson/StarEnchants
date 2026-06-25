/**
 * The concrete effect kinds — the merged Cosmic Enchants-style catalog (docs/architecture.md §3.1,
 * §7). Each is a small stateless class declaring its {@link engine.spec.EffectSpec}
 * and emitting intents through the {@link engine.sink.Sink}; none touches an entity,
 * block, or scheduler directly. {@link engine.effect.kind.BuiltinEffects} is the one
 * greppable file that registers them all.
 */
package engine.effect.kind;
