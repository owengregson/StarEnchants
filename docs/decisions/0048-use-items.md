# ADR 0048: Use-items — a right-click content family that lowers to the shared pipeline

- **Status:** Accepted
- **Date:** 2026-07-08
- **Deciders:** project owner + agent
- **Extends:** ADR-0014 (the content compiler + transactional reload), ADR-0039 (source-erased
  `Ability` + dense-id dispatch), ADR-0047 (feature-module wiring — the use-item wiring is one
  `FeatureModule`)
- **Relates to:** ADR-0034 (crystals — the dual root-shorthand / `abilities:`-list reader this
  reader mirrors), ADR-0041 (the held right-click gesture the listener clones from the unopened
  book), ADR-0045 (`/se why` — the same `GateOutcome` the cold-path seam reads)

## Context

Every effect-bearing thing in StarEnchants — enchants, set bonuses, weapon bonuses, crystals,
heroic — is a **source** that lowers to the one `Ability` record and runs the fixed gate sequence
(§3.3). All of them are worn or held gear resolved into `WornState`. What was missing is a
**consumable right-click item** whose own abilities fire when a player uses it: a Cosmic
Enchants-style "Rage Crystal" that grants a buff on right-click, on a cooldown, optionally
consuming one. Building that as a bespoke feature would re-implement cooldowns, conditions,
chance, souls and suppression — the exact drift ADR-0039's erasure exists to prevent.

## Decision

1. **A new content family `content/use-items/*.yml`** — the 4th loader family in `LibraryLoader`,
   alongside enchants / crystals / sets. Filename stem = the def key (like crystals). Each file
   declares BOTH a likeness (material/name/lore/shiny/consumable) and engine abilities.

2. **Abilities lower to the same source-erased `Ability`** (`SourceKind.USE_ITEM`), so a use-item
   passes the identical gate sequence (suppression → cooldown → condition → chance → PreActivate →
   souls) and interacts with every other feature for free. The reader (`UseItemDefReader`) mirrors
   `CrystalDefReader`'s dual form: a single-ability root shorthand OR an `abilities:` list keyed
   `use:<key>`, `use:<key>/a1`, ….

3. **The `USE` trigger is IMPLICIT** — appended last in `BuiltinTriggers` (a held/NEUTRAL trigger
   fired ONLY by the use-item flow, never a Bukkit event). An authored `trigger:` is a warning and
   forced back to `USE`, so a use-item can only ever fire on right-click.

4. **`commands:` lowers to `RUN_COMMAND` effects** appended to ability a0: a bare string runs as
   console, a `{ as: player|console, run: "…" }` map honours `as`. `RUN_COMMAND` gained an `as`
   param and runtime `{PLAYER}`/`{UUID}`/`{WORLD}` token fill (mirroring `MessageEffect.fill`), and
   `Sink.playerCommand` runs the command as the activating player on their entity thread.

5. **A cold-path outcome seam, not the hot path.** The combat path stays a void fold into the sink.
   A use-item right-click instead calls `AbilityExecutor.runUse` (via `TriggerDispatch.fireUse` →
   `TriggerRunner.runUse`), which walks the EXPLICIT candidate ids (resolved from the def's stable
   keys, not `byTrigger`) and returns a compact `UseAttempt` — collapsing the per-candidate
   `GateOutcome`s to activated / on-cooldown (+ remaining) / condition-failed (+ source index) /
   chance-failed / blocked. The feature layer maps that to the UNIVERSAL lang feedback. The full
   `FactMask` is populated (cold path, a safe superset) so any authored condition's facts resolve.

6. **Glint lives at the feature layer** (`shiny` → the menus' `MenuIcons.glow`), matching the
   repo's "glow lives in feature, not item" convention; identity is a PDC key (`UseItemCodec`,
   mirroring `UnopenedBookCodec`).

7. **Universal feedback, no prefix.** `use-item.success` / `.cooldown` / `.fail` are shared by every
   use-item and rendered WITHOUT the global message prefix (`Messages.fragment` → translate →
   `sendText`); an empty success is silent. The `features.use-items` toggle is LIVE (the listener
   registers and short-circuits on the flag).

## Consequences

- Adding a use-item is PURE YAML, validated by `CatalogValidationTest` / `CosmicPackValidationTest`
  in `./gradlew build` before it ships — one new file, no code.
- Adding the `USE` trigger and the `RUN_COMMAND` `as` param changes the registry fingerprint, so the
  DSL/authoring/catalog goldens are regenerated (`./gradlew regenDocs`).
- The cold-path seam duplicates the candidate walk of `AbilityExecutor.run` (deliberately — the hot
  path's signature and allocation profile are untouched), sharing the private effect-run/quarantine
  helpers.
- `WRITABLE_BOOK` ⇄ `BOOK_AND_QUILL` and `RED_DYE` → `REDSTONE` degradations were added so the
  use-item likeness materials resolve on the optional 1.8 lane.
