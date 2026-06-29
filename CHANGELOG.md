# Changelog

All notable changes to StarEnchants are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning: [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- **GUI overhaul — the menus are now the primary way to run StarEnchants (ADR-0030).**
  A themed, framed, highly-configurable menu chrome: a `border` picture-frame default,
  named/colour-coded navigation buttons (`« Previous`, `Next »`, `⤶ Go Back`, `✖ Close`),
  a self-describing info pane on every menu, and gentle glint on actionable tiles — all
  tunable per menu from `menus/<name>.yml` (frame, filler, per-button material/name, info
  pane), with `menus/apply.yml` documenting every field. New `/enchants` player hub (the
  benches + browsers, permission-free) closes the gap where admin-gated `/se` left players
  no entry; `/se menu` now opens an Operator Console that can grant books, **mint any item**,
  drill into **armour sets and mint each piece**, mint crystals, apply enchants, browse the
  DSL reference, and reload — all without leaving the game. The Alchemist and Tinkerer
  benches are redesigned. Both the default and cosmic packs ship a documented, themed
  `menus/*.yml` per GUI. Commands remain as aliases.

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

## [1.1.4-beta] - 2026-06-27

### Added

- **Opened enchant books now show the full spec.** The general enchant-book likeness
  (`items/enchant-book.yml`) renders a bold tier-coloured `Name Level` (Roman or Arabic per
  `config.yml` `lore.roman`), the word-wrapped description, an `&a..% Success Rate` /
  `&c..% Failure Rate` pair, and the applies-to kinds grammatically joined (`Sword`,
  `Sword & Axe`, `Boots, Leggings, & Helmet`) plus an `Enchantment` suffix. New placeholders `{TIER_COLOR}`,
  `{SUCCESS}`, `{FAILURE}`, `{KINDS}`, a configurable `wrap` (chars-per-line, colour codes don't
  count toward width), and a colour-aware word-wrap (`item.render.TextWrap`).
- **`/se admin` is now a tier → enchant → level drill-down.** Click a rarity tier to see its
  enchants, click an enchant to see one book per level, click a level to receive that exact
  guaranteed book (the menu stays open to grab several).
- **Tab-completable enchant levels.** `/se give book <player> <enchant> [level]`, `/se book
  <enchant> [level]`, and `/se enchant <key> [level]` now suggest the chosen enchant's valid
  levels (1..max).
- **Level numeral can inherit the tier colour.** `config.yml` `lore.level-color: ""` (blank) makes
  an applied enchant's level render in the enchant's tier colour instead of a fixed colour; the
  `elite-enchantments` pack ships with it blank.

### Fixed

- **Migrated cooldowns were 20× too short.** EliteEnchantments / AdvancedEnchantments author
  cooldowns in *seconds*, but StarEnchants reads the `cooldown` knob in *ticks* — so e.g. Divine
  Immolation imported with a 2-tick cooldown instead of 2 seconds. The migrator now converts
  seconds → ticks (×20, like the REPEATING period), and all 96 shipped `elite-enchantments`
  cooldowns were corrected.
- **Soul gem (and unopened book) ignored the first right-click.** The interact listeners used
  `ignoreCancelled = true`, so a `RIGHT_CLICK_BLOCK` (which arrives cancelled by default-deny /
  protection) silently dropped the gesture until `/se soulmode` was run once. They now run at
  `LOW` priority and read the main-hand item directly, so the first right-click toggles soul mode
  (and opens an unopened book) reliably.
- **Enchant chat messages showed raw `&` codes.** The `MESSAGE` effect's chat / actionbar / title
  output now translates legacy `&` colour codes to `§` (both the modern and 1.8.9 overlays), so a
  proc message renders coloured instead of printing literal `&c&l…`.

## [1.1.3-beta] - 2026-06-27

### Added

- **Enchant descriptions now render on the enchant book.** The general enchant-book likeness
  (`items/enchant-book.yml`) gained a `{DESCRIPTION}` placeholder that expands to the enchant's own
  description — one lore line per description line — so an unapplied book shows what it does, not
  just how to apply it.

### Fixed

- **Tier colours and multi-line descriptions in the lore (EE pack).** Enchant and crystal names in
  the browse/apply/admin GUIs now render in their rarity-tier colour (epic, legendary, …), matching
  the applied-gear lore. Multi-line descriptions now render as multiple lore lines everywhere
  instead of being crammed onto one line: the importers (EliteEnchantments + AdvancedEnchantments)
  join each source description line with a newline rather than a space, the migrator writes them as
  a readable YAML list, and every render site (menu icons + the enchant book) splits on the newline
  (item lore is a list of lines, so an embedded `\n` does not render as a break across the version
  range). All 122 shipped `elite-enchantments` descriptions were regenerated into the multi-line
  form.

## [1.1.2-beta] - 2026-06-26

### Fixed

- **Shipped `elite-enchantments` pack erroring on every affected enchant.** The EE port carried two
  handle tokens no live server can resolve, so every enchant using them logged `E_UNKNOWN_HANDLE`
  and lost the effect: the EE-only `BLEED` particle (no Minecraft equivalent) and the pre-1.13
  `ENDERDRAGON_GROWL` sound. `BLEED` now maps to the real `DAMAGE_INDICATOR` particle (in the pack
  and in the migrator's particle vocabulary, so re-imports stay clean), and `ENDERDRAGON_GROWL` →
  `ENTITY_ENDER_DRAGON_GROWL` is registered in the cross-version `Aliases` (with the `SMOKE_LARGE`/
  `SMOKE_NORMAL` particle renames the same EE vocabulary uses). The `ElitePackValidationTest` now
  resolves material/sound/particle/entity/attribute tokens *strictly* against the floor (1.17.1)
  Bukkit enums, so an unresolvable handle in the shipped pack fails `./gradlew build` instead of
  surfacing only at runtime.

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
