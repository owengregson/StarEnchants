# ADR 0032: Crystals rework — global likeness, multi-ability crystals, N-stack merge, 100% apply

- **Status:** Accepted
- **Date:** 2026-06-30
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0014 (crystal-as-list source), ADR 0016 (tiers), ADR 0012 (additive fold),
  ADR 0030 (GUI overhaul, crystal minting), docs/v3-directives.md §E (crystals)

## Context

The original crystal model (ADR 0014) treats a crystal as a levelless socket bonus expanding to **exactly one**
`AbilityDef`, minted from a **per-crystal** `material`/`name`/`lore` likeness that falls back to a shared
`items/crystal.yml`. Application is a **success roll** (`CrystalConfig.successChance`, optionally
`consume-on-fail`); merging is **pairs only** (`CrystalItemData.MAX_COMPONENTS = 2`); the extractor pops the whole
last **entry**; the on-gear crystal line renders `crystal-color + name1 + " + " + name2` **above** the orb-slots
line.

The project owner reworked the feature to a "combine crystals into a stronger crystal" model closer to a
Cosmic-Enchants-style **Armor Crystal**: crystals are authored content whose *likeness is global* (one template),
whose *effects are local* (per crystal, standard DSL), that **merge** up to a server cap, apply at **100%**, and
whose extractor pulls the **topmost single** off a stack. Several default crystals carry **two** bonuses spanning
different triggers (e.g. an on-attack damage bump *and* a permanent worn potion), which the one-crystal-one-ability
rule cannot express.

## Decision

### 1. Global likeness, local effects

The crystal **likeness** (material, name template, lore template, `lore-while-on-item`, sounds, extractor) is the
**single** `items/crystal.yml` config; a per-crystal file no longer carries `material`/`name`/`lore`. A crystal
file declares only its **identity + display + description block + effects**:

```yaml
display: "&c&lFlame"                 # styled name; its colour is carried inline (no separate color field)
description:                         # the "BONUS" block rendered into the crystal item lore verbatim
  - "&c&lFLAME CRYSTAL BONUS"
  - "&c* Deal 3% more damage to all enemies."
  - "&c* Permanent Fire Resistance"
applies-to: [HELMET, CHESTPLATE, LEGGINGS, BOOTS]
abilities:                          # one or more independent trigger/effect blocks (see §2)
  - { trigger: ATTACK,  effects: [ { DAMAGE_MOD: { side: attack, mode: add, amount: 3 } } ] }
  - { trigger: PASSIVE, effects: [ { POTION: { effect: FIRE_RESISTANCE, level: 1, duration: 100, who: "@Self" } } ] }
```

Likeness tokens (rendered at mint / on-gear):

- `{CRYSTAL}` — the component crystal display name(s), styled, joined by the name template's leading colour run
  and a comma (a single is just its own display name). A merged Chaos+Light renders `&4&lChaos&6&l, &e&lLight`.
- `{DESCRIPTION}` — each component's `description` block, stacked with a blank line between blocks. Expands to
  many lore lines (a **line-expanding** token, unlike simple replacements).
- `{KINDS}` — the item kinds the crystal applies to (the **intersection** for a multi — where the whole stack can
  legally sit), formatted like the enchant "Applies to" line.

The colour question is resolved the simplest way: the styled display name already carries its colour, so
`{CRYSTAL}` just joins those names. There is no `&[crystalcolor]` placeholder and no separate `color:` field.

### 2. Multi-ability crystals (mirror sets)

A crystal expands to **one or more** `AbilityDef`s, exactly like an armour set's `bonuses:` list. The first ability
keys to the crystal's base key (`crystals/flame`); further abilities to `crystals/flame/a1`, `/a2`, … all
`SourceKind.CRYSTAL`. `WornResolver` resolves the base id **and** walks `/aN` (the same loop sets already use), so a
stacked crystal contributes every one of its abilities. A single top-level `trigger`+`effects` shorthand remains
for one-bonus crystals.

### 3. N-component stacks, merge capped by `max-merge`, 100% apply

- `CrystalItemData` holds `1..ABSOLUTE_MAX` component keys (`ABSOLUTE_MAX` = the PDC-bloat guard, formerly
  `max-stack`), encoded `a+b+c`. `isMulti()` = more than one component.
- **Merge** (crystal dropped on crystal) concatenates the two key lists — single/single, single/multi, multi/multi —
  with the dropped (cursor) crystal on **top**, rejected if the result would exceed `crystals.max-merge`.
- **Apply** (crystal dropped on gear) is **always** committed (no roll, no `consume-on-fail`); the crystal occupies
  one crystal **slot** (`crystals.slots`) as one `a+b+…` entry, capped in component count by `max-merge`.
- `config.yml crystals:` gains **`max-merge`** (the "global max multi-crystal count"). Cosmic-pack default
  `slots: 1`, `max-merge: 2`. `successChance`/`consumeOnFail` are removed from `CrystalConfig`.

### 4. Component-level, topmost extraction

The extractor pops the **topmost single component** — from the last entry on gear, *or* from a multi-crystal item
it is dropped on. The popped component is minted back as a whole single crystal; the remainder stays (a reduced
stack, or the slot frees when the last component leaves). Applying an extractor to a plain single is the same as
extracting it whole.

### 5. On-gear line moves and re-renders as the crystal name

The gear crystal line moves to sit **below the orb-slots line and above the heroic line** and renders the crystal's
**`lore-while-on-item`** template (for the cosmic pack, identical to the item name — `Armor Crystal (Flame)`).
`LoreStyle.crystalColor` degrades to a fallback used only when no template is wired (tests). The name string is
single-sourced through one `CrystalNames` helper shared by the renderer and the mint path.

### 6. Minimal engine support for two default effects

Two cosmic-pack bonuses have no faithful existing kind, so (owner-approved) the engine gains:

- an **`EXP_GAIN`** trigger (bound to the player XP-gain event, cross-version) + a **double-XP** effect, so Light's
  "Double Experience Gain" is real on every XP source;
- an optional **`chance`** on the suppression-immunity effect, so Chaos's "4% chance to ignore Silence" is a real
  roll rather than blanket immunity.

Frost's "freeze" has no distinct vanilla status and renders as a strong, brief Slowness.

### 7. Content

The six base crystals migrate to the new format (they must change regardless). The five cosmic crystals — **Flame,
Frost, Chaos, Light, Nature** — ship in the cosmic pack with their descriptions authored verbatim and effects that
implement them.

## Consequences

- **A crystal is now as expressive as a set** without becoming a set — same `/aN` ability plumbing, no new resolver
  concept. Adding a crystal stays "one file, pure YAML."
- **The additive fold (ADR 0012) already gives "overlapping types sum" for free**, so stacking two damage crystals
  simply adds their percentages — no special merge maths.
- **No success roll** removes a whole failure branch from the apply path and the "two chances" confusion (the
  application roll is gone; only the per-ability *trigger* chance remains, unchanged).
- **PDC stays a stable-key `a+b+…` string**, so pre-rework single/pair items keep decoding; only the component cap
  and the extractor's granularity change behaviourally.
- **`crystals.max-merge` is the one server knob** for "how strong a stack may get"; `slots` still bounds entries per
  item; `max-stack`/`ABSOLUTE_MAX` remains only as a PDC sanity ceiling.
