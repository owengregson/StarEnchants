/**
 * Config packs (ADR-0023) — a portable, swappable ZIP snapshot of a whole StarEnchants config surface.
 * {@link pack.PackArchive} is the pure codec; {@link pack.PackStore} is the on-disk authority. Pure JDK,
 * no Bukkit; the composition root pairs {@link pack.PackStore#apply} with the transactional reloader.
 */
package pack;
