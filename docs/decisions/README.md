# Architecture Decision Records

Each ADR captures one significant decision: its **context**, the **decision**, and
the **consequences** — so the *why* survives long after the choice is made.

## Index

| # | Title | Status |
|---|---|---|
| [0000](0000-adr-template.md) | ADR template | — |
| [0001](0001-merge-scope.md) | Combine custom enchants and armor sets into one plugin | Accepted |
| [0002](0002-version-targets.md) | Target Paper 1.17.1–26.1.x + Folia from one universal jar | Accepted |
| [0003](0003-unified-effect-engine.md) | One unified effect engine; everything is an effect source | Accepted |
| [0004](0004-modern-dsl.md) | Modern compile-at-load DSL (expression conditions, variables, selectors) | Accepted |
| [0005](0005-item-data-pdc.md) | Item-data on PersistentDataContainer; drop NBT-API (item-NBT migration descoped) | Accepted |
| [0006](0006-config-and-migration.md) | Fresh unified config schema + migrator (configs and live items) | Accepted |
| [0007](0007-modernize-freely.md) | Modernize freely; adopt Cosmic Enchants-style engine-level improvements only (StatTrak later reversed into scope) | Accepted |
| [0008](0008-cross-version-and-folia.md) | Cross-version resolvers + Folia scheduling abstraction | Accepted |
| [0009](0009-git-workflow.md) | Git workflow: feature branch → PR → rebase-merge | Accepted |
| [0010](0010-architecture-derivation.md) | Concrete architecture is self-derived via a design workshop | Accepted |
| [0011](0011-engine-architecture.md) | Content-compiler + data-oriented runtime (see `docs/architecture.md`) | Accepted |
| [0012](0012-damage-stacking.md) | Damage stacking is fully additive | Accepted (full scope restored by 0037) |
| [0013](0013-command-surface.md) | Single `/se` command root (drop separate legacy roots; `/enchants` player root added by 0030, `/star` alias) | Accepted |
| [0014](0014-content-loader-and-reload.md) | Content loader + transactional reload | Accepted |
| [0015](0015-spigot-floor-fakeplayer-deferred.md) | Spigot-floor fake-player deferred; floor covered by non-fake suites | Accepted |
| [0016](0016-content-format-v2.md) | Content format v2 — verbose effects, item defs (scale/tier superseded by v3-directives explicit-levels) | Accepted |
| [0017](0017-protection-addon-packaging.md) | Protection/region integrations ship as separate add-on plugins | Superseded by 0027 |
| [0018](0018-spigot-floor-fakeplayer.md) | Spigot-floor fake-player harness — combat suites now run floor-wide | Accepted |
| [0019](0019-dust-success-bonus-combining.md) | Dust carrier kind — success-bonus combining (the last deferred carrier) | Accepted |
| [0020](0020-ae-migrator-dsl-coverage.md) | AdvancedEnchantments migrator — selector, condition, and effect DSL coverage | Accepted |
| [0021](0021-heroic-multiplicative-stage.md) | Heroic as a bounded multiplicative stage (amends 0012's scope) | Superseded by 0037 |
| [0022](0022-held-passive-lifecycle-and-command-trigger.md) | HELD/PASSIVE start-stop lifecycle + the COMMAND trigger (§B tail) | Accepted |
| [0023](0023-config-packs.md) | Config packs (swappable whole-config presets) | Accepted |
| [0024](0024-exotic-effect-ports.md) | Expression-valued effect args + the exotic Cosmic Enchants-style effect ports | Accepted |
| [0025](0025-automated-releases.md) | Automated releases — version-bump-driven GitHub Release of the universal jar | Accepted |
| [0026](0026-mental-knockback-coordination.md) | Coordinate KNOCKBACK_CONTROL with the Mental knockback plugin (reflective core edge) | Accepted |
| [0027](0027-bundled-soft-integrations.md) | Integrations bundled in the core jar — soft, compileOnly, optional; two bridge shapes (supersedes 0017) | Accepted |
| [0028](0028-documentation-site.md) | GitBook-style documentation site, generated catalog, GitHub Pages | Accepted |
| [0029](0029-web-creator-and-import-codec.md) | Web enchant creator + `SE1` paste-code import (`/se import`) | Accepted |
| [0030](0030-gui-overhaul.md) | GUI overhaul — themed menu chrome, hubs, and GUI-first management | Accepted |
| [0031](0031-heroic-vanilla-stats.md) | Heroic armour/durability as real vanilla stats (amends 0021's armour slice) | Accepted |
| [0032](0032-heroic-cross-plugin-combat-parity.md) | Heroic cross-plugin combat parity (amends 0031's display slice) | Accepted |
| [0033](0033-unified-message-catalogue.md) | Unified message catalogue — one YAML source (`se/compile/resources/lang.yml`), drift-guarded | Accepted |
| [0034](0034-crystals-rework.md) | Crystals rework — global likeness, multi-ability crystals, N-stack merge, 100% apply | Accepted |
| [0035](0035-crystal-stackability-and-multi-identity.md) | Crystal stackability, multi-crystal identity, whole-entry extraction (amends 0034) | Accepted |
| [0036](0036-legacy-overlay-and-mrjar.md) | Legacy 1.8.9 via srcDir overlay + a single Multi-Release jar (soundness-gated) | Accepted |
| [0037](0037-heroic-additive-fold.md) | Heroic percents join the additive damage fold (supersedes 0021) | Accepted |
| [0038](0038-addon-api.md) | Add-on API — a curated public SPI on `:schema`, `ServicesManager` discovery (drops the `:engine` re-export) | Accepted |

## Process

- New decision → copy `0000-adr-template.md` to `NNNN-short-title.md`, fill it in, add a row above.
- Statuses: **Proposed** → **Accepted** → (later) **Superseded by NNNN** / **Deprecated**.
- Never edit an Accepted ADR's decision; supersede it with a new ADR and link both ways.
- Keep them short. One decision per ADR.
