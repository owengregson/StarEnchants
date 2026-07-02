# ADR 0012: Damage stacking is fully additive

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

> **Note:** ADR-0021 briefly narrowed this by carving heroic out into a distinct multiplicative stage;
> [ADR-0037](0037-heroic-additive-fold.md) reversed that, restoring this ADR to its full scope — heroic
> percents fold into the additive buckets like any other source.

## Context

Damage/reduction bonuses come from five sources at once (enchant, armor set,
weapon, crystal, heroic). The originals compounded them multiplicatively by
accident (registration-order-dependent `setDamage` calls), producing runaway,
unpredictable numbers. The unified engine folds all sources in one pass
(ADR 0011 / `docs/architecture.md` §6.1), so the stacking formula is a choice.

## Decision

**Fully additive within each side, no multiplicative stacking across sources.**
Percentage bonuses fold multiplicatively against the base; flat bonuses (heroic
flat stats) are placed for predictability — added/subtracted in absolute terms so
an advertised "+5" or "−3" delivers that amount rather than a percent-scaled
surprise:

```
final = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)
```

All outgoing percentages sum into one factor; all reductions sum into a parallel
factor (no cross-source compounding). Flat **damage** is added after the outgoing
multiplier — so it is not inflated by the attacker's own percent buffs — but is
still subject to the defender's reduction, like any incoming damage. Flat
**reduction** is subtracted last, absorbing exactly the advertised amount. The
damage accumulator has additive buckets only (two percent, two flat).

## Consequences

- Predictable, tunable numbers; no accidental compounding.
- `DAMAGE_INCREASE` (equation-capable) contributes a compiled expression
  evaluated per hit, still into the additive bucket.
- A per-server config knob to select an alternative policy may be added later but
  is not required now.

## Alternatives considered

- Additive-within-class, multiplicative-across-class — rejected: less predictable.
- A config knob shipping both from day one — deferred (YAGNI for launch).
