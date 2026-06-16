/**
 * Component stores: the named, enumerable homes for mutable per-player runtime state
 * (docs/architecture.md §5.4). Data-oriented discipline — state lives in these stores,
 * not scattered in effect objects. Every store is concurrent and UUID-keyed (Folia:
 * any region thread may touch a player's state), TTL-evicting where applicable (bounded,
 * unlike the unbounded maps the originals leak), and cleared on quit ({@code clear})
 * and disable ({@code clearAll}). Time is an explicit tick count supplied by the caller,
 * never wall-clock, so the stores are deterministic and unit-testable.
 */
package engine.stores;
