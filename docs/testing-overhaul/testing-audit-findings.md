# Test-suite audit — findings (evidence appendix)

> **Status: the evidence behind the overhaul.** A swarm read and judged every test
> file in the repo (184 unit + 40 live), with specialist syntheses and adversarial
> verification of every proposed deletion. This is the reference the
> [overhaul plan](testing-overhaul-plan.md) executes against and the
> [rules](testing-rules.md) cite. The target design is
> [testing-architecture.md](testing-architecture.md).

## Headline numbers

| Metric | Value |
| --- | --- |
| Unit test files judged | 184 (~15.4k LOC) |
| Live suite files judged | 40 (~6.8k LOC, ~34 suites + harness) |
| Production LOC | ~43k / 491 files |
| Unit verdicts | keep 126 · trim 22 · merge (collapse) 34 · rewrite 2 · **delete 0** |
| Live verdicts | keep 31 · trim 12 · merge 4 |
| Live classification | must-be-live 31 · keep-thin-live 13 · redundant-with-unit 3 |
| Dominant problems | mock-heavy 50 · sparse-edge-cases 39 · redundant 39 · framework-test 24 · **two-place-coupling 23** · brittle-string-literal 15 · vacuous 12 |
| String-coupling instances | ~72 |
| Proposed deletions adversarially checked | 34 |
| **Deletions overturned (must keep)** | **27** |
| Confirmed safe removals | 3 (relocate `EePortGenerator`, retire `CatalogEquivalenceTest`, trim `PlatformResolversTest`) + 4 "merge-not-delete" (BreakBlock/Extinguish/FillOxygen/Ignite) |

**Read this first:** the suite is healthier than its size suggests. The damage is
localized (effect-kind boilerplate, ~12 string-coupling hotspots, ~20 sparse edges),
not pervasive. The overturn rate (27/34) is the single most important result: nearly
every effect-kind test is the **sole** guard of its kind's run→Sink wiring, so the
overhaul **collapses, it does not delete**.

## Per-area verdict summary

| Area | Files | Health | Key actions |
| --- | --- | --- | --- |
| **schema** | 10 | Strongest in the repo — asserts diagnostic **codes**, hand-computed, no Bukkit | Fill arithmetic / `HANDLE` / expression-arg gaps; collapse per-error boilerplate |
| **compile-load** | 12 | Good — dominant pattern is "own YAML, round-trip" (test-owned, not coupled) | Re-point `Lang`/`MasterConfig`/`Traks` defaults to `.defaults()`; add `CrystalDefReader`/`SetDefReader`/map-reader coverage |
| **compile-stages** | 12 | Model area — pins IR shapes + diagnostic codes | Collapse builders into `Defs`; add canonical-trigger + overflow + arithmetic-lowering gaps |
| **engine-effects-A** | 27 | Over-dense — ~20 identical pass-throughs; **zero** display-string coupling | Collapse to wiring tables (preserving every assertion); add HealthMod `set`, Immune codes, Fly mob-skip |
| **engine-effects-B** | 22 | Same shape (L–W) + registry/spec seams | Collapse ~10 forwarders; keep branching kinds; fill narrow null-actor gaps |
| **engine-core** | 12 | Healthy, behavior-first | **Sparse:** `runLifecycle` (zero refs), pipeline FORCE-ALLOW/OUT_OF_LEVEL, FactPopulator derived facts |
| **engine-state** | 24 | Among the best — tied to documented invariants | Collapse ~8 TTL stores → 1 contract; add Combo/Immune/Teleblock stores + Trench/Tunnel shapes |
| **item** | 15 | Good — render tests pin the *algorithm*, codecs pin *contracts* | Parameterize round-trips; pull `LoreRenderer` branch tests off the live lane; decouple ~3 constants |
| **feature** | 21 | Healthy on strings — feeds own inputs | Merge `CarrierCombine`+`CarrierService`; add EE-overhaul cold paths (transmog suffix, trak idempotency, `freedBy`) |
| **platform** | 8 | Healthy cores | **Sparse:** `Economy` withdraw/deposit, `ContentReloader` single-flight; **decouple** resolver tests from real `Aliases` |
| **integrate** | 8 | Unusually healthy seams | **Sparse:** `Integrations.active()` lazy-classloading (zero tests), CustomItems routing |
| **bootstrap** | 7 | Good — 5/7 are single-sourced drift/validation guards | `SeCommand` GIVE_TYPES↔give() contract; re-point completion literals; retire dormant `CatalogEquivalenceTest` |
| **migrate-pack** | 6 | Better than most — compiles migrator output through the real compiler | Collapse the 4 giant vocab methods; assert compiled snapshot not rendered YAML; relocate `EePortGenerator` (a tool, not a test) |

## Single-source-of-truth map

The canonical production source every test must **reference instead of re-typing**
(Rules doc [Rule 1](testing-rules.md#rule-1--never-re-type-a-string-production-owns-single-source-of-truth)).
~72 coupling instances trace to these:

| Concept (re-typed today) | Production source | Rule |
| --- | --- | --- |
| Shipped `lang.yml` messages / command output | `Lang.defaults()` (`compile/load/Lang.java`) | Test mechanics with a test-owned temp `lang.yml`; for default-keeps assert `lang.format(key,…) == Lang.defaults().format(key,…)`. |
| Lore enchant/level/crystal colors + unknown label | `LoreStyle.DEFAULT` (`item/render/LoreStyle.java`) | Pass an explicit in-test `LoreStyle`, or assert against `LoreStyle.DEFAULT.enchantColor()`/`.unknownLabel()`; never `§7`/`§8Unknown Enchant`. |
| Master config defaults (colors, command-trigger name) | `MasterConfig.defaults()` | Assert `defaults().lore().enchantColor()`, `defaults().commandTrigger().name()`; split the giant all-defaults pin. |
| Soul-gem color curve / per-item defaults | `SoulGemConfig.defaults()` / `ScrollsConfig.defaults()` | Prove the curve algorithm with a test-owned tier list; assert `.colorTiers()`/`.emptyColor()`. |
| Trak/item default materials | `TraksConfig.defaults()` | Assert the three are mutually **distinct** + carry `{COUNT}`; never `SLIME_BALL`/`MAGMA_CREAM`/`FIRE_CHARGE`. |
| DSL effect mode/channel/param tokens (`give`/`take`, `who`/`mode`/`side`) | each kind's `EffectSpec` (`D.enumOf(...)`/`.param(...)`) | Reference the spec's declared enum/param list; add a spec-conformance test deriving ctx stubs from each `ParamSpec`. |
| Selector/effect spec heads (`AOE`, `VICTIM`, `DAMAGE`) | each kind's `SPEC.head()` | Assert `registry.heads()`/`SPEC.head()`; don't re-type or enumerate the builtin list. |
| Diagnostic codes (`E_PARSE`, `E_COND_TYPE`, `E_DUP_KEY`…) | scattered string literals — **no central registry yet** | **Create `schema.diag.DiagCode`**; producer + test reference the symbol; always assert `code()`, never `message()`. |
| Comparator / string-op symbols (`==`, `contains`) | `Cmp.symbol()` / `StrOp.symbol()` | Derive expected symbols from `Cmp.values()`/`StrOp.values()`. |
| Trigger names (`ATTACK`, `DEFENSE`…) | `TriggerRegistry` / `BuiltinTriggers.registry()` | Assert each dispatch field `== registry.idOf(name)` and `>= 0`. |
| Var-vocabulary slot counts (`15/17/8`) | `BuiltinVars.vocabulary()` + `FactBuffer.MAX_FLAGS` | Assert counts equal #bindings and `flagSlots <= MAX_FLAGS`; never hardcode. |
| Cross-version alias content (`SULPHUR→GUNPOWDER`…) | `Aliases` tables (`platform/resolve/Aliases.java`) | Test the strategy with a **synthetic** map; pin real content in **one** well-formedness test. |
| Import wire prefix | `ImportCode.PREFIX = "SE1:"` | Reference the constant; one golden cross-encoder for the wire contract. |
| PDC namespace / crystal delimiter | `ItemBlobStore.NAMESPACE` / `CrystalItemData.DELIMITER` | Build expected from the constant. |
| Command surface (subcommands, give types, pack actions, flags) | `SeCommand.SUBCOMMANDS`/`GIVE_TYPES`/`PACK_ACTIONS` | Completion tests filter the **same** constant; add a GIVE_TYPES↔give() conformance test. |
| Migrator rendered YAML / TODO comments | `SchemaWriter` / `V2Effects` | Assert values read back from `library.snapshot()` after compiling the output, not the rendered text. |
| Shipped prose artifacts (`dsl-reference.md`, `surface.json`, `catalog.json`, `content/index.txt`) | the `render()`/generator behind a regen flag | **Sanctioned exact-text pin:** keep the committed file as a drift guard regenerated from the single source — never a hand-maintained copy. |

**Top offenders:** `LangLoaderTest` (4 shipped-English messages), `MasterConfigLoaderTest`
(~25-value all-defaults pin), `LoreRendererTest:33/83/93/102` (re-types `LoreStyle.DEFAULT`
instead of passing an explicit style), `SoulGemLoreTest:57-64` (default color curve),
`SeCommandCompletionTest` (re-types `SeCommand` constants), `HandleResolverTest`/`VocabularyResolversTest`
(real `Aliases` content baked into strategy tests), `TraksConfigTest:16-18`, the ~27 effect-kind
files (3-place DSL-token coupling), `MigratorTest:395/397/538/560` (rendered-YAML greps),
`EffectSpecTest:31-32` (self-defined doc/example round-trip — least-harmful variety).

## Collapse map

| Family | From | To |
| --- | --- | --- |
| Effect-kind pass-throughs | 46 files | ~6 data-driven (`EffectSinkWiringTest` fan-out + player-filter + location-gated, `ImmuneTypeCodeTest`, `SpecConformanceTest`) + ~12 retained branching kinds |
| TTL/lifecycle stores | 8 files | 1 `TtlStoreContractTest` (per-store adapter) + thin per-store remainders |
| Protection providers | 5 files | 1 parameterized `ProtectionGateTest` matrix *(optional — third-party mock types differ per row)* |
| Per-error boilerplate | 4 clusters (8× `E_COND_TYPE`, 3× WAIT, 5× selector-syntax, lexer disambiguation) | 4 `@ParameterizedTest` tables asserting `.code()` |
| Carrier book tests | 2 files | 1 `CarrierBooksTest` |
| `CombatDispatchTrigger` | 5 single-assert methods | 1 `@ParameterizedTest` (trident/arrow/melee/snowball/-1) |
| Drift guards | 2 byte-twins | 1 parameterized driver `(relativePath, render fn, regen flag)` |
| Migrator vocab | 4 giant methods (~80 asserts) | ~4 token tables + 1 description/cooldown table (keep the real-compiler integration tests) |
| Stage builders | `def(...)` re-declared in 5 files | 1 shared `Defs` fixture |
| Live redundant | `ImportSuite.rejectsGarbage`, `ContentFormatSuite`, `ItemViewSuite`, `ResolverSuite`+`RuntimeHandlesSuite` | delete / fold / merge into 1 `CrossVersionHandleSuite` |

## Coverage-gap backlog (too sparse)

### High severity

| Gap | Risk | Layer |
| --- | --- | --- |
| `AbilityExecutor.runLifecycle` (HELD/PASSIVE buff lifecycle) | **Zero refs anywhere.** Skipped `stop()`, WAIT leaking into teardown, or a failing teardown aborting siblings leaks buffs on unequip — the largest dark execution path | both |
| Folia **cross-region** correctness | Every live suite stages a single region; the region-ownership invariant the matrix exists for is never exercised | live |
| EraseStage canonical-trigger vocab + §3.7 mask-bit invariant + `>32`/`>64` overflow | A mis-interned trigger routes an ability to the wrong runtime trigger; an off-by-one corrupts masks — both ship green | unit |
| `SetSuite` set-bonus **payoff** | Stops at WornState membership; "wear set → take real DEFENSE hit → REGENERATION applied" is unverified anywhere | live |
| Expression arithmetic (`Arith` ADD/SUB/MUL/DIV, `Neg`) + numeric-arg-as-expression | Full arithmetic with precedence ships with no parser/lowering tests; `sexpr()` can't even render the nodes | unit |
| `Integrations.active()` wiring + lazy-classloading | Zero tests despite being fully unit-testable; a stray API ref `ClassNotFound`s only on a server lacking that plugin | unit |
| Migrator all-TODO `effects: []` YAML safety | A regression to a bare `effects:` key produces unreloadable content; no test compiles an all-unmapped level | unit |

### Medium severity (13)

ComboStore/ImmuneStore/TeleblockStore (no file exists); `ContentReloader`
single-flight/build-threw/swap-failure (needs the deferring backend); `ItemCodecSuite`
omits `added()`/`heroic()`/`setWeaponKey()` in the persist round-trip; holy keep-on-death
on a real `PlayerDeathEvent` + DEFENSE-trigger on a real hit; `LoreRenderer`
heroic/set/weapon/crystal-join branches (server-free, only the live lane touches them);
`EconomyService` withdraw/deposit branches (the `amount<=0 ⇒ true` rule);
EE-overhaul cold paths (transmog suffix no-stack, trak count idempotency, `freedBy`
net-zero slots, `keepFromDrops` filter); concurrent snapshot reader during reload
(torn-read); `CrystalDefReader`/`SetDefReader` diagnostic paths; `ImmuneEffect.typeCode`
all 5 tokens + HealthMod `set` + multi-victim transfer; GIVE_TYPES↔give() structural
drift guard; Trench/Tunnel shape assembly + `AppliedSlot` §I exclusivity.

## Live-suite disposition

Mostly healthy: **31 must-be-live**, 13 keep-thin-live, only 3 redundant. The harness
(`Harness`, `FakePlayers`) is high quality and **kept** — tick-anchored, concurrent
result map, `guard()`-captures-wrong-region-throw, careful netty-4.0/4.1 void channel,
correct `onGlobal → onRegion → onEntity` staging. The runtime suites are notably clean
of string coupling (the only string compares are test-supplied identifiers or real
Bukkit symbol names checked for cross-version presence — keep it that way).

**Disposition:** see the [live strategy](testing-architecture.md#live-server-strategy--keep-it-trim-it-complete-it)
for stays/leaves/added. The two harness-level redesign flags:

- **Cross-suite listener accumulation** — every combat-style suite registers a
  `CombatListener` and never unregisters; within one boot, a later suite's real event
  reaches all of them. Benign today (worn state is keyed by attacker UUID) but a latent
  flake and it defeats event-count assertions. Fix: per-suite `HandlerList.unregisterAll`
  via `CombatRig`.
- **Shared spawn-chunk force-load is a raced boolean** (not refcounted) and all suites
  act at the same spawn location — staging at distinct chunk offsets fixes both this and
  the cross-region gap.

## Infrastructure

The two-layer gate (`./gradlew build` → Paper+Folia matrix) is fundamentally sound and
its best parts must be preserved. The gaps:

**Do well today (do not regress):** the stale-jar trap is closed (all drivers rebuild,
jar pinned to the version parsed from `build.gradle.kts`, not `find | head -1`); honest
PASS verification is first-class (fresh-mtime + literal `PASS`, watchdog with process
reaping, tick-anchored harness); the MRJAR merge has a genuine class-set soundness gate +
bytecode self-check (the strongest infra in the repo); the legacy 1.8 edge is the
best-gated path (dual-compile + JDK-8 API gate + live boot, all in CI).

**Gaps (too sparse):**

1. **The modern live matrix is not in CI.** `ci.yml` carries a stale foundation-phase
   TODO; `run-matrix.sh`/`mega-smoke.sh` appear in no workflow. The entire modern
   behavioural surface (13 targets × 34 suites, all Folia correctness) runs only when the
   maintainer manually runs `run-matrix.sh --all`.
2. **The shipped plugin's modern `onEnable` is never gated in CI.** `run-matrix` boots the
   *tester*, not bootstrap; `release.yml` builds the mega-jar but never modern-boot-smokes
   it (only legacy-smoke runs). The optional 1.8 edge has strictly more automated boot
   coverage than the primary artifact.
3. **Build-cache hole in the drift guards.** With `org.gradle.caching=true`, the four
   golden tests read committed artifacts via an untracked repo-root walk with **no declared
   task inputs**, so a hand-edited golden (no source change) is served `FROM-CACHE` and the
   drift is missed by `./gradlew build`. Only the (bypassable, allowlist-scoped) pre-commit
   hook compensates. **Highest-leverage fix:** declare the golden paths as task inputs.
4. **Throughput discourages running Layer 2** — `run-matrix.sh` boots sequentially despite
   the concurrency guidance, so the only behavioural gate gets skipped.
5. **No suite discovery/selection** — 34 hand-registered `.add()` calls, no per-suite
   filter, so every target runs all 34 and a slim CI lane is impossible.
6. **`SurfaceCatalogDriftTest` hand-rolls JSON** via `StringBuilder` + a bespoke escaper —
   itself a brittle two-place format; use a real serializer.
7. **`imagegen` has no drift gate** — committed tooltip PNGs can silently drift in
   layout/sprites (text can't, it reuses `LoreRenderer`). Add a golden-image diff or
   document them as manual artifacts.

**Component dispositions:** `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`,
`libs.versions.toml`, the regen tasks, `mega-smoke.sh`, `legacy-smoke.sh`,
`build-mega-jar.sh`, `jdk8-api-gate.sh`, `legacy.yml` → **keep** (some with the noted
caveats). `ci.yml` → **rewrite** (add the modern lanes). `release.yml`,
`SurfaceCatalogDriftTest`, `imagegen` → **trim/harden**. `SeTesterPlugin` → **rewrite for
discovery + selection**. `ReferenceCatalogDriftTest` → **merge** into one parameterized
golden guard with `ReferenceDocDriftTest`. `Harness`, `LegacySmokeSuite` (exemplary
fragment-match, mechanics-not-text checks — the template) → **keep**.

## Verification ledger — overturned deletions

Of 34 proposed deletions/vacuous flags, **27 were overturned** by adversarial
re-check. The pattern: each effect-kind test is the **only** test exercising its
kind's `run()`, and its assertions catch a specific, plausible bug nothing else
covers. Representative findings (the full list lives in the audit output):

- `Cure`/`Damage`/`Kill`/`Health`/`Lightning`/`Particle`/`Projectile`/`Fly`/`GiveItem`/
  `Guard`/`Drop`/`Explode`/`Firework`/`Invincible`/`RemovePotion`/`RunCommand`… —
  **keep.** Each is the sole guard of its kind's ctx→Sink wiring: distinct-value args
  catch a transposition, two targets catch a broken fan-out loop, `verifyNoMoreInteractions`
  catches a spurious intent. Several flagged "merge" are safe **only** if the row preserves
  all of these.
- `HealthEffect` — keep: catches `HEALTH` miswired to current-HP `heal/setHealth` instead
  of `addMaxHealth`.
- `ScrollsConfigTest` clamp trio — keep-as-merge: the clamp+reorder is **three independent
  inline copies** (Black/Randomizer/Holy); deleting two drops the sole guard for two
  production copies. (The two `!= null` lines *are* vacuous — drop those.)
- `EffectSpecTest` doc/example — keep: a two-layer delegation to `ParamSpec`, the only test
  asserting those getters; the literals are self-defined, not production-coupled.
- `Towny`/`WorldGuardProvider` — keep: catch a policy inversion / wrong-flag query on a
  security-relevant protection gate; end-to-end is out-of-matrix so the unit is the only CI
  coverage.

**Confirmed safe** (the only no-regret removals): `PlatformResolversTest` (tests a test
double → trim), `CatalogEquivalenceTest` (dormant, gated off CI → retire with sign-off),
`EePortGenerator` (a code-gen tool with zero assertions → relocate out of the test set).
`BreakBlock`/`Extinguish`/`FillOxygen`/`Ignite` → merge **into a table that preserves their
assertions**, not bare delete.

> The lesson encoded as [Rule 5](testing-rules.md#rule-5--never-delete-coverage-re-home-it):
> "looks redundant" is a hypothesis to verify against production code, never a license to
> delete. Default to keep; delete only when the replacement is named and green.
