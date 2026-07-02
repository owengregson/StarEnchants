# ADR 0037: Heroic percents join the additive damage fold (supersedes 0021)

- **Status:** Accepted
- **Date:** 2026-07-01
- **Deciders:** project owner + engine work
- **Supersedes:** ADR 0021 (heroic as a bounded multiplicative stage)
- **Relates to:** ADR 0012 (fully-additive damage fold — restored to full scope); ADR 0031 (heroic
  armour/durability as real vanilla stats); docs/v3-directives.md §F

## Context

ADR 0021 reshaped heroic from a placeholder flat stat into **percent multipliers** and made them a
distinct, bounded **multiplicative** stage applied *after* the ADR-0012 additive fold — the one deliberate
exception to "no cross-source compounding" — so a "+50% heroic" felt like a genuine tier multiplier rather
than diluting among other additive buffs.

ADR 0031 then made heroic armour carry **real vanilla armour-point/toughness modifiers** (and a real
diamond max durability where the platform supports it), so a heroic piece already *is* diamond-equivalent
through the vanilla stat system, on the HUD and under other combat plugins (e.g. Mental).

With the diamond-like base now real, the owner reconsidered the damage model (decision 2026-07-01): heroic
should be **diamond-like armour plus a small damage bonus that behaves like any enchant's**, not a distinct
multiplier tier. The v3 §F distinct-multiplier framing is reversed. Keeping a separate multiplicative stage
(and its `heroic.max-outgoing-factor` clamp) adds a special case that no longer matches the intended feel.

## Decision

**Heroic percents feed the ordinary ADR-0012 additive buckets like any enchant contribution; the post-fold
multiplicative stage and its clamp knob are retired.**

- A heroic **weapon**'s outgoing percent sums into `Σ outgoing%`; a heroic **armour** piece's reduction
  percent sums into `Σ reduction%` — the same buckets an enchant `DAMAGE_MOD` uses, with the attack/defense
  sides preserved exactly as before. The fold is once more the pure ADR-0012 form:
  `final = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)`.
- `DamageFold` loses its `heroicOutgoing`/`heroicReduction` buckets, the post-fold multiply, the ceiling
  supplier, and `DEFAULT_MAX_HEROIC_OUTGOING_FACTOR`. `Sink`/`DispatchSink` lose
  `addHeroicOutgoing`/`addHeroicReduction`; the worn heroic contribution routes through the existing
  `addOutgoingDamage`/`addDamageReduction` entry points.
- The `heroic.max-outgoing-factor` config knob (and the `MasterConfig.HeroicSection` record it fed) is
  removed.
- **The on-disk combat blob format is unchanged.** `HeroicStat` keeps `(percentDamage, percentReduction,
  durability)` plus its flat diamond-delta fields; only the fold *semantics* move, so existing heroic items
  keep working — their values are simply reinterpreted in the additive buckets.

## Consequences

- Heroic now **dilutes additively** with other buffs — a "+50% heroic" is worth less when other additive
  bonuses are present than it was under the multiplicative stage. This is intended: heroic is an ordinary
  additive source on top of the (already real, ADR-0031) diamond base stats.
- The banned registration-order multiplicative compounding still cannot occur — there is no multiplicative
  stage at all now. Order-independence is total.
- Any heroic items forged before this change keep functioning; their stored percents feed the additive fold
  with no migration. The retired clamp knob in a live `config.yml` is simply ignored (an unknown key).
- Combat behaviour changes, so the change is held for the live Paper+Folia matrix before merge.
