/**
 * Component stores: the named homes for mutable per-player runtime state (docs/architecture.md §5.4).
 * Data-oriented discipline — state lives here, not scattered in effect objects. Every store is concurrent,
 * UUID-keyed (Folia: any region thread may touch a player's state), TTL-evicting where applicable, and
 * cleared on quit ({@code clear}) and disable ({@code clearAll}). Time is an explicit caller-supplied tick,
 * never wall-clock — deterministic and unit-testable.
 */
package engine.stores;
