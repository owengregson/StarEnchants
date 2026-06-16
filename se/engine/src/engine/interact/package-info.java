/**
 * The contribute-then-resolve arbiters (docs/architecture.md §6): the shared authorities
 * that make feature interactions correct <em>by construction</em>. Effects contribute to
 * an arbiter; the arbiter commits once. {@link engine.interact.DamageFold} is the single
 * additive damage fold (ADR-0012); {@link engine.interact.SuppressionSet} the O(1)
 * interned-id silence set; {@link engine.interact.SoulLedger} the single soul-spending
 * authority (no double-spend across region threads); {@link engine.interact.SlotLedger}
 * the enchant-slot capacity arithmetic. Each is per-event/per-activation scratch owned by
 * one thread (DamageFold/SuppressionSet) or serialised per key (SoulLedger), never a
 * shared object threaded across capabilities.
 */
package engine.interact;
