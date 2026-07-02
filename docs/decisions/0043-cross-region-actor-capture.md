# ADR 0043: Demand-captured actor-origin snapshot (capture-at-dispatch)

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** project owner + Folia autogen follow-up
- **Relates to:** ADR-0011 (engine architecture), ADR-0038 (add-on SPI surface),
  ADR-0039 (demand-driven precedent + LinkedContent dispatch), ADR-0042 (the Regions
  cross-region guard); `docs/architecture.md §3.4–3.6`; skills `effect-engine`,
  `folia-scheduling`, `performance-hot-paths`.

## Context

Effect bodies always run on the event-firing thread: `AbilityExecutor.runEffects`
calls `kind.run(ctx, sink)` inline, and only Sink *intents* hop threads later at
`DispatchPlan.flush`. On Folia a combat event (`EntityDamageByEntityEvent`) fires on
the region owning the hit — the **target's** region. The **actor** can be remote from
that thread: `CombatDispatch` resolves a projectile's damager to its shooter, so an
arrow fired across a region boundary runs the shooter's ATTACK abilities on the
victim's region thread with `ctx.actor()` remote; symmetrically the DEFENSE pass
exposes the remote shooter in the victim slot.

Five effect kinds read that remote entity's live positional state inside `run()`:

- `ParticleRingEffect` — `who.getLocation()` (default `@Self` = actor).
- `ParticleLineEffect` — `actor.getLocation()` (tether anchor).
- `WalkerEffect` — `who.getLocation()` (`@Self`).
- `SpawnEntityEffect` — `who.getLocation()` (`@Self`).
- `TeleportBehindEffect` — `reference.getLocation()` / `getDirection()` and
  `ctx.actor().getEyeLocation()` (the `of: ACTOR` reference + the sight-from eye).

An audit found the same defect class in two non-default modes: `TeleportEffect`
(`to: ACTOR` reads the actor location) and `VelocityEffect` (`mode: away` reads the
actor location, and NPEs on a null actor). Every other actor use in the kinds is an
identity/UUID read or a Sink write (hopped by the plan), which is safe.

`AffinityAutogenSuite` — the tester that fires each non-local kind across two Folia
regions — statically **skipped exactly these five** because their inline remote read
could not survive a cross-region firing thread. A precedent already existed for the
correct pattern: `FactPopulator` performs guarded firing-thread actor reads
(`try`/`catch` + `Regions.swallowed`, ADR-0042) for the same "cross-region shooter on
ATTACK" reason.

## Decision A — a per-kind demand bit

A kind declares `EffectSpec.actorOrigin()` (`needsActorOrigin()`) when its `run()`
anchors on the actor. The executor consults this on the already-dense-id-resolved kind
(`kind.spec().needsActorOrigin()` — a constant field behind the same `spec()`
dereference `slotMap` already pays on this path), so no extra bitset and no extra
per-hit work. The same declaration is surfaced to add-ons via `AddonSpec.actorOrigin()`
and translated by `AddonBridge`.

## Decision B — capture at dispatch, guarded, fail-closed

The executor lazily captures ONE `ActorOrigin` primitive snapshot (x/y/z, eye-Y,
yaw/pitch, world) per **activated** ability, on the firing thread, **before any region
hop** (intents hop only at flush). Capture is guarded ADR-0042-style: a
cross-region fault yields `ABSENT` (a null-world snapshot), never a throw and never an
effect fault. `run()` reads actor state only through `ctx.actorOrigin()` /
`ctx.actorOriginEye()`, each returning a fresh `Location` (hoist out of per-target
loops). Any remaining per-target live read of a *non-actor* resolved target (an AOE
scan member, the DEFENSE-slot attacker) stays a `Regions`-guarded live read that skips
that target on a fault. Capture is post-gates (only for activated origin-flagged
abilities — strictly less work than a pre-gate capture) and never runs for unflagged
kinds, so the per-hit gate walk and the JMH floors are untouched.

## Decision C — the snapshot is the actor's state at activation

The snapshot is the actor's pose **when the trigger fired**. `WALKER` lays its platform
where the actor stood at activation; `PARTICLE_RING`/`LINE` anchor there; even a
`WAIT`-deferred intent anchors on the activation-time origin, not on wherever the actor
has since wandered.

## Decision D — per-kind audit

- **PARTICLE_RING / PARTICLE_LINE / WALKER / SPAWN_ENTITY** — reads only. The actor
  target reads the snapshot; other resolved targets are `Regions`-guarded fail-closed.
  Their world mutations (`dust` / `tempPlatform` / `spawnEntity`) are location intents,
  hopped by the plan. `SPAWN_ENTITY` falls back to `ctx.location()` when the actor
  target is skipped.
- **TELEPORT_BEHIND** — the `of: ACTOR` reference and the sight-from eye read the
  snapshot; the possibly-remote VICTIM-slot reference (DEFENSE + projectile) stays a
  guarded live read; its only write (the mover teleport) already hops via the
  entity-owned `teleportSafe` intent, and the landing-safety block reads were already
  `Regions`-guarded in both overlay `DispatchSink`s.
- **TELEPORT (`to: ACTOR`) / VELOCITY (`mode: away`)** — same class, folded in;
  `VELOCITY away` additionally stops NPEing on a null actor.

## Consequences

- Autogen fires **43/43** non-local kinds (0 static skips) plus a Folia
  `affinity.autogen.staging` assertion that the staged attacker and victim really sit
  in distinct regions. The assertion immediately earned its keep: the historical
  512-block gap **collapsed into one region** on a fresh Folia test world (so the
  "cross-region" checks had been running same-region), and the autogen gap was widened
  to genuinely distinct regions — now the fix is exercised with a truly remote actor.
- Capture cost is one guarded read + one small record, only for activated
  origin-flagged abilities; the gate walk and JMH floors are unchanged (unflagged kinds
  pay a single boolean field read behind the `spec()` dereference the path already
  makes).
- **Residual, stated honestly.** `javap` on Folia 26.1.2 confirms
  `CraftEntity.getLocation()` reads the entity's NMS position/rotation fields directly
  and does **not** thread-check (unlike `getHandle()`, which calls
  `TickThread.ensureTickThread`). So a remote capture usually does **not** throw — it
  may observe a mid-tick pose (position and rotation are separate field reads). This is
  one bounded site captured once at activation, and the guard still fails closed on any
  read that does throw (e.g. a null world), which is strictly better than the prior N
  unguarded reads scattered through the effect body. The demand bit is per-kind, not
  per-arg-config, so a `VELOCITY: add` still captures — accepted for a per-kind flag.

## Alternatives considered

- **A per-ability bit folded at the compiler + a `WornState` per-trigger fold + capture
  in `TriggerRunner`** — rejected: capturing before the gates wastes work on rejected
  activations, and it grows `Ability`/`WornState`/the `Compiler.of` seam for no
  precision gain over the per-kind bit.
- **Capture at projectile launch, carried on the projectile** — rejected: a new state
  channel, wrong for non-projectile remotes, and "at activation" is the ratified
  semantic (Decision C).
- **Origin primitives on `ActivationContext`** — rejected: it forces the capture before
  demand is knowable and ripples through every context construction site.
