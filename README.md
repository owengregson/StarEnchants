<!--
  ════════════════════════════════════════════════════════════════════════
  StarEnchants — README (developer-facing)
  ────────────────────────────────────────────────────────────────────────
  All player/operator docs (install, configuration, commands, the DSL
  reference, the Enchant Creator) live on the docs site, generated from the
  engine so they never drift. This README keeps only the title card + feature
  cards and developer information.

  GitHub strips <style>/CSS, so the visual styling lives in committed SVGs
  under /assets. To change colors, titles, the logo or icons read README-GUIDE.md.
  ════════════════════════════════════════════════════════════════════════
-->

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="assets/hero-dark.svg">
    <img src="assets/hero.svg" width="860" alt="StarEnchants — a deeply-unified custom-enchantments &amp; armor-sets plugin for Minecraft (Paper 1.17.1 → 26.1.x and Folia)">
  </picture>
</p>

<p align="center">
  <a href="https://github.com/owengregson/StarEnchants/releases/latest"><img src="assets/buttons/download.svg" height="46" alt="Download the JAR"></a>
  &nbsp;
  <a href="https://github.com/owengregson/StarEnchants/releases"><img src="assets/buttons/releases.svg" height="46" alt="All releases"></a>
</p>

<p align="center">
  <b>A single drop-in jar</b> — legendary, open-source cosmic enchantments for your server.<br>
  Custom enchantments, armor sets, crystals, and a full item economy under <b>one config schema</b>, with a built-in migrator.
</p>

<p align="center">
  <code>✦ 51 effects</code> &nbsp;&nbsp; <code>✦ Armor sets &amp; crystals</code> &nbsp;&nbsp; <code>✦ Souls economy</code> &nbsp;&nbsp; <code>✦ Paper + Folia</code>
</p>

<br>

<p align="center"><img src="assets/headers/features.svg" height="54" alt="Features"></p>

<table>
<tr>
<td width="50%" valign="top">
<img src="assets/icons/effect.svg" width="40" alt="Effect engine"><br>
<b>Unified effect engine</b><br>
Enchantments, armor-set bonuses, and crystals/modifiers all feed <i>one</i> engine: <b>51 effects</b>, <b>21 triggers</b>, <b>17 selectors</b>, and a conditions DSL over <b>40 live variables</b> — plus rarity tiers, applies/targets, and per-level options (chance, cooldown, souls, condition).
</td>
<td width="50%" valign="top">
<img src="assets/icons/economy.svg" width="40" alt="Items and economy"><br>
<b>Item &amp; economy systems</b><br>
Enchant books, scrolls (white, holy-white, black, transmog, godly-transmog, randomizer), success dust, soul gems + a souls economy, slot-expander orbs, and item nametags.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<img src="assets/icons/armor.svg" width="40" alt="Armor sets"><br>
<b>Armor sets</b><br>
Full armour sets with a set-completion bonus and an optional matched set weapon, plus Heroic upgrades that boost damage, reduction &amp; durability — each just another source of effects feeding the same engine.
</td>
<td width="50%" valign="top">
<img src="assets/icons/gui.svg" width="40" alt="In-game GUIs"><br>
<b>In-game GUIs</b><br>
Enchanter, alchemist, tinkerer, transmog, and browser menus (enchants, sets, crystals, and the live DSL reference) — open any with <code>/se menu</code>.
</td>
</tr>
<tr>
<td width="50%" valign="top">
<img src="assets/icons/integrations.svg" width="40" alt="Integrations"><br>
<b>Integrations</b><br>
WorldGuard, Towny, Lands, SuperiorSkyblock, Factions, Vault, PlaceholderAPI, Mental, GrimAC, mcMMO, MythicMobs, ItemsAdder &amp; Oraxen — all bundled in the one jar, all optional, none required.
</td>
<td width="50%" valign="top">
<img src="assets/icons/migrator.svg" width="40" alt="Migrator"><br>
<b>Built-in migrator</b><br>
Bring your existing EliteEnchantments, EliteArmor &amp; AdvancedEnchantments configs straight into the unified schema — one command, <code>/se migrate</code>.
</td>
</tr>
</table>

<br>

<p align="center">
  <b>📖 Installation, configuration, commands, the full reference &amp; an interactive Enchant Creator live on the docs site:</b>
</p>
<p align="center">
  <a href="https://owengregson.github.io/StarEnchants/"><b>owengregson.github.io/StarEnchants&nbsp;→</b></a>
</p>
<p align="center">
  <sub>The docs are generated from the engine, so they're always current. <b>The rest of this README is for developers</b> building or contributing to StarEnchants.</sub>
</p>

<br>

<p align="center"><img src="assets/headers/building.svg" height="54" alt="Building from source"></p>

```bash
git clone https://github.com/owengregson/StarEnchants.git
cd StarEnchants
scripts/setup-dev.sh          # prereqs + git hooks + build (idempotent)
./gradlew build               # compile + pure unit tests
```

### Project layout

A flat, single-segment module tree under `se/` — each module's package is one segment (`engine`, `item`, …); sources in `src/`, tests in `test/` (no `src/main/java`). Shaded deps are relocated under their own root so the short roots never collide.

| Module | Responsibility |
| :-- | :-- |
| `schema` | the DSL grammar, `ParamSpec`/types, diagnostics |
| `compile` | YAML → an immutable `Snapshot` (the content compiler) |
| `engine` | the data-oriented runtime: systems, effects, conditions, selectors, triggers, the Sink |
| `item` | the one item-data layer — PDC codec, `ItemView` cache, `WornState`, lore render |
| `feature` | feature interactions, services, the `/se` commands, GUIs |
| `platform` | cross-version resolvers + the Folia-safe scheduling abstraction |
| `integrate` | the bundled, soft third-party integrations |
| `migrate` | the EE / EA / AE config importer |
| `pack` | the config-pack (ZIP snapshot) format |
| `bootstrap` | the Bukkit entry point + composition root (the shaded fat jar) |
| `tester` | the in-server Paper + Folia integration suites |
| `api` · `compat-folia` · `compat-modern` | the public event API + capability shims |

### Verification gate

```bash
./gradlew build          # compile + pure unit tests — always first
scripts/run-matrix.sh    # boot real Paper AND Folia servers across the range, run the live suites
```

A green Paper run says nothing about Folia — both must pass fresh. One universal jar runs the whole range (Paper 1.17.1 → 26.1.x + Folia): a version-agnostic core, boot-time resolvers for version-volatile surfaces, and one Folia-safe scheduling abstraction.

### The docs site is generated from the engine

`website/` is the Docusaurus docs site (auto-deployed to GitHub Pages). Its DSL reference and the web Enchant Creator are driven by `website/src/data/catalog.json`, which `engine.doc.ReferenceCatalogJson` generates from the live registries and a drift test (`ReferenceCatalogDriftTest`) keeps in lock-step — so changing an effect without regenerating fails `./gradlew build`. Regenerate the generated docs with:

```bash
./gradlew :engine:test --tests "*ReferenceDocDriftTest" --tests "*ReferenceCatalogDriftTest" -Dse.doc.regen=true
```

### Where to read more (contributors)

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — workflow, branching model, commit conventions.
- **[docs/architecture.md](docs/architecture.md)** — the self-derived engine design.
- **[docs/decisions/](docs/decisions/)** — the ADRs (the *why* behind every major choice).
- **[docs/development.md](docs/development.md)** — the build / run / see-it-live loop.
- **[docs/glossary.md](docs/glossary.md)** — domain vocabulary.
- **[CLAUDE.md](CLAUDE.md)** + **`.claude/skills/`** — the engineering invariants and hard-won, per-area knowledge.

<br>

<p align="center"><img src="assets/headers/license.svg" height="54" alt="License"></p>

Released under the **GNU Affero General Public License v3.0** (AGPL-3.0) — see [LICENSE](LICENSE).

<br>
<p align="center"><img src="assets/divider.svg" height="22" alt="✦"></p>
<p align="center"><sub><b>STARENCHANTS</b> &nbsp;·&nbsp; made with a little starlight ✦</sub></p>
