# ADR 0041: Unified gear-apply gesture

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** StarEnchants maintainers (design: Fable; implementation: Opus 4.8)

## Context

The cursor-consumable apply gesture — "click a consumable item on the cursor onto gear in your own
inventory" — was re-implemented once per family listener (scroll, holy scroll, crystal, slot orb, heroic,
trak, carrier, nametag, godly-transmog) with drifting guard chains, commit protocols, and message idioms.
Each family also carried a near-identical per-feature `Result` record (`ScrollResult`, `CrystalResult`,
`SlotResult`, `HeroicResult`, `TrakResult`, `CarrierResult`, `UnopenedResult`) with divergent vocabulary
(`consumed`/`commit`/`opened`, `give`/`produced`, `newTarget`). The give-with-overflow idiom
(`addItem(...).values().forEach(drop)`) was inlined at ~16 sites next to an existing `MenuItems.giveOrDrop`
helper (13 more call sites). Gesture result messages bypassed the `Messages.send` policy seam (feedback gate
+ PlaceholderAPI) with raw `player.sendMessage`. And crystal apply/extract outcomes had two parallel lang-key
families (`apply.crystal.*` alongside `crystal.*`), one a true duplicate (`apply.crystal.no-slots` vs
`crystal.no-slots`).

## Decision

- **One template.** `feature.apply.ApplyGestureListener` is an abstract `Listener` base with the whole guard
  preamble (click-shape → bottom-inventory → cursor claim → target-not-air → target claim) and commit protocol
  (consume cursor, replace/update target, give-with-overflow, cue, message, follow-up). Each family is a thin
  leaf overriding `claimsCursor` / `claimsTarget` / `claimsClick` and `apply(...)`. One listener per family
  keeps `StarEnchantsPlugin` registration order byte-identical (Bukkit registers inherited `@EventHandler`
  methods per leaf, the `EquipListenerBase` precedent).
- **One outcome.** `feature.apply.GestureOutcome(commit, consumeCursor, newTarget, produced, cue, message,
  followUp)` carries a PREFORMATTED message string; `commit && (newTarget == null || amount <= 0)` clears the
  clicked slot. `commit` and `consumeCursor` are separate flags (slot's failed roll consumes the orb without
  committing the gear). `followUp` (run LAST) carries nametag's open-anvil and godly-transmog's menu-open.
- **One policy seam.** `Messages.sendText(player, preformatted)` and `Messages.sendLines(player, key, …)` route
  gesture/service feedback through the same feedback gate + PAPI as `Messages.send`.
- **One give home.** `platform.item.Inventories.giveOrDrop` (feet + explicit-location overloads) with the Folia
  contract Javadoc; `feature.menu.MenuItems` is deleted, all inline copies swept (menus, `SeCommand`,
  `DispatchSinkBase`, `MineDrops`, soul split, nametag refund).
- **One crystal key family.** `apply.crystal.*` renamed into `crystal.*` (verbatim texts); the duplicate
  collapses to one `crystal.no-slots` (the informative "{MAX} max" wording). `ApplyResult.Reason` is deleted
  (its only consumer was the crystal wording substitution) and `apply.crystal.extracted` (a never-surfaced
  orphan) is dropped.

## Consequences

Per-family protocol equivalence (old listener → base):

| Family | Cursor | Target write | Notes |
| --- | --- | --- | --- |
| scroll | consume on commit | `newTarget` (gear) / `produced` (black book) | `interact` dispatch |
| holy scroll | consume on commit | gear (fail + applied both commit) | death/respawn hooks kept |
| nametag | `consumedOnly` (spend to begin) | none | `handledBeforeGesture` = anvil lock; `followUp` = open anvil |
| slot | `consumedOnly` on fail, consume+commit on success | gear | fail consumes orb, gear untouched |
| heroic | consume on commit | gear, or `null` (destroy-on-fail) | commit + null clears slot |
| trak | consume + commit | gear (same mutated object) | tracking hooks kept |
| carrier | consume on commit | gear (amount 0 = shatter clears) | dust/white-scroll delegate; `Cue` for dust fx |
| crystal | consume on commit | gear / merged / remainder; `produced` = minted single | `claimsClick` adds SWAP_WITH_CURSOR; `claimsTarget` = true |
| godly-transmog | not consumed | none | `claimsTarget` = has-enchants (no-enchants falls through to vanilla) |

Documented NON-template gestures (kept as-is):

- **`SoulInventoryListener.onClick`** (gem merge) — works in ANY inventory, LEFT-only, both stacks amount==1,
  the cursor is REPLACED not decremented, NORMAL priority. Forcing it into the template would change behaviour.
- **`UnopenedBookListener`** — a `PlayerInteractEvent` (held right-click), not a cursor-onto-gear click. It
  ADOPTS `GestureOutcome` + `Inventories` + `Messages.sendText`, but not the base.

Documented admin-echo / combat-messenger raw-`sendMessage` exemptions (unchanged): `SeCommand`,
`SplitSoulsCommand`, `CommandTriggerCommand`, `UserMenuCommand`, the `SetMessageDriver` injected consumer in
`StarEnchantsPlugin`, `ActivationMessenger`, and `DispatchSinkBase.message`.

Accepted player-visible deltas:

1. Gesture (and soul-mode) feedback now honours `messages.feedback` and applies PlaceholderAPI, where it
   previously always sent. Commands stay exempt, so operators are never silenced.
2. The drag-apply no-slots text becomes "This item has no free crystal slots ({MAX} max)." (the merged winner).
3. A user `lang.yml` override of an `apply.crystal.*` key stops applying — migration: rename it to `crystal.*`.

ADR-0040 restatement: apply-gesture services mutate PDC/marker state then recompose through the ADR-0040 seam
and report a `GestureOutcome` — never `setLore` on gear. The one remaining `setLore` is
`CarrierService.reRenderBookLore` on the BOOK likeness (a carrier item, not gear), which is explicitly allowed.

Folia correctness is preserved exactly: `InventoryClickEvent` fires on the clicking player's own region thread,
so the base's cursor/inventory mutation and the overflow drop are in-thread; the death/respawn/kill hooks keep
their existing `Scheduling.onEntity`/`onEntityLater` region hops.

## Alternatives considered

- **One mega-listener** dispatching by cursor type: would collapse the per-family `ignoreCancelled` first-cancel
  ordering and complicate the feature-gate ifs; rejected for the one-leaf-per-family base.
- **Re-key `ApplyResult` to (key + args)** instead of `Messages.sendText`: would ripple `ApplyResult`'s already-
  formatted messages across `SeCommand`, menus, and tests for zero player-visible gain; rejected.
