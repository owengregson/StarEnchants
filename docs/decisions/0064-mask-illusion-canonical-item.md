# ADR 0064: The mask illusion is a marked, deniable, losslessly-undressable item

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + engine work
- **Extends:** ADR-0053 (masks — the worn-illusion repaint)
- **Relates to:** ADR-0044 (era seams), ADR-0041 (apply gestures), ADR-0035 (crystals — the
  applied-to-gear precedent), ADR-0031 (heroic vanilla stats on the dressed head), the ratified
  vanilla-station guard scope (G04/G05/G06)

## Context

ADR-0053 §5 repaints a worn masked helmet — for observers *and* the wearer (the F5 self-view) —
with the mask's player-head likeness via a pure equipment packet; the real inventory item is never
touched. A client, however, applies an equipment packet about its OWN entity to its **local
inventory model**: while a mask is worn, the wearer's client believes its helmet slot holds a
dressed `PLAYER_HEAD`. That dressed head carries display name, lore, vanilla enchants, flags, and
worn attributes only — **no `starenchants:*` PDC, no state, no marker**. A creative-mode client is
inventory-authoritative: it echoes its believed slot contents back as `SetCreativeModeSlot` writes,
and CraftBukkit adopts them verbatim. `InventoryCreativeEvent` had **zero handlers** repo-wide, so
that echo **replaced the real masked helmet with the identityless dressed head** — destroying its
mask, crystals, and enchant state. The result read as a permanent, unmodifiable, mask-locked helmet
(every carrier's applies-to / state-identity check fails on a bare `PLAYER_HEAD`). The reported
"crystal-extract-while-worn brick" is one instance: an armor-slot cursor gesture performed while the
illusion is live and the client is (or flips to) creative.

The server-side PDC path itself is sound — every `CombatState` mutator preserves `maskKey` and the
codec round-trips it. The defect is architectural: a packet-only visual existed in a form
indistinguishable from a real item, with no marker and no guard on the one lane where clients write
inventory state back.

## Decision

One canonical seam, four layers, plus two audit fixes — not a per-carrier patch:

1. **Mark the illusion (item-data layer).** A new logical key `illusion` stamps every dressed head;
   its payload is Base64 of the **whole serialized real helmet** through a new `ItemBytes` ADR-0044
   era seam (`ModernItemBytes` probes Paper `serializeAsBytes`/`deserializeBytes` by name;
   `LegacyItemBytes` uses the v1_8_R3 NBT gzip stream). `IllusionMark` detects a marked stack
   everywhere and `undress()` reconstructs the exact helmet. A payload-less mark (byte codec absent)
   still detects → callers deny instead of repairing; corruption degrades to `null`, never a throw.
2. **Deny adoption (`IllusionCanonGuard`, always-on).** `InventoryCreativeEvent` at `LOWEST`: a
   marked stack arriving as a creative slot write is cancelled + `updateInventory()` — the server's
   real item stands, losslessly.
3. **Canonicalize before ANY gesture (same guard).** `InventoryClickEvent` at `LOWEST`: a marked
   stack in a real slot or on the cursor is undressed in place before the `HIGH` `ApplyGestureListener`
   family and `MaskRemoveListener` read it — one listener covers the whole carrier surface.
4. **Repair on refresh + kill the lie for creative wearers.** `MaskIllusionService.refresh()` first
   restores a marked head found in the real helmet slot (`repairWorn`); `sendToWorld` shows a
   **creative wearer the TRUE helmet** (observers still see the mask), and `PlayerGameModeChangeEvent`
   re-derives on mode flips — a creative client must never hold the lie it can write back.

Two audit fixes ride along: the vanilla-station guard predicate is broadened past set gear to the
single-sourced `StationGuardRules.pluginValueGear` (set ∨ mask ∨ crystals) so a masked/crystal
non-set helmet can no longer be sacrificed or laundered at a grindstone/anvil; and `extractCrystal`
gains the single-item stack guard its `removeMask` twin already had.

## Consequences

- Illusion packets grow by one PDC string (the serialized helmet); the hot combat path is untouched.
- Post-fix, bricks cannot recur (gate) and any marked leak self-heals (payload). Pre-fix bricks are
  unrecoverable from the item (the state never crossed the wire, and re-deriving it would mean
  parsing lore) — replacement is manual; a lore-parsing `/se maskrescue` recovery command is spec'd
  but **deferred pending the owner**, not built.
- Creative observers are unaffected — they only echo their own slots back.
