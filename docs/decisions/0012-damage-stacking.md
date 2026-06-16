# ADR 0012: Damage stacking is fully additive

- **Status:** Accepted
- **Date:** 2026-06-15
- **Deciders:** owengregson

## Context

Damage/reduction bonuses come from five sources at once (enchant, armor set,
weapon, crystal, heroic). The originals compounded them multiplicatively by
accident (registration-order-dependent `setDamage` calls), producing runaway,
unpredictable numbers. The unified engine folds all sources in one pass
(ADR 0011 / `docs/architecture.md` §6.1), so the stacking formula is a choice.

## Decision

**Fully additive within each side, no multiplicative stacking across sources:**

```
final = base × (1 + Σ outgoing%) × (1 − Σ reduction%)
```

All outgoing bonuses sum into one factor; all reductions sum into a parallel
factor. The damage accumulator has additive buckets only.

## Consequences

- Predictable, tunable numbers; no accidental compounding.
- `DAMAGE_INCREASE` (equation-capable) contributes a compiled expression
  evaluated per hit, still into the additive bucket.
- A per-server config knob to select an alternative policy may be added later but
  is not required now.

## Alternatives considered

- Additive-within-class, multiplicative-across-class — rejected: less predictable.
- A config knob shipping both from day one — deferred (YAGNI for launch).
