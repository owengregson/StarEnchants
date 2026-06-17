# ADR 0021: Heroic as a bounded multiplicative stage (amends 0012's scope)

- **Status:** Accepted
- **Date:** 2026-06-17
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0012 (fully-additive damage fold) — this narrows its scope; docs/v3-directives.md §F

## Context

ADR 0012 made the damage arbiter **fully additive**: all outgoing percentages sum into one factor and
all reductions into a parallel factor, so the result is order-independent and the registration-order
multiplicative compounding (the original catalogs' worst combat bug) cannot occur.

The v3 parity work reshapes **heroic** armour from the placeholder *flat* stat (`HeroicStat.flatDamage`/
`flatReduction`) to **percent multipliers** (EliteArmor's shape): a heroic piece grants `+X%` outgoing
damage / `−Y%` incoming. The directive (§F) requires heroic percents to behave as a genuine multiplier
on the final damage — not to be summed into the additive fold (which would make a "+50% heroic" worth
less when other additive buffs are present, and would not match player expectations of a distinct
"heroic tier" multiplier). That directly conflicts with ADR 0012's "no cross-source compounding."

## Decision

**The additive fold stays additive; heroic is a distinct, bounded, MULTIPLICATIVE stage applied AFTER
the fold.** The full pipeline is now:

```
folded = max(0, (base × (1 + Σ outgoing%) + Σ flatDamage) × (1 − Σ reduction%) − Σ flatReduction)
final  = max(0, folded × clamp(1 + Σ heroicOutgoing%, 0, 4) × clamp(1 − Σ heroicReduction%, 0, 1))
```

- **The fold (ADR-0012) is unchanged** — every enchant/set/crystal/flat-effect contribution remains
  additive and order-independent. Heroic does not enter the fold's `outgoing%`/`reduction%` buckets.
- **Heroic is the one deliberate exception** to "no compounding": two heroic pieces' percents sum
  within the heroic stage, then that stage multiplies the folded result once. It is **bounded** — the
  outgoing factor is clamped to `[0, 4]` (at most ×4) and the reduction factor to `[0, 1]` (at most
  100% off) — so it cannot run away the way the original unbounded compounding did.
- **`HeroicStat`** becomes `(percentDamage, percentReduction, durability)`, where `durability` is now a
  per-item probability `[0,1]` of cancelling an item-damage event (was an inert flat field).
- **Wiring:** `DamageFold` gains `heroicOutgoing`/`heroicReduction` buckets + the final stage;
  `Sink`/`DispatchSink` gain `addHeroicOutgoing`/`addHeroicReduction`; `TriggerRunner` contributes the
  worn heroic percents (attack side → outgoing, defense side → reduction). The flat buckets remain for
  flat-damage *effects* (`ADD_DAMAGE`/`REDUCE_DAMAGE`), which are not heroic.

## Consequences

- Heroic gives a predictable, multiplier-like feel without reintroducing the additive fold's banned
  compounding, and the clamp keeps the worst case bounded.
- The on-disk combat blob format is unchanged (still three doubles for heroic); the values are now
  interpreted as fractions. No real heroic items exist yet (apply UX lands in §F2), so no migration.
- Per-item heroic durability is read from the SPECIFIC damaged item (a `PlayerItemDamageEvent` listener),
  not the worn sum.
- Reduction *scope* (entity/PvP-only vs all-causes) and the heroic apply UX (the upgrade item, success
  range, material change, "heroic piece" lore) are the follow-up (§F2); this ADR covers only the damage
  model.
