# ADR 0070: Weapon Reforges — a weapon-socket applicable content family on the shared engine

- **Status:** Accepted
- **Date:** 2026-07-18
- **Deciders:** project owner + agent
- **Extends:** ADR-0014 (content compiler), ADR-0039 (source-erased `Ability`), ADR-0041
  (apply gestures), ADR-0047 (feature-module wiring), ADR-0048 (use-items — the implicit-USE
  active), ADR-0052 (pets — held-gate + `fireUse` split), ADR-0053 (masks — the applied-onto-gear
  head-likeness precedent)
- **Relates to:** ADR-0034/0035 (crystals — apply-onto-gear + the extractor), ADR-0040
  (render-from-state), ADR-0044 (era seams), ADR-0012/0050 (the additive damage fold),
  ADR-0071 (the ten reforge engine surfaces)

## Context

A weapon reforge drag-applies onto a WEAPON (swords + axes by default), gives that weapon one
signature active ability fired by SHIFT + right-click while it is held, and is removed with the
crystal extractor — which this ADR renames the **Item Extractor**. Every melee weapon has exactly
ONE reforge socket (the prior owner-approved framing). Crystals already prove apply-onto-gear and
an extractor; use-items prove the implicit-USE active; pets prove a held-gated `fireUse` activation
that resolves candidates from held state. Reforges are the 7th content family — a new source, a new
`LibraryLoader` loop, a universal item likeness, and a single-per-weapon combat-blob field — and
they carry ten new engine surfaces (ADR-0071). Numbers are FELT units, calibrated against the
flat-forward economy (ADR-0050 R3) via `/se damagedebug`; the counters deliberately avoid the
suppression system so they work through DOVAHKIIN's Silence.

## Decision

1. **A new content family `content/reforges/*.yml`** — the 7th `LibraryLoader` loop, directly after
   masks. Filename stem = the reforge key; stable keys `reforges/<stem>` (+ `/aN` for a multi-ability
   reforge, the crystal/mask reader shape — no levels, no brackets). A def declares `display`,
   `color`, `icon` (or `material`), `description`, and behaviour in the `abilities:` dual form or the
   top-level single-ability shorthand. `SourceKind.REFORGE` is added after `MASK`. `ReforgeDefReader`
   mirrors `MaskDefReader`; malformed input is `E_LOAD_REFORGE`, never a throw.

2. **The a0 ability is the SHIFT + right-click active — an implicit USE.** A declared `trigger:` on
   the active is `W_LOAD_REFORGE_TRIGGER`-warned and forced to USE (the use-item rule); support
   abilities (a1+) may author any trigger (PASSIVE/REPEATING/…) for maintained bits, defaulting to
   USE. `useStableKeys`/`conditionSources` carry the `fireUse` candidates (the pets split). The
   `{TIME_FORMATTED}` lore token renders a0's cooldown.

3. **One universal item likeness `items/reforge.yml`** (`type: reforge`, `ReforgeItemConfig`), tokens
   `{COLOR}`/`{NAME}`/`{NAME_UPPER}`/`{DESCRIPTION}`/`{APPLIES}`/`{TIME_FORMATTED}`. The item MATERIAL
   is per-reforge (`icon:`, cosmetic catalogue identity only); newer-than-1.8 icons (ENDER_EYE,
   STONE_BRICKS, CHORUS_FRUIT, TRIDENT, BELL, FIRE_CHARGE) degrade through the pinned
   `ItemFactory.LEGACY_FALLBACK` map, not a per-def `icon-legacy:` field. Physical reforge-item
   identity is `ReforgeCodec` (a STRING under `ItemKeys.reforgeItem`); the applied reforge lives on
   the weapon as `CombatState.reforgeKey`, `CombatCodec` blob label `r`.

4. **Gestures reuse the crystal/mask machinery.** Apply is an `ApplyGestureListener` subclass
   (`ReforgeListener`) claiming only reforge-item cursors; it always lands (no roll), rejecting a
   non-weapon target (`reforge.not-weapon`) or an occupied socket (`reforge.occupied`). Removal stays
   crystal-module-owned: the ONE Item Extractor cursor is claimed by `CrystalListener` alone, and on
   gear holding both a reforge and crystals the reforge pops FIRST, intact, back as a reforge item —
   via a `ReforgeExtractor` seam `CrystalService` consults before its crystal extract. The extractor
   is renamed Item Extractor across `items/crystal.yml`, `CrystalConfig.defaults()`, and its
   mint/give/tile labels.

5. **The on-weapon lore line is owner-pinned verbatim** — `&6&lWeapon Reforge (&r{NAME}&6&l)` where
   `{NAME}` is the colour-styled display — rendered by `LoreComposer.body()` directly BELOW the
   enchantment orb-slots line and ABOVE the crystal line(s), whenever `reforgeKey != null`
   (independent of the slots line, which only renders when added>0). `LoreRenderer.Config` gains a
   `reforgeLine` supplier + display lookup (the `maskLine` precedent).

6. **Held-gate, main-hand only.** A reforge contributes to worn state exactly like a set-weapon
   `on:weapon` bonus — the main-hand entry only, resolved from held state so USE candidates come off
   the held weapon (the pets bracket precedent). `features.reforges` is a LIVE toggle (the crystal/mask
   rule): listeners register regardless, the worn resolver + use listener short-circuit on the flag.
   `reforges.weapon-groups` (default `[SWORD, AXE]`) is read live and decides what counts as a weapon.

7. **A 2s shared pet-use gate rides this pass.** A per-player `PetSharedUseStore` (`PlayerScoped`,
   quit-swept) is armed by any successful pet activation and checked at the TOP of every pet USE
   flow, so a hotbar of pet actives cannot fire as one burst. **Owner ruling R1 — a Mole's own home
   recall is EXEMPT:** the pending-recall branch resolves BEFORE the shared-gate check, so the return
   trip is never delayed; the gate still guards active uses and digs, and a successful recall (past
   its point of no return) still arms the gate. Distinct from the per-ability gate-6 cooldown
   (ENCHANT scope); this one spans all pets for one player.

8. **Suppression stance is structural.** Reforge abilities carry `SourceKind.REFORGE` stable keys
   that are members of no enchant group/type id set, and the reader lowers no `suppressKey`, so the
   gate-5 suppression walk cannot match them — the owner's works-through-Silence rule holds with no
   suppression-system change and no pipeline special-case.

## Consequences

- Adding a reforge is local: one YAML in `content/reforges/` + (for a new mechanic) one engine
  surface in ADR-0071. The `features.reforges` toggle, the extractor rename, and the shared pet gate
  are all one-place changes.
- Golden churn is enumerated: `RegistryWiringTest` gains the reforge module's listeners/stops/mints
  and its store count grows (the pet gate + the reforge machines); the `regenDocs` surface gains the
  ten new effect heads and the reforge family row; `LoreComposerTest`/`CombatCodecTest`/
  `ItemEnchanterReforgeTest`/reader tests gain reforge rows.
- The extractor now serves two families; its single cursor stays CrystalListener-owned to avoid two
  listeners double-handling one cursor.
- The engine kinds are ADR-0071's concern; this ADR owns the family system, item plumbing, gestures,
  lore, held-gate, config/module wiring, and the shared pet gate.

## Alternatives considered

- **A per-def `icon-legacy:` field** — rejected; the deployed range is 1.17.1+ (has every icon) plus
  the 1.8.9 lane, and `ItemFactory.LEGACY_FALLBACK` is the established one-path degradation, so five
  code entries beat a field every author must remember.
- **A self-contained reforge likeness per content file (name/lore in the def)** — rejected; the owner
  pins one global likeness, so the def carries only `display`/`color`/`description` and the universal
  `items/reforge.yml` renders every reforge identically (the mask precedent).
- **A second extractor item for reforges** — rejected; one Item Extractor for both families is the
  owner's call, with reforge-first priority resolving the collision.
- **The shared pet gate blocking a Mole's own recall (the contracts-literal reading)** — rejected by
  owner ruling R1; a delayed return trip is a worse feel than a burst of digs, so recall is exempt.
