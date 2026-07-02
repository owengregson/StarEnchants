# StarEnchants v3 re-architecture — build directives

**Status: DELIVERED (all sections).** These are now historical build directives —
every section shipped across the v1.1.x–v1.2.x beta line; see ADRs 0016–0035 for
what landed. Kept as the record of the wave's intent, not an open worklist. It
supersedes the config-v2 design (ADR 0016) and the earlier parity-correction
notes where they conflict. Decisions here were settled 2026-06-16/17 from the
Cosmic Enchants-style parity fact-check (`docs/parity/parity-audit.md` + workflow
`wf_96f2aaeb-89d`) and the user's per-item direction.

Rule of thumb baked into every section: **deobf/ informs WHAT a feature is and
HOW it should behave for players, never HOW we build it.** Architecture stays
self-derived. Folia-correct or it doesn't ship. One item-data layer. Atomic
config swap. Version-agnostic core, version-specific edges. Adding a feature is
local. Flat `se/<module>/src` layout. Per-increment discipline (branch →
`./gradlew build` → live Paper+Folia matrix → adversarial review → PR →
rebase-merge → sync main).

---

## 0. Build order (foundation-first)

Effects can't be finalized before selectors/variables exist; most items depend on
triggers + conditions. So:

| # | Increment | Gate it unblocks |
| --- | --- | --- |
| 1 | **Unified conditions / variables / selectors** (§A) | every ability + the effect taxonomy |
| 2 | **Trigger listeners + lifecycle** (§B) — wire the 10 unwired + REPEATING + COMMAND; HELD/PASSIVE start+stop | item triggers, passives, auras |
| 3 | **Consolidated effect taxonomy** (§C) — maximal collapse + new primitives | all content |
| 4 | **Items** (§D–I) — soul gem, crystals + multi-crystal, heroic, slots, scrolls, dust, books, nametag | give commands, GUIs |
| 5 | **GUIs** (§K) — shared framework + full menu set | merchant/browse flows |
| 6 | **Master config.yml + lang.yml + items/ + menus/ surfacing** (§L) + **auto-doc** (§M) | configurability, docs |
| 7 | **Integrations** (§N) — Factions/Towny/Lands/Skyblock/mcMMO/anticheat/ItemsAdder/Oraxen/MythicMobs/PAPI/EliteBosses/Vault/… | — **LAST, after all of 1–6** |

Each numbered increment is itself split into shippable PRs; never one mega-PR.

---

## A. Unified conditions / variables / selectors  *(extend, don't greenfield)*

`se/` **already** has the Cosmic Enchants-class engine (tokenizer→AST→typed IR→flyweight
evaluator; `&& || ! ()` + six comparators; `%scope.name%` vars; `ConditionResult{Flow,chanceDelta}`
with `Flow CONTINUE/STOP/FORCE/ALLOW`). It is under-built — this increment fills
it out to **full Cosmic Enchants-style parity**:

- **Operators**: add `contains` (pipe-OR membership) and `matchesregex`.
- **Flow/chance authoring grammar**: make the `Flow`/`chanceDelta` types
  *reachable* — a compiler path that emits `%stop% / %force% / %allow%` and
  `±N %chance%` from condition clauses (today they exist but nothing emits them).
- **Variables**: port the full Cosmic Enchants-style vocabulary (~60 static + dynamic + computed) — and
  **populate the runtime `FactBuffer`** (today it's defined but never filled by
  production code; this is the load-bearing fix).
- **Selectors**: grow from 5 → the full Cosmic Enchants-style set (AllPlayers, NearestPlayer,
  PlayerFromName, EntityInSight, BlockInDistance, Block, EyeHeight, Add, and the
  mining shapes Trench/Tunnel/Vein), plus `@Aoe` target-filter + limit args.
- **Flag representation**: the `FactBuffer` flag space is one 64-bit long
  (`MAX_FLAGS=64`); the full Cosmic Enchants-style boolean vocab approaches/exceeds it → **widen the
  representation** (promote some booleans to number/string facts, or a second word).

**Reach (for now): abilities only.** Conditions/selectors/variables stay wired to
enchant/set/crystal ability levels (`AbilityDef.conditionExpr`). *Not* yet item
triggers, heroic apply, GUI actions, or command gates — that widening is a later
pass. The vocabulary is built full now; the *surface* widens later.

---

## B. Triggers — wire everything advertised, add lifecycle

`se/` declares 19 triggers in a closed vocabulary; only 9 have listeners
(ATTACK, DEFENSE, MINE, KILL, FALL, FIRE, INTERACT, INTERACT_LEFT, INTERACT_RIGHT).

- **Add real listeners** for the 10 unwired: **DEATH, BOW_FIRE, FISHING, EAT,
  ITEM_DAMAGE, BREAK, HELD, PASSIVE, TRIDENT,** and a **distinct BOW** (today BOW/
  TRIDENT hits fold into ATTACK; give them their own trigger id while still
  attributing projectile→shooter).
- **HELD + PASSIVE need a start *and* stop path.** Today `EquipListener` only
  refreshes the WornState cache. Activating on equip/hold and **deactivating** on
  unequip/swap-away (the Cosmic Enchants-style start/stop lifecycle). This is the deactivation half
  the engine currently lacks.
- **REPEATING**: promote into the vocabulary and actually schedule it
  (`RepeatStore` + `repeat` field exist but nothing arms the recurring task).
  Folia: per-entity/region repeating task, cancel on quit/unequip.
- **COMMAND**: add (does not exist today) — a trigger fired by a configured command.

All entity/world work via the `Scheduling` abstraction (Folia traps: AoE,
cross-entity steal/teleport, repeating auras).

---

## C. Effects — maximal collapse + new primitives

**Maximal collapse** chosen: parameterized primitives over many near-identical
kinds. Keep behavior greppable, but no duplicated logic.

**Merges (current redundancy → one primitive):**

| New primitive | Params | Replaces |
| --- | --- | --- |
| `VELOCITY` | x,y,z / direction, magnitude | THROW, LAUNCH (identical today), KNOCKBACK |
| `MODIFY_MONEY` | amount (signed), `from`/`to` transfer mode | GIVE_MONEY, TAKE_MONEY, STEAL_MONEY[_PERCENT] |
| `MODIFY_EXP` | amount (signed), transfer mode | GIVE_EXP, TAKE/STEAL_EXP |
| `MODIFY_HEALTH` | amount (signed/range), transfer mode | HEAL, HARM, lifesteal, STEAL_HEALTH |
| `MODIFY_FOOD` | amount (signed) | FEED (+ remove) |
| `DURABILITY` | amount (signed), item/all | ADD_DURABILITY, ADD_DURABILITY_ITEM, REPAIR, DAMAGE_ARMOR |
| `DAMAGE_MOD` | side (attack/defense), mode (add/flat) | ADD_DAMAGE, REDUCE_DAMAGE, FLAT_DAMAGE, FLAT_REDUCE *(stays in the additive fold; see §F for heroic)* |
| `MESSAGE` | channel (chat/actionbar/title) | MESSAGE, ACTIONBAR, TITLE |
| `SPAWN_ENTITY` | type, count, ttl, health, owner | TNT, FIREBALL, SPAWN (+ params) |
| `LIGHTNING` | visual flag, `:0` | LIGHTNING, STRIKE |

The **transfer mode** (`from`/`to` + a selector) is how STEAL_* collapses into
`MODIFY_*` — one effect, a selector decides who loses and who gains.

**New primitives to add** (cannot be compiled from the current 40):
`TELEPORT` (family: to-target / behind / drops), `BREAK_BLOCK`, `SET_BLOCK`,
`DROP_ITEM` / `GIVE_ITEM` / `REMOVE_ITEM`, `SUPPRESS_ENCHANT` (covers
DISABLE_ENCHANT / DISABLE_GROUP / DISABLE_TYPE — the gate + stores already exist, only the
effect kind + a `suppress()` Sink intent are missing), `SET_VAR` / `INVERT_VAR`
(pairs with the §A variable system), `FIREWORK`, `MOVEMENT_SPEED`, `WALKER`,
`IGNORE_ARMOR`, `KNOCKBACK_CONTROL`, `GUARD` / `KEEP_ON_DEATH` / `INVINCIBLE`,
`SPAWN_ARROWS` / `PROJECTILE`, `REMOVE_SOULS`.

**Compilations (do NOT add as effects — express from base set once §A selectors
exist):** SMITE = LIGHTNING@Aoe + DAMAGE_MOD@Aoe; BUTCHER = KILL@Aoe{monsters};
WRATH = DAMAGE_MOD@Aoe + POTION@Aoe; DODGE / FALL_DAMAGE / HUNGER_LOSS / DURABILITY-
negate = CANCEL_EVENT under a trigger + chance; DROP_HEAD = DROP_ITEM:head.
Each new Cosmic Enchants-style effect must pass the "is this a distinct primitive or a
compilation?" test before becoming a kind.

POTION uses **`level`** (1-based), never amplifier.

---

## D. Soul gem  *(own config file in `items/`)*

A **distinct item**, right-click to toggle soul mode on/off (not a stamp on any
item, not the current `/se soulmode` command).

- **Acquisition: deposit on *any* kill** (configurable per-kill amount, optionally
  per-mob-type), regardless of soul-mode state. *(New mechanic — no original has
  per-kill deposit; this is intentional.)* Plus keep give-command, combine, split.
- **Spending: gated by soul mode ON.** Souls power soul-cost enchant activation
  (pipeline gate, after the cancellable activation event) and the `REMOVE_SOULS`
  effect. Soul mode is the "spend switch."
- **Combine**: drag gem-onto-gem sums souls into a new gem (anvil sound). **Split**:
  `/se` split subcommand. Never auto-split.
- **Config knobs** (soul-gem config file): material, name, lore, format/placeholders
  (`{AMOUNT}`, `{SOUL-COLOR}`), **configurable soul-color tiers** (a Cosmic Enchants-style original's were
  hardcoded), **multiple** particles-while-active, **multiple** particles-on-activate,
  **multiple** particles-on-deactivate, messages on activate/deactivate, message on
  soul use, per-kill deposit amount (+ optional per-mob map), sounds on/off.
- Folia: deferred PDC writes (keep the current `SoulLedger` approach, by gem UUID).
- Anti-dupe: block placing the gem; guard the crafting grid (a Cosmic Enchants-style `SoulgemCraftEvent` analog).

---

## E. Crystals + multi-crystal  *(own config; `crystals:` section in master config)*

- **Per-item crystal slots, default 1** (configurable in the master config
  `crystals:` section). **Separate ledger** from enchant slots. No crystal-slot
  expander for now.
- **Physical crystal item**, minted via give command. **Drag-apply** onto gear
  (gesture, not a menu) with a **configurable success chance**, **consume-on-fail**,
  and apply/remove **messages & sounds** (all standard, configurable).
- Keep `se/`'s order-preserving crystal **list** (fixes a Cosmic Enchants-style last-of-type collapse).
- **Multi-crystal merge**: drag crystal-onto-crystal (SWAP_WITH_CURSOR), **pairs
  only**. Overlapping effect types **SUM** their magnitudes; distinct effects both
  carry. Extraction returns the multi-crystal **as a whole**. *(Brand-new — Cosmic Enchants-style
  "multi crystals" were hand-authored static files; we do it at runtime.)*
- No new armor sets, no omni gems this wave.

---

## F. Heroic  *(own config file)*

- **Percent multipliers** (Cosmic Enchants-style shape) — reshape `HeroicStat` from flat to percent.
  - **Conflict with ADR-0012 (fully-additive fold):** resolve by making heroic a
    **separate bounded multiplicative stage applied after** the additive enchant/
    set/crystal fold — outgoing `×(1 + Σheroic_damage%)`, incoming
    `×(1 − Σheroic_reduction%)`, clamped. The fold itself stays additive. **Write a
    short ADR amending 0012's scope** ("the fold is additive; heroic is a distinct
    bounded multiplicative modifier").
- **Durability**: configurable **%-chance to cancel item damage** — wire the
  listener (the field is stored but inert today).
- **Success**: configurable **randomized min/max range**, small.
- **On failure: consume the upgrade item, never harm the gear.**
- **Scope**: applies to **armor AND weapons**; **not set-bound** (any set/piece).
- **On success** (drag-drop onto a piece): change the armor **material** (configured
  per slot/type in heroic config) + add **"heroic piece" lore**.
- **Reduction scope** *(default — confirm on review)*: entity/PvP damage only,
  configurable to all-causes (a Cosmic Enchants-style original applied to all causes, flagged as a likely bug).

---

## G. Enchant relationships  *(general mechanism, not "heroic-tier"-specific)*

Three enchant-yml fields, evaluated at apply time (book + menu + any apply path,
**except admin force-give**):

- **`requires:`** — list of prerequisite enchants that must already be on the item.
  **Level-aware: each prerequisite must be present at a level ≥ the level of the
  enchant being applied.**
- **`blacklist:`** — enchants this one cannot coexist with. **Bidirectional**
  (neither may be applied while the other is present).
- **`removes-required:`** *(boolean)* — when `true`, on successful apply **remove
  all `requires` enchants** and keep this (superior) one; when `false`, keep them.
  *(No list form — it's all-or-nothing.)*

Slot interaction: when an upgrade removes its prerequisites, **net slot change is
zero** — the upgrade does **not** need a free slot. ("Heroic-tier" enchants are
just the canonical use of this; the fields are general.)

---

## H. Slots

- **1 slot per enchant** (no per-enchant cost). *(Already true in `se/`.)*
- **Base slots default 9**, configurable. *(Today hardcoded 6 → change.)*
- **Max-expander item**: grants a **configurable +N per expander**; expanders
  **stack**, clamped to a **hard universal maximum** total-slot cap defined in the
  expander's config. (e.g. base 9, +N expanders, up to the configured ceiling.)
- Persist per-item slot count to PDC (today `added` is never populated).

---

## I. Other economy items  *(each its own config file in `items/`)*

| Item | Behavior |
| --- | --- |
| **Holy / death scroll** | Survive death once: configurable per-item save chance, **consumed on the survived death**, includes offhand, respects keepInventory. |
| **Plain guard scroll** *(default: keep as separate item — confirm)* | The existing enchant-fail protect (save gear when an enchant apply fails). |
| **Black scroll** | Extract one enchant from gear → an enchant book (configurable use-random-chance). |
| **Transmog scroll** | Reorder enchant lore; applied-name suffix. **Godly transmog** → via the reorder GUI (§K) + interact lock. |
| **Randomizer scroll** | Reroll a book's success %. |
| **Upgrade orb / slot expander** | §H — raise an item's slot count by the configured +N. |
| **Slot gem** | +1 slot (subject to the §H hard cap). |
| **Item nametag** | Rename gear via chat; blacklisted-words guard. |
| **Dust** | **Single dust type** (no master dust). Two dusts **combine → sum %** (never split). A dust **applied onto an enchant book raises that book's success %** (clamped ≤ 100). Add sound + particle feedback. |
| **Unopened / randomized book** | Tier-scoped. Right-click (INTERACT) → a **concrete enchant book of a random enchant from that tier**, with random level + success %. |

---

## J. Commands — "the StarEnchants way"

Unified under **`/se`** subcommands (not the Cosmic Enchants-style separate root commands), one
`starenchants.admin` permission node, live Bukkit tab-completion (`@enchants`,
`@tiers`, `@items`, `@crystals`, `@sets`, `@players`, `@dusts`). Implement all
give-commands as SE subcommands:

- `/se give book <player> <enchant> <level> [success]`, `givebook`-random by tier,
  `give unopened <tier>`.
- `/se give item <player> <item-id> [args]` — dispatcher for **all** item types
  (soul-gem `<amount>`, scrolls, orb/slot-gem, nametag, dust, crystal `<id>`).
- `/se give set <player> <set> <piece|weapon>`, `/se give heroic`, `/se give upgrade`.
- Inverse: `/se removeenchant` / `unenchant`.
- Reference surface (overlaps §M): `/se effects | conditions | triggers | selectors |
  variables | list`.

Inventory-full guard on all give-commands (overflow → drop at feet, message).

---

## K. GUIs — one shared framework

Build **one reusable config-driven menu framework** (per-GUI files under `menus/`):
Button = item + click-action, paged, filler panes, back/nav, close handling,
title color/truncation, Folia open-hop. Then the **full menu set**:

Enchanter · Alchemist · Tinkerer · Enchants browser (tier→enchant) · Effects/
reference browser (§M) · Godly Transmog reorder · Armor-sets browser + preview ·
Crystals/Modifiers browser · Admin browser (all enchants).

Crystal **apply / extract / multi-crystal-merge stay drag gestures** (not menus).
All GUI actions permission-gated. **Excluded**: GKits, crates, custom crafting.

---

## L. Config + lang layout  *(one atomic reload)*

```
plugin data dir/
├── config.yml          # sectioned master: slots:, souls:, crystals:, heroic:, lore:, integrations:, reload:
├── lang.yml            # shipped fully populated (all messages)
├── content/            # ABILITIES (compiled into the Library snapshot): enchants/ sets/ crystals/ tiers.yml
├── items/              # NEW top-level: item "physical likeness" configs (soul-gem, scrolls, dust, orb, nametag, books)
└── menus/              # GUI layout configs (one per menu)
```

- Anything that produces an **Ability** → `content/` (compiled Snapshot).
- Cross-cutting knobs → `config.yml`. Per-feature likeness → `items/`. GUI layout →
  `menus/`. Messages → `lang.yml`.
- **Reload**: master config + `items/` + `menus/` + `lang.yml` load as **parallel
  immutable references swapped together in the same `/se reload` transaction** as the
  content Library — "one reload reloads everything" holds. This is the plugin's first
  settings→snapshot path; build it transactional from the start.

---

## M. Auto-doc dictionary

A Markdown dictionary **generated from plugin code** (the §C effect registry, §A
`BuiltinVars`/`BuiltinSelectors` + ParamSpecs, §B trigger vocabulary) so new
effects/conditions/variables/selectors appear automatically. Regenerate at build;
**drift-guard with a test** (generated == committed). Feeds the in-game reference
browser GUI (§K) and the `/se effects|conditions|…` commands (§J). Scope: full
reference (effects + triggers + conditions + variables + selectors).

---

## N. Integrations — LAST

Factions, Towny, Lands, SuperiorSkyblock, mcMMO, anticheat, ItemsAdder, Oraxen,
MythicMobs, PlaceholderAPI (expansion + passthrough), EliteBosses, Vault.

**Packaging (ADR 0027, supersedes 0017):** every integration is **bundled in the one
core jar and active out of the box when its plugin is present — never required.** Each
plugin API is `compileOnly` (never shaded) and each bridge loads only when its plugin is
detected, so the single jar carries them all with no hard dependency. They live in
`se/integrate`; the protection ones implement `ProtectionProvider`, Vault implements
`EconomyProvider`, both union with anything registered externally through the
`ServicesManager`. (Mental — ADR 0026 — follows the same soft model but lives in
`feature.combat` as it shares the live knockback store.)

**Delivered (all of §N):**

- *Protection* — WorldGuard, Towny, Lands, SuperiorSkyblock2, FactionsUUID (`ProtectionProvider`).
- *Economy* — Vault (`EconomyProvider`).
- *PlaceholderAPI* — `%starenchants_…%` expansion + chat passthrough.
- *Mental* — knockback coordination (ADR 0026).
- *Anti-cheat* — NoCheatPlus exemption (reflective) + GrimAC flag-cancel (compiled against GrimAPI; the
  Mental+SE+Grim combo); Vulcan/Matrix/Spartan detected + logged (closed APIs, native handling).
- *mcMMO* — party friendly-fire (no SE combat effects between party members).
- *MythicMobs* — `%victim.mobtype%` condition variable.
- *ItemsAdder / Oraxen* — `itemsadder:…` / `oraxen:…` custom-item materials in item + menu configs.

All bundled in the one jar and soft (each plugin API `compileOnly`, never required); unit-tested against the
real APIs where mockable, jar-verified to contain **zero** plugin-API bytecode; end-to-end per plugin is
verified out-of-matrix (no integration plugin runs on the live matrix). See `docs/integrations.md`.

EliteBosses/EliteMobs is intentionally out of scope (no usable public API; the user dropped it).

---

## Out of scope (this whole wave)

No custom crafting, no crates/lootboxes, no GKits, no web marketplace/panel, no
loot/mob-drop population, no custom-weapon item system. Do not
replicate Cosmic Enchants-style bugs (multiplicative enchant stacking — except the deliberate
heroic stage §F; last-of-type crystal collapse; silent unknown-effect no-ops;
fail-open conditions). No `/ee`+`/ea` aliases, no Splodgebox watermark.

> **[Reversal]** StatTrak was later brought into scope and **shipped** as
> `feature/trak` (four traks) in v1.1.5 — it is no longer out of scope (ADR-0007
> update).

## Defaults I chose (flag on review if wrong)

1. STEAL_* via a `from`/`to` transfer mode on `MODIFY_*` (not a separate TRANSFER primitive).
2. Heroic reduction = entity/PvP only, configurable to all-causes.
3. Two distinct save items: plain guard scroll (enchant-fail) **+** holy death scroll.
4. Reference browser = full (effects + triggers + conditions + variables + selectors).
5. Tier comes from the in-file `tier:` field; drop the `group:` field; flatten tier
   subfolders (resolving the v2 subfolder-vs-field tension — per the parity-corrections memory).
