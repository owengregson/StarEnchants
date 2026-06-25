/**
 * The compiled world — the pure, immutable {@link compile.model.Snapshot} contract between
 * {@code se-compile} and {@code se-engine} (docs/architecture.md §4). Zero Bukkit; nothing here parses or
 * schedules — the engine binds {@code head} strings to runtime kinds at snapshot load.
 */
package compile.model;
