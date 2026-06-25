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
Bring your existing enchantment &amp; armor configs over from other popular plugins straight into the unified schema — one command, <code>/se migrate</code>.
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
| `/se migrate <ee\|ea\|ae> <path>` | Import configs from another enchantment / armor plugin into the unified schema |
| `/se effects` · `triggers` · `selectors` · `conditions` · `variables` · `list` | Browse the live DSL reference in chat |

> `/se` is gated by `starenchants.admin` (default: **op**); individual GUIs can declare their own permission node.

<br>

<p align="center"><img src="assets/headers/configuration.svg" height="54" alt="Configuration"></p>

Everything StarEnchants reads lives under `plugins/StarEnchants/` as plain YAML. Edit a file and run `/se reload`, or manage it all from the in-game GUIs.

```text
config.yml     global toggles · combat caps · souls · crystals · heroic · lore · integrations · reload
content/       the content library
  ├─ tiers.yml      rarity tiers (color · weight · glint)
  ├─ enchants/      one file per enchant
  ├─ crystals/      one file per socketable crystal
  └─ sets/          one file per armor set
items/         one file per physical item — books · scrolls · dust · soul gem · orbs · nametag
menus/         in-game GUI layout overrides
packs/         saved config-pack snapshots (/se pack)
lang.yml       every player-facing message & GUI string
```

> **Keys are path-derived.** An enchant at `content/enchants/lifesteal.yml` has the key `lifesteal`; organize files into subfolders freely — the key is just the path under `content/`.

### config.yml

The cross-cutting settings. Defaults shown:

```yaml
features:                     # master on/off switches
  enchants: true
  sets: true
  crystals: true
  heroic: true
  slots: true
  souls: true
  scrolls: true
combat:
  max-bonus-damage: -1.0      # cap on total additive bonus damage; <0 = uncapped
  max-bonus-reduction: -1.0   # cap on total additive damage reduction; <0 = uncapped
  pvp: true
  pve: true
slots:
  base: 9                     # enchant slots on a fresh item
souls:
  deposit-on-any-kill: true   # the active soul gem banks a soul on any kill
crystals:
  slots: 1                    # crystal sockets per item
  max-stack: 16
heroic:
  max-outgoing-factor: 4.0    # ceiling on stacked Heroic damage multipliers
lore:
  enchant-color: "&7"
  level-color: "&f"
  crystal-color: "&b"
  roman: true                 # render levels as I, II, III…
  unknown-label: "&8Unknown Enchant"
integrations:
  protection: true            # gate effects behind region/claim plugins
  economy: true               # route money effects through an economy plugin
  named: {}                   # per-integration off switch, e.g. named: {worldguard: false}
reload:
  re-resolve-players: true    # re-apply worn state to online players after a reload
  auto-seconds: 0             # >0 = periodic auto-reload interval
command-trigger:
  enabled: true               # register a player-facing /cast command for COMMAND-trigger enchants
  name: cast
```

### Anatomy of an enchant

One enchant is one file under `content/enchants/`. A real example:

```yaml
# content/enchants/lifesteal.yml
tier: uncommon
display: "&cLifesteal"
description: "Heal when you strike an enemy."
trigger: ATTACK              # a single trigger, or a list: [ATTACK, BOW]
applies-to: [SWORD, AXE]     # which item kinds it can be applied to
group: combat               # shared cooldown / suppression group
levels:
  1: { chance: 25, effects: [ { MODIFY_HEALTH: { amount: 2 } } ] }
  2: { chance: 35, effects: [ { MODIFY_HEALTH: { amount: 4 } } ] }
  3: { chance: 45, effects: [ { MODIFY_HEALTH: { amount: 6 } } ] }
```

**Root keys** — `display`, `description` (string or list of lines), `tier`, `trigger` (one or many), `applies-to`, `group`, `repeat` (period in ticks for a `REPEATING` enchant), and the apply-relationship keys `requires` / `blacklist` / `removes-required`.

**Per-level keys** — `chance`, `cooldown`, `soul-cost`, `condition`, and `effects`. Any per-level key may instead be set once at the root as the default for every level. Levels need not be contiguous, so non-linear sets like `1, 3, 5` are fine.

A richer level — a gated, cooled-down crit with a targeted effect, particle, and message:

```yaml
  3:
    chance: 50
    cooldown: 100             # ticks
    condition: "%victim.health% < 6"
    effects:
      - { DAMAGE_MOD: { side: attack, mode: flat, amount: 8 } }
      - { PARTICLE: { particle: CRIT, count: 18 } }
      - { MESSAGE: { text: "&4&lExecuted!" } }
```

### Effects, triggers, selectors & conditions

The engine ships **51 effects**, **21 triggers**, **17 selectors**, and a **conditions DSL** over **40 live variables**. Browse the full, always-current reference in chat — `/se effects`, `/se triggers`, `/se selectors`, `/se conditions`, `/se variables` — or read [`docs/reference/dsl-reference.md`](docs/reference/dsl-reference.md).

**Effects** accept parameters two equivalent ways:

```yaml
effects:
  - "IGNITE:60"                                              # terse — positional args
  - { IGNITE: { duration: 60 } }                             # verbose — named args
  - { POTION: { effect: REGENERATION, level: 1, duration: 60, who: "@Self" } }
```

Two parameter names are special in the verbose form: `who:` chooses the **selector** (who the effect lands on) and `wait:` inserts a delay, in ticks, before that effect runs.

**Selectors** are written `@Name{arg=value}` — `@Self`, `@Attacker`, `@Victim`, `@Aoe{r=3}`, … Each effect has a sensible default target if you omit `who:`.

**Conditions** are boolean expressions over `%scope.name%` variables, combined with `&& || ! ( )` and the operators `== != < <= > >=`, `contains`, and `matchesregex`:

```yaml
condition: "%victim.health% < 6 && %self.onground%"
condition: "%victim.helditem% contains \"SWORD\""
```

A condition may also end in a flow clause — `: %stop%`, `: %force%`, or `: +15 %chance%` (nudge the activation roll) — otherwise it simply gates whether the enchant fires.

### Rarity tiers — tiers.yml

```yaml
default-tier: common
tiers:
  common:    { color: "&7",   weight: 10, glint: false }
  rare:      { color: "&b",   weight: 30, glint: false }
  legendary: { color: "&6",   weight: 50, glint: true  }
  mythic:    { color: "&c&l", weight: 60, glint: true  }
```

Each tier id (the map key) carries a lore `color`, a GUI sort `weight`, and whether items of that tier `glint`. Tiers are cosmetic metadata, never part of an item's identity.

### Crystals

A crystal is a levelless socket bonus — its own item likeness plus one trigger/effects block:

```yaml
# content/crystals/ember-crystal.yml
tier: uncommon
display: "&6Ember Crystal"
material: BLAZE_POWDER
name: "&6Ember Crystal"
lore: [ "&7Socket into a sword or axe.", "&660%&7 on hit to ignite your foe." ]
trigger: ATTACK
applies-to: [SWORD, AXE]
chance: 60
effects:
  - { IGNITE: { duration: 60 } }
  - { PARTICLE: { particle: FLAME, count: 8 } }
```

Two crystals can be **merged** into one paired socket; how many sockets an item has is set by `crystals.slots` in `config.yml`.

### Armor sets

A set declares its pieces, a single **completion** threshold, and the bonus that fires once the full set is worn — plus an optional matched **weapon** bonus:

```yaml
# content/sets/inferno.yml
display: "&cInferno"
description: "Burn attackers and shrug off fire when the full set is worn."
complete: 4
armor:
  lore: [ "&cInferno Set &7(4 pieces)" ]
  pieces:
    helmet:     { material: DIAMOND_HELMET,     name: "&cInferno Helm" }
    chestplate: { material: DIAMOND_CHESTPLATE, name: "&cInferno Chestplate" }
    leggings:   { material: DIAMOND_LEGGINGS,   name: "&cInferno Leggings" }
    boots:      { material: DIAMOND_BOOTS,      name: "&cInferno Boots" }
  trigger: DEFENSE
  chance: 45
  cooldown: 100
  effects:
    - { IGNITE: { duration: 80, who: "@Attacker" } }
    - { POTION: { effect: FIRE_RESISTANCE, level: 1, duration: 100, who: "@Self" } }
weapon:                       # optional — fires only while the full set is worn
  material: DIAMOND_SWORD
  name: "&cInferno Blade"
  trigger: ATTACK
  chance: 60
  effects: [ { IGNITE: { duration: 60, who: "@Victim" } } ]
```

### Items

Each physical item is one file under `items/`, tagged by `type:` and carrying its `material` / `name` / `lore` plus a few type-specific economy fields:

| Item | `type:` | Notable fields |
| :-- | :-- | :-- |
| Enchant book | `enchant-book` | `success-lore`, `destroy-on-fail`, `{ENCHANT}` / `{LEVEL}` tokens |
| White / holy-white scroll | `white-scroll` · `holy-white-scroll` | protect an item from a black scroll |
| Black scroll | `black-scroll` | `success-chance` |
| Transmog / godly transmog | `transmog-scroll` · `godly-transmog` | re-skin an item's appearance |
| Randomizer scroll | `randomizer-scroll` | `min-percent`, `max-percent` |
| Unopened book | `unopened-book` | `min-success`, `max-success`, `{TIER}` |
| Success dust | `dust` | `min-bonus`, `max-bonus` |
| Soul gem | `soul-gem` | `souls-per-kill`, `souls-per-mob`, `soul-colors`, sounds & particles |
| Crystal | `crystal` | `success-chance`, `consume-on-fail`, `extractor` |
| Slot orb | `slot-orb` | `orb-amount`, `hard-cap` |
| Heroic | `heroic` | `percent-damage`, `percent-reduction`, `durability`, `material-upgrades` |
| Nametag | `nametag` | `blacklist` (banned substrings) |

These files describe each item's **look** (material, name, lore) and its economy knobs; an item's identity and counters live in its persistent data, rendered back into lore automatically.

### Menus

GUI layouts are operator-tweakable, while click behavior, icons, and input slots stay in code. Drop a file under `menus/` to override only the chrome:

```yaml
# menus/apply.yml
title: "&dStarEnchants"
filler: "BLACK_STAINED_GLASS_PANE"
# also optional: rows (1–6), prev-slot, next-slot, back-slot, close-slot   (-1 hides a nav button)
```

Overridable menus: `apply`, `enchants`, `sets`, `crystals`, `reference`, `transmog`, `enchanter`, `alchemist`, `tinkerer`, `admin`.

### Config packs

`/se pack export <name>` snapshots your whole config surface — `config.yml`, `lang.yml`, `content/`, `items/`, `menus/` — into a portable ZIP under `packs/`. `/se pack apply <name>` backs up your current config, swaps the pack in, and reloads transactionally; `list` and `info` round out the set. Packs make a setup easy to share, version, or roll back.

### Language & messages — lang.yml

Every player-facing string is a dotted key in `lang.yml` (`command.*`, `apply.*`, `menu.*`, `soul.*`, …) using `&` color codes and `{TOKEN}` placeholders. Any key you omit falls back to the built-in English default, so you only override what you want to change.

### Reloading

`/se reload` rebuilds the entire content library **off-thread**, then swaps it in atomically — and only if it compiles clean. A broken edit keeps the running config live and reports diagnostics instead of taking the server down. `/se reload --dry-run` validates and reports without swapping.

### Migrating from another plugin

Coming from another enchantment or armor plugin? `/se migrate <ee|ea|ae> <path>` reads that plugin's config, maps it onto the unified DSL, and writes ready-to-review YAML into `plugins/StarEnchants/migrated/`. It never overwrites your live content and flags anything it can't translate cleanly, so you review the output before promoting it into `content/`.

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
