/**
 * The item read cache (§5.2): {@link item.view.ItemViewCache} decodes an item's combat blob into an
 * immutable {@link item.view.ItemView}, keyed by full content + generation (never copy-on-write
 * {@code ItemMeta} identity), so a hot-path item decodes once and later reads are lock-free lookups.
 * Reload swaps a fresh per-generation map, dropping prior views atomically.
 */
package item.view;
