---
name: legacy-1.8.9
description: Use when touching the 1.8.9 legacy overlay, -Pse.target=legacy builds, JvmDowngrader, the mega-jar, or the legacy/mega smoke gates.
---

# Legacy 1.8.9 tree

1.8.9 is a **separately-compiled tree of the same modules**, not a second
codebase (`docs/legacy-1.8.9-codeshare-design.md`). The modern floor stays
1.17.1; the shipped `StarEnchants-<ver>.jar` is a Multi-Release jar carrying both
the modern v61 tree and this downgraded v52 tree, the JVM selecting one at load.

## The overlay mechanism (ADR-0044 — seams, not same-FQN twins)

Each forking module keeps its shared, 1.8-safe logic in flat `src/` and adds two
trees under `se/<module>/overlay/{modern,legacy}/`. The active one is added as a
`main` srcDir by `-Pse.target` (default `modern`):
`sourceSets["main"].java.srcDir(if (legacy) "overlay/legacy" else "overlay/modern")`.
**The unit of era variation is the seam, not the file** — the same-FQN whole-file
swap of ADR-0036 is superseded (except the two bindings twins below):

- **Seams (the norm).** A version-specific capability is a **seam interface in
  `src/`** (`Hands`, `ParticleFx`, `ItemStateStore`, `EquipSource`, `ActorProbe`,
  `DropControl`, `Projectiles`, `VanillaStats`, `AnvilRename`, …) with two
  **era-exclusive** impls under **era-unique FQNs** (`ModernHands`/`LegacyHands`,
  `ModernDispatchSink`/`LegacyDispatchSink`, `ModernParticleFx`/`LegacyParticleFx`,
  `PdcItemStateStore`/`NbtItemStateStore`, …). Parity is a per-era `javac` fact
  (`implements`), not a convention, and drift can't slip past the merge gate. The
  impls are **constructor-injected** from the composition root — shared consumers
  name only the interface, never an era class (the `EraTreeGateTest` G1-c check
  enforces this).
- **Shared base + era leaf** stays valid where most of a class is era-neutral
  (`engine.sink.DispatchSinkBase` + `ModernDispatchSink`/`LegacyDispatchSink`;
  `feature.combat.EquipListener` shared + the era armour-change feeders). The base
  is in `src/`, so it lands in both class sets automatically.
- **Asymmetric seams** (one era works, the other stubs) are a shared default plus
  one override: `VanillaStats.NONE` / `AnvilRename.UNSUPPORTED` in `src/`, the real
  `ModernVanillaStats` / `ModernAnvilRename` in `overlay/modern`.
- **The ONLY same-FQN twins are the two composition-only bindings:**
  `bootstrap.compat.EraBindings` (implements the shared `EraServices` — the whole
  era wiring manifest, absorbing the former Wiring/Bridges/Targets/Commands) and
  `platform.resolve.HandleLookups`. Content rule (gated): **construction and
  one-line delegation only** — no `if/for/while/switch/try`, no mutable fields.
  Real per-era logic is extracted to era-exclusive classes (`LegacyTargets`,
  `LegacyEnchantResolver`, `LegacyGearPoll`), NOT inlined into a binding.
- **Must NOT diverge:** everything else in `src/` — `Sink`, the kinds,
  `WornState`/`ItemView`, the pure core. A same-FQN class lives in `src/` **or** an
  overlay, never both (a seam interface and its `Modern*`/`Legacy*` impls have
  *different* names, so this holds).

## `-Pse.target=legacy` → a disjoint buildDir

Legacy compiles redirect `buildDir` to `build-legacy/` (root `build.gradle.kts`),
so the modern `build/` jar is never clobbered and Gradle's incremental compiler
can't mix classes across the overlay swap. The merge (`build-mega-jar.sh`) reads
the modern jar from `build/` and the downgraded legacy jar from `build-legacy/`
in any order. `build-legacy/` is gitignored.

## The language wall — JvmDowngrader + JDK 8

The base tree targets **Java 8 (class v52)** — a 1.8.x server runs only on JDK 8.
`scripts/build-legacy-jar.sh` compiles at 17 then lowers the assembled jar
**61→52** with JvmDowngrader (pinned 1.3.6), shading its stdlib-API + `util`
runtime helpers under the `se_jdg` root so the jar is self-contained. Records,
sealed types, and switch-expressions lower cleanly; a JDG version bump is a gated
change requiring a fresh live smoke.

## MRJAR soundness — a DERIVED gate (ADR-0044), not a hand allowlist

On a modern JVM the classloader serves `META-INF/versions/17/` for any class
present there and the base (v52) copy otherwise. A class that exists in **only
one** era's tree, but is *reachable* from the other era's bytecode, would load
with the wrong-era signature → `NoSuchMethodError`. `scripts/tools/MegaJarGate.java`
(an ad-hoc-compiled ASM tool inside `build-mega-jar.sh`, like `Jdk8ApiGate`) proves
soundness **derived from the tree**, replacing the old hand
`ALLOW_ERA_EXCLUSIVE`(`_PREFIX`) allowlists:

- **P1** era-exclusivity is derived from the overlay dirs (a class under exactly
  one `overlay/<era>/` is legitimately one-era); a divergent class with no such
  source fails.
- **P2** a constant-pool walk asserts the modern jar references no legacy-exclusive
  FQN and vice-versa (the jar-level restatement of the compile-time fact).
- **P3** a legacy-excluded module (`:integrate`) is derived from the module set, not
  a hard-coded prefix.
- **P4/P5** the two bindings twins (`EraBindings`, `HandleLookups`, declared in
  `build-mega-jar.sh`'s `SE_MEGA_BINDINGS`) must be present in both trees, and the
  base `EraBindings` must reference `v1_8_R3` while its `versions/17` copy must not
  (the era-direction check, generalising the old `Commands` grep).
- **P6** the non-class resource trees match by SHA-256.

The fast companion `EraTreeGateTest` runs in every `./gradlew build` (G1-a twin
set = bindings; G1-b bindings composition-only; G1-c no src names an era class;
G1-d `EraBindings implements EraServices`), so a broken seam fails locally. The
**tester is excluded by design**: its era-specific suites live in `src/` includes
(not overlay dirs), so P1 rejects it exactly as before — `run-matrix.sh` boots the
modern tester, `legacy-smoke.sh` the downgraded legacy tester, `mega-smoke.sh`
boot-smokes the shipped plugin on both eras.

## The gates

| Gate | Script | Proves |
| --- | --- | --- |
| Legacy compile (1 + 1b) | `build-legacy-jar.sh` (`-Pse.target=legacy`) | overlay **and** shared `main` link against the real Spigot 1.8.8 + `v1_8_R3` jar — a 1.8-absent symbol is a `javac` error, not a runtime crash |
| JDK-8 API gate (2) | `jdk8-api-gate.sh` (embedded in `build-legacy-jar.sh`) | closed-world ASM scan of the downgraded v52 jar against a **real JDK 8 baseline** — an un-shimmable JDK-9+ `java.*`/`javax.*` reference fails here, not at runtime |
| Live legacy smoke (4) | `legacy-smoke.sh` | the downgraded tester boots on a real craftbukkit-1.8.8 under JDK 8 and the reduced in-server suite fresh-PASSes |
| Mega smoke | `mega-smoke.sh` | the merged mega-jar ENABLES on both eras (base v52 on 1.8, versions/17 v61 on Paper) with no wrong-era leak |

`jdk8-api-gate.sh` is the single chokepoint for the language wall, so it gates
every legacy-producing lane (`legacy-smoke.sh` on PR+push, `build-mega-jar.sh` on
release). `SE_SKIP_JDK8_GATE=1` is a loud, local-only escape hatch.

## Known traps

- **`Material.CLOCK`-class constants break the 1.8 compile.** Flattening renamed
  hundreds of Materials; a hard-referenced modern constant is absent on the 1.8
  jar. Resolve by name through the resolver, never a compile-time constant.
- **Empty-hand NPE.** 1.8 `getItemInHand()` on an empty hand can return null
  (modern returns AIR); the `LegacyActorProbe` / `LegacyHands` impls must guard it.
- **One legacy gear poll, not three.** 1.8 has no `PlayerArmorChangeEvent` /
  `PlayerItemDamageEvent`, so equip-refresh, ITEM_DAMAGE, and heroic-durability are
  all driven by the single `feature.trigger.LegacyGearPoll` (one repeating task, one
  5-slot scan). Its **fixed per-slot subscriber order is load-bearing** — ITEM_DAMAGE
  fires before any heroic restore, and the poll records the post-restore durability so
  next tick sees no phantom delta. Do not re-split it into per-concern polls.
- **Stale-jar selection.** `build-legacy/libs` can retain an old-version
  `-legacy.jar` a bare `find | head -1` would grab by directory order — a false
  PASS on old code. The scripts pin the **exact current-version filename**.
- **`ARCH_PREFIX` under `set -u`.** The Apple-Silicon `arch -x86_64` prefix array
  is often empty on x86/CI; expand it as `${ARCH_PREFIX[@]+"${ARCH_PREFIX[@]}"}`
  so bash 3.2 doesn't error on an unbound empty array.
- **Chance rolls must be injectable.** JvmDowngrader makes
  `ThreadLocalRandom.nextDouble(double)` unstubbable in the legacy smoke — the roll
  must come from an injected supplier (the pipeline already reads `act.chanceRoll()`),
  or every proc throws on the real 1.8 jar.

See `paper-cross-version` for the modern range, `matrix-gate` for the full gate,
and `docs/legacy-1.8.9-codeshare-design.md` for the derivation.
