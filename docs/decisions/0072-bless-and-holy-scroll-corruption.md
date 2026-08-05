# ADR 0072: `/bless` and holy white scroll corruption

- **Status:** Accepted
- **Date:** 2026-08-01
- **Deciders:** project owner + agent
- **Extends:** ADR-0013 (the command surface), ADR-0040 (sectioned lore composition),
  ADR-0047 (the module fold)
- **Relates to:** ADR-0016 (per-item PDC keys off the combat blob), ADR-0022 (permanent-while-worn
  passive potions), ADR-0065 (freeze windows), ADR-0069 (the combo-DoT park ledger)

## Context

Two unrelated player-facing gaps, decided together because both landed in one increment.

1. There is no way for a player to shed the debuffs an opponent has landed on them. The Clarity set's
   "Bless Passive Ability" is a `CURE { category: HARMFUL }` on a 5-tick `REPEATING` bonus — continuous
   immunity for one set's wearers, tied to wearing that set, and covering potion effects only.
2. A holy white scroll is infinitely re-appliable, so a single valuable item can be carried through
   unlimited deaths. There is no cost to the item for being saved.

## Decision

### The cleanse

There is ONE cleanse in the plugin: `CURE { category: HARMFUL }`. Everything that cleanses runs it —
clarity's Bless on a 5-tick `REPEATING` bonus, the Cow Pet's right-click, and `/bless` once on demand.
`/bless` is defined as *one application of the set effect*, so it holds no cleanse logic of its own; it
contributes only a permission and a cost/cooldown policy around the shared sweep.

A HARMFUL sweep is therefore more than a filtered clear. It:

- removes harmful potion effects, classified by `PotionCategories` so the SLOW/SLOWNESS-era renames
  resolve alike across 1.8.9 → 26.x;
- **extinguishes burning** — a damage-over-time the holder did not choose, and the only non-potion DoT
  that is not itself a freeze;
- **spares permanent effects** — see below.

The other `CURE` categories (ALL, BENEFICIAL, NEUTRAL) stay a blunt clear and are untouched by this ADR:
nothing lands a buff on you, so there is nothing there to protect, and no fire to put out.

**What is deliberately NOT cleared.** Freezes — the `FREEZE` window's DoT chain and its slow — survive a
cleanse. A freeze is confinement, thawed by `TRAP_BREAK`, not a debuff to shrug off. This is the one place
"clear all DoTs" is knowingly not taken literally, and on this codebase the freeze chain is the *only*
engine DoT, so the practical scope of the DoT clause is burning plus POISON/WITHER (already potions).
Marks, combat maluses (WEAKEN/REFLECT/TELEBLOCK/DISARM), `DISABLE_*` suppression windows, potion LOCKS,
and the combo-DoT park ledger are all likewise out of scope — a cleanse is not a combat-state reset.

**Permanence.** A debuff the holder carries by their own choice — a helmet granting mining fatigue — is
never stripped. Two independent tests, deliberately overlapping:

- `PermanentPotions.maintains` — SE's own permanent-while-worn grants, **re-derived from live worn state +
  live suppression** on each ask (`WornPotionGrants.fn`, the `LightningBoost` rule) rather than read from
  `PassiveEffectDriver`'s cache, which can trail a gear change by a sweep. Both consult the same pure
  `computeDesired`, so the two cannot disagree about what the gear implies. A suppressed passive grants
  nothing and is therefore cleansable.
- `PermanentPotions.permanentDuration` — an effectively infinite remaining duration: the 1.19.4+ infinite
  marker, or past `PERMANENT_FLOOR_TICKS` (4 h). This catches another plugin's permanent grant SE knows
  nothing about, and holds even where no bridge is wired. The floor sits above vanilla Bad Omen (100 min,
  which stays cleansable) and below the 1 000 000 ticks the driver applies.

`PermanentPotions` rides `SinkEnv` as **instance wiring**, not a mutable static installer — the
`movementExemption` rule. A first attempt used an `Allies`-style static hook and was correctly rejected by
the ADR-0047 G2-c frozen-installer gate.

### `/bless`

**Surface.** A standalone `/bless` registered dynamically on the command map (the `/enchants`,
`/splitsouls` shape), plus `/se bless [player]` as the admin mirror — the identical cleanse with the gate
skipped, on the sender or a named online player.

**Permissions.** `starenchants.bless` (default true, the `starenchants.use` precedent) and
`starenchants.bless.bypass` (default op) which skips cooldown and cost.

**Policy.** `config.yml` `bless.cooldown-seconds` (default 60) and `bless.cost` (default 0, charged
through the economy bridge). Both read live. Three rules the gate holds:

- The cooldown survives a relog. A landed cooldown a reconnect could shed would make the knob decorative.
- A cost with no economy provider installed **refuses** rather than running free — a missing Vault is an
  operator misconfiguration, not a discount.
- Charging is the last gate and commits only once every other check passes, so a refused bless never
  costs a player anything. `cooldown-seconds: 0` means no cooldown, including for a window armed before
  the knob was turned down.

`features.bless` gates the module at BOOT (a command name cannot be cleanly re-bound mid-run), matching
`command-trigger`; the cost/cooldown knobs it reads are live. The permanence bridge is wired at the
composition root, NOT in this module — with `/bless` switched off the shipped `CURE` callers still need it.

### Holy white scroll corruption

An item may only ever SPEND `max-protections` holy white scrolls — 7 in the built-in defaults and in the
shipped pack.

**The count rises on the DEATH that cashes a marker in, not on apply.** A scroll applied but never used
costs the item nothing; the allowance measures protections *delivered*. `HolyScrollService.keepFromDrops`
is the single site that bumps it, immediately beside the `AppliedSlot.HOLY` release it pairs with.

**Stage is a percentage of the allowance**, so the lore reads the same at any configured maximum:
1–49 % `SEMI CORRUPT`, 50–99 % `VERY CORRUPT`, 100 % `CORRUPTED`. At `CORRUPTED` the item refuses further
holy white scrolls, and the refused scroll is **not** consumed (the `scroll.holy.already` shape).

**State** is a per-item PDC integer under its own key (`holyprotections`), never the combat blob — the
`TrakCodec` counter rule, so a death-time bump cannot thrash the `ItemView` content-hash cache.

**Lore** renders from that count directly below the holy PROTECTED line, inside the existing ADR-0040
protection section. Unlike the markers above it, this line is permanent once earned: the PROTECTED line
vanishes with its marker while the corruption line stays and advances.

`max-protections: 0` disables corruption entirely — unlimited scrolls, no line, no refusal. A count at or
beyond the maximum is CORRUPTED, so lowering the allowance retroactively corrupts an item that has already
outrun it rather than un-corrupting it back to a middle stage.

## Consequences

- Clarity's set passive and the Cow Pet's right-click both gain the new cleanse semantics, because they
  share the one definition. Two balance changes follow, and both are intended by the unification:
  - **A full Clarity set now confers effective fire immunity** — its passive re-runs ~4×/sec, so a wearer
    cannot burn. The Cow Pet becomes a fire-out button on a cooldown.
  - Neither strips the wearer's own permanent gear debuff any more, so a deliberate
    permanent-fatigue trade-off finally sticks while wearing them.
- `SinkEnv` gains a `permanentPotions` component and a new `of(...)` overload; the prior overload delegates
  with `PermanentPotions.NONE`, so every non-root construction site is unchanged.
- One more feature module (`bless`) in the fold registry, between `traks` and `enchants`.
- `ScrollsConfig.Holy` gains `maxProtections` + `corruptLines`; `HolyScrollService` gains a
  `HolyProtectionCodec` constructor parameter. Both are breaking for direct callers inside the repo only.
- Existing items carry no `holyprotections` key and read 0 — uncorrupted, no line, no migration needed.
  Items that were saved by holy scrolls before this change do not retroactively count those saves.

## Amendment (2026-08-05): `/bless` is the first-debuff cleanse, not the unified one

`/bless` (and its `/se bless [player]` mirror) is no longer "one application of the set effect". Commit
`284680be` gave the command its own body, `feature.bless.BlessEffect`: play the splash cue, then remove the
FIRST non-permanent active effect whose name is in a fixed **13-name** debuff family (blindness, nausea,
instant damage, hunger, poison, slowness, mining fatigue, weakness and wither, with both the old and new
Bukkit spellings paired), and report success only when a debuff was actually removed. Everything above that
line is unchanged: the permission pair, the cooldown and cost gates, the charge-last ordering, and the
boot-time `features.bless` switch.

This is deliberate (owner ruling **R-QC28a**), not drift, and it narrows the § Decision text above. "There
is ONE cleanse in the plugin … `/bless` is defined as *one application of the set effect*, so it holds no
cleanse logic of its own" still describes the `CURE` effect and its CONTENT callers; it no longer describes
the command. A single-debuff, single-cue cleanse is the felt behaviour the command is expected to have —
`CURE { category: HARMFUL }` on demand is a far stronger button than that.

The set passive and the Cow Pet are untouched and still run the unified `CURE`. The Consequences note
below — a full Clarity set re-running the cleanse ~4×/sec, so a wearer effectively cannot burn — is
**owner-confirmed** (**R-QC28b**) as intended, not a bug to be trimmed back.
