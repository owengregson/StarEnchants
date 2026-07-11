# ADR-0051: Heal/death coherence — same-event health credits and the liveness gate

- **Status:** accepted
- **Date:** 2026-07-11
- **Relates to:** ADR-0012 (fully-additive damage fold), ADR-0036 (era-neutral sink core),
  ADR-0043 (Folia capture-at-dispatch)

## Problem

Players "half-die": a lethal hit fires the death event, but an enchantment heals them in
the same window, leaving a corpse holding health — the server thinks a player state the
client never saw, the client sits on a death screen over a live health bar, and both bug
out until a relog.

Root cause is an ordering race the sink kernel guaranteed we lose. Every world mutation —
including `heal`/`setHealth` — is captured into the `DispatchPlan` and *scheduled* onto
the target's owning thread at flush, never run inline (§3.6). The scheduler runs those
batches strictly after the current call stack, i.e. after the damage event handler
returned, after vanilla subtracted the final damage, and after death fully processed. So
for any hit that is lethal *before* the heal:

1. the DEFENSE walk procs the heal and queues it,
2. vanilla applies the damage → health ≤ 0 → death fires (drops, packets, stats),
3. the queued heal lands on the corpse: `setHealth(>0)` on a dead-but-not-removed player
   is the ghost-maker.

The race is not incidental — the content *requires* the opposite order. Phoenix is
literally advertised as "a blow that would kill you instead restores a burst of health",
and Death God / Ender Walker heal in the ≤25% band, exactly where hits are lethal. A
heal-when-hit that cannot land before the kill decision either does nothing or
necromances; it can never do its job.

The considered alternative — folding same-hit heals into the damage commit as "net
damage" — is quantitatively wrong on the deployment target: the era combat plugin
redistributes our committed damage through a non-linear curve (ADR-0050), so −2 damage
≠ +2 health. Heals must stay in health-space.

## Decision

Two kernel rules in `DispatchSinkBase`, replacing the unconditional defer for
current-health writes (`heal`, `setHealth` — the only ops that can raise a corpse;
`damage` is vanilla-guarded on dead entities, `drainMaxHealth`'s clamp only lowers):

1. **Same-event health credits.** The combat dispatcher declares the event's own entity
   (`SinkReadback.eventEntity(victim)`). A zero-WAIT health write targeting that entity
   (matched by UUID, not instance) is held as a *credit* and run inline at `flush()` —
   still inside the event handler, before vanilla applies the final damage. The firing
   region thread owns the event's entity by definition, so the inline run is
   Folia-correct; it fires no nested events (plain `setHealth`), so no reentrancy. The
   vanilla kill decision then sees post-heal health: the save either genuinely saves, or
   the victim dies cleanly with the heal already consumed. Credits run before the
   deferred plan, in emission order, with the plan's warn-and-skip isolation.
2. **The liveness gate — the dead stay dead.** Every *deferred* health write (WAIT
   tiers, cross-entity targets like lifesteal's attacker-side heal) re-checks the target
   on its owning thread at execution time: `isValid() && !isDead() && getHealth() > 0`,
   else the write is dropped. `getHealth() > 0` carries the check on the 1.8 lane, where
   a 0-hp player's `isDead()` can lag the dead flag. Nothing revives by side effect —
   resurrection, if we ever ship it, must be an explicit death-trigger mechanic, not a
   stale heal.

Walk semantics are unchanged: credits run at flush, not at emit, so facts and conditions
read the same mid-walk state as before. Cancelled hits still flush their credits (a
proc that fired takes effect and armed its cooldown, exactly as deferred heals always
did). Other dispatchers never declare an event entity and are untouched apart from the
gate.

## Consequences

- Phoenix/Death God/Ender Walker behave as written: a save that beats the blow keeps the
  victim alive with coherent client/server state; a blow that beats the save kills
  cleanly. No half-deaths from SE heals in either order.
- A heal that lands after its target respawned still applies to the fresh body (the gate
  reads liveness, not lineage) — vanilla-like, accepted.
- Live proof: `DeathRaceSuite` stages both orders against a real vanilla kill decision
  (fake-player victims, real `EntityDamageByEntityEvent`); the routing and gate are
  pinned by `DispatchSinkHealthCreditTest`.
