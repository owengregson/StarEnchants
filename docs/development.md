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

> The Gradle build and run tasks referenced here land with the project scaffold
> (after the architecture is approved). This document defines the intended loop
> and the commands so the tooling is ready the moment the build exists. Anything
> not yet wired is marked **(planned)**.

## The fast inner loop

```bash
./gradlew runPaper           # (planned) boot a dev Paper server with the freshly
                             # built plugin auto-installed; edit → re-run → see it
./gradlew runFolia           # (planned) same, on a real Folia server
```

These use the `run-paper`/`run-folia` Gradle tasks (xyz.jpenilla.run-paper),
which download and cache the server jar, install the freshly-built StarEnchants
jar, and boot — so "see your change" is a single command. Pick the version with
a property, e.g. `./gradlew runPaper -Pmc=1.21.4` **(planned)**.

Why not hot-reload? Bukkit plugin reload is unsafe in general (leaks listeners,
tasks, classloaders). The reliable fast loop is rebuild + re-run the dev server;
the run task makes that quick. In-game `/se reload` reloads **config**, never
code.

## The full test gate

```bash
./gradlew build              # compile + unit tests (pure logic) — always first
<integration matrix>         # boot real Paper AND Folia servers across the
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

On merge, `.github/workflows/release.yml` runs `./gradlew build :bootstrap:jar`,
creates the `v<version>` tag, and publishes a GitHub Release with the universal
plugin jar (`StarEnchants-<version>.jar` + `.sha256`) and auto-generated notes.
`-SNAPSHOT` versions never release, and an existing `v<version>` is a no-op, so
ordinary merges never publish. After releasing, bump back to the next
`-SNAPSHOT` (e.g. `1.0.1-SNAPSHOT`). `workflow_dispatch` re-runs the check by hand.

The release build runs the **unit gate only** — the live Paper+Folia matrix needs
the gitignored server reference cache, so it stays the local pre-release gate (run
it before the bump).

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
  tick doesn't trip the legacy watchdog **(the run tasks set this)**.
