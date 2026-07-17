# ADR 0065: Ice Aspect — the FREEZE kind and the frozen-window sink

- **Status:** Accepted
- **Date:** 2026-07-16
- **Deciders:** project owner + agent
- **Extends:** ADR-0044 (era erasure — seam-not-twin leaves), ADR-0047 (one-module wiring rule), ADR-0060 (the plugin-owned attribute-modifier channel precedent)
- **Relates to:** ADR-0049 (the effect-kind matrix), ADR-0050 (the DoT budget), ADR-0051 (liveness-gated writes / inline Phoenix heals), ADR-0054 (deferred attributed DoT on the bleed path)

## Context

Ice Aspect is an epic SWORD enchant (I/II/III, chance 50/75/100, cooldown 200t) that pins its victim in a
frozen window: the vanilla powder-snow visual (full blue vignette + blue hearts), an attacker-attributed
DoT, and a movement slow. None of vanilla's freeze machinery is exposed as a clean "hold this entity frozen"
API, and two vanilla side-effects fight a naive pin. The design is grounded in javap over the reference cache
(`nms-archaeology`), not guessed.

Freeze-tick facts (Mojang-mapped, 1.20.6 / 26.1.2 / Folia 26.1.2 / patched 1.17.1):

| Behavior | Where | Guard |
| --- | --- | --- |
| Freeze decays −2/tick outside powder snow | `LivingEntity.aiStep` | skipped under Paper `freezeLocked` |
| A burning entity's freeze is zeroed + a 1009 hiss replayed | `Entity.baseTick` | `freezeLocked` on Paper ≥1.18.2; **UNguarded on 1.17.1** |
| Fully-frozen self-damage `hurt(freeze, 1.0)` every 40t | `LivingEntity.aiStep` | **NOT** behind `freezeLocked` |
| Frost slow: MOVEMENT_SPEED `−0.05 × percentFrozen` ADD, on-ground | `LivingEntity.tryAddFrost` | outside both the lock and client guards |
| `lockFreezeTicks`/`isFreezeTickingLocked` | `org.bukkit.entity.Entity` | present from Paper 1.18.2 (absent on the 1.17.1 floor) |
| `setTicksFrozen` writes the synced metadata directly | `Entity.setTicksFrozen` / Bukkit `setFreezeTicks` | never gated by `freezeLocked` (1.18.2 → 26.1.2 + Folia) — our pin always lands, locked or not |
| `freezeLocked` persists to entity NBT | `Entity.saveWithoutId`/`load` | a locked entity stranded across a chunk unload stays frozen forever |

## Decision

1. **One new Sink intent `freeze(...)` and one new effect kind `FREEZE`** (Affinity `TARGET_ENTITY`, fan-out
   over `who`=`@Victim`, attribution = `ctx.actor()` per ADR-0054). All window mechanics live in
   `DispatchSinkBase.freeze` (the `potionLock` window-task shape); a per-victim `FrozenTargets` registry (the
   `LockedPotions` static/concurrent/wall-clock shape) holds the deadline, attribution, and the window's
   idempotent teardown. A re-proc **refreshes** the live window (deadline + attribution) — it never stacks a
   second DoT chain (owner rule).

2. **Every victim rides Paper's freeze-tick lock where it exists.** On Paper ≥1.18.2 (and all Folia, floor
   1.19.4) a single `lockFreezeTicks(true)` + one `setFreezeTicks(max)` pins forever — fire included — because
   the lock guards BOTH vanilla writes (the decay and the burning-entity clear) while `setTicksFrozen` itself
   is unguarded, so there is zero per-tick work and the behavior is identical on Paper and Folia, players and
   mobs alike. The lock is the ONLY mechanism that holds under fire: scheduled tasks run before entity ticking,
   so an unlocked re-pin is re-zeroed by the unguarded clear (plus the 1009 hiss) every tick. `freezeLocked`
   persists to NBT, so a victim unloaded mid-window would strand frozen forever; rather than refusing mobs the
   lock (which silently broke the fire-coexistence promise for every mob), the guard listener reconciles the
   strand at entity load (§3). The 1.17.1 floor has no lock: every victim there degrades to an unlocked
   per-tick re-pin at `max + 2` (the `+2` outruns the −2/tick decay so the synced value is exactly max),
   **skipped while the target burns** (the unguarded `baseTick` clear would zero it and replay the 1009 hiss
   every tick) — the visual honestly drops until the fire ends. 1.8.9 has no freeze concept: the visual is a
   recorded no-op; DoT + slow still land.

3. **Vanilla freeze self-damage is cancelled for the window.** A full pin makes vanilla deal 1 damage every
   40t (and it is inconsistent per-victim — leather wearers are exempt via `canFreeze`). A modern-overlay
   `FreezeDamageGuardListener` cancels `DamageCause.FREEZE` on any victim inside a live window, so the engine
   DoT is the ONLY damage source and it is uniform across victims. The same listener reconciles stranded
   `freezeLocked` state: a locked player with no live window on join (crash strand), and any other locked
   entity on world-add (unload strand — a freshly-loaded instance has no live window tasks, so its lock is
   stale by construction; players are excluded because their instance and tasks survive world changes).
   1.8.9's binding is inert.

4. **The DoT is a deferred attributed hurt (ADR-0054, the bleed path).** Each tick is `target.damage(dot,
   attacker)` inside the `EngineDamage` frame via the base's one `hurt()` seam. Consequences: it does not join
   the fold and does not ride `combat.attack-scale` (the authored `dot` is raw pre-armor half-hearts); it never
   stamps `ReHitGuard`, never advances the attacker's rage, and never breaks the victim's rage combo (ADR-0058
   excludes engine damage); a fatal tick fires a real killer-typed event so Mental / kill-credit / logging see
   the attacker and Phoenix-class zero-WAIT heals join the kill decision inline (ADR-0051); all deferred writes
   are liveness-gated (the dead stay dead). Unlike vanilla burning, an attributed hurt IS armor-reduced.
   **Cadence and window end are tick-space.** The chain claims each `dot-period` slot against a TICK budget
   (`duration` at arm; a re-proc extends it from the chain's last completed slot, so a refresh buys exactly
   `duration` more lattice). The boundary slot at t+`duration` lands (inclusive), and the final in-budget slot
   lands its hurt and tears the window down in the same task run — the tick count per window is exact on every
   version and on Folia. Gating the tick-lattice task on the wall clock instead provably drifts: catch-up
   bursts add a slot and an exactly-on-time server structurally drops the boundary slot (`now >= deadline`).
   The wall-clock deadline survives only as the per-tick pin's and the damage guard's read (their call sites
   have no tick anchor); under sustained lag those honestly stop early — a DoT slot is never lost. A `dot: 0`
   window fires no hurts (no empty damage events / i-frames); durations that are not `dot-period` multiples
   end at the last lattice slot (down-quantized) — authored durations are period multiples.

5. **Vanilla's frost slow is neutralized so the authored percent is the real slow.** At a full pin vanilla
   applies `−0.05` MOVEMENT_SPEED (≈50% ground slow) server-side and client-predicted. The sink applies a
   plugin-owned `+0.05 ADD_NUMBER` frost-offset (exactly cancels vanilla's additive frost; inert airborne
   because survival air-accel is the constant 0.02, not the attribute) plus a `−slow% MULTIPLY_SCALAR_1`
   modifier — net ground speed 0.95 × base at `slow: 5`, both synced to the client so prediction stays
   coherent. `neutralize-frost-slow` is a kind param; the owner may flip it to `false` (with `slow: 0`) to keep
   vanilla's ≈50% instead. The channel identities (`starenchants.frozen_slow`, `starenchants.frozen_frost_offset`)
   are distinct from every existing channel and resolved by NAME (`GENERIC_MOVEMENT_SPEED`) through the alias
   chain (the 1.21.3 rename). From 1.21 the Bukkit `AttributeModifier` UUID ctor DISCARDS its name (the key
   becomes the UUID string and `getName()` echoes the key), so the modern leaf builds the modifiers through the
   keyed ctor where it exists (`starenchants:<wire name>`, probed once) and strips them by id-or-wire-name —
   the wire identity is version-stable. On 1.8.9 there is no vanilla frost, so the offset is never applied and
   the slow rides the NMS `GenericAttributes.MOVEMENT_SPEED` modifier path.

6. **Lifecycle.** Teardown (cancel tasks, end the visual, strip the slow, disarm the registry) is one
   idempotent generation-guarded closure. For players it is registered in `timedReverts` so a logout drains it
   before the playerdata save; it is stored in the window for the module-disable sweep (`FrozenTargets.teardownAll`,
   the `SwarmSpawns.removeAll` best-effort shape). Natural expiry (the final budget slot) and death claim the
   token from the chain's own run. Arming over a surviving entry (wall-lapsed under lag, or a chain that died
   with its unloaded entity) runs the survivor's teardown first — never two windows, and a stale teardown can
   never fire later and clobber the new window's visual/slow. **Suppression:** Silence gates NEW procs at gate 5; a live window is sink state (not an
   ability) and expires naturally — the shipped suppress-semantics precedent, no code.

## Consequences

- One new kind drifts the ADR-0046 authoring-surface fingerprint + the DSL docs + the pack stamp
  (`./gradlew regenDocs`); fuzz and conformance pick it up automatically.
- Wiring goldens: the controls module gains a `freezeDamageGuard()` binding (inert on 1.8.9) and a "frozen
  windows" disable stop.
- The 1.17.1 floor honestly drops the visual while the victim burns (DoT + slow persist); a leather
  victim still takes the full pin + visual + DoT (vanilla only suppresses its OWN freeze damage via `canFreeze`,
  which we replace) — a deliberate uniform-enchant choice.
- The DoT's 1-tick phase offset vs `remainingFireTicks % 20` is irreducible without reading the victim's fire
  phase; the shared i-frame window makes the net per-window damage identical, so it is the honest approximation.
- The full-vignette client render while the local player burns cannot be proven from server jars; the live
  suite pins the synced server-side metadata, and one manual client eyeball is the honest final check.
