# Architecture Decision Records

Each ADR captures one significant decision: its **context**, the **decision**, and
the **consequences** — so the *why* survives long after the choice is made.

## Index

| # | Title | Status |
|---|---|---|
| [0000](0000-adr-template.md) | ADR template | — |
| [0001](0001-merge-scope.md) | Merge EliteEnchantments + EliteArmor into one plugin | Accepted |
| [0002](0002-version-targets.md) | Target Paper 1.17.1–26.1.x + Folia from one universal jar | Accepted |
| [0003](0003-unified-effect-engine.md) | One unified effect engine; everything is an effect source | Accepted |
| [0004](0004-modern-dsl.md) | Modern compile-at-load DSL (expression conditions, variables, selectors) | Accepted |
| [0005](0005-item-data-pdc.md) | Item-data on PersistentDataContainer; drop NBT-API | Accepted |
| [0006](0006-config-and-migration.md) | Fresh unified config schema + migrator (configs and live items) | Accepted |
| [0007](0007-modernize-freely.md) | Modernize freely; adopt AE engine-level improvements only | Accepted |
| [0008](0008-cross-version-and-folia.md) | Cross-version resolvers + Folia scheduling abstraction | Accepted |
| [0009](0009-git-workflow.md) | Git workflow: feature branch → PR → rebase-merge | Accepted |
| [0010](0010-architecture-derivation.md) | Concrete architecture is self-derived via a design workshop | Accepted |
| [0011](0011-engine-architecture.md) | Content-compiler + data-oriented runtime (see `docs/architecture.md`) | Accepted |
| [0012](0012-damage-stacking.md) | Damage stacking is fully additive | Accepted |
| [0013](0013-command-surface.md) | Single `/se` command root (drop `/ee`, `/ea`) | Accepted |

## Process

- New decision → copy `0000-adr-template.md` to `NNNN-short-title.md`, fill it in, add a row above.
- Statuses: **Proposed** → **Accepted** → (later) **Superseded by NNNN** / **Deprecated**.
- Never edit an Accepted ADR's decision; supersede it with a new ADR and link both ways.
- Keep them short. One decision per ADR.
