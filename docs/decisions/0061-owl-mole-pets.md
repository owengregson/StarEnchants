# ADR 0061: Owl and Mole pets — POTION_LOCK reveal sweep and the service-owned DIG_HOME recall

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + agent
- **Extends:** ADR-0052 (pets), ADR-0059 (pet leveling; the use-XP success hook), ADR-0060 (new pets; the WATER_SPEED service-owned marker precedent)
- **Relates to:** ADR-0033 (unified lang), ADR-0050 R4 (cooldown scopes)

## Context

Two new ACTIVE cosmic pets. The Owl (strip + deny Invisibility around the holder) is expressible in pure YAML
on existing machinery. The Mole (sneak-to-dig a 30s home, click-again-to-recall) needs one thing that does not
exist: a SECOND right-click that must reach code while the pet cooldown — armed at the dig — would normally
short-circuit it at gate 6.

## Decision

1. **Owl is pure YAML.** `POTION_LOCK { effect: INVISIBILITY }` on `@Aoe{r, filter=PLAYERS}` per bracket:
   strip-now + a deny window (modern: the potion-event guard cancels re-application; 1.8.9: the per-tick
   re-strip is the sole enforcer — full fidelity everywhere). Bracket `duration` mirrors the lock ticks so the
   universal ENDED message lands when invisibility returns. Deliberately no condition: an empty sweep burns
   the cooldown — the scouting cost.
2. **`DIG_HOME { window, range }` is a SERVICE-OWNED marker kind** (the WATER_SPEED no-op-run rule): its
   `run()` emits nothing; the pets cold path reads the compiled args at the activation success point. The
   source-erased engine never learns pet identity or carries teleport state.
3. **The recall bypasses the gate walk by design.** `PetService.use` resolves the bracket, detects `DIG_HOME`
   (the cage pre-check scan), and if the player holds a LIVE window the click is a RECALL handled before
   `fireUse` — the armed cooldown never blocks the return trip. The dig itself is gated by the authored
   `condition: "%sneaking%"` (gate 7 — a failed condition burns no cooldown); a window that expires unused
   leaves the cooldown spent.
4. **`PetHomeStore`** (feature-layer, the PetArmedStore twin): one window per player — world UID + primitive
   coords (never a retained Location/World), recall range, expiry tick, arm generation. Cleared on recall,
   generation-guarded expiry, death, quit, disable; never persisted. NOT an EngineStores component: that
   aggregate is a closed golden record and the store is pet-feature state.
5. **Recall execution**: teleblock check first — the pack-wide `TeleblockStore` read directly (the
   launch/teleport listener covers only pearl/chorus causes, not a PLUGIN teleport) — denial = the universal
   pet fail line, window kept. Range/world check next — refusal = the NEW universal prefix-free lang message
   `feedback.out-of-range` ("&c&l(!) You are too far away!", canonical catalogue only; packs may overlay),
   window kept for retry. Success: window consumed FIRST, then `TriggerDispatch.teleport` (a passthrough over
   the existing `Sink.teleport` intent — anti-cheat-exempted, the player's own entity scheduler, async on
   Folia/modern and sync on 1.8), the universal ENDED message marks the consume, and the ADR-0059 use-XP is
   granted HERE — the recall, never the dig, is the XP moment (the dig activation takes the arm branch of the
   success hook instead of `creditUseExp`).

## Consequences

- One new kind drifts the ADR-0046 fingerprint + DSL docs + pack stamp (`./gradlew regenDocs`); fuzz and
  conformance pick it up automatically.
- Wiring goldens: quit-swept module stores 7 → 8; the stop order gains "pet home windows".
- `feedback.*` is a new UNIVERSAL lang root (prefix-free action feedback) owned by the pets module for
  introspection until a more central owner exists.
- Recall teleports to the exact dug coordinates with no safe-destination probe (blocks placed over the home
  after the dig can trap the returner) — accepted; `Sink.teleportSafe` is the one-line upgrade if it bites.
- Edge accepted: the home window is per-player, so a second digger-style pet could recall a window dug by the
  first (only one digger ships).

## Amendment (2026-07-16): home-window visuals, recall/dig cues, and the recall gate audit

1. **Window-tied visuals** (`PetHomeVisuals`, feature-layer beside the store): while the window is live, a
   10-tick pulse draws a dust tracer line home→player and a pulsating ring (0.25/0.5/0.7 expanding ping,
   6/9/12 motes) on the home block, both in the pet's `{#RRGGBB}` colour at the KOTH feet offset (0.1). The
   task is generation-guarded like the expiry, self-cancels when the store entry is consumed/expired/
   replaced, and is cleared on recall and death (`PetService`), quit (module store) and disable (module
   stop). REPEATING-authored visuals were rejected: the mole has no armed window (duration 0), an armed
   window would outlive the recall consume, the pet-scope cooldown armed at dig would block gate 6, and no
   selector can target a stored location.
2. **Plumbing**: two cold-path `TriggerDispatch` passthroughs (`dust` batch, `sound`) over the existing sink
   intents — per-mote region routing on Folia and the 1.8.9 offset-RGB dust degrade come free (the KOTH
   PARTICLE_LINE path). Everyone nearby sees the shapes (world spawn) — finding the home is the counterplay.
   The colour resolves once per dig via `ChatColorRgb.of`, never per pulse. The line is capped at 100 steps
   (a wander past the 50-block range thins the tracer instead of unbounding it).
3. **Cues**: the dig gains a BLOCK_CRACK/DIRT burst (authored YAML, beside its existing sound; plain-burst
   degrade on 1.8.9 — the Bleed rule); a landed recall plays a small colour ring + `BLOCK_GRASS_BREAK`
   (depart pitch 1.2 / arrive 0.8) at both ends — service-owned, since the recall bypasses the gate walk.
   New sound alias `DIG_GRASS ↔ BLOCK_GRASS_BREAK` also makes the authored dig sound audible on 1.8.9.
4. **Recall gate audit** (owner-ratified): the recall leg carries EXACTLY (a) window live, (b) within range
   — cross-world resolves as the out-of-range feedback — and (c) not teleblocked (stays). No cooldown /
   sneak / enemy-proximity gate exists on the leg; the only preceding checks are the pet-use family
   preconditions (active def, permission, non-empty bracket) that equally gate the dig. Nothing removed.
5. Wiring goldens drift: quit-swept module stores 8 → 9; the stop order gains "pet home visuals".
