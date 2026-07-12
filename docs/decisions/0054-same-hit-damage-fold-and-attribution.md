# ADR-0054: Same-hit DAMAGE joins the fold; separate procs carry attribution

- **Status:** accepted — **superseded in part by [ADR-0055](0055-rider-effective-units-and-scaling-classes.md)**:
  folded riders land in EFFECTIVE units (authored = delivered pre-armor, never attack-scaled), not in
  the scaled flat bucket; decision §1's "post-multiplier" unit reading is retired. All other
  decisions stand.
- **Date:** 2026-07-11
- **Relates to:** ADR-0012 (fully-additive damage fold), ADR-0026 (Mental knockback coordination),
  ADR-0036 (era-neutral sink core), ADR-0051 (event-entity carve-out, liveness gate)

## Problem

Every damage-dealing effect landed as a bare `target.damage(amount)` — an ownerless CUSTOM hurt —
including a zero-WAIT `DAMAGE` rider aimed at the very victim the firing melee is still processing.
Two distinct defects follow, both diagnosed from the Mental (era combat) side but general to any
plugin that consumes damage events:

1. **The same-hit rider arms a second immunity window.** A bare second hurt on the current victim
   re-arms vanilla's `noDamageTicks`/`lastHurt`. The attacker's NEXT melee, arriving inside that
   window with `amount <= lastHurt`, is window-rejected by vanilla with **no event at all** — so
   every downstream per-hit consumer (hit sounds, damage indicators, authoritative knockback
   delivery) silently skips a hit that visibly connected. The rider also bypassed the §6.1 damage
   arbiter entirely: bonus damage on the current hit is exactly what `DamageFold` exists to carry.
2. **Separate procs are unattributable.** Genuinely separate applications — the WAIT-delayed bleed
   DoT ticks, LIGHTNING bolt damage, Hex reflect and Vengeful Diminish overflow — fired plain
   `EntityDamageEvent`s (cause CUSTOM, no damager). Downstream plugins cannot tell whose damage it
   is; kill attribution, combat logging, and era-combat delivery all see ownerless hurts.

The bare form was also load-bearing: "no damager → cannot re-enter this handler" was the combat
dispatch's only re-entrancy guard, and the plain-event shape was what kept SE's own EDBEE listeners
(rage, immune typing) blind to engine-issued damage.

## Decision

Two sink-kernel rules in `DispatchSinkBase`, one guard in the combat pipeline:

1. **Same-hit riders fold.** A zero-WAIT `damage`/`damagePercentOfMax` intent aimed at the declared
   `eventEntity` (the ADR-0051 seam; only the combat dispatcher declares one) contributes
   `fold.addFlatDamage(...)` instead of scheduling a hurt. One hurt, one immunity window, one
   knockback; the rider is committed with the event by the dispatcher's single `fold().apply` and
   dies with a dodged/cancelled hit — same-hit means same fate. It thereby enters the §6.1 additive
   economy (attack-scale included), where flat bonus damage always belonged. Owner-confirmed
   2026-07-12: folded riders deliver post-multiplier EFFECTIVE damage by design ("whatever enchant
   damage gets folded into the hit should be the effective damage as StarEnchants delivers it").
   WAIT tiers and non-victim targets are untouched routing-wise.
2. **Separate procs attribute.** The sink's one `hurt()` seam applies deferred damage as
   `target.damage(amount, attacker)` whenever an attacker entity is in scope — the activator for
   effect kinds (`DAMAGE`, `MODIFY_HEALTH take/transfer`, `LIGHTNING`), the victim for the two
   dispatcher retaliations (Hex reflect, Vengeful Diminish overflow) — falling back to the bare form
   only with no attacker or a self-target. Downstream plugins now see a real, attributed
   `EntityDamageByEntityEvent`, and vanilla applies its usual source-aware handling (knockback away
   from the source included — era-coherent for reflects and DoT, and delivered by the era-combat
   plugin where one owns knockback).
3. **The `EngineDamage` frame replaces the structural guard.** Every engine hurt runs inside a
   ThreadLocal frame (`EngineDamage.active()`), exact on Paper and Folia because Bukkit fires damage
   events synchronously on the target-owning thread. Consumers preserving the old contract:
   `CombatDispatch.onDamage` stands down (a reflect can never proc a reflect, a DoT tick procs no
   walks), `RageStacksListener` ignores engine damage (rage is for real swings), and
   `ImmuneListener` applies exactly the pre-attribution semantics to engine damage — blanket ALL
   cancels; the damager's held item is proc bookkeeping and never SWORD/AXE-types the hit. Trak
   kill-credit and pet-summon hit-gating intentionally see the attributed events (a fatal bleed tick
   IS the attacker's kill; an AoE proc on a tracked summon IS a player hit).

Dodge/immune/inversion cancel behavior is untouched — a genuinely cancelled or rejected hit stays
silent (the era-combat side reconciles its own pre-sent knock; spec §2.1 there).

## Consequences

- Same-hit bonus damage can no longer eat the attacker's next melee, and it now respects the combat
  caps/attack-scale economy instead of bypassing the arbiter; a rider on a dodged hit no longer
  lands as a phantom second hit.
- Bleed ticks, bolt damage, and reflects are attributed: kill credit resolves (a fatal DoT tick
  yields a real killer-typed fatal event), era-combat plugins deliver and journal them, and vanilla
  applies source-aware knockback to them (new, era-coherent; absent only where a knockback-owning
  plugin takes over).
- A `Type.ALL` IMMUNE holder still shrugs off engine damage exactly as before; SWORD/AXE immunity
  still never applies to it.
- The addon SPI (`AddonSink`) keeps its unattributed two-arg forms — they now default through the
  three-arg seam, gaining the same-hit fold routing when an addon damages the current victim.
- Pinned by `DispatchSinkDamageFoldTest` (fold-vs-defer routing, attribution, the frame) and
  `EngineDamageReentryTest` (dispatch stand-down, rage, immune preservation); the effect-kind tables
  carry the attribution wiring rows.
