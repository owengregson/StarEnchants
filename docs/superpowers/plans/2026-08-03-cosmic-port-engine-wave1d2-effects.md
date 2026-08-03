# Cosmic Port — Engine Wave 1d.2 (nine new effect kinds) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land wave-1 PR 2d.2: nine new effect kinds per
`docs/dev/cosmic-port/proposed-primitives.md` (§ New effects), as re-scoped by
the 1d.1 execution rulings.

**Re-scopings from 1d.1 (binding):** `SOUL_COST` is NOT in this PR — the
envelope `soul-cost` + gate 10 already ship; the out-of-souls feedback branch
moves to 2d.3 as part of the gate-verdict-feedback mechanism. `OUTGOING_DEBUFF`
gains a per-hit `feedback` message param (absorbing the dropped MARK-side
consume feedback — its Unfocus consumer is per-read, not per-consume).

**Architecture:** every kind is the standard one-class shape: `EffectSpec`
(ParamSpec + targets + Affinity + `.actorOrigin()` where actor-anchored), typed
`run()` emitting intents through narrow sink verbs, explicit registration in
`BuiltinEffects`, goldens regen. New stores follow the `EngineStores` pattern;
new listeners only for genuinely new event sources, dispatched through the
existing layers.

## Global Constraints

- Spec + rulings R1–R9; §4 bar. Binding skills: `starenchants-conventions`,
  `effect-engine`, `folia-scheduling`, `writing-tests`, `performance-hot-paths`,
  `feature-interaction-rules`, `code-comments`. Parallel-agent git protocol.
- Branch `feat/cosmic-wave1d2-effects` from freshly-fetched `origin/main`.
- Engine PR: full verification (R4); never bypass red. Wave-1 traps
  (full-registry end-to-end; stage-rebuild coverage) apply.
- Sink verbs stay narrow — one side-effect each. Damage flows through the fold
  (`damageDelta`), never `setDamage`. Current-health writes follow ADR-0051.
- Goldens verbatim; fingerprint VERSION unchanged.
- On low context: stop at a clean commit boundary, push WITHOUT the PR, report.

## The nine kinds (contracts authoritative in proposed-primitives § New effects)

| Kind | Implementation notes |
| --- | --- |
| `PERIODIC_DAMAGE` | actor-attributed DoT: amount/period/duration + optional named-vanilla-DoT replacement + per-tick feedback hooks. Deferred-intent batches on the entity timer (WAIT machinery precedent); liveness-gated per ADR-0051; damage through the fold each tick |
| `HEAD_TROPHY` | arm an on-death flag on the victim (store keyed by UUID, TARGET_VAR-style cleanup); on their next death (any cause) a player-head item (skull owner = victim) with templated name/lore ({VICTIM}/{KILLER}/{MONTH}/{DAY}/{YEAR}/{X}/{Y}/{Z}/{ITEM} brace tokens resolved at death) joins the drops; killer-less death → owner-only head, no lore; flag consumed. Verbatim strings incl. load-bearing trailing spaces come from the matrix (03/04) at CONTENT time — the kind carries templates as params |
| `DESPAWN` | silent removal of target NON-player living entities: no drops, no XP, no death event (entity remove intent); refuses players (diagnostic-free no-op) |
| `PROC_REBOUND` | on an incoming weapon-enchant ability, chance to re-execute it with actor/victim swapped, gated by `tier-max` and rebound level ≥ incoming level; the reflected enchant is then not applied to the reflector. The 1c `TriggerRunner.runDetached` (explicit candidates, full FactMask) is the execution seam; the incoming ability's identity is available at the dispatch layer. If the swap cannot reuse runDetached cleanly, STOP with the shape you found |
| `VIEWER_HIDE` | hide target player from scoped viewers (`viewer=attacker\|all`) for duration: packet-level hide (Player#hidePlayer era seam — check `Projectiles`/probe-style seam conventions; 1.8 uses the deprecated single-arg form), auto-unhide on expiry/relog; maintains a named var while active only if the matrix consumer needs it (check Undead Ruse in matrix/02 — author the minimum) |
| `PROJECTILE_DRESSING` | attach a rider entity (type param) to a fired projectile with ttl + invulnerable window + no-pickup; rider despawns on landing (PROJECTILE_LAND wiring from #274 is the landing signal on modern; on 1.8.9 the rider ttl alone bounds it — era note, not a blocker) |
| `OUTGOING_DEBUFF` | timed outgoing-damage debuff stored on the target (store + defense-side... NO — attack-side read: when the debuffed player deals damage, their outgoing fold takes the configured multiplier if the damage cause matches the filter (`cause=projectile\|melee\|all`)); per-hit `feedback` message to the debuffed player each time it applies (the Unfocus spam — strings verbatim at content time); non-stacking, refresh-on-reapply |
| `DOT_AMPLIFY_MARK` | mark on the victim amplifying named vanilla DoT causes (`causes=[WITHER, POISON]`) by `factor` for `duration`; the amplification applies at the damage event for those causes (HURT-path adjacency from #274 — the environmental context now carries the pending hit); refresh-on-reapply unconditionally |
| `SUMMON_REBIND` | replace an owned summon (the actor's, from the summon flags/ownership the engine already tracks) with a fresh upgraded one: despawn silently (no death event), spawn replacement at +2 blocks with configured tier/name/self-destruct window — compose from GUARD/SPAWN_ENTITY's param surface (1d.1 row 11) rather than duplicating stats params |

## Tasks

### Task 1: Branch + baseline

- [ ] `git fetch origin && git switch -c feat/cosmic-wave1d2-effects origin/main`;
  baseline `./gradlew build -q` green.

### Tasks 2–5: the kinds, batched by seam locality

Batch (2): `DESPAWN`, `VIEWER_HIDE`, `PROJECTILE_DRESSING` (entity-lifecycle
intents). Batch (3): `PERIODIC_DAMAGE`, `DOT_AMPLIFY_MARK`, `OUTGOING_DEBUFF`
(damage-path family — one review pass over fold interactions). Batch (4):
`HEAD_TROPHY`, `SUMMON_REBIND` (death/summon seams). Batch (5): `PROC_REBOUND`
(the riskiest — last, alone).

Per kind, the invariant recipe:

- [ ] Failing spec-conformance + kind-behavior tests first (concrete numbers
  from the matrix consumers), including the full-registry end-to-end.
- [ ] Implement: EffectSpec (+Affinity, doc line, example), `run()` intents,
  narrow sink verb(s), store if needed (EngineStores pattern + death/quit
  cleanup), explicit `BuiltinEffects` registration.
- [ ] Fuzz entries; suite green; commit (`test:` then `feat:`).

### Task 6: Docs, goldens, gate, PR

- [ ] `regenDocs` goldens verbatim (effects 88+13-extension-params → +9 kinds;
  fingerprint VERSION unchanged); full build green.
- [ ] PR `feat(engine): wave 1d.2 — nine new effect kinds`, body citing spec
  §5 row 2 + proposed-primitives § New effects + the SOUL_COST/MARK-feedback
  re-scopings; `--rebase --auto`; full CI green (R4). Report and stop — 2d.3
  closes wave 1.

## Self-review record

- Nine kinds = proposed-primitives § New effects minus SOUL_COST (re-scoped,
  gate-verdict feedback → 2d.3) — the tenth row's absence is deliberate.
- PROC_REBOUND and VIEWER_HIDE carry the era/seam risk; both have STOP-or-note
  instructions. HEAD_TROPHY's verbatim templates are content-time params — the
  kind stays item-name-free (§4).
- All damage flows through the fold; all stores get lifecycle cleanup.
