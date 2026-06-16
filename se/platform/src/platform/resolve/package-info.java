/**
 * Cross-version handle resolution (docs/architecture.md §9): the pure rename strategy.
 * {@link platform.resolve.Aliases} is the legacy&rarr;modern rename knowledge,
 * {@link platform.resolve.HandleResolver} the bidirectional modern/legacy resolution
 * order, and {@link platform.resolve.VocabularyResolvers} an alias-aware
 * {@code PlatformResolvers} the compiler can use directly — the server-free core the
 * Bukkit-registry-backed resolver mirrors. The runtime only ever sees the interned ids
 * produced here, never a renamed constant.
 */
package platform.resolve;
