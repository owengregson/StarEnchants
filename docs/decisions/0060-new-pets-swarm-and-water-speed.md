# ADR 0060: Sheep/Kraken/Bat pets — SPAWN_SWARM and the worn WATER_SPEED channel

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + agent
- **Extends:** ADR-0052 (pets), ADR-0050 (fold caps), ADR-0043 (actor origin)

## Context

Three new cosmic pets. Sheep (scaling fall-damage reduction → true immunity) and the Kraken's drowning
immunity ride existing machinery; two mechanisms don't exist: an exact-percent underwater speed boost and
a ring summon with vanilla AI at a scaled speed.

## Decision

1. **Sheep is pure YAML.** FALL is environmental — `TriggerDispatch.fireDamage` installs no ADR-0050
   caps/attack-scale (those are `CombatDispatch`-only), so `DAMAGE_MOD side:defense` percents land exactly;
   level 100 is `CANCEL` (the ethereal-dodge negate), a cancelled event, not a 100% fold.
2. **Kraken drowning = the maintained PASSIVE `WATER_BREATHING`** (aquatic precedent) — air never
   depletes, every era including 1.8.9. DROWNING damage never enters the trigger pipeline by design.
3. **`WATER_SPEED { efficiency }` + `WaterSpeedDriver`** — a worn PASSIVE/HELD channel reconciling ONE
   plugin-owned `water_movement_efficiency` modifier (the `MaxHealthDriver` twin; SET-not-add, fixed
   identity `starenchants:worn_water_speed`). NMS: underwater accel is a flat 0.02F; the only per-percent
   synced lever is this attribute (1.21+). No in-water condition — the attribute is inert out of water.
   Degrades, recorded: 1.17.1–1.20.6 and 1.8.9 get no boost (leaf no-ops). Alias
   `GENERIC_WATER_MOVEMENT_EFFICIENCY → WATER_MOVEMENT_EFFICIENCY` covers the 1.21.3 rename.
4. **`SPAWN_SWARM { type count radius rise ttl speed }`** — evenly-spaced ring at `rise` (chest 1.2)
   around the activator's ADR-0043 origin, spawn-location yaw facing outward, VANILLA AI (never NoAI),
   TTL removal per summon on its own entity scheduler, and `speed < 1` as a per-tick velocity damp
   (`s = q/(k + q(1−k))`, k = 0.1 from `Bat.customServerAiStep` — the movement-speed attribute does not
   steer Bat AI; realized speed ~±10% of q by tick ordering, stated honestly). Live summons register in
   `SwarmSpawns` (the `PetSummons` era-agnostic UUID registry) and the pets module removes them on
   disable (best-effort cross-region on Folia; residual life ≤ the TTL).

## Consequences

- Two new kinds drift the ADR-0046 fingerprint, DSL docs and the pack stamp (`./gradlew regenDocs`);
  fuzz/conformance/affinity coverage grows automatically.
- The stop-order golden gains "bat swarms" (RegistryWiringTest).
- Bat swarm speed and the Kraken efficiencies are calibration numbers, tunable in YAML without code.
