---
name: legacy-1.8.9
description: Use when touching the 1.8.9 legacy overlay, -Pse.target=legacy builds, JvmDowngrader, the mega-jar, or the legacy/mega smoke gates.
---

# Legacy 1.8.9 tree

1.8.9 is a **separately-compiled tree of the same modules**, not a second
codebase (`docs/legacy-1.8.9-codeshare-design.md`). The modern floor stays
1.17.1; the shipped `StarEnchants-<ver>.jar` is a Multi-Release jar carrying both
the modern v61 tree and this downgraded v52 tree, the JVM selecting one at load.

## The overlay mechanism

Each forking module keeps its shared, 1.8-safe logic in flat `src/` and adds two
seam trees under `se/<module>/overlay/{modern,legacy}/` in the **same
single-segment FQN**. The active one is added as a `main` srcDir by
`-Pse.target` (default `modern`) — a whole-file swap, not a diff:
`sourceSets["main"].java.srcDir(if (legacy) "overlay/legacy" else "overlay/modern")`.

- **May diverge (overlay only):** the platform edge — `DispatchSink`/`DispatchSinkFactory`
  (particles via packet, attributes via NMS `GenericAttributes`), `RuntimeHandles`/`RegistrySupport`
  (pre-flattening name→id tables), the NBT `ItemBlobStore`, `EquipSource`, `HeldItem`/`EntityCompat`.
- **Must NOT diverge:** everything in `src/` — `Sink` (fully interned, zero version-volatile referents), the
  effect/condition/selector kinds, `WornState`/`ItemView`, the whole pure core. A same-FQN class may exist in
  `src/` **or** an overlay, never both.

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

## MRJAR soundness — only identical class sets may merge

On a modern JVM the classloader serves `META-INF/versions/17/` for any class
present there and the base (v52) copy otherwise. A class that exists in **only
one** era's tree therefore loads with that era's bytecode and calls shared
classes with the wrong-era signature → `NoSuchMethodError`. So merging is sound
**only** when the two trees have identical class sets (bar the allowlisted
era-exclusive seam pair). `build-mega-jar.sh` enforces this at build time and
**rejects** a divergent tree. The **tester is excluded by design**: its two
trees diverge in era-specific suites, so it is never MRJAR-merged — the modern
matrix (`run-matrix.sh`) boots the modern tester, `legacy-smoke.sh` boots the
downgraded legacy tester, and `mega-smoke.sh` boot-smokes the shipped plugin on
both eras.

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
- **`HeldItem` empty-hand NPE.** 1.8 `getItemInHand()` on an empty hand can
  return null (modern returns AIR); the legacy `HeldItem` overlay must guard it.
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
