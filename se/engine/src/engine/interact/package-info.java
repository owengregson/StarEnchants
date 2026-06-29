/**
 * The contribute-then-resolve arbiters (docs/architecture.md §6) that make feature
 * interactions correct <em>by construction</em>: effects contribute, the arbiter commits
 * once. {@link engine.interact.DamageFold} (additive fold, ADR-0012), {@link
 * engine.interact.SuppressionSet} (O(1) interned-id silence), {@link
 * engine.interact.SoulPool} (the per-player cross-gem soul authority, no double-spend
 * across region threads), {@link engine.interact.SlotLedger} (enchant-slot arithmetic).
 * Each is per-event/per-activation scratch owned by one thread, or serialised per key
 * (SoulPool) — never a shared object threaded across capabilities.
 */
package engine.interact;
