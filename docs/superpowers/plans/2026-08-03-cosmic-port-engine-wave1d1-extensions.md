# Cosmic Port — Engine Wave 1d.1 (thirteen kind extensions) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land wave-1 PR 2d.1: the thirteen parameter extensions to existing
effect kinds, per `docs/dev/cosmic-port/proposed-primitives.md`
(§ Kind extensions) — no new kinds, no new subsystems.

**Architecture:** Each row is the same move: extend one kind's `ParamSpec`
(new param with default preserving today's behavior byte-identically), extend
its `run()`/sink verb minimally, regen goldens. Defaults are the back-compat
contract: an ability authored yesterday compiles and behaves identically.
Where a row says the SINK grows, it grows a narrow verb parameter — never a
routine (spec §4).

**Tech Stack:** `se-engine` effect kinds + sink, `se-compile` (ParamSpec only),
JUnit, fuzz corpora, `regenDocs` goldens.

## Global Constraints

- Spec + rulings R1–R9; §4 bar. Binding skills: `starenchants-conventions`,
  `effect-engine`, `folia-scheduling`, `writing-tests`, `performance-hot-paths`,
  `code-comments`. Parallel-agent git protocol (own worktree only).
- Branch `feat/cosmic-wave1d1-kind-extensions` from freshly-fetched `origin/main`.
- Engine PR: full verification (R4); `--rebase --auto`; never bypass red.
- The two wave-1 traps: full-registry end-to-end tests; stage-rebuild coverage
  for any field crossing compile stages.
- Every extension: (1) failing test first, (2) a back-compat golden — the kind
  WITHOUT the new param lowers and runs byte-identically, (3) fuzz entries
  (valid + out-of-range + wrong-type), (4) spec-conformance suite stays green.
- Goldens regenerated verbatim; fingerprint VERSION unchanged.

## The thirteen rows (contracts — proposed-primitives § Kind extensions is the authority)

| # | Extension | Notes for implementation |
| --- | --- | --- |
| 1 | `DURABILITY` + `select` (`whole-set`\|`slot:<name>`\|`most-damaged`\|`least-damaged`\|`random-piece`, default `whole-set`) + `skip-undamaged` (BOOL, default false) | today's whole-set behavior is the default enum value; slot addressing covers the 03-doc ARMOR_SLOT_DURABILITY consumers |
| 2 | `DURABILITY` + `mode=percent` (amount read as percent of max durability) | joins the existing mode enum |
| 3 | `CURE` + `count` (INT, default 0 = unlimited) — remove exactly N matching effects, first-enumerated order | Blessed both docs |
| 4 | `REFLECT` + `cap` (DOUBLE, default 0 = uncapped flat ceiling per hit) + `feedback` (STRING template, optional) | REFLECT is percent-only today |
| 5 | `FREEZE` + `breakout-chance` (DOUBLE 0–100, default 0) — per-blocked-action chance the target shatters the root early | struggle-out roots (02) |
| 6 | `VELOCITY` + `anchor` (`activator`\|`attacker`\|`victim`, default `activator`) + `mode=toward` | `away` is hardwired to the activator today; `toward` magnitude may be an expression (EXPR_PARAMS shipped) |
| 7 | `FILL_OXYGEN` + `amount` (INT ticks, default 0 = full restore) with clamp to max air | AIR_TICKS_RESTORE; keep the measured skip-if-overflow OFF (ledgered as-intended) |
| 8 | `FLY` + `speed` (DOUBLE, default = server default fly speed) | Rocket Escape |
| 9 | `MODIFY_FOOD` + `mode=scale-gain` (factor over an EAT event's gain) | only meaningful on EAT-trigger abilities — diagnostic if authored elsewhere is NOT required; document the no-op |
| 10 | `MODIFY_FOOD` + `mode=cancel-drain` (armed while worn; vetoes food-level decreases) | pairs with the PASSIVE/worn lifecycle the potion grants already use |
| 11 | `GUARD` + `health`, `effects` (potion list); `SPAWN_ENTITY` + `name`, `speed` | param unification — copy each param's spec shape from the kind that already has it |
| 12 | `SUPPRESS` + consume-time feedback (`consumed-message-actor`, `consumed-message-victim`, `consumed-sound`, all optional); same trio on `MARK` consumption | fires when the window/one-shot actually BLOCKS/consumes, not when arming — the suppression store's consume site is the emit point |
| 13 | `TEMP_BLOCK` + `fill-chance` (DOUBLE 0–100 per column, default 100) + `revert-effects` (effect list run at revert for entities within 2 blocks of a restored block); FOOTPRINT radius cap 4 → 5 | revert hook rides the TempBlockLedger's existing revert path; the cap bump is a spec max change (goldens) |

Rows 9/10 and 12 touch listener/store seams (EAT path, suppression consume
site) — if the seam you find differs structurally from the note, STOP and
report rather than improvising.

---

### Task 1: Branch + baseline

- [ ] `git fetch origin && git switch -c feat/cosmic-wave1d1-kind-extensions origin/main`;
  baseline `./gradlew build -q` green.

### Task 2–5: The rows, batched by seam locality

Work in four commits-per-batch (test → impl), batching to keep each commit
reviewable: (2) rows 1–4 (item/potion-adjacent kinds), (3) rows 5–8
(movement/survival kinds), (4) rows 9–12 (food + summon params + consume
feedback), (5) row 13 (temp-block family).

Per row, the invariant recipe:

- [ ] Failing test: new param drives the new behavior (concrete values from the
  matrix consumers — e.g. row 1: two worn pieces at different damage,
  `select=most-damaged` repairs only the more-damaged one).
- [ ] Back-compat golden: the kind authored WITHOUT the new param lowers to the
  same compiled args and behaves identically (assert against a pre-change
  recording where the test fixture supports it).
- [ ] Minimal implementation; sink verbs stay narrow.
- [ ] Fuzz entries; suite green; commit.

### Task 6: Docs, goldens, gate, PR

- [ ] `./gradlew :bootstrap:regenDocs` goldens verbatim (params appear in the
  effects section; fingerprint VERSION unchanged); full `./gradlew build -q`.
- [ ] Push; PR `feat(engine): wave 1d.1 — thirteen kind extensions`, body
  citing spec §5 row 2 + proposed-primitives § Kind extensions;
  `--rebase --auto`; full CI green (R4). Report and stop — 2d.2 (new effects)
  is planned main-loop-side.

## Self-review record

- Rows 1–13 map one-to-one onto proposed-primitives § Kind extensions
  (SHOP_SELL-adjacent items were already excluded by R9 and do not appear).
- Two seam uncertainties (EAT path, suppression consume site) carry STOP
  instructions; everything else extends files the wave already touched.
- Back-compat goldens are the load-bearing discipline: thirteen default-valued
  params must not shift one byte of existing compiled content.
