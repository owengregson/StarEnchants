/**
 * The content compiler: authored YAML+DSL → an immutable validated Snapshot.
 *
 * <p>This module is PURE (zero Bukkit) so it is deterministically testable;
 * cross-version resolution arrives via the injected
 * {@link compile.resolve.PlatformResolvers} facade
 * (docs/architecture.md §2.1). The first slice present here is the line-compile
 * seam: {@link compile.LineCompiler} resolves a head via the
 * explicit {@link compile.SpecRegistry} and validates arguments
 * into a typed {@link compile.CompiledLine}. Later stages
 * (resolve / typecheck / lower / erase / snapshot, per docs/architecture.md §2)
 * build on this toward source erasure into one {@code Ability[]}.
 */
package compile;
