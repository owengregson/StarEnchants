# Development guide — the change → see-it-live loop

The goal: edit code, and watch the change work on a **real** server (Paper or
Folia, any version in the range) in seconds — not guess from mocks.

## Fresh machine / new-agent setup

One command takes a fresh clone — or a coding agent — to a workable state:

```bash
scripts/setup-dev.sh                 # prereqs + git hooks + build
scripts/setup-dev.sh --with-reference    # also fetch the Paper/Folia jar+doc cache
scripts/setup-dev.sh --full              # everything (fetch + decompile + build)
scripts/setup-dev.sh --help              # all flags & env overrides
```

It is idempotent, non-interactive, and safe to re-run — every step detects
whether it is already done and skips it. It (1) verifies prerequisites, (2)
installs the git hooks, (3) optionally fetches the reference cache, (4)
optionally decompiles it, then (5) runs `./gradlew build` — skipping cleanly
while the Gradle scaffold doesn't exist yet. It exits non-zero only on hard
failures (missing required tool, failed build), never on a skipped optional
step.

**Toolchain expectations.** You need `git` and a JDK ≥ 17 on `PATH`. The version
matrix uses JDK 17 (≤ 1.20.4) and JDK 21 (1.20.5+); CI builds on 21/25. The
bootstrap *warns* (doesn't fail) if 17/21 aren't both installed — install them
before running the live matrix. On non-macOS, point it at them with
`JAVA17_HOME` / `JAVA21_HOME`. See `paper-cross-version`.

**Local-only artifacts** (gitignored — never committed, regenerate locally):

- `reference/` — per-version Paper/Folia server jars (for `javap`) plus cached
  Paper/Folia docs. Regenerate: `scripts/setup-dev.sh --with-reference` (or
  `scripts/fetch-reference.sh` directly — it downloads the whole supported range,
  skipping anything already cached). Decompile to browsable source with
  `scripts/setup-dev.sh --with-decompile` (or `scripts/decompile-reference.sh`).
  See the `reference-cache` and `nms-archaeology` skills.
- `deobf/` — the local analysis workspace (gitignored) for a Cosmic Enchants-style
  reference plus the Vineflower jar the analysis step uses. Populated by hand, never
  committed; it informs *what* to build, never *how*. The `pre-commit` hook
  refuses to commit anything under it.

> The inner loop is **script-driven**, not `run-paper`/`run-folia` Gradle tasks —
> those were never wired. `scripts/run-matrix.sh`, `scripts/mega-smoke.sh`, and
> `scripts/legacy-smoke.sh` build the jar and boot a real server for you. The
> server jars come from the gitignored reference cache (`reference-cache`).

## The fast inner loop

```bash
scripts/run-matrix.sh paper:1.21.4     # rebuild + boot ONE real Paper server, run
                                       # the live suites, read a fresh PASS/FAIL
scripts/run-matrix.sh folia:1.20.6     # same, on a real Folia server
```

`run-matrix.sh` auto-rebuilds the tester fat jar before every run (since #56, so
it never boots a stale jar — pass `--no-build` to skip only right after a fresh
build). Prefer a **single target** over `--all` for the edit→see-it loop: pass
only the `platform:version` whose code path you changed (the scheduler path, say,
needs one Paper + one Folia). To smoke the *shipped* Multi-Release jar itself,
`scripts/mega-smoke.sh` boots the one mega-jar on both eras and
`scripts/legacy-smoke.sh` boots the downgraded 1.8 tester (see the legacy section).

Why not hot-reload? Bukkit plugin reload is unsafe in general (leaks listeners,
tasks, classloaders). The reliable fast loop is rebuild + re-boot a real server;
the scripts make that one command. In-game `/se reload` reloads **config**, never
code.

## The full test gate

```bash
./gradlew build              # compile + unit tests (pure logic) — always first
scripts/run-matrix.sh --all  # boot real Paper AND Folia servers across the
                             # version matrix; run the in-server live suites;
                             # write PASS/FAIL per (platform, version)
```

- Unit tests pin pure logic (DSL parsing, chance/cooldown math, interaction
  precedence, resolver alias maps) — fast, no server.
- The live matrix pins that the engine actually does the right thing to the real
  world. See the `live-server-testing` and `matrix-gate` skills.
- **Read results honestly**: confirm each server's result file is a *fresh*
  PASS; a green banner can hide a server that never booted. A green Paper run
  says nothing about Folia.

## Cutting a release

Releases are automated (ADR-0025). The trigger is a **version bump**: the version
lives in one place — `version` in the root `build.gradle.kts` (stamped into
`plugin.yml`) — and CI publishes when that value on `main` becomes a non-`SNAPSHOT`
version that hasn't been released yet.

```bash
scripts/run-matrix.sh --all          # 1. local pre-release gate: all targets PASS
# 2. bump version in build.gradle.kts, e.g. "0.1.0-SNAPSHOT" -> "1.0.0"
# 3. open a PR with that bump, get CI green, rebase-merge to main
```

On merge, `.github/workflows/release.yml` builds and publishes a **single
Multi-Release mega-jar** — `StarEnchants-<version>.jar` (+ `.sha256`) is the SOLE
artifact, one download that covers every supported version. It carries the modern
Java-17 (class v61) tree the JVM loads on Paper/Folia 1.17.1 → 26.1.x and the
legacy Java-8 (class v52) tree it loads on Minecraft 1.8.x; `scripts/build-mega-jar.sh`
merges the two pre-built trees (base = legacy v52, `META-INF/versions/17/` = modern
v61, `Multi-Release: true`) and self-checks the era seam. There is no separate 1.8
asset. Before that build the workflow runs two gates that BOTH must pass or the
release fails: the unit gate (`./gradlew build`) and the **live 1.8 legacy smoke**
(`scripts/legacy-smoke.sh` boots a BuildTools-compiled craftbukkit-1.8.8 under JDK 8),
so per the §11 ownership commitment the legacy tree never ships without its Gate-4
green. (Landing this wave: `feat/legacy-gate-integrity` adds a `scripts/mega-smoke.sh`
boot step that proves the merged mega-jar enables on both eras before it is published.)

`-SNAPSHOT` versions never release, and an existing `v<version>` is a no-op, so
ordinary merges never publish. After releasing, bump back to the next
`-SNAPSHOT` (e.g. `1.0.1-SNAPSHOT`). `workflow_dispatch` re-runs the check by hand.

The release runs the **unit + live-1.8 gates only** — the live Paper+Folia matrix
needs the gitignored server reference cache, so it stays the local pre-release gate
(run `scripts/run-matrix.sh --all` before the bump).

## Legacy 1.8.9 build

The 1.8 tree is a **separately-compiled** overlay of the same modules, not a
second codebase. `-Pse.target=legacy` swaps each module's overlay and redirects
the build to a disjoint `build-legacy/` buildDir (so the modern `build/` jar is
never clobbered), then compiles against a real Spigot 1.8.8 + `v1_8_R3` jar.

```bash
scripts/build-legacy-jar.sh          # dual-compile + JvmDowngrader 61→52 → the legacy jar
scripts/legacy-smoke.sh              # boot it live on craftbukkit-1.8.8 under JDK 8 (Gate 4)
```

`build-legacy-jar.sh` needs **JDK 8** available (for the closed-world JDK-8 API
gate and the live boot) plus the BuildTools-local craftbukkit-1.8.8 in `~/.m2`.
The shipped mega-jar merges this legacy tree with the modern one; the full
overlay mechanism, gates, and traps are in the **legacy-1.8.9** skill and
`docs/legacy-1.8.9-codeshare-design.md`.

## Version matrix (target)

Paper: 1.17.1 (floor), 1.18.2, 1.19.4, 1.20.6, 1.21.x, 26.1.x (ceiling) — and
both sides of the 1.20.5 spigot→mojang mapping flip.
Folia: 1.19.4+ (Folia's first version), e.g. 1.20.6, 1.21.x, 26.1.x.

Java toolchains are provisioned per version (17 for ≤1.20.4, 21+ for 1.20.5+).
See `paper-cross-version`.

## Debugging a version-specific surprise

Don't guess — read the server. `javap` the actual paper/folia jar for the
failing version (the matrix caches them) and confirm the real field/method.
See the `nms-archaeology` skill.

## Local conveniences

- `scripts/setup-hooks.sh` — enable shared git hooks once per clone.
- Dev servers run under `run/` (gitignored); delete it to reset world state.
- Pass `-Ddisable.watchdog=true` when running heavy suites locally so a slow
  tick doesn't trip the watchdog **(the `run-matrix.sh`/`legacy-smoke.sh`/`mega-smoke.sh`
  scripts already set this on the servers they boot)**.
