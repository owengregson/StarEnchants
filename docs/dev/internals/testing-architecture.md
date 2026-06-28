# Testing architecture

> **Status: realized.** This is the design the test-suite overhaul was built into;
> it is the reference for how tests in this repo are shaped. The durable rules that
> govern authoring *new* tests are the `writing-tests` skill (in
> `.claude/skills/writing-tests/`). Where this conflicts with the older
> `live-server-testing` / `matrix-gate` prose, this wins; those remain the source
> for live-harness and matrix mechanics.

## What shipped vs. what was deferred

The overhaul realized the high-leverage core and deferred the live-suite refactor as
a separate, matrix-gated effort (it can only be verified under the slow Paper+Folia
matrix, so churning it blind was judged not worth the risk on already-passing
suites). Concretely:

- **Shipped:** `schema.diag.DiagCode` (producers + tests reference the enum; `E_PARSE`
  split into sub-codes); the `se/testfx` module with `FakeEffectCtx`, `SpecDrivenCtx`,
  and `Defs` (the ability/lowered builders); the **46 effect-kind tests collapsed
  into 4 data-driven tables** — `FanOutEffectTest`, `ModeDispatchEffectTest`,
  `LocationEffectTest`, `FlagAndSoulEffectTest` (every assertion ported as a row);
  `SpecConformanceTest` (drives every kind from its own spec); the string-coupling
  fixes across schema/compile (defaults referenced via `.defaults()`, diagnostics via
  `DiagCode`); per-module JaCoCo reports.
- **Not built (the design records them; build just-in-time when first needed):**
  `YamlFixture` — unnecessary, because `YamlNode.compose` is package-private to
  `compile.load`, so it *is* the in-package fixture; `RenderGolden` / `TtlStoreAdapter`
  / `CorpusLoader` — the render and store tests were already model-quality, so no
  fixture was warranted; `DeferringSchedulerBackend` — not needed for the realized
  live-suite work.
- **Shipped (the second slice — deferred above, now realized):** the **per-module
  branch-coverage floor** as a blocking gate (`jacocoTestCoverageVerification` wired
  into `check` for schema/compile/engine/item, each minimum calibrated just under the
  module's actual); the **`CombatRig`** live fixture closing the cross-suite listener
  leaks across **10** combat-dispatch suites (the leak fix is the correctness win —
  see the distinct-chunk nuance below); the **modern live-matrix CI lane**
  (`live-matrix.yml`: a risk-edge subset on PR, the full range nightly); and
  `RenderingDisciplineArchTest`, which bans `ChatColor` from the harness so a suite can
  never again reconstruct a production-rendered string (the transmog
  formatting-coupling class — `writing-tests` Rule 1).

The body below is the design rationale — the *why* behind the shape above; treat the
fixture table and "open decisions" as the record of the choices, several of which
resolved to "not needed" as noted.

## Why a redesign (the one-paragraph case)

A full-suite audit (224 test files: 184 unit, 40 live) read and judged every
file. The headline: **the suite is healthier than its size suggests, but its
problems are real and sharply localized.** The pure-logic areas (schema,
compiler, engine math, codecs, worn-state) are model behavior-first tests. The
damage is concentrated in three shapes: (1) **per-instance boilerplate** — ~46
near-identical effect-kind tests and ~8 TTL-store copies that re-prove one trivial
contract dozens of times, one file per instance; (2) **string coupling** — ~72
spots where a test re-types a user-facing or config string that production already
owns, forcing a two-place edit on every wording change; and (3) **sparseness on
load-bearing edges** — the HELD/PASSIVE buff lifecycle, Folia cross-region
correctness, expression arithmetic, and reload-under-concurrent-read all ship with
zero tests. The fix is not "more tests" or "fewer tests" — it is **the right
shape**: data-driven families, one source of truth for every string, and a thin
but deliberately complete live layer.

## Five principles

Every choice below derives from these. They are the test contract for the repo.

1. **Test behavior and contracts, never user-facing text or incidental
   implementation.** A test asserts what a real bug would violate — a number, a
   slot, an enum, a routing decision, a serialized byte layout — not the English
   wording of a lore line or the spacing of a rendered YAML block.

2. **One source of truth.** A value production reads from a constant, registry,
   `EffectSpec`, or `.defaults()` accessor is *referenced*, never re-typed in a
   test. Diagnostic codes become a `schema.diag.DiagCode` symbol shared by
   producer and test. DSL tokens are asserted through the kind's own spec. All
   shipped display copy is pinned **once**, as a regenerable golden snapshot — the
   single change that kills the maintainer's #1 anti-pattern.

3. **Format-spec and shipped-copy are different tests.** An *algorithm* test
   (the lore renderer, numerals, word-wrap, color-translate) feeds its **own**
   in-test inputs and asserts the transform — so a default-color retune can't break
   unrelated logic. The shipped *catalogue* of strings lives only in the golden.
   `LoreRendererTest` and `MenuTextTest` are already this model and stay as-is.

4. **Match weight to risk along a strict pyramid.** Many fast, server-free
   unit/corpus/contract tests (the bulk, gated by `./gradlew build`); a thin band
   of golden/drift guards; and a deliberately **small** set of live Paper+Folia
   suites reserved for behavior only a booted game can prove. A test that a mocked
   object or synthetic event can prove is a unit test.

5. **Pin error and edge paths as first-class.** Assert diagnostic *codes* not
   message wording; drive the negative / absence / overflow / cross-region /
   torn-read branches that currently ship dark. Sparseness on edges is as much a
   defect as density on the happy path.

## The layers (the pyramid)

| Layer | Scope | Gated by | Framework |
| --- | --- | --- | --- |
| **Pure-unit** | DSL grammar/parse/typecheck, compiler IR shapes & interning, engine math (fold, num-expr, conditions), codec wire formats & round-trips, render *algorithms*, worn/set resolution | `./gradlew build` | JUnit 5, **no Bukkit, no Mockito** |
| **Data-driven corpus** | Families of one behavior as ONE table: effect→Sink wiring, mode-dispatch arithmetic, the TTL-store contract, resolver strategy over a synthetic alias map, migrator token vocab, clamp/order invariants | `./gradlew build` | JUnit 5 `@ParameterizedTest` + `@MethodSource`/`@CsvSource` |
| **Contract & property** | Single-source enforcement & architectural invariants: spec-conformance (run() touches only its spec's params), diagnostic-code contract, core-purity & integrate-lazy-classloading guards, structural shape (vocab slots == #bindings, every alias has its inverse, every GIVE_TYPE is dispatched) | `./gradlew build` | JUnit 5 + ArchUnit; reflection-driven conformance derived from the production spec |
| **Golden / snapshot** | Whole-artifact regenerable pins of everything user-facing or generated: the DSL reference, website `catalog.json`/`surface.json`, `content/index.txt`, **and a new render golden** for lore/name/menu output | `./gradlew build` | The existing `*DriftTest` pattern (read committed artifact, assert `== render()`, regen via a flag) + a `RenderGolden` helper |
| **Live integration** | ONLY what a real booted server proves: server-side world-state change, real PDC serialize across the mapping flip, Folia region/thread + cross-region, real Bukkit events, cross-version registry existence/absence, the fake-player keystone, reload atomic swap under a concurrent reader, real economy deposit, the 1.8.9 legacy smoke | the **matrix** (`run-matrix.sh`), NOT `gradle test` | the bespoke `Harness.Scenario` model in `se/tester`, on real Paper **and** Folia |

The base is the largest layer and runs on every build. The top is small and runs
on the matrix. The model files already in the tree, per layer:

- **Pure-unit:** `engine/interact/DamageFoldTest` (each case shows
  `(10×1.2+5)×0.7−1=10.9` inline), `item/render/LoreRendererTest` (feeds its own
  `LoreStyle` + name function, asserts the transform). Keep essentially verbatim —
  they are the template the rest imitates.
- **Contract:** `compile/arch/CorePurityArchTest` (keep verbatim).
- **Golden:** `engine/doc/ReferenceDocDriftTest` (fold its byte-twin
  `ReferenceCatalogDriftTest` behind one parameterized driver).
- **Live:** `tester/suite/CombatSuite` (fake player → real `EntityDamageByEntityEvent`
  → POISON on a real cow, `probe.count() == 1`). Keep its shape; route its ~40
  lines of engine wiring through a shared `CombatRig` fixture.

## The shared fixtures (a `se/testfx` module)

The flat, single-segment-package layout forbids casual cross-module test-source
sharing, which is exactly why today's helpers are copy-pasted. The overhaul lands
**one** test-only module, `se/testfx` (package `testfx`, mirroring how `tester` and
`imagegen` sit outside the shipped jar), that every module's `testImplementation`
depends on. Live-only helpers stay in `se/tester`. The fixtures:

| Fixture | Replaces | What it is |
| --- | --- | --- |
| **`DiagCode`** (a `schema.diag` enum, in *production*) | Raw diagnostic-code string literals re-typed in producer **and** test (`E_PARSE`, `E_COND_TYPE`, the easily-confused `E_DUP_KEY`/`E_DUPLICATE_KEY`…), and the message-substring matching `ExprParserErrorTest` is forced into | A production enum with a stable `code()`; producers call `diags.error(DiagCode.E_COND_TYPE, …)`, tests assert `first.code() == DiagCode.E_COND_TYPE.name()`. Add finer parser sub-codes (`E_PARSE_CHAINED_CMP`, `E_PARSE_UNCLOSED_GROUP`) so error tests assert a code, not English. **The single highest-leverage change.** |
| **`FakeEffectCtx` + `SpecDrivenCtx`** | The 3-place param/enum coupling (spec declares `who`/`mode`, `run()` reads it, test hardcodes it) across ~49 effect-kind tests, and the vacuous "verify the mock was called" shape | A real (non-Mockito) `EffectCtx` backed by a map keyed by the param names the kind's `EffectSpec` declares. `SpecDrivenCtx.from(kind.SPEC)` auto-fills declared params and **fails if `run()` reads an undeclared key** — powering both the wiring table and the spec-conformance test. Mockito kept only for the few genuine branch/interaction-order tests. |
| **`Defs`** (ability/lowered/def builder) | The ~18-positional-arg `AbilityDef`/`LoweredAbility` constructors re-declared as a private `def(...)` helper in five stage tests (a new record field forces five edits) | `Defs.ability().key("enchants/x").trigger("ATTACK").effect("DAMAGE:6").build()`. Add a field → change one builder. |
| **`YamlFixture` + `assertDefault`** | The good-but-ad-hoc "write my own YAML to `@TempDir`, round-trip through the real compiler" pattern duplicated across compile-load, and the default-literal couplings in `LangLoaderTest`/`MasterConfigLoaderTest`/`TraksConfigTest` | `YamlFixture.write(tmp, "enchants/x.yml", yaml).compile(resolver)` → a `Library`; plus `assertDefault(actual, MasterConfig.defaults().lore().enchantColor())` so default-checks reference the accessor. Codifies the dominant healthy pattern. |
| **`RenderGolden`** | Every exact user-facing-string assertion: `RenderSuite`'s `§7Venom §fIII`, `CarrierSuite`'s `Success Rate`, `MenuSuite`'s `guaranteed level`/`Epic`, `SoulGemLoreTest`'s default color curve, `LangLoaderTest`'s shipped English | `RenderGolden.snapshot(corpus, renderer)` writes the production-rendered lore/name/menu output for a fixed item/enchant corpus to a committed file; a drift test diffs and regens via `-Dse.render.regen`. **One golden owns all shipped copy.** Algorithm correctness stays in pure-unit with in-test styles. |
| **`TtlStoreAdapter`** | ~8 near-verbatim copies of the half-open-expiry + lazy-eviction + non-positive-TTL-noop + clear/clearAll boilerplate across the store tests | `interface TtlStoreAdapter { void arm(now, ttl); boolean isLive(now); void clear(); void clearAll(); }` implemented per store; ONE `TtlStoreContractTest` parameterized over the adapters. Each store file shrinks to **only** its distinctive semantics (key packing, sliding vs merge-extend, sentinel, ramp). |
| **`CorpusLoader`** | The scattered per-member loader round-trips, the four giant exact-token migrator vocab methods (~80 one-per-line asserts), and the `[0,100]` clamp tests duplicated across configs | A `@MethodSource` provider feeding token-mapping and clamp rows from compact in-test tables. The migrator **keeps** its gold-standard "compile the output through the real compiler" integration tests; only the vocab *enumeration* collapses to rows. |
| **`CombatRig`** (live, in `se/tester`) | The ~40-line compiler→holder→codec→itemViews→triggers→worn→executor→dispatch→listener wiring copy-pasted across 7 combat-style suites, **and** the cross-suite listener-accumulation flake | `CombatRig.of(plugin, yaml).onProc(cb).build()` returns a wired rig whose listeners register on entry and `HandlerList.unregisterAll` on `close()`. Each suite then declares only its enchant YAML + assertion. Fixes a latent double-delivery bug and makes per-suite intent legible — the single biggest live maintainability win. |
| **`DeferringSchedulerBackend`** (live-adjacent) | The inability of today's inline scheduler double to exercise reload single-flight `busy()`, the off-thread build-threw path, and the swap-failure guard-release | Keep `SyncSchedulerBackend` (inline) and `RecordingSchedulerBackend`; **add** a backend that queues off-thread work for manual pumping, so reload single-flight and guard-release become **server-free unit tests** instead of live-only. |

## Live-server strategy — keep it, trim it, complete it

Real Paper **and** Folia booting stays. It is the one external practice this
project deliberately adopts and it is irreplaceable — but it is reserved for the
behavior unit tests structurally cannot reach, and trimmed of the pure logic that
leaked in. The bespoke `Harness` is **kept as-is** (tick-anchored polling,
concurrent result map, `guard()`-captures-wrong-region-throw, fresh-result-files-
then-shutdown); JUnit-in-server buys nothing and would fight the region/thread
model.

**Stays live** (a real server is the only oracle):

1. Real PDC serialize/deserialize across the spigot→mojang mapping flip
   (`ItemCodecSuite`, `HeroicApplySuite`, `WornResolverSuite`).
2. Folia region/thread + cross-region correctness (`SchedulingSuite`, `SinkSuite` —
   the single best Folia test).
3. Real combat/world events → observable server state (`CombatSuite`,
   `CrystalSuite`, `ConditionSuite`, `ProtectionSuite`, `LifecycleSuite`'s
   equip→buff→unequip→removal).
4. Real `InventoryClickEvent` routing & inventory mutation (`GuiSuite`).
5. Cross-version registry existence and graceful absence (`CatalogSuite`, a merged
   `ResolverSuite`+`RuntimeHandlesSuite`).
6. The clientless fake-player keystone (`FakePlayers`, `FakePlayerSuite`).
7. Reload off-thread→global atomic swap (a trimmed `ContentLoaderSuite`).
8. Real economy provider discover + deposit (`EconomySuite`).
9. The 1.8.9 legacy smoke (`LegacySmokeSuite`).

**Leaves live** (pure logic that crept in — move to unit):
`ImportSuite.rejectsGarbage` → `ImportCodeTest`; `ContentFormatSuite` (3 checks
already in `ContentFormatTest`) → fold one parse smoke into `ContentLoaderSuite` and
delete; `ItemViewSuite` interning → `ItemViewCacheTest`, keep one thin real-PDC read
in `ItemCodecSuite`; `CombatFlagsSuite.keepOnDeathArmsStore` → store unit;
`CarrierSuite` gesture/cap/RNG math → `CarrierService` unit; `RenderSuite` exact-line
lore → render golden (relax the live persist check to "contains `§7Venom §f`");
`MenuSuite` render-inspection + brittle labels → `MenuText` unit + golden (keep only
the real-click-applies-enchant check); `EconomyItemsSuite` slot/cap arithmetic →
`SlotService` unit (keep a thin PDC round-trip).

**Is added live** (the dark integration corners the suites step up to and skip):
DEFENSE-trigger dispatch end-to-end (every suite only fires ATTACK); a real
`PlayerDeathEvent` firing holy keep-on-death and filtering drops; **cross-region
combat** (attacker and victim in distinct Folia regions — the actual region
invariant, never exercised because everything spawns at one location); a concurrent
snapshot reader **during** the reload swap (torn-read); the resolver **negative**
path (a token genuinely absent on the floor resolves empty + warn-and-skip); the
full set-bonus payoff (wear set → take real DEFENSE damage → REGENERATION on the
wearer); and event-count exactly-once guards where a double-fire is currently
undetectable.

**Harness hygiene fixes** — *realized*. The **10** combat-dispatch suites register through
`CombatRig`, whose `teardown()` runs `HandlerList.unregisterAll` per suite — closing the
cross-suite leak that let a stranded `CombatListener`/`TriggerListeners` dispatch on every
later suite's hit.

**Timing-flake fixes (the class, not the instance).** Two live suites asserted emergent
server state on a *fixed* tick, which is a latent flake the instant matrix load defers the
work past the wait — exactly what bit `TeleportSuite` on `paper:1.21.4`. Both are now
tick-anchored polls of the actual semantic, with generous budgets (a wide cap only lengthens
the FAIL path; PASS fires the instant the condition holds). `TeleportSuite` polls the
attacker's **proximity to the victim** (the real claim — "teleported *to* the victim" — and
tolerant of the floor's collision push-out) instead of "moved ≥ 3 from origin", and reads
only the actor's own location on the actor's own scheduler. `CombatSuite` polls for the
POISON, which lands on a scheduled `onEntity(victim)` hop rather than inline in `damage()`,
so a 10-tick fix-wait was a load-flake-in-waiting. The shared `Proximity.awaitWithin(…)` poll
is the one place this discipline lives for both teleport suites. Re-ran `paper:1.21.4` four
times post-fix — clean.

**Genuine cross-region coverage now exists** (`CrossRegionTeleportSuite`). The earlier note
here claimed `victim.damage(attacker)` was *itself* an illegal cross-region write, so a real
cross-region scenario was deferred. The matrix corrected that: fired on the **victim's** region
thread — where Folia actually delivers a combat event, since the victim is the entity being
mutated — the attacker is only *referenced* for knockback, which Folia permits. So the suite
stages the attacker at world spawn and the cow 512 blocks away (a distinct Folia region via the
fixture's two-chunk `onArena(primary, secondary, …)`), fires the hit on the victim's thread,
and the `TELEPORT:VICTIM` proc must carry the attacker across the boundary via the Sink's
`onEntity(actor)` hop. A naive *inline* teleport would pass every Paper run and throw a
wrong-region access on Folia; this is the one suite that proves the hop. **Green on all five
Folia versions** (1.19.4 → 26.1.2) and all eight Paper versions. Still open (low-risk
follow-ups): refcount the shared spawn-chunk force-load instead of the raced boolean; suite
tags/selection (the CI lane subsets by **version**, which is what the mapping-flip / floor /
ceiling / Folia risk edges actually need); and the same two-chunk pattern could extend to AoE
bystander effects (`DAMAGE_ARC`, `WRATH`) for cross-region coverage beyond teleport.

## Infrastructure this requires

The two-layer gate is sound (honest fresh-PASS reads, a closed stale-jar trap, a
genuine MRJAR soundness gate). Most of the infra below has now **landed**; the
remaining items are flagged open:

- **Done — close the build-cache hole in the drift guards** (highest-leverage): the
  four golden tests read committed artifacts via an untracked repo-root walk, so with
  `org.gradle.caching=true` a hand-edited golden was served `FROM-CACHE` and the drift
  missed by `./gradlew build`. The golden paths are now declared as content-hashed task
  inputs (commit `3a49ebd`).
- **Done — wire the modern live matrix into CI.** `live-matrix.yml`: a curated
  risk-edge subset (floor + the 1.20.5 flip on both platforms + ceiling) on PR, the
  full range from `gradle.properties` nightly on a cron. `run-matrix.sh` is made
  Linux-portable (guarded `caffeinate`) and `fetch-reference.sh` takes a single target
  so a shard pulls only its one server jar. `ci.yml`'s stale TODO is replaced.
- **Done — wire JaCoCo per module** as a blocking gate: `jacocoTestCoverageVerification`
  on `check` with a per-module BRANCH floor (schema/compile/engine/item), so a
  covered-branch regression fails `./gradlew build`.
- **Open — boot-gate the modern half of the shipped artifact** (release only
  legacy-smokes the jar today), **add suite discovery + selection** to `SeTesterPlugin`
  (34 hand-registered `.add()` calls, no filter), **parallelize `run-matrix.sh` boots**,
  and **unify the four drift guards** on one shared golden helper.

## Open decisions (with recommendations)

These are the calls worth confirming before the swarm starts. Each carries a
recommendation; the plan assumes the recommendation unless overridden.

1. **Central `schema.diag.DiagCode` enum, referenced by producers and tests?**
   → **Yes, Phase-0 blocking.** Adopt the enum and split the single `E_PARSE` into
   sub-codes. It eliminates the most pervasive coupling in the suite and lets
   `ExprParserErrorTest` assert `code()` instead of brittle English.

2. **Where do shared fixtures live under the flat layout?** → **A dedicated
   `se/testfx` module** (package `testfx`) on every module's `testImplementation`,
   mirroring the `tester`/`imagegen` "tool-only, not shipped" precedent. Live-only
   helpers (`CombatRig`) stay in `se/tester`.

3. **Drive effect-kind tests from a `FakeEffectCtx` off the spec, or keep Mockito
   per test?** → **Real `FakeEffectCtx`** for the wiring table + conformance test;
   retain Mockito only for the few genuine branch/interaction-order tests (Money
   modes, Potion lifecycle inverse, RemoveSouls). Makes the spec the single source
   and turns ~49 files into ~4.

4. **How to pin user-facing strings?** → **Both, by purpose:** a regenerated render
   golden for the shipped catalogue, explicit in-test inputs for algorithm tests.
   Separates format-spec from shipped-copy.

5. **Property-based testing (jqwik)?** → **Stay on JUnit 5 parameterized tables;**
   trial jqwik **narrowly** for only the fold/clamp algebraic properties
   (order-independence, clamp monotonicity). Don't adopt broadly.

6. **Keep the bespoke `Harness` for live suites?** → **Yes, exactly as-is;** apply
   only the `CombatRig` + listener-teardown + distinct-chunk hygiene refactors.

7. **JaCoCo per module as the safe-deletion ledger?** → **Yes;** treat a
   covered-branch decrease as a blocking PR check alongside the audit's enumerated
   contract list.

8. **Retire the dormant `CatalogEquivalenceTest` (v1→v2, gated off CI) now?** →
   **Retire in the final phase, with a one-line maintainer confirm** that v2 is the
   baseline. It is the one adversarially-risky delete, so it warrants explicit
   sign-off rather than a swarm judgment call.
