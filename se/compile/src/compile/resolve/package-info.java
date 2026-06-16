/**
 * The injected cross-version resolution seam.
 *
 * <p>{@link compile.resolve.PlatformResolvers} lets the pure
 * compiler turn version-volatile names into stable interned handles at compile
 * time without depending on Bukkit. Production injects se-platform's resolvers;
 * tests inject a fake (docs/architecture.md §2.1, §9).
 */
package compile.resolve;
