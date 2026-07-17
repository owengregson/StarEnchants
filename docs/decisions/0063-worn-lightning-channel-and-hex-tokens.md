# ADR 0063: Worn lightning channel (LIGHTNING_MOD) + {#rrggbb} crystal likenesses

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + engine work
- **Relates to:** ADR-0034/0035 (crystals), ADR-0054 (bolt attribution / EngineDamage stand-down),
  ADR-0060 (the WATER_SPEED worn-channel precedent), ADR-0012/0050 (damage economy), ADR-0062
  (the `{#rrggbb}` colour-token parser, landed in parallel — both crystals' likenesses consume it)

## Context

Two owner crystals: **Wind** (+1% outgoing, stackable — pure YAML, the Frost line) and **Bolt**
(+10% to the wearer's outgoing lightning damage, stackable). Bolt cannot be content-only: engine
bolt damage is applied inside the `EngineDamage` frame, and `CombatDispatch.onDamage` stands down
for engine-issued damage (ADR-0054), so no gate walk — and therefore no conditioned `DAMAGE_MOD` —
ever sees a bolt's payload; the vanilla splash is dealt by the `LightningStrike` entity, which is
not a Player, so the attack walk never runs for it either.

Both crystals' authored likeness uses `{#rrggbb}` colour tokens; their parsing is owned by ADR-0062.

## Decision

1. **`LIGHTNING_MOD`** — a worn PASSIVE channel in the WATER_SPEED mold: a no-op marker kind whose
   summed `amount` percent is read on demand. `feature.trigger.LightningBoost` resolves an actor's
   fraction from live `WornState` + live suppression; `SinkEnv` carries it as instance wiring
   (`ToDoubleFunction<UUID> lightningBoost`, default 0); `DispatchSinkBase.lightningAndDamage`
   scales the **authored bolt payload** at emit time on the firing thread (primitive capture,
   §3.6). Clamped at 0 — a full-negation debuff yields a cosmetic bolt. The vanilla ~5-splash and
   `damage: 0` cosmetic bolts are untouched; generic `DAMAGE` effects that merely accompany a
   cosmetic bolt (natureswrath) are not "lightning damage".
2. **Content:** Wind (rare, ATTACK `DAMAGE_MOD add 1` — crystals stay percent per ADR-0050 R3) and
   Bolt (rare, PASSIVE `LIGHTNING_MOD 10`), both stackable, both `applies-to: [ARMOR]`, likenesses
   verbatim with the roster's `<NAME> CRYSTAL BONUS` header idiom. Both authored with the
   `{#rrggbb}` colour tokens ADR-0062 parses ({#e9ecec} Wind, {#5eb3f6} Bolt) — this ADR consumes
   that parser, it does not define it.

## Consequences

- Lightning-flavoured kits gain a real, tunable amplifier that no walk-order or attribution quirk
  can miss; stacking is the plain sum of worn sources, muted live by suppression.
- The hot path is untouched: the channel is read only when a bolt actually fires.
- The `Ability` record, the gate order, and the fold are unchanged.
```
