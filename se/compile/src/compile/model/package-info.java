/**
 * The compiled world — the immutable data the engine consumes (docs/architecture.md
 * §4). These types are the contract between {@code se-compile} (which produces a
 * {@link compile.model.Snapshot}) and {@code se-engine} (which walks it): the
 * source-erased {@link compile.model.Ability}, its flyweight
 * {@link compile.model.CompiledEffect}/{@link compile.model.CompiledCondition}/
 * {@link compile.model.CompiledSelector}, the {@link compile.model.Interner}
 * tables, and the {@link compile.model.StableKeyIndex} / {@link compile.model.SourceMap}
 * side-indices.
 *
 * <p>Everything here is pure (zero Bukkit) and immutable once a snapshot is
 * published. {@code se-engine} binds the {@code head} strings to runtime kind
 * instances when it loads a snapshot; nothing here parses or schedules.
 */
package compile.model;
