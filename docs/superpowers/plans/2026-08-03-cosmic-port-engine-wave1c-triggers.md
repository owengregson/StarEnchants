# Cosmic Port — Engine Wave 1c (four triggers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land wave-1 PR 2c: the four new triggers — `HURT`, `EQUIP_CHANGE`,
`PROJECTILE_LAND`, `PROXIMITY_EVENT` — per
`docs/dev/cosmic-port/proposed-primitives.md` (§ New triggers).

**Architecture:** Triggers are registry entries plus one dispatch site each; the
pipeline, gates, and WhyRecorder come for free once an ability walks. `HURT`
follows `FALL`'s exact registration shape (targetless DEFENSE direction) but
unfiltered by cause, with the existing `%damagecause%` fact bound.
`EQUIP_CHANGE` rides the EquipListener hook family (`onHeldChange` from PR #273
is the composition template). `PROJECTILE_LAND` maps ProjectileHitEvent through
the `Projectiles` era seam that PR #273 just extended, dispatching at the
landing position with no victim. `PROXIMITY_EVENT` is the one genuinely novel
shape: an observer trigger — a nearby entity's event (player-death first;
tagged-effect-application deferred, see contract) activates abilities on
WEARERS within range, filtered by relation through the #270 alliance predicate.

**Tech Stack:** `se-engine` (BuiltinTriggers, trigger registry), `se-feature`
(TriggerListeners/TriggerDispatch, EquipListener, CombatDispatch adjacency),
`Projectiles` era seam, JUnit, fuzz corpora, `regenDocs` goldens.

## Global Constraints

- Spec + rulings R1–R9; §4 primitive bar. Binding skills: `starenchants-conventions`,
  `effect-engine`, `folia-scheduling` (dispatch sites run on event threads —
  entity work stays on the right region), `writing-tests`,
  `performance-hot-paths`, `code-comments`.
- Parallel-agent git protocol (own worktree only). Branch
  `feat/cosmic-wave1c-triggers` from freshly-fetched `origin/main`.
- Engine PR: full verification (R4); `--rebase --auto`; never bypass a red check.
- **Trigger registrations are append-only, at the end of `BuiltinTriggers`,**
  with the file's ADR-comment convention (prior ids must not shift — the
  REPEATING comment in that file states the rule).
- End-to-end tests use FULL `BuiltinEffects`/`BuiltinSelectors`/`BuiltinTriggers`
  registries (the dense-id trap). Stage-rebuild trap: any new field crossing
  `AbilityDef → LoweredAbility → Ability` gets full-pipeline coverage.
- Diagnostics via `DiagCode` only; compile Bukkit-free; goldens regenerated
  verbatim; fingerprint VERSION unchanged (new triggers are hashed content).

## Contracts

**HURT** — `Direction.DEFENSE`, targetless (mirror `FALL`'s constructor flags),
fires on EVERY damage-taken event regardless of cause, null-attacker safe,
`%damagecause%` and `%posthit.health%` populate normally. Dispatch beside the
existing FALL/FIRE mapping in the damage listener path — ONE dispatch per event
(HURT is additional to, not a replacement for, DEFENSE; an ability authors one
or the other). Consumers: Inversion (all-cause), Guided Rocket Escape lethal
check, death-saves, Nutrition-class sustain.

**EQUIP_CHANGE** — fires on equip AND unequip of the carrying piece; the
authored ability distinguishes direction via a bound string fact
`%equipchange%` = `EQUIP` | `UNEQUIP` (a new BuiltinVars string slot,
append-only, populated only for this trigger). The ability's carrying piece is
the piece that changed — worn-source resolution must run against the NEW worn
state on equip and the OLD worn state on unequip (the unequipping piece's
ability must still fire once as it leaves; state the chosen mechanism in the PR
body — WornState snapshots before/after the refresh are both available in
EquipListener). Consumers: last-stand absorption (02), mask transition hooks.

**PROJECTILE_LAND** — fires when a player-fired projectile lands (block hit or
expiry; entity hits already dispatch BOW), no victim, activation anchored at
the landing location so `@Aoe` selectors center there. Ride ProjectileHitEvent
through the `Projectiles` era seam; only projectiles with a player shooter
dispatch. Consumers: landing-AoE bow abilities (05), web fields.

**PROXIMITY_EVENT** — observer trigger, `player-death` event only in this PR:
when a player dies, every OTHER player within `range` blocks wearing content
that authors this trigger activates, subject to a relation filter
(`ALLIES`/`ENEMIES`/`ALL` against the #270 alliance predicate). Params ride the
ability envelope as trigger args (`trigger: PROXIMITY_EVENT` +
`proximity: { event: player-death, range: N, relation: ALLIES }` — follow
whatever per-trigger parameterization convention the schema already has; if
NONE exists, STOP and report the options rather than inventing one silently).
The dying player binds as the activation's victim-side entity so
`%victim.relation%` and victim facts populate. Radius scan joins the existing
nearby-entity walk pattern (#270's `nearbyallies` sharing is the template — no
second walk). The `tagged-effect-application` variant from the original
contract is explicitly DEFERRED to wave 2 (its only consumer is Blood Lust's
ally-leech secondary; note it in the PR body). Consumers now: Avenging Angel.
Death events are rare — this is not hot-path, but the scan still must not
allocate per-tick (it runs per death only).

---

### Task 1: Branch + baseline

- [ ] `git fetch origin && git switch -c feat/cosmic-wave1c-triggers origin/main`;
  baseline `./gradlew build -q` green (stop otherwise).

### Task 2: HURT (smallest — sets the per-trigger recipe)

- [ ] Failing tests: registration (id appended, direction DEFENSE, flags match
  FALL's), dispatch on a non-entity damage event (fall/fire/poison) with
  `%damagecause%` bound, null-attacker safety, and an end-to-end YAML ability
  `trigger: HURT` + condition `%damagecause% == "POISON"` firing through the
  full pipeline.
- [ ] Implement: `BuiltinTriggers` append + the dispatch line beside FALL/FIRE.
- [ ] Suites green; commit (`test:` then `feat:`).

### Task 3: EQUIP_CHANGE

- [ ] Failing tests: `%equipchange%` slot registration + mask-gated population;
  equip fires with `EQUIP`, unequip fires with `UNEQUIP` (the leaving piece's
  ability fires once); no fire on unrelated inventory refresh (the chest-close
  case — reuse PR #273's `onHeldChange` discrimination precedent); end-to-end.
- [ ] Implement: trigger append, BuiltinVars slot append, EquipListener hook.
- [ ] Suites green; commit.

### Task 4: PROJECTILE_LAND

- [ ] Failing tests: player-shot arrow landing on a block dispatches at the
  landing location (assert the activation origin), non-player shooters don't
  dispatch, entity-hit does NOT double-dispatch (BOW owns that), end-to-end
  with an `@Aoe`-anchored effect.
- [ ] Implement: trigger append + ProjectileHitEvent mapping via the
  `Projectiles` seam (both era impls; 1.8.9 ProjectileHitEvent exists — no
  block-hit accessor there, derive the landing position era-safely).
- [ ] Suites green; commit.

### Task 5: PROXIMITY_EVENT

- [ ] FIRST: find the per-trigger parameterization convention (how REPEATING
  gets its period is the known precedent — `repeatTicks` rides AbilityDef). If
  trigger-scoped params have no authoring surface, STOP and report options.
- [ ] Failing tests: death of a player activates a nearby wearer's ability
  (range respected, relation filter respected via the alliance predicate,
  dying player bound victim-side), no activation on the dying player's own
  gear, end-to-end.
- [ ] Implement: trigger append + death-listener dispatch + range/relation
  gating; any new AbilityDef fields get the full-pipeline drop test.
- [ ] Suites green; commit.

### Task 6: Docs, goldens, gate, PR

- [ ] Fuzz corpus entries (each trigger in valid + malformed YAML);
  `./gradlew :bootstrap:regenDocs` goldens verbatim (triggers 25 → 29);
  full `./gradlew build -q` green.
- [ ] Push; PR `feat(engine): wave 1c — HURT, EQUIP_CHANGE, PROJECTILE_LAND,
  PROXIMITY_EVENT triggers`, body citing spec §5 row 2 + proposed-primitives
  § New triggers + the deferred tagged-effect-application note;
  `--rebase --auto`; full CI green (R4). Report and stop — 2d is planned
  main-loop-side.

## Self-review record

- Covers exactly the four triggers of proposed-primitives § New triggers; the
  tagged-effect-application deferral is explicit and single-consumer-justified.
- Seams named from shipped code: FALL registration shape, EquipListener hook
  family (#273), Projectiles era seam (#273), alliance predicate (#270),
  nearby-walk sharing (#270). One declared unknown (trigger-param authoring
  convention) carries a STOP instruction.
- Both wave-1 traps restated as constraints.
