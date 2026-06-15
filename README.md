# StarEnchants

A single, deeply-unified custom-enchantments **and** armor-sets plugin for
Minecraft servers — merging the full feature sets of EliteEnchantments and
EliteArmor into one engine, cross-compatible across **Paper 1.17.1 → 26.1.x**
and **Folia**.

> **Status: design & bootstrap.** The architecture is being designed from
> scratch (uniquely for this plugin) before implementation. This repo currently
> holds the project foundation — agent skills, contributor tooling, and docs.
> Plugin code lands after the design is approved.

## What it will do

- **Unified effect engine** — custom enchantments, armor-set bonuses, and
  crystals/modifiers are all *sources of effects* feeding one engine: a rich
  effects DSL (~60 effects), a conditions DSL, trigger types, rarity groups,
  applies/targets, and per-level options (chance, cooldown, souls, condition).
- **Item & economy systems** — enchant books, scrolls (white/holy/black/
  transmog/randomizer), dust, soul gems + a souls system, slot orbs/gems,
  item nametags.
- **Armor sets** — full sets with set bonuses, pairwise set-synergy crystals,
  omni crystals, Heroic upgrades, crafting, and crates.
- **GUIs** — enchanter, alchemist, tinkerer, and enchant-browser menus.
- **Integrations** — WorldGuard, Factions, Towny, PlaceholderAPI, Vault.
- **Migrator** — import existing AdvancedEnchantments and
  EliteEnchantments/EliteArmor configs into the unified schema.

## Cross-version & Folia

One universal jar serves the whole range. The version-agnostic core compiles
against the floor API; version-volatile surfaces resolve through boot-time
resolvers; all entity/world work goes through a scheduling abstraction that is
correct on Paper **and** Folia. See the agent skills in
[`.claude/skills/`](.claude/skills/).

## Repository layout

```
.claude/skills/   project agent skills (cross-version, Folia, testing, conventions)
docs/             contributor + development documentation
.github/          CI workflows, PR/issue templates, CODEOWNERS
.githooks/        shared git hooks (conventional commits, hygiene)
scripts/          developer helper scripts
                  (Gradle multi-module source tree lands with the project scaffold)
```

## Contributing & development

- Workflow, branching model, and commit conventions: [CONTRIBUTING.md](CONTRIBUTING.md)
- The build / run / "see your change live" loop: [docs/development.md](docs/development.md)

## License

See [LICENSE](LICENSE).
