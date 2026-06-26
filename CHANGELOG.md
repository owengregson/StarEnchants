# Changelog

All notable changes to StarEnchants are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

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
