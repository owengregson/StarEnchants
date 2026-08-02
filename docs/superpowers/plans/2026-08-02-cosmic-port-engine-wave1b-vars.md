# Cosmic Port — Engine Wave 1b (entity-scoped vars + facts batch + EXPR_CHANCE) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land wave-1 PR 2b: `TARGET_VAR` (entity-scoped counters + cross-entity
reads), the nine new condition facts, and the carried `EXPR_CHANCE` (expression-
valued `chance:`), per `docs/dev/cosmic-port/proposed-primitives.md`.

**Architecture:** Three legs share one design insight. (1) `SET_VAR` already
targets via `who` and dynamic vars already read through `VarStore` in
`FactPopulator` — TARGET_VAR adds an `op=increment/step/cap` counter mode, mob
carriers, and a **prefix-recognized dynamic operand** `%victim.var.<name>%` in
`ConditionCompiler` (compile-time prefix → victim-scoped VarStore read). (2) The
two keyed fact families (`%actor/victim.potion.<effect>%`) ride the SAME prefix
mechanism; the seven scalar facts are ordinary append-only `BuiltinVars` slots
populated by `FactPopulator` under their `FactMask` bits. (3) `EXPR_CHANCE`
threads an optional `NumExpr` beside `baseChance` through
`AbilityDef → LoweredAbility → Ability` and evaluates it at the chance-roll gate
with the already-populated fact buffer, clamped to `[0,100]`.

**Tech Stack:** `se-schema` (grammar untouched), `se-compile` (ConditionCompiler,
lower/erase stages), `se-engine` (BuiltinVars, FactPopulator, VarStore, SetVarEffect,
ActivationPipeline), JUnit, fuzz corpora, `regenDocs` goldens.

## Global Constraints

- Spec + rulings R1–R8; primitive bar §4. Skills binding: `starenchants-conventions`,
  `effect-engine`, `config-and-migration`, `writing-tests`, `performance-hot-paths`
  (facts are hot-path: populate ONLY under the FactMask bit), `code-comments`.
- Parallel-agent git protocol (own worktree; never the primary checkout). Branch
  `feat/cosmic-wave1b-vars` from freshly-fetched `origin/main`.
- Engine PR: full verification before merge (R4) — arm `--rebase --auto`, never
  bypass a red check.
- **End-to-end tests compile with the FULL `BuiltinEffects`/`BuiltinSelectors`
  registries** — a subset registry renumbers ADR-0039 dense kind ids and silently
  mis-dispatches (trap recorded on PR #268).
- `BuiltinVars` slots are **append-only** (§3.4) — new slots go at the end with
  the ADR-comment convention the file already uses.
- Diagnostics via `DiagCode` only; compile stays Bukkit-free (facts that need
  Bukkit reads live in `FactPopulator`'s populate step, not in compile).

## Contracts (from proposed-primitives.md — the authority on semantics)

**TARGET_VAR:** `SET_VAR` gains `op` (`set`|`increment`, default `set`), `step`
(INT, default 1), `cap` (INT, 0 = uncapped), and non-player `LivingEntity`
carriers (VarStore keys by entity UUID already — verify, else extend); conditions
gain `%victim.var.<name>%` (victim-scoped read; absent → 0/empty). Victim
counters clear on carrier death (hook where the engine already observes deaths —
find the existing death cleanup used by stores and join it; if none exists for
VarStore, add the listener at the feature layer, not in the engine core).
Consumers: Bleed stacks, Rage/Execute, Devour/Corrupt/Hex gates, Soul Trap,
Inquisitive, Mark-of-the-Beast payout marks.

**Facts batch (scalar slots):**

| Slot | Kind | Populate |
| --- | --- | --- |
| `posthit.health` | number | DEFENSE scope: actor health minus pending post-mitigation damage (event payload; 0 outside DEFENSE) |
| `victim.fromspawner` | flag | entity spawn provenance (spawner vs natural — spawn-reason tracking; if the server exposes none pre-1.17, populate false and note the era hazard) |
| `heldticks` | number | ticks since the actor's held-slot change (engine store fed by the held-change listener the WIP called HeldChanges — implement fresh, small map + listener) |
| `victim.relation` | string | ALLY/MEMBER/ENEMY/NEUTRAL, duel-aware — resolve through the SAME ally model the `ENEMIES` selector filter uses (one source of truth; grep the filter's provider) |
| `nearbyallies` | number | radius-scoped ally count — mirror `%nearbyenemies%`'s existing population + radius convention exactly |
| `impactheight` | number | projectile-hit activations: projectile Y minus victim feet Y; 0 otherwise |
| `projectilekind` | string | ARROW/FIREBALL/THROWN/OTHER from the damaging projectile; empty for non-projectile |
| `actor.souls` / `victim.souls` | number ×2 | total souls across carried gems — read through the existing soul service the engine already exposes to effects (SoulDebit/SoulSpender path); victim side reads the snapshot-safe worn/carried state, never a live cross-region walk (§5.5 rule) |

**Keyed families (prefix operands, NOT slots):** `%actor.potion.<effect>%` and
`%victim.potion.<effect>%` — BOOL-or-amplifier semantics: numeric context yields
active amplifier+1 (0 when absent) so `> 0` is the boolean idiom; the `<effect>`
token resolves through the platform potion resolver at compile (unknown effect →
diagnostic, warn-and-skip that op).

**EXPR_CHANCE:** `chance:` accepts an expression (functions included, PR #267);
constant stays the primitive-double fast path — the `Ability` record gains an
optional `NumExpr chanceExpr` (null for constants; hot-path cost is one null
check). Evaluated at the chance-roll gate with the populated fact buffer,
clamped `[0,100]`, `FactMasks` unions its facts so the populator computes them.
Consumers: bows `VAR_SCALED_CHANCE`, Feign Death.

---

### Task 1: Branch + baseline

- [ ] `git fetch origin && git switch -c feat/cosmic-wave1b-vars origin/main`;
  `./gradlew build -q` → SUCCESS (stop otherwise).

### Task 2: TARGET_VAR — failing tests, then implementation

**Files:** `se/engine/src/engine/effect/kind/SetVarEffect.java`,
`se/engine/src/engine/stores/VarStore.java`,
`se/compile/src/compile/cond/ConditionCompiler.java`,
`se/engine/src/engine/run/FactPopulator.java`; tests beside each (extend
`VarVocabularyTest` / `ConditionEvaluatorTest` / the SetVar kind test / a new
victim-scope test in `engine/test/engine/stores/`).

- [ ] Failing tests: (a) `SET_VAR op=increment step=1 cap=20 who=@Victim` twice
  on the same mob victim reads back 2 via `%victim.var.bleedstacks%` in a
  condition; (b) increment past cap pins at cap; (c) `%victim.var.x%` with no
  victim or unset var evaluates 0 and the condition is still well-formed;
  (d) back-compat: bare `%name%` actor-scoped reads are byte-identical to today
  (existing tests stay green untouched); (e) carrier death clears the victim's
  vars (test through the cleanup hook's seam).
- [ ] Implement: ParamSpec additions (`op` enum, `step`, `cap`); VarStore mob
  carriers + increment-with-cap + death cleanup; `victim.var.` prefix operand in
  ConditionCompiler (before the PAPI fallthrough) with its FactMask contribution;
  FactPopulator victim-scoped read.
- [ ] Suites green; fuzz corpus entries (valid + unknown-prefix + malformed);
  commit per step (`test:` then `feat:`).

### Task 3: Facts batch — failing tests, then implementation

**Files:** `se/engine/src/engine/condition/BuiltinVars.java` (append-only),
`se/engine/src/engine/run/FactPopulator.java`, a new small held-change store +
listener (`se/engine/src/engine/stores/` + `se/feature/src/feature/trigger/`
wiring beside the existing listener registrations), tests in
`VarVocabularyTest`/`FactPopulatorTest`.

- [ ] Failing tests per slot (populate + mask-gated skip: unreferenced slot is
  NOT computed — assert via the populator's existing mask-test pattern).
- [ ] Implement the seven scalar slots + the two potion prefix operands (compile
  resolves the effect handle; evaluator reads the entity's active effect).
- [ ] `victim.relation`/`nearbyallies` MUST reuse the existing ally/enemy
  provider — if you find two ally models while wiring this, STOP and report
  (that's an N-implementations smell, not something to fork a third time).
- [ ] Suites + fuzz green; commit.

### Task 4: EXPR_CHANCE — failing tests, then implementation

**Files:** `se/compile/src/compile/def/AbilityDef.java` (+ readers that construct
it), `se/compile/src/compile/stage/DefaultLowerStage.java` +
`LoweredAbility.java` + `DefaultEraseStage.java`, `se/compile/src/compile/model/Ability.java`,
`se/engine/src/engine/pipeline/ActivationPipeline.java` (chance gate),
`se/compile/src/compile/model/FactMasks.java`; tests:
`ActivationPipelineTest` + lower/erase stage tests + an end-to-end (full
registries!) asserting `chance: "min(50, %recentattackers% * 10)"` activates at
the expected rate under an injected roll supplier.

- [ ] Failing tests first: constant `chance:` byte-identical lowering (golden);
  expression chance evaluates per-activation with facts, clamps to [0,100];
  FactMask union includes the expression's facts.
- [ ] Implement; whole gate order untouched (§3.3 — the expression only changes
  the VALUE compared at the existing roll gate).
- [ ] Suites + fuzz green; commit.

### Task 5: Docs, goldens, gate, PR

- [ ] Docs: generator-driven surfaces regenerate via `./gradlew :bootstrap:regenDocs`
  (new vars appear in the vars section of `authoring-surface.txt` — commit the
  regenerated goldens verbatim; the fingerprint version stays — adding vars is a
  content change the fingerprint hashes, not a serialization-format change);
  hand-written shape docs only if a page already documents `chance:`/`SET_VAR`
  (extend in place, match tone).
- [ ] `./gradlew build -q` → SUCCESS. Push; PR
  `feat(engine): wave 1b — entity-scoped vars, facts batch, expression chance`,
  body citing spec §5 row 2 + proposed-primitives.md; arm `--rebase --auto`;
  full CI including live matrix must be green (R4).
- [ ] On merge: report back and stop — the 2c plan is generated main-loop-side.

## Self-review record

- Covers exactly proposed-primitives.md §"Entity-scoped state" + §"New condition
  facts" + the EXPR_CHANCE carry from the 2a execution notes. POSITION_VARS
  stays wave 2 (masks-only consumer).
- Files verified this session: BuiltinVars/FactPopulator/VarStore/SetVarEffect/
  ConditionCompiler shapes read at HEAD; AbilityDef/LoweredAbility/Ability chain
  named from the 2a agent's audit. Where a hook is uncertain (death cleanup,
  spawner provenance, ally provider) the task says find-or-STOP, never fork.
- No placeholders; every test spec carries concrete inputs and expected values.
