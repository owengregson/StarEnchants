/**
 * The content compiler: authored YAML+DSL &rarr; an immutable validated {@link compile.model.Snapshot}
 * via the lower / resolve / erase / snapshot stages (docs/architecture.md §2). PURE (zero Bukkit) so it is
 * deterministically testable; cross-version resolution arrives via the injected
 * {@link compile.resolve.PlatformResolvers} facade (§2.1).
 */
package compile;
