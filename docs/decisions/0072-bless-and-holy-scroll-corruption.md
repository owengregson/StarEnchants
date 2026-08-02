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

### `/bless`

A one-shot, on-demand cleanse, gated by permission and by a cost/cooldown policy.

**What it strips.** Every debuff an opponent landed, across the four surfaces that carry one:

| Surface | Cleared by |
| --- | --- |
| Harmful potion effects | `PotionCategories.HARMFUL`, so the SLOW/SLOWNESS-era renames classify alike |
| Freeze windows (Ice Aspect DoT + slow) | `FrozenTargets.breakNow` — the window's own idempotent teardown |
| Parked DoT damage | `DotParkLedger.clear` |
| Marks against the player (Mark of the Reaper, …) | `DamageMarks.clear` |

Burning is extinguished too: fire ticks are a damage-over-time the player did not choose, and no other
surface owns them.

**What it spares.** A PERMANENT debuff the player carries by their own choice — a helmet granting
mining fatigue — is never stripped. Two independent rules protect it, deliberately overlapping:

- the effect is a `PassiveEffectDriver`-maintained grant (exact, and the driver would re-apply it on its
  next refresh regardless, so stripping it would only produce a flicker); **or**
- its remaining duration is effectively permanent — `PERMANENT_FLOOR_TICKS` (4 h), or the 1.19.4+
  infinite marker. This catches another plugin's permanent grant that SE knows nothing about.

The floor sits well above the longest real debuff (vanilla Bad Omen, 100 min, stays cleansable) and well
below the 1 000 000 ticks the passive driver applies, so a worn grant stays spared however long it has
been since the driver last refreshed it.

Marks are directional: `/bless` lifts marks held **on** the runner, never marks the runner holds on
others — blessing yourself must not disarm your own offence.

**Boundary.** Combat maluses an opponent armed (WEAKEN, REFLECT, TELEBLOCK, DISARM), `DISABLE_*`
suppression windows, and potion LOCKS are explicitly **out of scope**. They are opponent-landed too, but
folding them in would make `/bless` a general combat-state reset rather than a cleanse. A consequence
worth naming: a harmful effect held by a live `POTION_LOCK` is removed and then immediately re-denied
back on, so the lock outlives the bless.

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
  costs a player anything.

`features.bless` gates the module at BOOT (a command name cannot be cleanly re-bound mid-run), matching
`command-trigger`; the cost/cooldown knobs it reads are live.

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

- One more feature module (`bless`) in the fold registry, between `traks` and `enchants`.
- `ScrollsConfig.Holy` gains `maxProtections` + `corruptLines`; `HolyScrollService` gains a
  `HolyProtectionCodec` constructor parameter. Both are breaking for direct callers inside the repo only.
- Existing items carry no `holyprotections` key and read 0 — uncorrupted, no line, no migration needed.
  Items that were saved by holy scrolls before this change do not retroactively count those saves.
- `DamageMarks` gains `anyOn(victim)`, the victim-side counterpart to `marked(marker)`.
- Clarity's set passive is deliberately **left as it is**: it still cures harmful potions ~4×/sec with no
  permanent-grant exemption. It is continuous set-scoped immunity, a different mechanic from the on-demand
  command, and changing it would alter shipped set balance. The inconsistency is recorded, not fixed.
