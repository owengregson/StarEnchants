/**
 * Config packs (ADR-0023) — a portable, swappable snapshot of an entire StarEnchants configuration.
 *
 * <p>A <em>pack</em> bundles the whole authored config surface ({@code config.yml}, {@code lang.yml},
 * and the {@code content/}, {@code items/}, {@code menus/} trees) plus a {@link pack.PackManifest}
 * into one ZIP file. {@link pack.PackArchive} is the pure codec (a tree of files ↔ a ZIP);
 * {@link pack.PackStore} is the on-disk authority over a {@code packs/} directory — it lists the
 * available packs, exports the live config into a new pack, and <em>applies</em> a pack over the live
 * config (snapshotting the current config into a backup pack first, then staging + swapping the
 * surface so a failed write never half-clobbers).
 *
 * <p>This module is intentionally dependency-free (pure JDK + {@code java.util.zip}). It never touches
 * Bukkit, the compiler, or the reload; the composition root pairs {@link pack.PackStore#apply} with the
 * transactional content reloader so a swapped pack takes effect live (or, for a broken pack, leaves the
 * previous state untouched).
 */
package pack;
