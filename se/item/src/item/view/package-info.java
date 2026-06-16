/**
 * The item read cache (docs/architecture.md §5.2): {@link item.view.ItemViewCache} turns an item's
 * raw combat blob into a cached, immutable {@link item.view.ItemView}, keyed by full content +
 * snapshot generation (never copy-on-write {@code ItemMeta} identity), so a hot-path item decodes
 * exactly once and every later read is a lock-free map lookup. A reload swaps a fresh per-generation
 * map, dropping every prior view atomically. The cache logic is unit-tested (incl. contention); the
 * read-through over a real item's PDC is verified live (§11).
 */
package item.view;
