# Changelog

All notable changes to StarEnchants are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- **Closed-world JDK-8 API gate (legacy "Gate 2").** `scripts/jdk8-api-gate.sh` +
  `scripts/tools/Jdk8ApiGate.java` walk the downgraded Java-8 (v52) jar with ASM and fail the
  build if any `java.*`/`javax.*` reference is absent from a real JDK 8 — the static net for
  un-shimmable JDK-9+ stdlib APIs that JvmDowngrader passes through silently (they compile and
  downgrade green, then `NoSuchMethodError` on a real 1.8 server, where the reduced live smoke
  can miss them). Embedded in `build-legacy-jar.sh` after the downgrade, so it gates every
  legacy lane: `legacy-smoke.sh` (PR + push) and `build-mega-jar.sh` (release). Public `java/*`
  is a hard failure; JDK-internal `sun/jdk/com.sun` warns (`--strict-internal` to promote);
  `SE_SKIP_JDK8_GATE=1` is a loud, local-only escape hatch.

- Repository foundation: hygiene config (gitignore/gitattributes/editorconfig),
  project agent skills, contributor + development guides, guarded CI workflow,
  PR/issue templates, CODEOWNERS, and conventional-commit git hooks.
- Project structure: ADR decision log, glossary, root agent guide (CLAUDE.md),
  Code of Conduct, Security policy, docs index, Dependabot, release-notes
  config, and a markdown/workflow lint CI.
- Developer reference cache: `scripts/fetch-reference.sh` (downloads + extracts
  per-version Paper/Folia server jars for javap via the PaperMC Fill v3 API,
  1.17.1 → 26.1.2) and a `reference-cache` skill describing the cache + the
  cached Paper/Folia docs (cache itself is local-only / gitignored).
- Approved architecture: `docs/architecture.md` (content-compiler + data-oriented
  runtime, derived via a multi-lens design workshop) and ADRs 0011 (architecture),
  0012 (fully-additive damage), 0013 (single `/se` command root).

## [1.1.1-beta] - 2026-06-26

### Changed

- **One jar for every version.** Minecraft 1.8.9 support now ships *inside* the single
  `StarEnchants-<version>.jar` as a Multi-Release JAR (base = legacy Java-8/v52 tree,
  `META-INF/versions/17/` = modern Java-17/v61 tree, merged by `scripts/build-mega-jar.sh`):
  a 1.8.x server's JVM loads the v52 tree automatically, a 1.17.1+ JVM loads the v61 tree.
  The separate `StarEnchants-<version>-1.8.9.jar` release asset is gone — `release.yml` now
  publishes exactly one jar. Verified live by booting the same jar on craftbukkit-1.8.8
  (JDK 8), Paper 1.17.1 (JDK 17), and Paper 26.1.2 (JDK 25) via `scripts/mega-smoke.sh`.
- **Order-independent cross-version build.** `-Pse.target=legacy` now compiles into a separate
  `build-legacy/` directory, so the modern and legacy trees can never collide — no clobbered
  jar, no overlay-swap incremental contamination, no build-order dependency. `build-mega-jar.sh`
  enforces a soundness gate that refuses to merge any module whose two trees diverge in class
  set (only the plugin qualifies; the era-specific tester stays two artifacts).

### Fixed

- **1.8 empty-hand condition facts.** The legacy main-hand read NPE'd for an empty-handed
  entity (1.8 `getItemInHand()` returns null where modern returns AIR), silently corrupting
  the `helditem` / `actor.type` condition facts; it now normalizes to AIR to match the modern
  path.
- **Test-gate jar selection.** `legacy-smoke.sh` and `run-matrix.sh` now pin the tester jar by
  the canonical project version — a `find | head -1` could pick a stale older-version jar (a
  false PASS) — and guard an empty-array expansion under `set -u` on non-arm64 macOS.

## [1.1.0-beta] - 2026-06-26

### Added

- **Optional Minecraft 1.8.9 jar** — the whole engine, built from the same source
  via the `-Pse.target=legacy` overlay and lowered to Java 8, shipped as a separate
  `StarEnchants-<version>-1.8.9.jar` release asset. Includes a `v1_8_R3` fake-player
  smoke harness (8/8 live on a real 1.8.8 server under JDK 8), full §6 degrade parity
  (ITEM_DAMAGE / heroic-durability / instant-armour-refresh polls + a real NMS
  knockback-resistance hook), and the legacy sound/particle/material resolver fixes.
  The floor stays 1.17.1 — the 1.8.9 jar is optional and separate
  (docs/legacy-1.8.9-codeshare-design.md, and the Legacy 1.8.9 page on the docs site).
- **CI gate for the 1.8.9 lane** — `.github/workflows/legacy.yml` compiles
  craftbukkit-1.8.8 on the runner (Spigot BuildTools, cached) and runs the live JDK-8
  smoke on every push/PR; `release.yml` runs the same gate and publishes the 1.8.9
  asset only when it is green (§11 ownership made mechanical).

### Fixed

- The per-activation chance roll used a `ThreadLocalRandom` overload JvmDowngrader
  cannot stub for Java 8 (it resolves through the JDK-17 `RandomGenerator` interface),
  which would have thrown on every proc on the 1.8 jar; switched to a downgrade-safe
  form, identical on the modern range.

### Removed

- The empty `compat-modern` placeholder module (no sources, no consumers).
