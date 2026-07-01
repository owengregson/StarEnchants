# ADR 0035: Crystal stackability, multi-crystal identity, and whole-entry extraction

- **Status:** Accepted
- **Date:** 2026-07-01
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0034 (crystals rework — amends its §4 extraction and §5 on-gear line),
  ADR 0012 (additive fold), docs/v3-directives.md §E (crystals)

## Context

The crystals rework (ADR 0034) shipped crystals that always stack: two of the same crystal, whether merged into
one multi-crystal or worn on separate pieces, sum their bonuses through the additive fold. Some cosmic-pack
crystals should instead be **capped** — a Cosmic-Enchants "Armor Crystal" is often marked *"… is not stackable"*,
meaning its bonus applies only once no matter how many copies are worn. The model had no way to express that.

Two smaller gaps surfaced while adding content:

- A **merged** crystal was still named `Armor Crystal (…)`, indistinguishable at a glance from a single.
- The gear extractor popped the **topmost single component** off gear (ADR 0034 §4), silently splitting a
  multi-crystal apart on the gear — the owner wants the whole crystal to come off gear intact, and splitting to be
  a deliberate second gesture on the popped item.

And one new bonus (Frost's "deal 4% more damage while on ice") needs an on-terrain condition the fact vocabulary
did not expose.

## Decision

### 1. Per-crystal stackability (`stackable`, default `true`)

A crystal file may declare `stackable: false` (absent → `true`, so every existing crystal is unchanged). A
**non-stackable** crystal is capped two ways:

- **No merge-with-self.** Merging that would place the same non-stackable key twice in one multi-crystal is
  rejected (`CrystalService.merge` → `crystal.merge-not-stackable`). Different keys still merge; two *stackable*
  copies still merge.
- **Once per wearer.** At worn-state flatten time (`WornResolver.resolveFrom`), a non-stackable crystal
  contributes its abilities **at most once** across all worn pieces and slots — legs + boots both bearing Dark
  grant the bonus once, not twice. Dedup is by base key and scoped to crystals; enchants and stackable crystals
  keep full multiplicity. The set of non-stackable keys is read live, so `/se reload` re-tunes it.

Stackability is crystal-source metadata, not a hot-path `Ability` field: the feature layer reads
`CrystalDef.stackable()` directly, and the resolver takes a live `Supplier<Set<String>>` of non-stackable base
keys (wired from the library exactly like the per-feature master toggles).

### 2. Merged crystals render as "Multi Crystal"

The likeness (`items/crystal.yml`) gains `name-multi` and `lore-while-on-item-multi` templates, used once a
crystal carries **more than one** component. `name-multi` defaults to `name`, and `lore-while-on-item-multi`
defaults to `name-multi` — the on-gear line follows the item name, as in ADR 0034 §5 — so a pack that sets none of
them resolves everything to one uniform name. The cosmic pack renames a merge `Armor Crystal (…)` →
`Multi Crystal (…)`, at both the item name (mint path) and the on-gear line (`LoreRenderer`), single-sourced
through the same `{CRYSTAL}` token.

### 3. Gear extraction pops the whole entry intact (amends ADR 0034 §4)

The extractor applied to crystal-bearing **gear** now pops the entire last crystal **entry** off — a multi-crystal
comes back as one whole multi-crystal item, and its slot frees. Splitting a multi-crystal into singles is a
**second** extractor gesture, applied to the popped multi-crystal *item* (which still pops its topmost single, as
before). Extraction from a multi-crystal item is unchanged.

### 4. New condition fact `%actor.groundblock%`

The fact vocabulary gains one appended string var: the Material name of the block directly beneath the actor's
feet. It enables on-terrain conditions such as `%actor.groundblock% contains "ICE"` (Frost). It is read on the
actor's own region, so it is Folia-safe and guarded with the other actor reads.

### 5. Content

- Three new cosmic crystals: **Water** (`+2%` damage in water, stackable), **Ender** (`+10%` damage to mobs via
  `%victim.type% != PLAYER`, stackable), **Dark** (`+5%` dealt / `+5%` taken, not stackable).
- **Chaos** reworked to *take 50% less durability damage* (a 50% roll to cancel the item-damage tick) and *enemies
  deal 10% more damage to you* (a negative defense fold), not stackable; **Frost** reworked to *+1% to all enemies*
  and *+4% while on ice*, not stackable.
- The remaining cosmic crystals (**Light** recoloured `&e` → `&f`, **Flame**, **Nature**) gain their
  *"… is [not] stackable"* line and drop trailing periods to match the authored copy verbatim.

## Consequences

- **"Not stackable" is a real cap**, enforced at the two places multiplicity can arise (merge and worn flatten),
  so the lore line matches behaviour.
- **The `Ability` hot-path record is untouched** — stackability rides as crystal-source metadata, keeping the
  gate loop free of a crystal-only concern.
- **Extraction is now lossless on gear**: a player never accidentally shatters a multi-crystal by pulling it off
  gear; splitting is explicit.
- **The PDC layout is unchanged** — `stackable` is authored metadata, not stored on items; pre-existing crystal
  items keep decoding.
- **One new condition fact** widens the on-terrain vocabulary generally (not just for Frost); appended, so no
  previously-compiled condition's slot moves.

## Alternatives considered

- **Carry `stackable` on the compiled `Ability`.** Rejected — it is crystal-only metadata that four of the five
  sources ignore, and it would churn every `Ability` construction site for a concern the resolver can answer from
  a small live set of keys.
- **Block *applying* a second non-stackable crystal to another piece.** Rejected — the owner wants a player free to
  wear duplicates; the cap is on the *effect* (dedup at flatten), not on the *inventory action*.
- **Implement Frost's original "no slip on ice" as a movement mechanic.** Rejected — ice friction is client-side
  with no faithful cross-version server API; the owner replaced it with the on-ice damage bonus instead.
