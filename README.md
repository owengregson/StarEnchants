<!--
  ════════════════════════════════════════════════════════════════════════
  StarEnchants — README
  ────────────────────────────────────────────────────────────────────────
  GitHub strips <style>, CSS classes and inline style="" from READMEs, so all
  of the visual styling lives inside committed SVG image files under /assets.
  The body copy below is plain Markdown / simple HTML — edit it freely.

  ▶ To change colors, titles, the logo, icons or links, read README-GUIDE.md.
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
  <b>A single drop-in jar</b> that merges <b>EliteEnchantments</b> and <b>EliteArmor</b> into one deeply-unified engine —<br>
  custom enchantments, armor sets, crystals, and a full item economy under <b>one config schema</b>, with a built-in migrator.
</p>

<p align="center">
  <code>✦ 50+ effects</code> &nbsp;&nbsp; <code>✦ Armor sets &amp; crystals</code> &nbsp;&nbsp; <code>✦ Souls economy</code> &nbsp;&nbsp; <code>✦ Paper + Folia</code>
</p>

<br>

<p align="center"><img src="assets/headers/features.svg" height="54" alt="Features"></p>

<table>
<tr>
<td width="50%" valign="top">
<img src="assets/icons/effect.svg" width="40" alt="Effect engine"><br>
<b>Unified effect engine</b><br>
Enchantments, armor-set bonuses, and crystals/modifiers all feed <i>one</i> engine: <b>51 effects</b>, <b>21 triggers</b>, <b>17 selectors</b>, and a conditions DSL over ~40 live variables — plus rarity tiers, applies/targets, and per-level options (chance, cooldown, souls, condition).
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
Full armour sets with set bonuses, pairwise set-synergy crystals, omni crystals, and Heroic upgrades — every one just another source of effects feeding the same engine.
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
Import existing EliteEnchantments, EliteArmor, and AdvancedEnchantments configs straight into the unified schema — one command, <code>/se migrate</code>.
</td>
</tr>
</table>

<br>

<p align="center"><img src="assets/headers/installation.svg" height="54" alt="Installation"></p>

> **Requirements** &nbsp;·&nbsp; Java 17+ &nbsp;·&nbsp; Paper 1.17.1 → 26.1.x (or Folia). Everything else is optional.

1. **Download** the latest `StarEnchants.jar` from the [Releases](https://github.com/owengregson/StarEnchants/releases/latest) page.
2. Drop it into your server's `plugins/` folder.
3. *(Optional)* Drop in any supported plugin — Vault, PlaceholderAPI, WorldGuard, GrimAC, MythicMobs, ItemsAdder, … — and StarEnchants wires up to it automatically. None is required.
4. **Restart** the server — StarEnchants generates its config in `plugins/StarEnchants/`.
5. Tweak the configs, then run `/se reload` — no restart needed.

<p align="center">
  <a href="https://github.com/owengregson/StarEnchants/releases/latest"><img src="assets/buttons/download.svg" height="46" alt="Download StarEnchants.jar"></a>
</p>

<br>

<p align="center"><img src="assets/headers/commands.svg" height="54" alt="Commands and permissions"></p>

Everything lives under one root command — **`/se`**. Tab-completion guides every argument.

| Command | What it does |
| :-- | :-- |
| `/se reload [--dry-run]` | Rebuild the content library off-thread and hot-swap it in (or just validate) |
| `/se enchant <key> [level]` · `/se removeenchant <key>` | Apply or strip an enchant on the held item |
| `/se book` · `crystal` · `gem` · `orb` · `heroic` · `dust` · `nametag` · scrolls… | Mint any item to yourself |
| `/se give <type> <player> …` | Give any item — books, scrolls, dust, gems, orbs, crystals, sets, heroics — to a player |
| `/se menu [name]` | Open an in-game GUI (enchanter, alchemist, tinkerer, transmog, browsers) |
| `/se pack <list\|info\|apply\|export>` | Manage config-pack snapshots of your whole setup |
| `/se migrate <ee\|ea\|ae> <path>` | Import EliteEnchantments / EliteArmor / AdvancedEnchantments configs |
| `/se effects` · `triggers` · `selectors` · `conditions` · `variables` · `list` | Browse the live DSL reference in chat |

> `/se` is gated by `starenchants.admin` (default: **op**); individual GUIs can declare their own permission node.

<br>

<p align="center"><img src="assets/headers/configuration.svg" height="54" alt="Configuration"></p>

Everything lives under `plugins/StarEnchants/`:

```text
config.yml     global settings · souls & economy · integration toggles · storage
content/       the content library — enchants/ · crystals/ · sets/ · tiers.yml
items/         one file per physical item — books, scrolls, dust, gems, orbs, nametags
menus/         in-game GUI layouts
packs/         saved config-pack snapshots (/se pack)
lang.yml       every player-facing message & GUI string
```

Edit by hand, or manage everything from the in-game GUIs. Coming from another plugin? Run `/se migrate` to import your existing enchantments & armor.

<br>

<p align="center"><img src="assets/headers/compatibility.svg" height="54" alt="Cross-version and Folia"></p>

One universal jar runs the whole range — no per-version downloads. Three layers keep it honest:

- **Version-agnostic core** — compiles against the floor API, so the logic is written once.
- **Boot-time resolvers** — version-volatile surfaces (materials, sounds, particles, enchantments, attributes…) resolve by name at startup.
- **Folia-safe scheduling** — all entity/world work flows through one scheduling abstraction, correct on both Paper **and** Folia.

<br>

<p align="center"><img src="assets/headers/integrations.svg" height="54" alt="Integrations"></p>

Every integration ships **in the one jar** and is **soft** — it activates only when the matching plugin is installed, and *none* is ever required ([ADR 0027](docs/decisions/0027-bundled-soft-integrations.md)). The jar carries zero third-party plugin bytecode.

| Area | Plugins |
| :-- | :-- |
| Region / claim gating | WorldGuard · Towny · Lands · SuperiorSkyblock2 · FactionsUUID |
| Economy | Vault |
| Placeholders | PlaceholderAPI — `%starenchants_…%` expansion + chat passthrough |
| Combat / movement | Mental (knockback) · GrimAC · NoCheatPlus · mcMMO (party friendly-fire) |
| Content | MythicMobs (`%victim.mobtype%`) · ItemsAdder · Oraxen (custom-item materials) |

Vulcan, Matrix, and Spartan are detected and logged — they handle engine-applied motion natively. Toggle any integration under `integrations` in `config.yml`. Full details: **[docs/integrations.md](docs/integrations.md)**.

<br>

<p align="center"><img src="assets/headers/building.svg" height="54" alt="Building from source"></p>

Most servers just need the JAR above. To build it yourself or contribute:

```bash
git clone https://github.com/owengregson/StarEnchants.git
cd StarEnchants
scripts/setup-dev.sh          # prereqs + git hooks + build (idempotent)
```

- **[CONTRIBUTING.md](CONTRIBUTING.md)** — workflow, branching model, and commit conventions.
- **[docs/development.md](docs/development.md)** — the build / run / “see your change live” loop.

<br>

<p align="center"><img src="assets/headers/license.svg" height="54" alt="License"></p>

Released under the terms in the [LICENSE](LICENSE) file.

<br>
<p align="center"><img src="assets/divider.svg" height="22" alt="✦"></p>
<p align="center"><sub><b>STARENCHANTS</b> &nbsp;·&nbsp; made with a little starlight ✦</sub></p>
