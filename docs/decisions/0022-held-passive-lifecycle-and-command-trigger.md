# ADR 0022: HELD/PASSIVE start-stop lifecycle + the COMMAND trigger (§B tail)

- **Status:** Accepted
- **Date:** 2026-06-18
- **Deciders:** project owner + engine work
- **Relates to:** ADR 0011 (engine architecture), ADR 0003 (unified effect engine); docs/v3-directives.md §B;
  closes the §B tail left after the listener wave (REPEATING shipped earlier)

## Context

§B requires every advertised trigger to be wired. The listener wave bound the event-driven ones
(DEATH, BOW_FIRE, FISHING, EAT, ITEM_DAMAGE, BREAK, a distinct BOW/TRIDENT) and the timer-driven
REPEATING. Three were left, and they are not "add a Bukkit listener" jobs:

- **HELD** and **PASSIVE** are *maintained buffs* — "while you hold this / wear this, you have X." EE
  applies the effect when the item is equipped/held and **removes it** when it is unequipped/swapped away.
  Our engine is intent-based: an `EffectKind.run` emits a one-shot intent (`apply potion`, `deal damage`)
  and there was **no deactivation path at all** — the `KEEP_ON_DEATH` doc even said "the engine has no
  unequip teardown." A `WornState` refresh on equip change only replaced the cached snapshot; nothing fired,
  and nothing un-fired.
- **COMMAND** did not exist in the vocabulary — a trigger fired by a configured command, not a game event.

The hard question for HELD/PASSIVE is *what "stop" means* when effects are one-shot intents.

## Decision

### 1. A teardown seam on `EffectKind` (`stop`, default no-op)

`EffectKind` gains `default void stop(EffectCtx, Sink)` — a no-op for the common one-shot kind (a `MESSAGE`
on equip does not un-send). A **maintained-buff** kind overrides it to emit the inverse intent. For the §B
tail only **`POTION`** overrides it (`removePotion` of the same compile-resolved handle) — the dominant
passive case and an exact inverse with no timing subtlety. `FLY`/`MOVEMENT_SPEED`/`INVINCIBLE` already
self-expire via their duration and have restore-timing quirks, so they stay one-shot for now; the seam is
open for them later (express a maintained non-potion buff via `POTION` or pair it with `REPEATING`).

### 2. The lifecycle is a distinct, GATELESS path (not the gate pipeline)

A `LifecycleDriver`, called by `EquipListener` after every worn-state refresh, **diffs** the player's
currently-worn HELD/PASSIVE abilities (keyed by **stable key**, §5.3 — so the set survives a reload's dense-id
churn) against the set it last saw, and asks `TriggerDispatch.fireLifecycle(player, stops, starts)` to:

- **STOP** (run `EffectKind.stop`) every source that left — **unconditionally** (never gated by
  chance/cooldown/condition/world), so a buff can never leak. The engine only stops what it actually
  started, so a no-op `stop` for a one-shot effect is always safe.
- **START** (run `EffectKind.run`) every source that arrived — honouring only the **world-blacklist**
  (gate 1); a world-disabled passive stays off. STOP runs before START so swapping levels of the same
  enchant removes the old buff before applying the new, into one sink flushed once.

A maintained buff is deterministic, so the chance/cooldown/condition/soul gates are deliberately **not**
applied — this is a separate mechanism, not a reordering of the §3.3 gate sequence (which governs *triggered*
activations and is still never reordered). `AbilityExecutor.runLifecycle` is the gateless effect-runner; it
does not notify the public `ActivationListener` (a maintained buff is not a gated activation) and applies no
`WAIT` deferral (a teardown lands with the unequip).

### 3. COMMAND is a normal triggered activation

`COMMAND` is appended to `BuiltinTriggers` last (so prior trigger ids are unshifted), NEUTRAL +
equipment-scanned. It runs through the **full** gate sequence (chance/cooldown/condition/souls) — the player
explicitly invokes it, so it is a real activation; only the entry point differs. A configurable standalone
command (`config.yml` → `command-trigger.{enabled,name,description}`, default `/cast`) is registered once at
boot through the server command map (its name is dynamic, so it cannot live in `plugin.yml`); on run by a
player it calls `TriggerDispatch.fireCommand`. Registration is guarded — a server without an accessible
command map just leaves the trigger unfireable, never crashing the boot.

## Consequences

- HELD/PASSIVE finally have the EE deactivation half: a worn "Strength while held" turns on at equip and off
  the instant the weapon is put away, with no buff leak across world changes, swaps, or reloads.
- Folia-correct: the lifecycle fires on the player's own thread (equip events are player-owned) and the buff
  intents route through the same `Scheduling`/`DispatchSink` path as everything else.
- **Known limitation:** START honours world-blacklist but a *world change* is not an equip event, so a
  passive disabled in world A does not auto-re-apply when the player walks into allowed world B until the
  next equip change. Acceptable for v1; a world-change re-evaluation is a possible follow-up.
- `command-trigger.name`/`enabled` are read once at boot (like the integration toggles) — a change needs a
  restart, not a `/se reload`, because a command name cannot be cleanly re-bound mid-run.
- Tracking state (`Map<UUID, Set<String>>` of started keys) lives in the driver, concurrent, cleared on quit
  (no teardown — the entity is gone, its potions vanish, and re-join re-applies) and on disable.
- Demo content ships for all four §B mechanisms (HELD `Bloodlust`, PASSIVE `Featherweight`, REPEATING
  `Lifebloom`, COMMAND `Channel`), and a live `LifecycleSuite` proves start/stop/command on Paper and Folia.
