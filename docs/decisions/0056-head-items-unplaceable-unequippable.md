# ADR 0056: SE cosmetic head items do only their intended action — never placed, never helmet-worn

- **Status:** Accepted
- **Date:** 2026-07-12
- **Deciders:** project owner + agent
- **Extends:** ADR-0044 (era seams), ADR-0052 (pets — hotbar-activated heads), ADR-0053 (masks —
  helmet-applied heads), the 1.8.1 vanilla-mechanic guard (`VanillaGuardListener`)
- **Relates to:** ADR-0047 (feature-module wiring), ADR-0040 (render-from-state)

## Context

Masks and pets are `PLAYER_HEAD` items, and a bare player head is natively both PLACEABLE (as a skull
block) and WEARABLE (in the helmet slot). Neither is intended for these families: a mask activates only when
APPLIED ONTO a helmet (its drag gesture — ADR-0053), a pet activates from the HOTBAR (ADR-0052). The 1.8.1
`VanillaGuardListener` already cancels the block placement of every plugin item, but two gaps remained.

1. **Placement flicker.** The cancel keeps the stack server-side, but the client has already PREDICTED the
   placement (ghost block + a held-count decrement). On the older-protocol lanes (notably 1.8.9) the server
   does not re-send the held slot on a bare cancel, so the count reads one short until the next inventory
   sync — the "placed, then refunded" flicker the owner reported.

2. **Helmet equip was unguarded.** Nothing stopped a raw mask/pet head being slotted into the helmet: direct
   place, shift-click auto-equip, hotbar number-key swap, right-click auto-equip, or dispenser-equip. Worn
   there, a mask grants nothing (its abilities key off the on-helmet applied state, not the head item) and a
   pet's hotbar contract is bypassed — a confusing dead end. The owner wants the client itself to refuse the
   slot where the protocol can express it.

## Decision

**An SE cosmetic head does ONLY its intended action; every vanilla place/equip vector is denied, server-side
across the whole range and client-side where the protocol allows.**

1. **Placement resync.** `VanillaGuardListener#onPlace` now calls `player.updateInventory()` right after the
   cancel, resyncing the predicted held-count so there is no visible decrement→refund cycle. It fires only on
   the (rare) cancelled place of a plugin item — not a hot path — and is a harmless no-op on the modern
   ack-driven lanes the client has already reconciled. The placement remains a clean outright cancel (no
   ghost that sticks, no refund path); this completes it for the legacy lane.

2. **Server-side helmet-equip guard.** A new always-on `HeadEquipGuard` (the `VanillaGuardListener` rule —
   keyed off item identity, not a feature toggle) cancels every INVENTORY route a mask/pet head can reach the
   helmet by: a direct place / hotbar number-key swap onto an armour slot, a drag onto the helmet cell (raw
   slot 5, vanilla-stable 1.8.9 → 26.x — `InventoryView.getSlotType(int)` does not exist on 1.8.9), and the
   shift-click auto-equip in the player's own inventory screen. An UNEQUIP (shift-click FROM the armour slot)
   is deliberately left alone, so a head slotted before this shipped can still come off. Right-click
   auto-equip is already denied by `VanillaGuardListener` (masks/pets are plugin items, so their vanilla
   item-use is suppressed). Dispenser-equip is a modern-only overlay seam, `DispenseArmorGuard`, cancelling
   `BlockDispenseArmorEvent` — the event is absent on the 1.8.9 floor, so the legacy era binds the inert
   `NoopListener` (no such event to fire there).

3. **Client-side equippable strip (1.21.2+).** A new ADR-0044 era seam `item.head.HeadEquip` (`NONE` default)
   with `ModernHeadEquip` overrides the head's `minecraft:equippable` data component at mint — the DEFAULT
   `slot=head` a player head carries is replaced wholesale with a non-head slot (`mainhand`) plus
   `swappable=false`/`dispensable=false`, so vanilla's own armour-slot predicate (`Slot#mayPlace`) refuses the
   helmet without a server round-trip and every route above falls through client-first. Wholesale override
   (not `setEquippable(null)` removal) so the outcome is deterministic regardless of how a build handles
   removing a material-default component. The whole component surface is a 1.21.2+ API absent from the 1.17.1
   compile floor, so it is a once-probed `MethodHandle` (the `ModernHeadAttributes` precedent); below that era
   it degrades inert and the server-side guard carries the block alone. Applied once at mint on both the pet
   and mask mint paths; the render-from-state re-render (`ItemFactory.decorated`) preserves the component
   across a pet's level-up.

## Consequences

- Masks/pets can never be placed as a block, worn as a helmet, or half-consume the held stack on a cancelled
  place — on the whole range, with the client refusing the slot itself on 1.21.2+.
- One new always-on listener (`HeadEquipGuard`) + one modern overlay seam (`DispenseArmorGuard`) + one era
  seam (`HeadEquip`), all folded through the guard module — the golden listener sequence
  (`RegistryWiringTest`) drifts by the two guard names.
- The equippable strip is best-effort by design: a head minted before this shipped, or on a sub-1.21.2 server,
  has no client hint — the server-side guard is the durable enforcement; the component is the client polish.
- Because the guard is keyed off item identity (not a feature toggle), a lingering mask/pet head stays
  un-wearable even with its family disabled — the plugin-item-guard convention, consistent with the vanilla
  place guard.

## Alternatives considered

- **`setEquippable(null)` to strip the component** — cleaner in intent, but the removal semantics for a
  material-DEFAULT component (a player head's `slot=head`) are build-dependent; a wholesale non-head override
  is deterministic on every 1.21.2+ build in the matrix.
- **Client strip alone (no server guard)** — leaves 1.17.1 → 1.21.1 and any pre-strip head wide open; the
  server guard is the range-wide floor and the component the client-side improvement on top.
- **A reflective spigot-mapped equip-cancel packet below 1.21.2** — disproportionate; the server-side
  inventory guard already covers that lane (the ADR-0053 §4 "reflective packet rejected" precedent).
