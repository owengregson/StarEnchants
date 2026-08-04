# ADR 0074: Multi-mask composites (per-item), and IMPACT scoped to its arming group

- **Status:** Accepted
- **Date:** 2026-08-04
- **Deciders:** project owner + agent
- **Extends:** ADR-0053 (masks), ADR-0034/0035 (crystals — the multi-identity precedent)
- **Relates to:** ADR-0039 (source-erased `Ability`), ADR-0040 (render-from-state), ADR-0041
  (apply gestures), ADR-0043 (origin capture), ADR-0050 R4 (group ids are match keys, never
  cooldown scopes), ADR-0062 (`{#RRGGBB}` tokens), ADR-0065 (freeze windows), ADR-0073
  (which recorded four of these as open)

## Context

Wave 2d.2 stopped `WORN_COMPOSITE` — the multi-mask — on a contradiction inside its own
matrix entry rather than on size. One line gives the primitive `params: children (list of
mask keys)`, a DEF-level declaration; another says it is "minted with explicit child names"
and interpolates per-ITEM `{child1}, {child2}` into both the compound name and the attached
stamp. The two readings build different things, and the def-level one builds the wrong
thing: a def cannot know which children an ITEM carries, so it could only ever describe one
fixed composite forever.

Four smaller surfaces were recorded and not built alongside it (ADR-0073): `IMPACT` source
scoping, `%status.freeze%`, a per-recipient relation colour on `MESSAGE`, and a Y offset on
the cue kinds.

## Decision

### 1. A composite is PER-ITEM, and it is the Multi Crystal shape

Children live on the ITEM, in PDC. The owner ruling follows the crystal precedent, and so
does the implementation: `MaskItemData` is `CrystalItemData`'s twin — an ordered child list
packed into ONE entry string, `"masks/a+masks/b"`. The shared packing lives once, in
`item.codec.KeyEntries`, because the two families must agree on the delimiter and on the
singleton rule.

**The codec is untouched.** A helmet's `CombatState.maskKey` holds that entry, and a plain
key has no delimiter — so every mask minted or applied before this decodes as the single
mask it always was, and the `m` blob label, the record's arity and its ~30 positional
construction sites are all unchanged. This is what turned the option the 2d.2 stop priced as
"larger than the rest of the masks pool combined" into a small change: the packing question
was already answered.

**There is no container def.** "Multi-Mask" is a LIKENESS — the `name-multi` template on
`items/mask.yml` — exactly as "Multi Crystal" is, so `MaskDefReader` needs no change and
never has to accept an ability-less mask.

`WornResolver`'s mask branch becomes the crystal component walk verbatim: split the entry,
and resolve each child's primary plus its own `/aN` chain. Every folded child's ability set
fires as if that child were the worn mask, which is the whole contract. A child whose content
disappeared across a reload contributes nothing and its siblings still fire.

### 2. The three gameplay rulings

- **Creation is the mask-onto-mask apply gesture**, mirroring crystal-onto-crystal:
  `MaskListener` widens to `SWAP_WITH_CURSOR` and claims a mask target, `MaskService.interact`
  routes fold-vs-apply, the cursor lands ON TOP, and the cap is `masks.max-merge` (default 2,
  `1` = no folding). A refused fold spends nothing.
- **The illusion shows the FIRST child's head.** A body has one face and the illusion one
  texture, so a fold cannot average them; the first child is the one the wearer folded ONTO,
  which makes the worn face a choice rather than an ordering accident. It also supplies the
  compound name's `{COLOR}`.
- **Extraction is whole-entry.** A composite pops off a helmet as ONE composite (ADR-0035 §3),
  never as N loose masks to re-fold. Splitting is the second gesture: the Item Extractor on a
  composite ITEM pops its topmost child. Folding is a 100 %-commit gesture that spends the
  cursor, so it has to be undoable or a mis-fold is permanent.

The extractor reaches masks through a `MaskSplitter` seam, the `ReforgeExtractor` arrangement
exactly — the extractor cursor stays claimed by `CrystalListener` alone while the masks module
keeps its own economy. Branch order: crystal item → composite mask → gear reforge → gear
crystal.

### 3. Rendering keeps the two paths separate

A PLAIN mask renders exactly as before: `{COLOR}` is the def's colour and `{NAME}` its BARE
display, because the template supplies the styling. A composite cannot work that way — one
`{COLOR}` cannot style N children — so there `{NAME}` carries each child's own colour inline
through `StyledNames` (the `{CRYSTAL}` join, generalised), `{DESCRIPTION}` stacks every child's
block, and `{NAME_UPPER}` upper-cases each child's WORDS before styling, never the codes
around them. `name-multi` / `lore-while-on-item-multi` each default to their single form, so a
pack that sets neither renders one uniform name.

### 4. `IMPACT` fires only the group that armed it

A landing fired EVERY `IMPACT` ability its owner wore. The `defId` filter is **disqualified,
not deferred**: a field's arm and its payload are two separate authored bonuses with two
different ids, so it would fire nothing at all. The identity they SHARE is the authored
`group:`, which every reader already parses and gate 5 already matches on — safe to reuse
precisely because it is a match key and never a cooldown scope (ADR-0050 R4).

So: `EffectCtx.sourceGroup()` fed from `Ability.cdScopeGroup()`, one int through each carrier,
and `TriggerRunner.runGrouped`. `-1` is unscoped and runs the whole roster, so nothing authored
today changes. All three feeders carry it — falling blocks in the `Cast`, turrets on the
EMPLACEMENT (a turret outlives its activation and re-reads its owner per shot, so a captured
group would scope only the first), summon couriers on `SummonFlags` (the strike rung forgets
the registries before it dispatches).

### 5. The three small surfaces

- **`%status.freeze%`** — actor-scoped like `%status.teleblock%`, reading `FrozenTargets`'
  TICK-budget liveness so fact and window cannot disagree under lag. No victim reading:
  `STATUS_CLEAR` on a target is already idempotent.
- **`{RELATION_COLOR}` on `MESSAGE`** — the second per-copy token beside `{SELF}`, its two
  colours in `ally-color`/`enemy-color` params (the wave-1e rule: bindings in the parameter,
  never in the text). It could not ride `tokens`, which is numeric end to end, nor live in
  `Colors.translate`, which cannot see who is reading — so the colour PARSE stays where
  ADR-0062 put it and only the SELECTION is new. The actor's own copy reads ALLY.
- **`dy` on `SOUND`/`PARTICLE`** — the measured "+4 Y" cue anchor. The location branch moves
  the point inline; the entity branch cannot (that read is the sink's, ADR-0043), so the offset
  rides the intent. It TRANSLATES the anchor where `spread-y` only widens a scatter.

## Consequences

- Folding masks is a player-facing gesture with a live cap; a server that wants ADR-0053's
  one-mask-per-helmet sets `masks.max-merge: 1`.
- `LoreRenderer.Config` gains `maskLineMulti`; `MaskItemConfig` gains two templates;
  `MasksModule` is now constructed before `CrystalsModule` (the extractor hook), registry
  order unchanged — the reload↔menus precedent.
- The ADR-0046 fingerprint and docs goldens drift for `dy`, the two `MESSAGE` params and
  `%status.freeze%` (`./gradlew regenDocs`); the composite itself touches no compiled surface.
- `%victim.*` freeze and the composite's ATTACHED-stamp string are deliberately not built:
  nothing asks for the first, and the second is jar wording our own likeness replaces.
- The ledger's premise that "every other `STATUS_CLEAR` rung has a guard fact" was wrong —
  only TELEBLOCK had one. POTION_LOCK and DISARM still have none, and stay unbuilt.
