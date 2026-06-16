---
name: feature-interaction-rules
description: Use when two or more features interact — damage/reduction stacking, DISABLE_ENCHANT/GROUP/TYPE suppression, souls, slots, crystal stacking, omni/multi-set completion, or EE-on-EA enchant stamping.
---

# Feature-interaction rules

Interactions are resolved by **contribute-then-resolve arbiters** in
`se-engine/interact`, fed by a Snapshot-precomputed plan (§6). Effects
*contribute*; the arbiter commits **once**. An arbiter is **per-event primitive
scratch owned by the single firing thread** — NOT a kernel object threaded across
capabilities (that races on Folia). Source erasure (§4.1) makes all five sources
(enchant/set/weapon/crystal/heroic) feed every arbiter **uniformly**: they are
all `Ability`s, so no per-source special-casing. The gate walk that drives them
is **effect-engine**.

## When to use / not

Use when reasoning about how features *combine*. NOT for *how one effect runs*
(**effect-engine**) or *how state is stored* (**item-data-model**). Per-hit
allocation budget is in **performance-hot-paths**; cross-region commit rules in
**folia-scheduling**.

## The arbiters

| Concern | Arbiter | Rule |
| --- | --- | --- |
| Damage/reduction | `DamageFold` (§6.1) | Effects are FORBIDDEN from `event.setDamage`; they contribute deltas. ONE fold commits. |
| DISABLE_ENCHANT/GROUP/TYPE | `SuppressionSet` (§6.2, §4.1) | Interned-id, O(1) membership at gate 5. Role-correct (see below). |
| Souls | `SoulLedger` (§6.3) | The ONLY code that debits souls; reads the gem by **PDC UUID**, debits atomically. |
| Slots | `SlotLedger` (§6.4) | `max = base + addedSlots`, `used`, `remaining`; computed from `ItemView` and **persisted in PDC**. |
| Crystals | list semantics (§6.5) | Crystals are a **LIST** of keys; N crystals → N `Ability`s. No last-of-type collapse. |
| Omni / multi-set | `WornState` resolver (§5.5, §6.6) | Omni = wildcard resolved **synchronously, read-time** inside the ONE resolver; `activeSets` is a SET. |
| EE-on-EA stamping | `ItemDataService` (§6.7) | Custom-enchant stamping is **synchronous, one write path** in the build path — never fire-and-forget. |

## Damage: fully-additive, one fold (ADR-0012, §6.1)

The approved policy — **no multiplicative stacking across sources**:

```
final = base × (1 + Σ outgoing%) × (1 − Σ reduction%)
```

All same-side sources sum into ONE additive outgoing bucket and ONE additive
reduction bucket — heroic flat stats + set DAMAGE/REDUCTION + crystal + weapon
all feed the same accumulator, order-independent by construction. The accumulator
stores compiled expression closures (not constants), so equation-capable
`DAMAGE_INCREASE` evaluates per-hit but still folds once.

## Suppression is role-correct (§6.2)

The DISABLE op's lowering records, per op, *whose* suppression it keys:

- **`DISABLE_ENCHANT`** keys the **defender** (`equalsIgnoreCase` → interned).
- **`DISABLE_GROUP`** keys the **activator** (`equals` → interned).

`Ability.suppressKey` (interned enchant|group|type) makes gate 5 (SUPPRESSION) an
int compare — case folded at compile time, killing the EE case-sensitivity
divergence. Because crystals are first-class `Ability` sources,
crystal-`DISABLE_ENCHANT` works (dead in EA). A cancellable `PreActivate` event
remains for add-on interception.

## Example: an effect contributes, never commits

```java
@Override public void run(EffectCtx ctx, Sink sink) {
    // CONTRIBUTE a delta — do NOT call event.setDamage (§6.1).
    sink.damageDelta(SourceKind.ENCHANT, +0.25);   // +25% outgoing, folded once
    // SuppressionSet / SoulLedger / SlotLedger are reached the same way:
    // via the Sink/gate, never by mutating shared state directly.
}
```

The fold runs after the gate walk over attacker `combatAttack[]` + victim
`combatDefense[]`, then writes the event **once** on the firing thread. The event
belongs to the firing region, so this is always correct on Folia
(**folia-scheduling**).
