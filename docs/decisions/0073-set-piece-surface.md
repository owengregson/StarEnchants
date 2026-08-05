# ADR 0073: The set-piece surface

- **Status:** Accepted
- **Date:** 2026-08-04
- **Deciders:** project owner + agent
- **Extends:** ADR-0014 (armour sets), ADR-0021 (heroic stats), ADR-0040 (the one recompose seam)
- **Relates to:** ADR-0019 (no invented data), ADR-0039 (fact masks), ADR-0049 (suppression windows)

## Context

A `SetDef` gave every armour piece ONE shared `armor.lore` and ONE shared `armor.enchants`, and a
`pieces:` member carried `material` + `name` and nothing else. Every per-piece fact the armour-set
port measured — a piece's own flavour lore, a slot's own enchant roster, the leather dye, and
whether the piece ships already heroic — had nowhere to live, and twelve authored set files carry
those facts as comments rather than lose them.

The consequence chain matters more than any one field. Nothing could mint a heroic piece, so
`%victim.heroicpieces%` read 0 on every server, so the enchants gating on it were inert by
construction. The fact was wired end to end and no content could ever move it off zero.

## Decision 1 — the surface lives on `SetDef.Member`, not on a new `items/` type

The port's stopgap was a second file per piece: fourteen `items/<set>-<piece>.yml` declaring
`type: set-piece`, against an `ItemsLoader` that does not claim it. We fold the surface onto the set
def instead.

- `items/` holds ONE **universal likeness per item family** — the crystal, the mask, the pet, the
  scrolls. Not one file per instance. Per-instance identity has always lived in `content/` beside
  the def that names it, and a set piece's material and name already did.
- `ItemsLoader` output is **config only and never reaches the compiler**. A per-piece `enchants:`
  map holds `enchants/<id>` refs that must be validated library-wide and stamped into combat state
  at mint — compiler data, on the wrong side of that line.
- `ItemsConfig` is a closed record of singleton `Optional`s. Set pieces are N per set; claiming them
  there means a keyed collection plus a new cross-loader referential check between `items/*.yml` and
  `content/sets/*.yml`, with no precedent.
- One set's authored truth stays in one file, and the shared roster's existing `LibraryLoader`
  validation extends to the per-piece one for free.

Members take `lore`, `enchants`, `color` and `heroic`. Lore and enchants **refine** the shared ones
rather than replace them: a piece's flavour prints above the shared block, its roster entries apply
over the shared ones (a re-stated ref overrides its roll). All four are absent on every set authored
before this, so those sets mint and render byte-identically.

**Per-piece lore renders from state** (ADR-0040), never baked at mint. The discriminator is the
item's gear KIND — `LEATHER_BOOTS` → `BOOTS` — which IS the slot name for anything a set can mint,
so nothing new is stamped on the item and the codec is untouched. `SetLore.armor(setKey, slotToken)`
is a default method delegating to the set-key-only read, so a lookup written before this keeps
answering.

## Decision 2 — a mint roster entry is a ROLL, resolved by the minter

`armor.enchants` was `ref: level`, one fixed integer, and this family's mint is a draw. An entry now
takes either a bare level or a roll map:

| Form | Meaning |
| --- | --- |
| `PROTECTION: 5` | fixed, always minted, **consumes no draw** |
| `{ min: 2, max: 5 }` | uniform over the band |
| `{ nearly-maxed: M }` | `min(M, max(1, M - 2) + rand(3))` |
| `{ chance: 17.5, min: 1, max: 4 }` | a probability gate over either shape; fractional (R-QC51), drawn in basis points |

`nearly-maxed` is reproduced **literally**, not as a uniform band over the same bounds: the outer
`min` collapses two of the three rungs once `M < 3`, and that skew is the measured distribution.

`EnchantRoll` is pure data in `se-compile` (which is RNG-free); the draw is `feature.apply.SetMint`
over the injected `Rolls` vocabulary. The image-fixture importer takes the top of the band instead —
generated art must not depend on a draw. Validation checks the whole BAND against the enchant's max,
so a roll cannot mint a level nobody authored on some fraction of pieces and pass review on the rest.

## Decision 3 — `heroic: true` mints through the upgrade gesture's own stats write

Extracted as `HeroicStamp`: the stats half of the gesture with none of its economy — no success
roll, no consumed item, and deliberately **no material swap** (an authored piece already names its
final likeness). `HeroicService.applyTo` and the set minter call the same instance, wired once at the
composition root, so both grant exactly the tier the pack configured.

The per-slot heroic LADDER (a piece's own armour value, durability and reduction rung) stays
deferred with the second heroic tier: a minted heroic piece takes the pack's single configured tier,
which is what `items/heroic.yml` can express today.

**A set that already folds its heroic wall into a completion bonus cannot take the stamp.** The
stamp routes the per-piece reduction into the SAME additive channel a `DAMAGE_MOD(side: defense)`
row feeds — `DamageFold` sums `heroicReductionPercent + reductionPercent` — so a set whose 4/4 bonus
IS its heroic reduction folded (the four M-Kit sets, at a measured 45 %) would bill 45 + 27 = 72 %.
Dropping the folded row instead is not the fix: `items/heroic.yml` carries the plain tier, so the
set would quietly lose 18 points of its measured wall. Those four keep the fold and take no stamp;
the M-Kit's steeper tier is what unblocks them. A set with no such row (KOTH) takes the stamp and
gains a channel it did not have. This is the standing note those files already carried, made
load-bearing.

## Decision 4 — a levelless `PROC_REBOUND` rule has no level bound

`AbilityDef.level` is an ENCHANT level; every non-enchant reader passes a literal `0` because a set,
crystal, mask or pet bonus has none to state. `ReboundPlan.claim` refused on
`ability.level() > rule.level()`, and every enchant level starts at 1 — so a set-contributed reflect
lost the gate to everything on the server and claimed nothing, forever, however its band was
authored.

Level `0` now reads as **no level bound** rather than as the weakest grade. The band still bounds
it; enchant-armed rules keep their gate unchanged, ties included.

## Decision 5 — a defender-keyed window merge never weakens what is live

`SuppressionStore.defend` merged by keeping the record with the later expiry WHOLE — chance,
attribution and cue along with it. A set and its own matching crystal arm the same
`(holder, scope, key)` triple from two independent abilities on the same cadence, so whichever fired
later in a cycle governed the next window, and the crystal's chance is always the lower one. A
wearer who completed the set AND carried its crystal was permanently WORSE off than one wearing the
set alone.

Merge to the **stronger chance over the later expiry**. Chance decides the identity (and carries its
own attribution and block line) because chance is the point of the window and expiry is a refresh
detail; a chance tie falls back to the later expiry, and a full tie keeps the incumbent — the same
"ties keep what is already live" rule the activator-side merges use.

## Decision 6 — `%actor.setweapon%` answers the `on: weapon` gate's own question

`on: weapon` gates a WHOLE bonus and can modulate no number, so a set whose armour and weapon rolls
differ only in a RATE had to ship the difference as a second independent roll — two fields where the
source has one. `%actor.helditem%` cannot stand in: it is a material name, never an item identity.

The flag is resolved at equip in `WornResolver`, where slot provenance still exists, and read by
UUID off `WornState` — no live main-hand read on the hit path. It is deliberately the SAME condition
that admits an `on: weapon` bonus (its weapon in the MAIN hand and its set complete), so an author
reading the fact and an author writing `on: weapon` are asking one question.

## Recorded, not built

- **`IMPACT` source scoping.** A landing block fires EVERY `IMPACT` ability its owner wears, so a
  Dimensional Traveler wearer also carrying Tombstone fires Tombstone's whole-set armour damage on
  each of ~142 landing blocks. The obvious filter — carry the arming `ctx.sourceDefId()` in the cast
  and match `Ability.defId` at the landing — **does not work**: `defId` is assigned per ABILITY, and
  a field's arming bonus and its `IMPACT` payload are two separate `bonuses:` entries with two
  different ids, so that filter would fire nothing at all. Scoping needs an identity the arming
  effect and its payload SHARE — the authored `group:`, which both `AbilityDef` and the set reader
  already carry — plus a group accessor on `EffectCtx` and a group-filtered candidate run through
  the existing `TriggerRunner.runCandidates`. Unscoped stays the default so nothing authored today
  changes. The same shape would scope `TURRET_RING` and `SUMMON_STRIKE_PAYLOAD`, though a turret
  outlives its activation and must carry the group from `bindTurret` through to `bindShot`.
- **A per-recipient relation-colour token on `MESSAGE`.** One broadcast line needs a different
  colour per recipient, because the colour is a property of the READER, not of the activation.
  `%victim.relation%` is a FACT and the two are not interchangeable. This is the same per-viewer
  shape `PHANTOM_BLOCKS` needed and wants the same kind of seam; it is a `MESSAGE`-surface question
  for the owner, not a set question.
- **A Y offset (or location anchor) on `SOUND`.** `SOUND` reads only the entity channel of `who`, so
  a cue authored at "the target +4 Y" plays at the target. Cosmetic; the knob would have to resolve
  inside the sink, where the entity's region is already held.
- **`%status.freeze%`,** the guard fact paired with the new `FREEZE` rung — every other `STATUS_CLEAR`
  rung has one, on the reasoning that "you must be affected by X" is otherwise inexpressible.
  `FrozenTargets.isFrozen` is already `public static` and UUID-keyed, so it is a drop-in.
