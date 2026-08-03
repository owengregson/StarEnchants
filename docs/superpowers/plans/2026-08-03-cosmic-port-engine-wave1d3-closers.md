# Cosmic Port — Engine Wave 1d.3 (selectors, food subsystem, gate-verdict feedback) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close engine wave 1 with PR 2d.3: the three selector/filter items, the
`MODIFY_FOOD` subsystem (1d.1 rows 9–10), and the gate-verdict feedback
mechanism (the 1d.1 row-12 re-ruling + the descoped SOUL_COST's out-of-souls
branch). `PROC_REBOUND` is NOT here — deferred to wave 2 by owner ruling
(joins the incoming-direction family with DEFENDER_KEYED_SUPPRESSION; design
input = the #276 STOP analysis).

## Global Constraints

Same as 1d.1/1d.2 (spec R1–R9, §4 bar, binding skills incl.
`feature-interaction-rules`, parallel-agent git protocol, R4 full verification,
both wave-1 traps, narrow sink verbs, goldens verbatim + fingerprint VERSION
unchanged, low-context handoff protocol). Branch
`feat/cosmic-wave1d3-closers` from freshly-fetched `origin/main`.

## Contracts

**1. Selector/filter items (proposed-primitives § New selectors / filters):**

- Filter conjunction on `AOE`/`NEAREST`: `filter` accepts a composed value
  (`ENEMIES+PLAYERS` or a list — match the existing filter param's authoring
  shape; pick the one the current grammar parses most naturally and document
  it). Consumers: player-only hostile payloads (02), Plague Carrier, Smoke Bomb.
- Face-oriented block box selector: a w×h cross-section marched `depth` layers
  into the struck face from the activation block (per-axis extents; orientation
  = mined face). TRENCH/`@Tunnel` are the existing block-shape precedents —
  extend that selector family, don't fork it. Consumers: tunneling tools (06).
- Block material filter on block selectors (`materials` handle-list param —
  the 1d.1 row-11 `list` flag on HANDLE is the shape). Consumers: selective
  break/convert tools (06).

**2. `MODIFY_FOOD` subsystem (approved 1d.1 STOP shape):** one shared
`FoodLevelChangeEvent` listener (feature layer, new event source — wired like
the existing trigger listeners), a per-player armed-window store in
`EngineStores` with lifecycle cleanup:

- `mode=scale-gain` (factor): while armed, a food-level INCREASE is scaled by
  factor (the listener rewrites the event's new level; era check: the event
  exists on both floors).
- `mode=cancel-drain`: while armed, food-level DECREASES are cancelled.
  Arming: the effect arms a short-TTL window re-applied by its PASSIVE/
  REPEATING ability (the KEEP_ON_DEATH/WARD precedent named in the 1d.1
  report).

**3. Gate-verdict feedback (one mechanism, two verdicts):**

- Suppression (timed windows only): when gate 5 blocks an activation AND the
  blocking suppression entry carries feedback config
  (`consumed-message-actor` / `consumed-message-victim` / `consumed-sound`,
  authored on the SUPPRESS effect that armed it — params shipped as data even
  if 1d.1 didn't add them; add them here if absent), the DISPATCH layer emits
  it (the pipeline stays pure — it already reports the per-gate verdict to the
  WhyRecorder; the dispatch site sees the same verdict). One-shot windows and
  MARK are OUT (masks-wave / dropped, per the rulings).
- Out-of-souls (gate 10): when the soul spend aborts an activation, an
  ability-level `no-souls-message` (new optional envelope param riding
  AbilityDef→LoweredAbility→Ability — full stage-rebuild coverage) emits to the
  actor via the same dispatch-layer path, throttled per player (300 t — the
  cosmic OUT OF SOULS cadence from the matrix; make the throttle a constant,
  not a param).
- Both emits are rare-path (a blocked/aborted activation), but the LOOKUP must
  be allocation-free on the common no-feedback case.

## Tasks

- [ ] **Task 1:** branch + baseline build.
- [ ] **Task 2:** selector items (failing tests → impl → fuzz → commit; the
  conjunction needs a compile-side grammar/typecheck decision — follow the
  existing filter param's parse path; STOP only if both candidate shapes
  require new grammar productions).
- [ ] **Task 3:** food subsystem (store + listener + two modes; end-to-end:
  an armed cancel-drain wearer keeps food through a forced drain event; a
  scale-gain wearer eating gets the scaled delta).
- [ ] **Task 4:** gate-verdict feedback (SUPPRESS feedback params if absent +
  dispatch-layer emit off gate-5 verdict; `no-souls-message` envelope param +
  gate-10 emit + throttle; full-pipeline drop coverage for the new field;
  end-to-end both paths).
- [ ] **Task 5:** fuzz entries, `regenDocs` goldens verbatim, full build, PR
  `feat(engine): wave 1d.3 — selectors, food subsystem, gate-verdict feedback`
  citing spec §5 row 2 + the 1d.1/1d.2 rulings; `--rebase --auto`; full CI
  green (R4). Report and stop — **this closes engine wave 1**; content batch
  1 (armor A–L) is planned main-loop-side.

## Self-review record

- Sums exactly: proposed-primitives § selectors (3) + the two carried 1d.1
  STOPs as re-ruled + the SOUL_COST remainder. PROC_REBOUND's absence is the
  recorded owner ruling, not an omission.
- The one new compile-stage field (`no-souls-message`) carries the
  stage-rebuild trap instruction explicitly.
