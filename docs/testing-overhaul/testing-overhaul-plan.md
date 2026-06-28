# Test-suite overhaul — swarm execution plan

> **Status: proposed plan, not yet executed.** This is the step-by-step plan to
> rewrite the entire test suite into the [target architecture](testing-architecture.md),
> using a swarm of agents, without losing coverage. The durable policy the rewrite
> enforces is [testing-rules.md](testing-rules.md); the per-file evidence the swarm
> consumes is [testing-audit-findings.md](testing-audit-findings.md).

## Goal and the "replace every file" reconciliation

The objective is a clean-sheet pass: **every test file is reviewed and re-emitted
into the new architecture** — fixtures instead of copy-paste, data-driven families
instead of per-instance boilerplate, one source of truth instead of re-typed
strings, and the dark edges lit up. "Replace every file" is the *process*, not a
mandate to discard coverage.

That distinction is load-bearing. The audit (224 files read and judged) found the
suite is **mostly sound**: 126 of 184 unit files were judged keep-as-contract, and
adversarial verification **overturned 27 of 34 proposed deletions** because nearly
every effect-kind test is the sole guard of its kind's wiring. A literal "delete
everything and rewrite from scratch" would silently drop real bug-catching coverage.

So the rewrite is **subtractive only after it is additive**: collapse the 46
effect-kind files into ~6 tables that carry the *same rows*; fold the 8 store files
into one contract; re-point ~72 string literals at their source; **add** ~18 tests
for the edges that ship dark. Net file count drops materially; **net covered
branches only grow.** Coverage is the invariant, not file count.

### Net shape (audit estimate)

| | Before | After (est.) |
| --- | --- | --- |
| Unit test files | 184 | ~150 |
| Effect-kind files | 46 | ~18 (~6 tables + ~12 branching kinds) |
| TTL-store files | 8 | 1 contract + thin per-store remainders |
| Live suite files | ~34 | ~31 (5 merged/deleted, gap-suites added) |
| Brittle user-facing-string asserts | ~72 instances | 0 (golden or synthetic) |
| Concept collapse | — | ~63 files/concepts → ~12 |
| New tests for dark edges | — | +18 (12 unit, 5 live, 1 both) |
| Covered branches | baseline | **≥ baseline, enforced** |

## Audit findings summary

The full evidence is in [testing-audit-findings.md](testing-audit-findings.md).
The actionable shape:

- **Over-dense (collapse):** ~46 effect-kind tests, ~8 TTL-store copies, 5
  protection-provider tests, 4 per-error boilerplate clusters, 2 Carrier duplicates,
  the 2 byte-twin drift guards, the 4 giant migrator vocab methods.
- **String-coupled (re-point):** ~72 instances, concentrated in config-default
  re-typing (`LangLoaderTest`, `MasterConfigLoaderTest`, `TraksConfigTest`), the
  3-place DSL-token coupling across effect kinds, and the live render/menu/carrier
  suites. The engine/effect/codec layers are essentially clean.
- **Too sparse (add):** 7 high-severity gaps — `AbilityExecutor.runLifecycle` (zero
  refs anywhere), Folia cross-region (every live suite is single-region), expression
  arithmetic, `Integrations.active()` lazy-classloading, the EraseStage
  canonical-trigger invariant, the set-bonus payoff, the migrator all-TODO YAML
  safety — plus 13 medium gaps.
- **Infra:** the drift-guard build-cache hole, the absent modern live-matrix CI
  lane, no suite selection, sequential matrix boots.

## Foundation-first, additive-then-subtractive order

Coverage never regresses mid-flight because nothing is deleted until its
replacement is green. Phases 0–2 are serial and prove the fixtures on the healthiest
areas; phases 3–7 then run **concurrently**; live is last.

**Phase 0 — Fixtures (serial, blocking, purely additive, one PR each).** Land the
shared infrastructure with nothing deleted yet:

- the production `schema.diag.DiagCode` enum, wired into every producer (this is a
  production change, gated by its own conformance test);
- the test-only `se/testfx` module (package `testfx`) holding `Defs`,
  `FakeEffectCtx`/`SpecDrivenCtx`, `YamlFixture`/`assertDefault`, `TtlStoreAdapter`,
  `RenderGolden`, `CorpusLoader`, on every module's `testImplementation`;
- the live-side `CombatRig` and `DeferringSchedulerBackend` in `se/tester`;
- the JaCoCo per-module wiring + the covered-branch PR gate;
- the drift-guard build-cache fix (declare golden paths as task inputs).

**Phases 1–7 — by module (the parallelization unit; modules are independent
compilation units, files within a module are independent):**

| Phase | Area | Core work |
| --- | --- | --- |
| 1 | **schema** | Validate the parameterized + `DiagCode` + collapse patterns on the healthiest area first. Fill the arithmetic/`HANDLE`/expression-arg gaps. |
| 2 | **compile** (loaders + stages) | `Defs` builder, `YamlFixture`, `DiagCodes`; collapse per-error/clamp boilerplate; fill `CrystalDefReader`/`SetDefReader`/map-reader gaps; re-point `Lang`/`MasterConfig`/`Traks` defaults. |
| 3 | **engine-effects** | Collapse to `EffectSinkWiringTest` + `ModeDispatchEffectTest` + `SpecConformanceTest` + `ImmuneTypeCodeTest` via `FakeEffectCtx`. Each old `@Test` → a row that **keeps** distinct args + multi-target + `verifyNoMoreInteractions`. Add the dark branches (HealthMod `set`, Immune all 5 codes, Fly mob-skip). |
| 4 | **engine-state/core** | `TtlStoreContractTest` + the dark stores (Combo/Immune/Teleblock, Trench/Tunnel shapes); add `runLifecycle`, pipeline FORCE-ALLOW/OUT_OF_LEVEL, FactPopulator derived-facts, EraseStage canonical-trigger + overflow gaps. |
| 5 | **item** | Codec/worn parameterization; the `RenderGolden`; the untested `LoreRenderer` branches (heroic/set/weapon markers, crystal join); `AppliedSlot` §I exclusivity. |
| 6 | **feature / platform / integrate** | Merge `CarrierCombine`+`CarrierService`; dedupe couplings to spec/`.defaults()`/`Aliases`; add `IntegrationsTest`, `CustomItemsResolver`, `Economy` withdraw/deposit, `ContentReloader` single-flight via the deferring backend, the EE-overhaul cold paths (transmog suffix, trak idempotency, `freedBy`). |
| 7 | **bootstrap / migrate / pack** | `SeCommand` surface-contract (GIVE_TYPES↔give()); migrator vocab tables; the all-TODO `effects: []` safety; pack edge paths; relocate `EePortGenerator` out of the test set; retire `CatalogEquivalenceTest` (with sign-off). |
| 8 | **live** (last) | Trim per the [live strategy](testing-architecture.md#live-server-strategy--keep-it-trim-it-complete-it); route 7 suites through `CombatRig`; add the gap suites (DEFENSE end-to-end, real death, cross-region, torn-read, resolver-negative, set payoff). Gated by the matrix, not gradle. |

**Dependencies:** phases 3–7 run concurrently *after* phase 0 lands and phases 1–2
prove the fixtures. Live (8) waits on item/feature being green (it reuses
`CombatRig` and the codecs).

## The agent swarm

Run the rewrite as a sequence of per-phase workflows (one phase = one fan-out), so
the maintainer reviews between phases and the fixtures are proven before they are
relied on. The audit that produced [the findings](testing-audit-findings.md) is the
template for the swarm's *inputs*; this swarm produces *edits*.

**Topology per phase:**

1. **Fan out one agent per module-area** (the 13 audit areas map directly to rewrite
   agents). Each agent gets: its file list, the per-file audit verdict + enumerated
   contracts, the fixtures it may use, and the [rules](testing-rules.md). It rewrites
   its area in an **isolated git worktree** (parallel agents mutate files — use
   worktree isolation) and returns a structured report (files touched, contracts
   re-homed, rows added, coverage delta).
2. **Pipeline each area through a verifier stage** (no barrier): a second agent
   adversarially checks the rewrite against the **coverage ledger** — for every
   deleted/merged file, it must point at the specific row/golden/case that now
   carries each old contract, and confirm `./gradlew :<module>:test` is green and the
   JaCoCo branch count did not drop. A failed verification bounces the area back.
3. **Barrier + integrate:** once an area's verification passes, its worktree merges
   to the phase branch; the phase lands as one rebase-merge PR.

**Why a workflow, not freehand agents:** the control flow is deterministic
(fan-out → verify → integrate, looped per phase), the coverage ledger is a hard
gate, and the adversarial re-check on every deletion is exactly the discipline that
saved 27 tests in the audit. Encode it; don't improvise it.

**Scale:** ~13 rewrite agents + ~13 verifiers per phase, phases 3–7 concurrent.
Phase 0 is serial (the fixtures are shared and must compile-green before anything
depends on them).

## Per-file rewrite checklist

Every agent applies this to every file (it is [Rules](testing-rules.md) 1–5 made
mechanical):

1. Read the audit verdict (`keep | trim | rewrite | merge | delete | split`) and its
   **enumerated contracts** for this file.
2. If it asserts user-facing text → move the assertion to the **golden**
   (`RenderGolden` / a `*DriftTest`); regen and commit the golden in the same PR.
3. If it hardcodes a diag code / DSL token / default literal → reference
   `DiagCode` / the kind's `EffectSpec` / `.defaults()`.
4. If it is per-instance boilerplate in a family → convert **each** old `@Test` into a
   **row** in the family's parameterized table, **carrying every assertion**
   (distinct-value args, multi-target, `verifyNoMoreInteractions`, null-location
   no-op). Then delete the file.
5. If it duplicates coverage pinned elsewhere → merge/delete — **but only after**
   step 6.
6. **Before deleting, transcribe every contract the old test pinned** into the
   replacement (a row, a case, or a golden line). This is the gate, not a suggestion.
7. Run `./gradlew :<module>:test` green; confirm the JaCoCo per-module branch count
   did not drop.

## Coverage-preservation protocol (the safety net)

A swarm replacing every file in parallel needs an objective "did we drop a contract"
signal. Three layers, all blocking:

- **The contract ledger.** The audit's per-file enumeration of *what each test
  pins* is the checklist. A merge/delete PR must map each old contract to its new
  home. No mapping → no merge.
- **JaCoCo per-module branch diff.** Wired in phase 0. A covered-branch decrease is a
  **blocking PR check**. This catches a contract dropped *despite* a plausible
  mapping.
- **Adversarial re-check on every deletion.** The verifier agent (and the human
  reviewer) must point at the specific row/golden/case carrying the deleted file's
  contract — the same discipline that overturned 27 of 34 audit deletions. Default to
  keep; delete only when the replacement is named and green.

Within a module PR, old and new tests run **side-by-side**; the old is removed in the
**same PR** once the table/golden demonstrably subsumes it. Never delete on faith.

## Infrastructure changes (carried alongside the rewrite)

Detailed in [the findings](testing-audit-findings.md#infrastructure); landed as their
own small PRs, mostly in phase 0 and phase 8:

- **Phase 0:** close the drift-guard build-cache hole (golden paths as task inputs);
  wire JaCoCo + the branch-diff gate; unify the four drift guards on one shared golden
  helper (and replace `SurfaceCatalogDriftTest`'s hand-rolled JSON with a real
  serializer).
- **Phase 8 / CI:** add a least-cost modern live lane to `ci.yml` (one Paper + one
  Folia) on PR and the full `--all` on a schedule, mirroring `legacy.yml`'s
  cached-server approach; add suite **tags + a selector** to `SeTesterPlugin` so the
  thin lane runs a subset; **parallelize** `run-matrix.sh` boots; **boot-gate the
  modern half** of the shipped artifact in `release.yml` via a `mega-smoke.sh` target.
- **Preserve as fixed invariants** (do not regress): the version-pinned (not
  `find | head -1`) jar selection, the fresh-mtime + literal-PASS honest read, the
  tick-anchored harness, and `build-mega-jar.sh`'s class-set soundness gate.

## Docs and skills to retire or rewrite

Per the standing decision that existing rules are inputs, not constraints, the
overhaul **supersedes** the old testing docs/skills. As part of phase 8 (or a
trailing docs PR):

- **Fold into this set and delete:** `docs/dev/writing-a-live-test.md` →
  [testing-rules.md](testing-rules.md) Rule 4 + the live strategy in
  [testing-architecture.md](testing-architecture.md). Its flake-proofing list is
  preserved verbatim in Rule 4.
- **Rewrite to point here:** the `matrix-gate` and `live-server-testing` skills — keep
  the matrix-running and Folia mechanics, but the "what to test live vs unit" content
  is now owned by the rules doc; have the skills reference it rather than restate it.
- **Update:** `verification-gate.md` to describe the new CI lanes and the JaCoCo gate.
- **Add a skill** (optional) `test-authoring` pointing at the rules + fixtures, so new
  features get the decision rubric at the point of writing.

## Verification gate for the overhaul itself

Each phase PR is green only when: `./gradlew build` passes; the JaCoCo per-module
branch count is `≥` the pre-phase baseline; the contract ledger maps every removed
test; and — for the live phase and any version/NMS/Folia-touching change — a fresh
matrix PASS on Paper **and** Folia (never a banner; verify `test-results.txt` is
fresh and reads PASS per the honest-read discipline). Pure-logic module phases gate
on `./gradlew build` + JaCoCo alone, per the test-scope calibration: reserve the slow
matrix for the changes that actually need it.
