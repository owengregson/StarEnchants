# ADR 0068: Bat-cloud swarm — attacker vision cloud + spawn-Y scatter

- **Status:** Accepted
- **Date:** 2026-07-17
- **Deciders:** project owner + agent
- **Extends:** ADR-0060 (`SPAWN_SWARM`), ADR-0043 (cross-region actor capture)
- **Amends:** ADR-0060 §4 — spawn Y becomes `rise ± 0.6` uniform scatter (X/Z ring unchanged), and
  the attacker-cloud orbit overrides the per-tick damp while a target is live

## Context

The owner wants the summoned bat swarm to gain an AI target: circulate around the 1x2x1 block pillar
directly in front of whoever attacked the summoner most recently (tracking them as they move and
turn) to cloud their vision; with no such nearby attacker, pure vanilla AI. Spawn heights should
also scatter ±0.6 blocks around the ring instead of a flat plane.

Bat movement is hardcoded on every supported version (javap, 1.8.8 → 26.1.2): `customServerAiStep`
rewrites velocity each tick with a `signum·(0.5/0.7/0.5)` lerp at K = 0.1 toward a private hover
target. There are no registered goals (Paper `MobGoals` cannot steer it) and the movement-speed
attribute is ignored (already recorded in ADR-0060). Combat events fire on the victim's region
thread; the attacker may be remote (ADR-0043).

## Decision

1. **Mechanism — three cooperating pieces, all shared `src/`:**
   - *Producer:* `CombatDispatch` stamps the attacker **entity reference** + hit tick into
     `engine.sink.SwarmClouds` beside the existing gank-window record (capture-at-dispatch,
     ADR-0043) — a no-op CHM `get` unless the victim owns a live cloud.
   - *Publisher:* `spawnSwarm` with the new `cloud` flag arms ONE per-owner task on the owner's
     entity scheduler. Each tick it prunes the bat census / enforces the deadline / tears itself
     down, seeds once from `RecentAttackersStore.latest` (pre-summon aggro, at the ORIGINAL hit
     tick), validates window + range, does ONE `Regions`-guarded read of the attacker's pose, and
     publishes an immutable `CloudTarget` (attacker feet + 1.0 block along the horizontal facing).
     O(1) per owner per tick, shared by all bats.
   - *Consumers:* the ADR-0060 damp task becomes `armSwarmSteer` — with a fresh (≤ 40-tick) target
     each bat seeks its slot on the orbit via clamped `setVelocity`; with none it is exactly the old
     damp. A bat task reads only its own entity plus the volatile snapshot — never the owner or
     attacker (the hard Folia invariant).
2. **Steering math is pure and code-level** (`SwarmRing` constants: orbit radius 0.9, Ω 0.35
   rad/tick, chase cap 0.9 b/t, 5 height bands 0.2–1.8, bob 0.15/0.12, jitter ±0.6) — calibration
   lives in code, not YAML; the only authored knobs are `cloud` (default false) and `cloud-range`
   (default 16.0, min 1).
3. **Target definition:** continuous full-vector horizontal facing — pillar base = attacker FEET +
   forward·1.0 using the pinned `SwarmRing.offsetX/offsetZ` yaw convention; never block-snapped
   (grid snapping jerks the cloud at every boundary); pitch ignored. `bandHeight` spans the
   two-block column foot→head, crossing eye height at the top bands.
4. **Speed-param interaction:** the authored `speed` damp governs ONLY idle flight. While clouding,
   `CLOUD_CHASE_CAP`/`CLOUD_OMEGA` govern — a cloud capped at the damped vanilla pace (~0.2 b/t)
   cannot hold an orbit around a sprinting attacker (~0.28 b/t).
5. **Attacker definition:** any entity whose hit reaches `CombatDispatch.onDamage` with the summoner
   as victim — players AND mobs; projectiles resolve to their shooter. SE-issued `EngineDamage`
   frames and self-hits never reach the producer (the ADR-0058 exclusion semantics).
   Most-recent-wins by single-slot overwrite.
6. **Windows and range:** `WINDOW_TICKS = 200` (10 s, deliberately a separate constant from the gank
   store's — a gank retune must not silently change bat behaviour; the seed can never resurrect an
   evicted attacker), `STALE_TICKS = 40` (consumers ignore older targets — the
   retired-publisher degrade horizon), publisher deadline = arm + ttl + 100. Range gate has
   hysteresis: engage at dist ≤ `cloud-range`, release only past `cloud-range + 1.0` — a
   boundary-straddling attacker must not flip the swarm orbit↔damp on successive ticks.
7. **Threading (ADR-0043 shape):** reference capture at hit (victim's thread, zero attacker reads);
   the publisher does the one possibly-cross-region `getLocation` under `Regions.swallowed`
   fail-closed (a fault keeps the last target, which ages past `STALE_TICKS` → vanilla damp); bats
   revert seamlessly in every teardown case because `target()` returning null IS the fallback — no
   mode flag. Entry dies with its swarm, the owner's quit (`PetsModule` store sweep), the TTL
   deadline (arm-time reap routes through `drop()` so a same-tick re-arm cannot leave a zombie
   publisher), or disable (`clearAll` stop).
8. **Spawn-Y scatter:** per bat `y = rise + yJitter(roll)` ∈ [−0.6, +0.6), rolled with the no-arg
   `ThreadLocalRandom.current().nextDouble()` (the JDG-safe shape); X/Z ring offsets unchanged.
9. **Legacy 1.8.9:** everything is shared `src/` on 1.8-present Bukkit API and 1.8.8 `EntityBat`
   has the identical lerp AI — legacy gets the FULL cloud behaviour, no degrade.

## Consequences

- The swarm reads as a vision cloud on a moving attacker while staying pure Bukkit velocity writes —
  one code path 1.8.9 → 26.1.x, Folia-correct by construction, zero per-hit cost with no live cloud.
- Vanilla AI's same-tick lerp perturbs the realized velocity ≤ ~10% — organic flutter, stated
  honestly (the ADR-0060 damp precedent).
- Orbit feel constants are code-level; retuning them is a follow-up code change, not content.
- `RecentAttackersStore` gains a `latest()` accessor; `Sink.spawnSwarm` grows to 9 args
  (`cloudOwner`, `cloudRange`).

## Alternatives considered

- **NoAI + teleport chains** — violates ADR-0060's ratified "never NoAI statues", stroboscopic for
  clients, and per-tick `teleportAsync` at 40 entities is unusable on Folia.
- **Paper MobGoals** — the Bat never consults its goal selector; a goal fights the hardcoded AI
  every tick; Paper-only API.
- **NMS `targetPosition` injection** — 3+ per-version reflection edges for a worse result (the hover
  target is block-quantized and randomly re-picked; no per-bat orbit slots).
- **Reusing `CombatTag`/`DamageMarks`/`RecentAttackersStore` as the live store** — wall-clock or
  entity-less shapes; per-tick UUID→Entity resolution does not exist on 1.8. The gank store is still
  reused as the pre-summon seed.
