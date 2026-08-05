# Matrix 13 — Support Items

Source codex: `10-armor-sets.md` Part C (crystals, heroic armour, mastery shards),
`08-enchant-economy-items.md` (§1 books, §4 orbs, §5 dust, §6 scrolls, §9 transmog,
§10 randomization + EMP), `12-pets.md` Part C (rare candy), `16-item-builders.md`
Part B.1 + appendix (decoded names/lore for the loot-side builders). 27 entries in
codex order within three groups. Behavioral authority is the codex; this doc records
the decomposition onto the pack's **item-likeness surface** — the 22 `type:` keys the
compiler's item loader accepts, one instance of each per pack.

This is the §C decomposition pass commissioned by **R-QC12 + R-QC47**. The headline
result: the claim carried in `deferred-content.md` (that the codex "records no verbatim
name or lore for the crystal item, the upgrade item or the shard") is **false on all
three** — every one of them is recorded verbatim, along with its material. The
port-choice comments in `packs-src/cosmic-pack/items/crystal.yml` and `heroic.yml` are
settleable from source and should be replaced with the strings below.

## Family-level facts

- **Which pack ships what.** The cosmic pack currently carries **4 of the 22 likeness
  types** (`crystal`, `heroic`, `mask`, `pet`); the signature pack carries 22. Where an
  entry below says "the pack ships …", the value quoted is the **signature pack's**, used
  as the comparison baseline — the cosmic pack has no file for that item at all. That
  absence, not any wrong value, is the bulk of the gap this doc closes.
- **One likeness per type per pack.** The engine mints every instance of a support item
  from a single YAML likeness with brace tokens; the jar mints a **separate item per
  variant** (six rarities of dust, eight sets of crystal, four sets of shard, two orb
  types, seven tiers of mystery book). Every entry below records which jar dimension the
  engine folds into a token and which one has nowhere to go.
- **The apply gesture is uniform.** Nearly every jar support item is applied by holding
  it on the **cursor** and clicking it onto the target inside the player's own inventory
  screen (view title `container.crafting`); the click is cancelled and the item consumed
  on commit. That is exactly the engine's drag-n-drop apply, and the shared footer line
  `§7Drag n' Drop onto item to enchant.` / `…to apply.` / `…to extract.` is already the
  pack's convention. Right-clicking most of them prints a "drag n' drop instead" help
  line — the engine has no help-on-right-click hook (gap `ITEM_HELP_HINT`).
- **Consume-before-roll is the house gamble.** Armor Crystal, Anti-M-Kit Crystal and
  Heroic Upgrade all decrement the cursor **before** rolling; a failed roll destroys the
  item and the target is untouched. The engine's crystal likeness has no roll at all
  (ADR-0034, confirmed R-QC53) and the heroic likeness keeps `success-min/max` with
  `destroy-on-fail: false` — see D-13-1.
- **Three different world gates, all infrastructure.** Crystal apply and Heroic Upgrade
  apply compare the player's world to `worlds[0]` **and** compare the *player's name* to
  the string `"world_end"` (an obvious typo for the world name); crystal combining
  compares the world object to `worlds[0]`; mastery-shard apply is blocked in worlds
  whose name starts with `world_duels`. All three are interaction-layer world conditions,
  off by default in the port, exactly as the set/outpost gates in matrix/10.
- **Text-as-storage.** Every enchant-economy item stores its numbers in **lore text** and
  parses them back out (books, orbs, dust, black scrolls, randomization scrolls). The
  engine stores state in PDC and renders lore from state (project invariant, one
  item-data layer) — infrastructure class, not a deviation row. The consequence worth
  recording: in the jar any lore rewrite (a transmog scroll, a rename) silently corrupts
  those values, so a transmog + dust interaction that looks like a bug in play is
  structural.
- **Dupe blacklisting.** Crystals, shards, heroic upgrades, black scrolls, rare candy and
  applied books stamp an external item-id and blacklist it on use. The engine's PDC
  identity + one-shot state replaces it wholesale; not replicated, not a ledger row.
- **Shared economy feedback template** (books, orbs, dust, transmog, white scroll):
  success = `Sound.LEVEL_UP` vol `1.0F` pitch `0.75F` + entity effect `VILLAGER_HAPPY` +
  `SPELL` speed `0.8F` count `85` + `MAGIC_CRIT` speed `0.65F` count `30`;
  failure = `Sound.LAVA_POP` `1.0F`/`0.75F` + `VILLAGER_ANGRY` + `LAVA` speed `0.35F`
  count `20`. Crystal/heroic/shard use their own cues, listed per entry. Both particle
  bursts land at **+2.0 Y**, not +1.0 Y, because the location object is mutated in place
  by the first call — cosmetic, not ported.
- **Era (family-wide).** Every material below is a 1.7/1.8 name and needs the alias
  resolver in both directions: `EMPTY_MAP`→`MAP`, `INK_SACK:0`→`INK_SAC`,
  `INK_SACK:11`→`YELLOW_DYE`, `INK_SACK:7`→`LIGHT_GRAY_DYE`, `INK_SACK:2`→`GREEN_DYE`,
  `INK_SACK:6`→`CYAN_DYE`, `SULPHUR`→`GUNPOWDER`, `FIREBALL`→`FIRE_CHARGE`,
  `EYE_OF_ENDER`→`ENDER_EYE`, `REDSTONE_TORCH_ON`→`REDSTONE_TORCH`,
  `GOLD_AXE`/`GOLD_SWORD`→`GOLDEN_AXE`/`GOLDEN_SWORD`. `NETHER_STAR`, `GHAST_TEAR`,
  `SUGAR`, `GLOWSTONE_DUST`, `PAPER`, `BOOK`, `CHEST`, `RED_MUSHROOM` and the leather
  armour set are spelled identically on both eras. Sounds `GLASS`, `LEVEL_UP`, `FIZZ`,
  `ORB_PICKUP`, `LAVA_POP`, `VILLAGER_YES`, `BAT_TAKEOFF`, `WITHER_IDLE` are all legacy
  names.
- **Out of scope (ruling R9, economy is note-only):** the trader NPC that buys set pieces
  and shards for crystals, the enchanter NPC that sells mystery books, and the alchemist
  that combines books/dust. Their exchange rates are recorded here where they set an
  item's numbers (they fix the crystal's 20 % and the anti-M-Kit's stack-size-as-percent)
  and nowhere else.

## Entries

### Armor Crystal (`items/crystal.yml`)

- **codex:** `10-armor-sets.md` §C.1, §A.2 (per-set description lists), §A.7 (what it
  grants), §C.10 (the 20 % source)
- **likeness — VERBATIM, all fields recorded:**
  - material `Material.NETHER_STAR`, amount 1, **no data value, no leather colour, no
    enchants, no item flags, no glow** — the pack's port-chosen `NETHER_STAR` is a
    coincidental exact hit and should lose its "(port-chosen)" note
  - name (single set): `§6§lArmor Crystal (<setDisplayName>§6§l)`
    → e.g. `§6§lArmor Crystal (§c§lPhantom§6§l)`
  - name (multi): `§6§lMulti Armor Set Crystal (<L1>§6, <L2>§6§6§l)` where each `<Li>` is
    the set's colour + bold + first letter (`§c§lP`, `§b§lY`, `§f§lM`, `§a§lR`, `§2§lS`,
    `§5§lD`, `§e§lD`, `§b§lK`) → e.g.
    `§6§lMulti Armor Set Crystal (§c§lP§6, §b§lY§6§6§l)`. The trailing `§6` before the
    trimmed `", "` survives — reproduce the doubled code verbatim.
  - lore (single Phantom crystal at 20 %), **verbatim**:

    ```text
    §a20% Success Rate
    §7Can be applied to any non
    §7armor set that is not
    §7already equipped with a
    §7bonus crystal to gain
    §7a passive advantage!
    (blank)
    §6§lCrystal Bonus:
     §c§l§c§lPhantom
      §c§l* §c+5% DMG
    ```

    The set line is `" " + setColor + §l + setDisplayName` and the display name already
    carries its own colour+bold, so **two consecutive colour sequences** are emitted.
    For Supreme this means `§2§l` followed by `§4§l` (renders dark red).
  - **per-set bonus lines — VERBATIM** (these are the jar's own crystal descriptions;
    the pack currently carries hand-written paraphrases marked "not matrix-recorded"):
    | Set | description list |
    | --- | --- |
    | Phantom | `+5% DMG` |
    | Yeti | `-2.5% Incoming DMG`, `+2.5% Outgoing DMG` |
    | Mother of Yijki | `-5% Incoming DMG`, `20% Revenge of Yijki Ability` |
    | Ranger | `-5% Incoming Bow DMG`, `+6% Outgoing Bow DMG`, `20% Immune to Teleblock` |
    | Supreme | `+3% Outgoing DMG` |
    | Dimensional Traveler | `+7.5% Outgoing DMG`, `20% Dimensional Shift Ability` |
    | Dragon Slayer | `+3% PvP DMG`, `-5% Incoming DMG`, `10% Silence Immunity`, `10% Freeze Immunity` |
    | KOTH | `+5% PvP DMG`, `+12.5% PvE DMG` |
- **apply flow (guard order, exact):** target must be armour → cancel the click → the
  cursor must carry crystal data → **refuse on a set piece**
  (`You cannot apply an Armor Set Crystal to an existing Armor Set piece!`) → dupe check
  → **overworld gate** → **refuse a second crystal**
  (`You cannot apply multiple Armor Set Crystals to a single piece of gear!`) → **refuse
  on mastery-enchanted gear** unless every mastery enchant on the piece is one of the
  three whitelisted (`Discombobulate`, `Explosives Expert`, `Lava Strider`, exact
  case-sensitive match) → **consume the crystal** → roll.
- **the 20 % roll, exactly:** the stored `bonusChance` is a **double percentage**;
  success is `random() <= bonusChance × 0.01`. When the key is absent the default is
  **`20.0`**, which is also the fixed rate the trader mints at. `100.0` always succeeds;
  `0.0` effectively never does. The displayed number truncates (`20.7` shows as
  `20% Success Rate` while the stored value stays `20.7`).
- **decomposition:** the crystal likeness (`type: crystal`) carries material + `name` +
  `name-multi` + `lore` + `lore-while-on-item(-multi)` + apply/remove sounds; the per-set
  grant is one `DAMAGE_MOD` per crystal-bearing piece under the additive fold
  (`stackable: true` **is** the linear-in-n scaling, see matrix/10 §A.7 and
  `content/crystals/phantom.yml`); `crystals.slots: 1` × four armour slots **is** the
  one-per-piece/max-four cap.
- **not expressible on the likeness (gaps):** the apply gamble (`APPLY_GAMBLE`), the two
  refusal rules (`ITEM_REFUSAL_PREDICATE`), and the applied-lore's own name form. The
  jar's on-gear line is `§6§lArmor Crystal (<name1>§7, <name2>§7…§6§l)` — note the
  separator is `§7` on the gear line and the trailing one survives the trim, e.g.
  `§6§lArmor Crystal (§c§lPhantom§7§6§l)`; the pack's `lore-while-on-item` currently
  reproduces the item name form instead.
- **strings (apply outcomes) — VERBATIM:**
  - refusals: `You cannot apply an Armor Set Crystal to an existing Armor Set piece!` /
    `Armor set crystal already used!` /
    `You must apply this Armor Set Crystal in the Overworld!` /
    `You cannot apply multiple Armor Set Crystals to a single piece of gear!` /
    `You cannot apply Armor Set Crystals to an item that has a Mastery Enchantment equipped!`
    (each carries an external prefix — **codex-silent** on the exact prefix format; the
    message bodies are exact)
  - failure: `Your Armor Crystal (<crystalDisplayName>§c) fails to apply!`
  - success block:

    ```text
    (blank)
    §6§l(!) <crystalDisplayName> §6applied to <targetDisplayName>§6!
    §6§lCrystal Bonus:
     <setDisplayName>:
    <setColor>§l  * <setColor><descriptionLine>
    (blank)
    ```

- **sounds:** failure `GLASS` vol `1.1F` pitch `0.8F`; success `LEVEL_UP` vol `1.0F`
  pitch `1.1F`. The pack's amethyst cues are port-chosen and modern-only.
- **numbers:** default apply chance `20.0` %; one crystal per piece, max 4 per wearer;
  per-set percentages per the table above; trader mint rate fixed `20.0` %.
- **era:** `NETHER_STAR` is era-stable; `GLASS`/`LEVEL_UP` are legacy sound names.
- **interactions:** stacking is per-piece and linear in n (matrix/10 §A.7); a Multi
  crystal counts toward **every** set it names, so four multi-crystals naming three sets
  each yield n=4 for all three; the mastery gate blocks crystals on `mastery = true`
  ability-set gear but *not* on `mastery = false` ability-set gear.

### Anti-M-Kit Crystal (`content/crystals/anti-m-kit-*.yml` on the shared likeness)

- **codex:** `10-armor-sets.md` §C.2 (the codex numbers it §C.6 in its own prose under a
  `C.2` heading — source-side numbering drift, recorded not resolved)
- **likeness — VERBATIM, its own item in the jar:**
  - material `Material.INK_SACK` data `11` (Dandelion Yellow dye → modern `YELLOW_DYE`).
    **This is a second physical identity the port does not have** — the pack folds it onto
    the one crystal likeness, so it reads `Armor Crystal (Anti-M-Kit (Ghost))`.
  - name (single): `§6§lAnti M-Kit Crystal (<setDisplayName>§6§l)`
    → e.g. `§6§lAnti M-Kit Crystal (§3§lGhost§6§l)`
  - name (multi): `§6§lMulti Anti M-Kit Crystal (<letters>§6§l)`, same stray-`§6` quirk
  - lore — **VERBATIM header, dynamic tail:**

    ```text
    §a<N>% Success Rate
    §7Apply to any piece of armor
    §7to negate the following
    §7custom Mastery Enchantments:
    §6§l * §4<MasteryEnchantName>        (one line per matching enchant)
    ```

    In multi mode the set's own display name is inserted before its enchant block.
    The enchant list is enumerated live from the registry at mint time — **codex-silent**
    on the concrete names (the owning field is external), but matrix/07 has the four-name
    mastery pool per set, which reconstructs it exactly.
  - on-gear line: `§6§lAnti M-Kit Crystal (<setDisplayName>§7§6§l)`
- **apply flow:** target must be armour → **refuse a second one**
  (`You can not apply more than 1 Anti M-Kit Crystal to a single piece of gear!`) → dupe
  check → cancel → **consume before the roll** → `random() <= success × 0.01`. `success`
  is an **int**, so whole-percent granularity; the mint default is **`1` (1 %)** when the
  first argument does not parse — three sibling items, three different defaults
  (`20.0`, `1`, `1`).
- **what it actually does:** **100 % cancellation, no proc roll.** One crystal on **one**
  worn piece cancels every mastery-pool ability of that set aimed at the wearer; the
  worn-set scan is a flat union across the four slots (a count is never taken) and is
  cached for **1 s** per player. The advertised percentage is the *apply* chance only.
  This is exactly the pack's `SUPPRESS_INCOMING`-per-pool-key decomposition with
  `stackable: false`.
- **combining two crystals (the jar's own merge):** same material + both parse as
  anti-M-Kit + same key set → result success = `(successA × amountA) + (successB ×
  amountB)`, **hard-refused above 100** rather than clamped. So 10× 5 % + 10× 5 % = 100 %
  in one click, while two singles at 60 % each are refused outright. Sounds `LEVEL_UP` and
  `ORB_PICKUP`, both vol `10.0F` pitch `1.1F`.
- **decomposition:** crystal likeness + `stackable: false` + one `SUPPRESS_INCOMING`
  (`scope: ENCHANT`, `who: "@Self"`) per pool key, re-armed on a `REPEATING` window.
  Already authored; this pass only adds the verbatim identity and the combine rule.
- **strings — VERBATIM:**
  `You can not apply more than 1 Anti M-Kit Crystal to a single piece of gear!` /
  `Anti M-Kit Crystal already used!` /
  `<crystalDisplayName>§c fails to apply and has been destroyed!` /
  `§6§l(!) §6<crystalDisplayName>§6 has been applied to <targetDisplayName>§6!` /
  `§7While equipped you will be immune to any Mastery Enchantments associated with that /mkit(s)!` /
  `§c§l(!) §cYou must combine these crystals in the Overworld!` /
  `§c§l(!) §cYou must combine Anti M-Kit Crystals of the same type!` /
  `§c§l(!) §cYou cannot combine an Anti M-Kit Crystal over 100% success rate!` /
  `§c§l(!) §cAnti M-Kit crystal already redeemed!` /
  operator debug (typo is load-bearing, reproduce): `§c§lDBG: §cAnti M-Mkit Cancelled (<SET>) from <source> - <enchantName>`
- **sounds:** apply failure `FIZZ` **and** `GLASS`, both `3.0F`/`1.1F`; apply success
  `LEVEL_UP` `3.0F`/`1.1F`; combine `LEVEL_UP` + `ORB_PICKUP`, both `10.0F`/`1.1F`.
- **numbers:** default mint `1` %; one per piece, max 4; cancellation 100 %; worn-scan
  cache 1 s; combine cap 100.
- **era:** `INK_SACK:11` → `YELLOW_DYE`; `FIZZ` is a legacy sound name.
- **gaps:** `SECOND_CRYSTAL_LIKENESS` (a per-family item identity so the counter-crystal
  can carry its own name/material), `CRYSTAL_MERGE_RATE` (the amount-weighted combine
  arithmetic with a hard refusal above the cap).

### Crystal Extractor (`items/crystal.yml` → `extractor:`)

- **codex:** `10-armor-sets.md` §C.8
- **likeness — VERBATIM:**
  - material `Material.GHAST_TEAR`
  - name `§6§lCrystal Extractor`
  - lore (with the stamped percentage `25`):

    ```text
    §7Removes 1 random crystal from
    §7an armor piece and converts it
    §7into its applicable form:
    §f§l * §6§lAnti M-Kit Crystal (§f25% Success§6§l)
    §f§l §f§l§nOR
    §f§l * §6§lArmor Crystal (§f25% Success§6§l)
    ```

- **mechanics:** **extraction never fails — 100 %.** The stamped integer is not an
  extraction chance; it is the **apply chance stamped onto the crystal that comes back
  out**. Which crystal is returned: anti-M-Kit only → anti-M-Kit; armor crystal only →
  armor crystal; **both present → 50/50**; neither → refusal
  `No armor crystals to extract from this armor piece!`. The armour is returned with that
  crystal's data keys and lore line stripped. The extractor is consumed on success only;
  there is no partial-loss path and — unlike the two crystals — **no dupe protection at
  all**.
- **decomposition:** the pack's Item Extractor (ADR-0035/0070/0074) is a superset: it
  pops reforges, then crystals, then folded masks, always intact, always no-roll. The one
  jar behaviour it does not carry is **stamping a new apply chance onto the extracted
  crystal** — the engine returns the crystal unchanged, which is strictly better and is
  the ADR-0034 no-roll posture anyway.
- **strings:** success `§a§l(!) <newCrystalDisplayName>§a extracted from <armourDisplayName>§a!`
  (the name shown is the **stripped armour's**, a jar wording quirk).
- **sounds:** `LEVEL_UP` `10.0F`/`1.1F` and `WITHER_IDLE` `1.0F`/**`3.0F`** — the pitch is
  outside Minecraft's valid `0.5–2.0` band and is clamped client-side.
- **numbers:** default stamped percentage `1`; 50/50 branch when both crystal kinds are
  present.
- **era:** `GHAST_TEAR` era-stable; `WITHER_IDLE` legacy name; the pack's `BUCKET` is
  port-chosen because the engine default is a 1.17+ material.

### Heroic Armour piece (state, minted)

- **codex:** `10-armor-sets.md` §C.3
- **likeness — VERBATIM (fresh mint):** dyed **leather**, `Color.RED` (`#FF0000`),
  Protection IV + Unbreaking III on every piece and **Depth Strider III on the boots**.
  - names: `§4§lHeroic Helmet`, `§4§lHeroic Chestplate`, `§4§lHeroic Leggings`,
    `§4§lHeroic Boots`
  - lore (helmet shown), verbatim, 4 lines:

    ```text
    (blank)
    §7+3 Armor Value
    §7810 Durability
    §4This armor is stronger than diamond.
    ```

- **absolute numbers (the values `deferred-content.md` lists as missing):**
  | Slot | material | armour points | durability | M-Kit armour points |
  | --- | --- | --- | --- | --- |
  | Helmet | `LEATHER_HELMET` | **3** | **810** | 2 |
  | Chestplate | `LEATHER_CHESTPLATE` | **8** | **1000** | 4 |
  | Leggings | `LEATHER_LEGGINGS` | **6** | **935** | 3 |
  | Boots | `LEATHER_BOOTS` | **3** | **686** | 2 |
  | **Full set** | | **20** (= vanilla diamond) | **3431** | **11** |

  M-Kit points are `ceil(points / 2)` applied at upgrade time when the piece carries an
  ability-set marker.
- **upgrade path vs mint path:** the upgrade path retypes the piece to leather, halves the
  armour value for M-Kit gear, appends the same four lore lines **idempotently** (guarded
  on the tagline already being present), and — unlike the mint path — **does not** add
  Depth Strider / Protection / Unbreaking. A background pass re-checks and rewrites a
  stale armour-value line on every inventory click, with no version marker.
- **damage reduction (§C.4) — per-slot percents:**
  | Slot | non-M-Kit | M-Kit |
  | --- | --- | --- |
  | Chestplate | **10 %** | **16 %** |
  | Leggings | **8 %** | **13 %** |
  | Boots | **4.5 %** | **8 %** |
  | Helmet | **4.5 %** | **8 %** |
  | **Full set** | **27 %** | **45 %** |
- **outpost drawback (§C.4 part B):** an attacker holding a trainee outpost adds `0.025`,
  an attacker holding the courtyard outpost adds `0.0625`, summed and multiplied by the
  victim's heroic-leather piece count → max `0.35` → **+35 % damage taken**. External
  server system; interaction-layer, off by default.
- **Infinite Luck negation (§C.5):** **12.5 percentage points per worn heroic leather
  piece** (12.5/25/37.5/50 %), compared against a single draw. The jar reads the *victim's*
  armour, i.e. the player Infinite Luck protects — already ledgered as D-10-1; not
  duplicated here.
- **decomposition:** `type: heroic` carries one uniform `percent-reduction` per piece.
  The pack ships `0.0675` to land the 27 % full set exactly and eats the per-slot
  asymmetry (a lone chestplate reads 6.75 % where the jar reads 10 %). The M-Kit grade,
  the absolute durabilities and the per-slot split are the three gaps below.
- **era:** leather armour dyeing is era-stable; the armour-point/toughness re-stat runs
  through the vanilla attribute path on modern and the plugin's own combat maths on
  1.8.9, which has no armour attribute (already recorded on every leather-heroic entry).
- **gaps:** `PER_SLOT_HEROIC_STATS` (per-slot reduction + per-slot armour points + per-slot
  absolute max durability), `HEROIC_GRADES` (a second, steeper tier selected by a marker
  on the target — here the ability-set marker).

### Heroic Weapon (state, minted)

- **codex:** `10-armor-sets.md` §C.6
- **likeness — VERBATIM:**
  - Heroic Axe: `Material.GOLD_AXE`, name `§c§lHeroic Axe`
  - Heroic Sword: `Material.GOLD_SWORD`, name `§c§lHeroic Sword`
  - lore appended to both:

    ```text
    (blank)
    §71952 Durability
    §4+4 Bonus Attack Damage
    §4This weapon is stronger than diamond.
    ```

    Note the order is Durability → Attack → tagline, the **opposite** of armour
    (Armor Value → Durability → tagline).
- **numbers:** durability **1952** for both; attack modifier **`4.0` flat**, rendered
  with the trailing `.0` stripped. **This settles the pack's unauthored
  `percent-damage`:** the jar's heroic weapon bonus is a **flat +4 attack damage, not a
  percentage** — so there is no codex value for a percentage knob, and the honest reading
  is "codex-silent as a percent, +4 flat as measured". The declared vanilla base damages
  (axe `3.0`, sword `4.0`) are stored but never read by the plugin.
- **quirk worth ledgering:** the axe's maxed `Silence` and `Lifesteal` grants sit **inside
  the cosmetic add-lore branch**, so a conversion that skips lore produces an axe with no
  custom enchants at all.
- **decomposition:** `items/heroic.yml` `material-upgrades` already maps
  `DIAMOND_SWORD → GOLDEN_SWORD` / `DIAMOND_AXE → GOLDEN_AXE`, which is the jar's own gold
  form; `diamond-stats: true` supplies the "stronger than diamond" contract. The flat +4
  attack and the absolute 1952 durability have no knob (same two gaps as armour).
- **era:** `GOLD_AXE`/`GOLD_SWORD` → `GOLDEN_*` through the alias resolver.

### Heroic Armour Upgrade — targeted (`items/heroic.yml`)

- **codex:** `10-armor-sets.md` §C.7
- **likeness — VERBATIM, all fields recorded** (the pack currently leaves `name`, `lore`,
  `success-min/max` and `percent-damage` unauthored on the grounds that nothing fixes
  them — that is now settleable):
  - material `Material.INK_SACK` data `11` (Dandelion Yellow → `YELLOW_DYE`)
  - name (set-specific): `§6§lHeroic §6(<setDisplayName>§6) §6§lUpgrade`
    → e.g. `§6§lHeroic §6(§a§lRanger§6) §6§lUpgrade`
  - name (generic): `§6§lHeroic ArmorUpgrade` — **the missing space is real**, reproduce
  - lore (set-specific, success 75), **verbatim**:

    ```text
    (blank)
    §7Apply to any §a§lRanger§7 armor
    §7for a §675%§7 chance to imbue it
    §7with the power of Heroic Armor!
    (blank)
    §7This will increase the armor's base
    §7stats as well as add a §612.5% negation
    §7to enemy §6Infinite Luck§7 enchantments.
    ```

    Generic line 2 reads `§7Apply to any §7armor set`. The displayed percentage
    **truncates**; the stored value keeps the double.
- **apply flow (guard order):** left/right click in the player's own inventory → target
  is armour → cursor is the upgrade item → cancel → **overworld gate (the same
  player-name-vs-`world_end` typo as the crystal)** → set match → **material gate: only
  `DIAMOND_*` armour may be upgraded** → dupe check → clamp the rate to `[0, 100]` →
  **consume** → roll `d0 >= randInt(1..100)`, i.e. exactly `floor(d0)` percent.
- **outcome strings — VERBATIM:**
  - success: `§a§l(!) §aYou have upgraded your <itemDisplayName>§a to the Heroic Armor version!`
  - failure: `§c§l(!) §cYou failed to apply your <upgradeDisplayName>§c!`
  - wrong set: `§c§l(!) §cYou must apply this Heroic Armor Upgrade to a <Set Name>§c Armor Set piece!`
  - wrong material: `§c§l(!) §cYou cannot apply this to non diamond armor set pieces!`
  - already used: `§c§l(!) §cThat Armor Upgrade has already been used!`
  - world gate: `§c(!) §cYou must apply this Upgrade in the Overworld!` — the **only**
    `(!)` prefix in the whole plugin that is not bold
- **sounds & particles:** success `HAPPY_VILLAGER` count `30` at player +0.4 Y,
  `VILLAGER_YES` `1.4F`/`0.9F`, `LEVEL_UP` `1.4F`/`0.9F`; failure `LAVA_POP`
  `1.0F`/`0.9F` + `LAVA` count `30`.
- **numbers:** success is per-item, stored as a double and clamped to `[0, 100]`; the
  random-upgrade redemption mints a **uniform integer `1..100`**; the loot-side builder
  rolls uniformly in **`[min, max + 1.0)`** — an integer idiom applied to a double, so
  every loot upgrade can overshoot its configured maximum by up to a full point.
- **decomposition:** `type: heroic` with `success-min`/`success-max`,
  `destroy-on-fail: false`, `material-upgrades`, `diamond-stats`, `lore-line`. The
  set-targeting (`setType`) has no analogue — the port's heroic is **not set-bound** by
  design, which also disposes of the unreachable-wildcard bug below.
- **era:** `INK_SACK:11` → `YELLOW_DYE`; the pack's `GOLD_INGOT` is port-chosen and can now
  be replaced with the recorded dye if verbatim identity is wanted.
- **gaps:** `ITEM_TARGET_FILTER` (an applied item that only accepts targets carrying a
  named family marker — here a set piece of one named set).

### Random Heroic Upgrade (no pack likeness)

- **codex:** `10-armor-sets.md` §C.7
- **likeness — VERBATIM:**
  - material `Material.CHEST`, no data value
  - name: `§6§lRandom Heroic Upgrade §7(Right Click)`
  - lore:

    ```text
    §7Click to receive a random §6§nHeroic Armorset Upgrade
    §7from one of the following armor sets!
    (blank)
    §6§lAvailable Armor Sets
    §6§l* §6§b§lYeti
    §6§l* §6§f§lMother of Yijki
    §6§l* §6§4§lSupreme
    §6§l* §6§a§lRanger
    §6§l* §6§c§lPhantom
    §6§l* §6§5§lDimensional Traveler
    ```

- **mechanics:** right-click → overworld gate (this one reads the world name correctly) →
  dupe gate (**only when the stack size is exactly 1**, so a stacked one bypasses it) →
  roll a set → mint a targeted upgrade at a uniform `1..100` success →
  `§a§l(!) §aYou have received a <upgradeDisplayName>§a from your Random Heroic Upgrade!`
  + `LEVEL_UP` `1.0F`/`1.1F`.
- **roll table:** 70 % common tier → Yeti / Supreme / Ranger (23.3̅ % each); 30 % rare tier
  → Phantom / Mother of Yijki / Dimensional Traveler (10 % each). Dragon Slayer and KOTH
  are excluded; a KOTH upgrade silently becomes a Ranger upgrade.
- **decomposition:** no engine surface. It is the unopened-book shape (right-click →
  weighted reveal → mint a concrete item) pointed at a *different* item type. Closest fit
  is a generalisation of `type: unopened-book` from "tier of enchant book" to "weighted
  table of any minted item".
- **gap:** `UNOPENED_ITEM` — right-click a container item to reveal a weighted random
  minted item (any likeness type), with the tier weights and the per-mint numeric roll
  authored per entry. Consumers: this entry, the Mystery Pet Box (matrix/12 §C.1, out of
  scope by R3), Secret Dust below.

### Mastery Shard (no pack likeness)

- **codex:** `10-armor-sets.md` §C.9, §C.10 (trader rate), `08-enchant-economy-items.md`
  §1.7 step 13 (the restriction it lifts)
- **likeness — VERBATIM, fully recorded** (`deferred-content.md` says the codex records no
  name or lore for this item — it records **both**, plus the material and the per-set dye
  data):
  - material `Material.INK_SACK`, per-set data value:
    | Set | data | dye | name (verbatim) |
    | --- | --- | --- | --- |
    | Ghost | `7` | Light Gray | `§6§lMastery Shard §6(§3§lGhost§6)` |
    | Necromancer | `2` | Cactus Green | `§6§lMastery Shard §6(§2§lNecromancer§6)` |
    | Death Knight | `6` | Cyan | `§6§lMastery Shard §6(§9§lDeath Knight§6)` |
    | Architect | `6` | Cyan | `§6§lMastery Shard §6(§b§lArchitect§6)` |

    Death Knight's and Architect's shards are **visually identical** apart from the name —
    which is the observation `deferred-content.md` already carried, now with its source.
  - lore (Ghost shown), verbatim, 3 lines:

    ```text
    §7Apply to an enchantment book
    §7to imbue it with the power to
    §7affect §3§lGhost§7 Mastery Kit items.
    ```

- **the restriction it lifts, exactly:** ability-set armour carries a `noEnchants` marker.
  When a book is dragged onto a piece carrying that marker, the apply is refused unless
  the **book** carries a matching `masteryShard` entry naming that piece's ability set.
  The refusal is three lines, **verbatim**:

  ```text
  §c§l(!) §cThis armor is too powerful to modify its enchantments!
  §7Use a §7§n<Ability Set Title Case> Mastery Shard§7 on this book to allow it to be applied!
  §7Mastery Shards can be obtained by trading your Mastery Kit Items to the §7§n/tinkerer§7!
  ```

- **apply flow:** target must be a plain `Material.BOOK` that is a recognised enchant book
  (not an enchanted book); cursor must be a shard; blocked in `world_duels*`; **duplicate
  shards are refused without consuming** (`§c§l(!) §cThis book already has this Mastery Shard equipped!`);
  otherwise **100 %, no roll**. The book stores a **list**, so one book can carry all four
  shards, one of each. One lore line is appended per shard:
  `§6§l* Mastery Shard §6(<setDisplayName>§6)`.
- **success strings — VERBATIM:**
  - `§6§l(!) §6§n<StrippedSetName> Mastery Shard§6 applied to your <bookDisplayName>§6!`
  - `§7This book is now powerful enough to attempt to apply to the <setDisplayName>§7 /mkit items!`
  - sound `BAT_TAKEOFF` `1.0F`/`1.1F`
- **acquisition:** trading N mastery shards to the trader yields **one Anti-M-Kit Crystal
  whose success percentage equals the stack size N**, uncapped at this stage (60 shards →
  a 60 % crystal; 120 shards → a 120 % crystal that always applies). Zero XP is paid; the
  crystal *is* the payment. Trading one armour-set piece yields one Armor Crystal for that
  set at exactly **20 %**.
- **decomposition:** **none — this is the doc's one true structural hole.** The engine has
  no gear-side "refuses ordinary books" state and no book-side "carries a family key"
  state, so an authored shard would unlock a lock nobody can set. Both halves must land
  together or neither is meaningful.
- **quirk to *not* replicate:** applying one shard clears the **entire cursor stack** — a
  stack of 64 is destroyed to apply one. Every sibling item decrements properly.
- **era:** three `INK_SACK` data values → `LIGHT_GRAY_DYE` / `GREEN_DYE` / `CYAN_DYE`;
  `BAT_TAKEOFF` is a legacy sound name.
- **gap:** `BOOK_RESTRICTION_PAIR` — a gear-side flag (`refuses ordinary enchant books`,
  keyed by a family name) plus a book-side key list that lifts it for a matching family,
  with the refusal message authored on the gear side and the shard applying at 100 % into
  a set-valued book field. Consumers: this entry, the four ability sets in matrix/10, the
  §C.10 trader loop.

### Enchantment Book (`items/enchant-book.yml`)

- **codex:** `08-enchant-economy-items.md` §1.1–§1.11
- **likeness — VERBATIM:** material `Material.BOOK` (a *plain* book, not
  `ENCHANTED_BOOK`), stack size 1.
  - name: `{tierColor}§l§n{EnchantName} {RomanLevel}`
  - lore: `§a{success}% Success Rate` / `§c{destroy}% Destroy Rate` / wrapped description
    lines each prefixed `§e` / `§7{EnchantmentType} Enchantment` /
    `§7Drag n' Drop onto item to enchant.`
- **numbers:** success and destroy are each an independent uniform integer **`[1, 100]`**
  rolled at mint. Description wrapping is greedy at **38 characters**. The tier colour is
  the enchant's rarity colour.
- **apply flow, exactly:** equipped-armour slots are refused
  (`§cYou cannot apply enchantments to your armor while it is equipped.`) → the enchant
  must fit the item → **slot-count gate** → same-enchant upgrade gate
  (`§c§l(!) §cThat item already has {ench} {level}!`) → heroic-prerequisite gate
  (`the item must have the max level of the non-heroic version ({name}) first`) →
  heroic-already-present gate → the **mastery-shard gate** above → consume → resolve.
- **success/destroy resolution — the numbers that matter:**
  - bonuses accumulate first: hero-outpost holder `+10` success / `−25` destroy; a rank
    permission `+5`; a one-shot pet effect `+N`
  - then, for everyone except two hard-coded account names, **`success −= 10`** (floored
    at 1) and **`destroy = (int)(destroy × 1.15)`** — a third hard-coded name is forced to
    100 % success
  - the roll is `randInt(0..99) > success` → failure, so `success = 99` and `success = 100`
    are **both 100 %** and `success = 0` is still 1 %
  - on failure: a rank permission saves the item outright at 10 %; else if the item is
    white-scrolled, a rank permission saves the **scroll** at 15 %, otherwise the scroll is
    consumed and the item saved; else the destroy roll fires
  - **the white scroll burns on *any* failure**, not only on a destroy — it is failure
    insurance, not destruction insurance
  - destroy can exceed 100 (a book rolled at 100 becomes 115 after the ×1.15) with no clamp
- **decomposition:** `type: enchant-book` with `{SUCCESS}/{FAILURE}/{TIER_COLOR}/{ENCHANT}/
  {LEVEL}/{KINDS}/{DESCRIPTION}` and `destroy-on-fail`. The engine's failure is binary
  (`100 − success`), which is *deliberately* simpler than the jar's independent
  success/destroy pair — record it, do not chase it.
- **strings — VERBATIM (slot lock, worth keeping):**
  - `§c§l(!) §cYou are not skilled enough to add another enchantment to this item.`
  - `§c§l(!)§c You are not skilled enough to fully use your weapon.` +
    `§c§nLocked:§7 {comma-separated coloured enchant names}` — rate-limited to once per
    **60 s**; the sweep only names tiers 5..0, so tiers 6–8 are never listed
- **era:** `BOOK` is era-stable.
- **gaps:** `INDEPENDENT_DESTROY_RATE` (a destroy chance decoupled from success),
  `BOOK_RATE_MODIFIER` (already declared in matrix/12 for the pet that arms it — it
  absorbs the outpost/rank/pet one-shot adders here), `USABLE_ENCHANT_CAP` (a cap on how
  many of an item's enchants *function*, distinct from how many may be stored, with the
  lowest tiers admitted first and a rate-limited lock notice).

### Mystery (unopened) Enchantment Book (`items/unopened-book.yml`)

- **codex:** `08-enchant-economy-items.md` §1.12, `16-item-builders.md` (mystery book)
- **likeness — VERBATIM, all seven tiers:** material `Material.BOOK`, lore line 1 always
  `§7Examine to recieve a random` (**the misspelling is load-bearing** — recognition
  matches that exact string).
  | Tier | name | lore line 2 |
  | --- | --- | --- |
  | 1 | `§fSimple Enchantment Book§7 (Right Click)` | `§fsimple§7 enchantment book.` |
  | 2 | `§aUnique Enchantment Book§7 (Right Click)` | `§aunique§7 enchantment book.` |
  | 3 | `§bElite Enchantment Book§7 (Right Click)` | `§belite§7 enchantment book.` |
  | 4 | `§eUltimate Enchantment Book§7 (Right Click)` | `§eultimate§7 enchantment book.` |
  | 5 | `§6§lLegendary Enchantment Book§7 (Right Click)` | `§6legendary§7 enchantment book.` |
  | 6 | `§c§lSoul Enchantment Book§7 (Right Click)` | `§csoul§7 enchantment book.` |
  | 7 | `§d§lHeroic Enchantment Book§7 (Right Click)` | `§dheroic§7 enchantment book.` |

  Tiers 5–7 bold the **name** but not the lore-2 colour code.
- **decomposition:** `type: unopened-book` with `{TIER}/{TIER_NAME}/{TIER_COLOR}` and
  `min-success`/`max-success` — a direct hit. The pack's
  `"{TIER_COLOR}&l{TIER_NAME} Enchantment Book &r&7(Right Click)&r"` differs from the jar
  only in bolding tiers 1–4 and in fixing the misspelling; both are worth a deliberate
  decision rather than a silent drift.
- **numbers:** the tier ladder is `Unknown/Simple 1, Unique 2, Elite 3, Ultimate 4,
  Legendary 5, Godly 6, Heroic 7, Soul 7` — and the tier→book mapping is **off by one
  label** at the top: tier `Godly` mints the *Soul* book and tier `Soul` mints the
  *Heroic* book. `Heroic` and `Soul` are indistinguishable to every consumer.
- **era:** era-stable.

### Enchantment Orb (`items/slot-orb.yml`)

- **codex:** `08-enchant-economy-items.md` §4
- **likeness — VERBATIM:** material `Material.EYE_OF_ENDER`, stack size 1. **Two orb
  types** — `Armor` and `Weapon`.
  - name: `§6§l{Armor|Weapon} Enchantment Orb [§a§n{maxSlots}§6§l]`
  - lore, 9 lines:

    ```text
    §a{successRate}% Success Rate
    (blank)
    §6+1 Enchantment Slots
    §6{maxSlots} Max Enchantment Slots
    (blank)
    §eIncreases the # of enchantment
    §eslots on a piece of {armor|weapon} by 1,
    §eup to a maximum of {maxSlots}.
    §7Drag n' Drop onto {armor|weapon} to apply.
    ```

    Line 7 reads "a piece of weapon" on the weapon variant — ungrammatical, verbatim.
  - on-gear stamp: `§a§l{maxSlots} Enchantment Slots§7 (Orb [§a+{maxSlots - 9}§7])`
- **numbers:** **+1 slot per orb**; the baseline is **9** (the stamp shows
  `maxSlots − 9`), which does not match the stored-enchant cap ladder whose highest rung
  is **8**. The apply roll is `randInt(0..99) < successRate`, i.e. exactly
  `successRate/100` — a *different* off-by-one convention from books.
- **apply flow:** type must match the target (armour orb on armour, weapon orb on
  weapons — **tools can never receive either**, they always fall out with a mismatched
  message) → the target's current bonus must be below the orb's maximum → the item's
  current enchant count must be below the orb's maximum → consume → roll.
- **decomposition:** `type: slot-orb` with `orb-amount`, `hard-cap`,
  `min-success`/`max-success`, `applies-to`. The pack ships `orb-amount: 3`,
  `hard-cap: 14`; the jar is **+1 per orb** with a per-orb ceiling carried on the item
  itself. The per-orb ceiling (an orb that can only raise an item *to* N) has no knob —
  the engine's `hard-cap` is global.
- **strings — VERBATIM:**
  `§c(!) This is an Armor Enchantment Orb and can only be used on armor pieces.` /
  `§c(!) This is a Weapon Enchantment Orb and can only be used on weapons.` /
  `§c(!) This Enchantment Orb can only increase an item's maximum enchantment slots up to {N}.` /
  `§c(!) This item has more enchantments on it than this Enchantment Orb allows.`
- **era:** `EYE_OF_ENDER` → `ENDER_EYE`.
- **gaps:** `PER_ITEM_SLOT_CEILING` (an orb carrying its own maximum, so orb grade is an
  item property rather than a pack constant); `ORB_KINDS` is already covered by
  `applies-to`.

### Magic Dust (`items/dust.yml`)

- **codex:** `08-enchant-economy-items.md` §5.3, §5.6, §5.7; `16-item-builders.md` (dust)
- **likeness — VERBATIM, six rarity variants:** material `Material.SUGAR`
  - name: `{rarityColor}{Rarity} Magic Dust` — `§fSimple`, `§aUnique`, `§bElite`,
    `§eUltimate`, `§6Legendary`, `§cSoul`
  - lore, 5 lines:

    ```text
    §a+{n}% Success
    §7Apply to a {rarityColor}{Rarity}§7 Enchantment Book
    §7to increase its success rate by {rarityColor}{n}%
    (blank)
    §7Place dust on enchantment book.
    ```

- **mechanics:** the book's success line is rewritten to `min(100, current + increase)` —
  **hard cap 100**, destroy rate untouched, additive across repeated applications. Dust is
  refused on books already at 100 %, on stacked books, and on heroic books
  (`§c§l(!) §cYou cannot apply Magic Dust to Heroic Enchantment Books.`). **The dust's
  rarity must match the book's rarity**, cross-checked against the display-name colour
  prefix, or the click is silently ignored.
- **decomposition:** `type: dust` with `{MIN}/{MAX}/{BONUS}/{MAXSUCCESS}` and
  `min-bonus`/`max-bonus`, clamped to the global success ceiling — a direct hit on the
  mechanic. The **rarity dimension has no home**: one dust likeness, no rarity token, no
  rarity match on apply.
- **era:** `SUGAR` era-stable. The pack's `GLOWSTONE_DUST` is the jar's **Primal** dust
  material, not Magic dust's — worth a deliberate choice.
- **gap:** `ITEM_RARITY_VARIANTS` — a likeness minted per rarity rung (name/colour token
  from the rung) plus a `rarity-match` apply predicate. Consumers: Magic Dust, Primal
  Dust, Secret Dust, Randomization Scroll (four items, one capability).

### Primal Dust (no pack likeness)

- **codex:** `08-enchant-economy-items.md` §5.4
- **likeness — VERBATIM:** material `Material.GLOWSTONE_DUST`; name
  `{rarityColor}§l{Rarity} Primal Dust`; lore identical in shape to Magic Dust but bolded:
  `§a§l+{n}% SUCCESS` / `§7Apply to a {rarityColor}§l{Rarity}§7 Enchantment Book` /
  `§7to increase its success rate by {rarityColor}§l{n}%` / (blank) /
  `§7Place dust on enchantment book.`
- **notes:** it is the same mechanic at a higher grade; both dust grades are recognised by
  the same material-or-material test, so they share every apply path.
- **decomposition:** none — one `type: dust` per pack. Gap: `ITEM_GRADES` (a second grade
  of an existing likeness with its own material/name/number band). Same shape as the
  `HEROIC_GRADES` and heroic-black-scroll gaps; cluster them.

### Secret Dust (no pack likeness)

- **codex:** `08-enchant-economy-items.md` §5.2, §5.8
- **likeness — VERBATIM:** material `Material.FIREBALL`; name
  `{rarityColor}{Rarity} Secret Dust §7(Right Click)` (note the space before `§7`); lore:

  ```text
  §aSuccess: +0-{i}%
  §7Contains: §bMagic, §ePrimal, §7or §fMystery§7 dust.
  §7An unidentified satchel of dust.
  ```

- **mechanics:** right-click to open (requires an empty inventory slot —
  `§cYou need an empty inventory slot to examine secret dust.`). Outcome distribution per
  examine: **75 % Mystery (junk) dust, 23.75 % Magic dust, 1.25 % Primal dust**. The
  granted bonus is `randInt(1..maxIncrease)`, and the primal branch multiplies by 3 with a
  **floor of 10**, so a `+0-1%` secret dust can produce a **10 %** primal dust — ten times
  its own advertised ceiling.
- **decomposition:** none. Same `UNOPENED_ITEM` gap as the Random Heroic Upgrade: a
  right-click container that reveals a weighted item. This is the second consumer.
- **era:** `FIREBALL` → `FIRE_CHARGE`.

### Mystery (junk) Dust (no pack likeness)

- **codex:** `08-enchant-economy-items.md` §5.1
- **likeness — VERBATIM:** material `Material.SULPHUR`; name `§fMystery Dust`; lore
  `§7The failed bi-product of` / `§7Magic and Primal dust.`
- **mechanics:** it is a pure sink — the 75 % branch of a Secret Dust open, with no apply
  path of its own. Note-only; no engine surface needed beyond a cosmetic item.
- **era:** `SULPHUR` → `GUNPOWDER`.

### White Scroll (`items/white-scroll.yml`)

- **codex:** `08-enchant-economy-items.md` §6.1, §1.8 (consumption semantics)
- **likeness — VERBATIM:** material `Material.EMPTY_MAP`
  - name: `§eWhite Scroll` — **not bold**, unlike the Black Scroll
  - lore, 3 lines:

    ```text
    §7Prevents an item from being destroyed
    §7due to a failed Enchantment Book.
    §ePlace scroll on item to apply.
    ```

  - applied line: `§f§lPROTECTED`
- **mechanics:** applies at **100 %, no roll**; re-application is blocked (one guard per
  item). Consumed on **any** failed book application, whether or not the destroy roll
  would have fired. **Worn armour can be white-scrolled** — the slot guard blocks raw
  slots 0–3 only, and the four armour slots are raw slots 5–8 in that view (books block
  0–8 and black scrolls reject the armour slot type explicitly, so this is a
  white-scroll-only hole).
- **decomposition:** `type: white-scroll` with `protected-line` and
  `min-success`/`max-success` (the pack ships `100/100`, matching the jar's no-roll
  apply) — a direct hit including the verbatim `§f§lPROTECTED` line, which the pack
  already carries.
- **strings:** right-click help,
  `§e§l(!) §eTo apply this White Scroll to an item, simply drag n' drop the scroll onto the item you'd like to protect in your inventory!`
- **era:** `EMPTY_MAP` → `MAP`.

### Black Scroll (`items/black-scroll.yml`)

- **codex:** `08-enchant-economy-items.md` §6.2, §6.4
- **likeness — VERBATIM:** material `Material.INK_SACK` data `0`
  - name: `§f§lBlack Scroll`
  - lore, 4 lines:

    ```text
    §7Removes a random enchantment
    §7from an item and converts
    §7it into a §f{successRate}%§7 success book.
    §fPlace scroll on item to extract.
    ```

- **numbers:** the default mint rolls the drawn book's success uniformly in
  **`[51, 100]`**; the pack ships `min-convert: 50` / `max-convert: 100`, one rung off.
- **mechanics:** extraction targets a random enchant from the item, excluding mastery
  enchants and (on the plain grade) heroic enchants. Armour must be **unequipped**:
  `§c§l(!) §cPlease remove your armor before attempting to Black Scroll it!`
- **decomposition:** `type: black-scroll` with `{SUCCESS}`, `applies-to`,
  `min-convert`/`max-convert` — a direct hit. The pack's `DRIED_KELP` (degrading to
  `INK_SACK` on 1.8.9) is port-chosen; the recorded identity is plain `INK_SACK`.
- **era:** `INK_SACK:0` → `INK_SAC`.

### Heroic Black Scroll (no pack likeness)

- **codex:** `08-enchant-economy-items.md` §6.3, `16-item-builders.md` (black scroll)
- **likeness — VERBATIM:** material `Material.INK_SACK` data `0`, **glowing**
  - name: `§d§lHeroic Black Scroll`
  - lore, 4 lines:

    ```text
    §7Removes a random enchantment
    §7from an item and converts
    §7it into a §d{min}%-{max}%§7 success book.
    §d§l(!)§d Chance to extract Heroic Enchantments
    ```

  - data keys: `cosmicType = "heroicBlackscroll"`, `cosmicData = { min, max }`
- **numbers (the random mint), exactly:** min starts at `10`, max seed at `35`; `+10` at
  50 %, another `+10` at 40 %, `+15` at 30 %, and at 30 % the min drops by `randInt(1..3)`.
  Resulting min ∈ `{7, 8, 9, 10}` (10 with p=0.7, each of 7/8/9 with p=0.1); max seed
  distribution `35`:21 %, `45`:35 %, `50`:9 %, `55`:14 %, `60`:15 %, `70`:6 %. The
  displayed upper bound is then a uniform draw in `[min, maxSeed]`.
- **decomposition:** none — one black-scroll likeness per pack, and its band is a single
  `[min-convert, max-convert]` rather than a per-item rolled band with a distinct
  extraction eligibility. Gap: `ITEM_GRADES` (shared) plus
  `EXTRACTION_ELIGIBILITY` (which enchant classes a given scroll grade may draw).
- **era:** `INK_SACK` → `INK_SAC`; the glow flag is era-stable.

### Transmog Scroll (`items/transmog-scroll.yml`)

- **codex:** `08-enchant-economy-items.md` §9.1–§9.3
- **likeness — VERBATIM:** material `Material.PAPER`
  - name: `§e§lTransmog Scroll`
  - lore, 4 lines:

    ```text
    §7Organizes enchants by §e§nrarity§7 on item
    §7and adds the §dlore §bcount§7 to name.
    (blank)
    §e§oPlace scroll on item to apply.
    ```

    The closing line is italic here and plain yellow on the White Scroll — three scrolls,
    three formattings of the same sentence.
- **mechanics:** walks tiers **8 down to 1**, hoists each enchant's coloured line to the
  top of the lore, appends every surviving non-enchant line below (so a `§f§lPROTECTED`
  tag and orb stamps survive, just relocated), then rewrites the display name to
  `{base} §d[§b§l§n{count}§d]`, truncating any previous count suffix. **Pure display
  reorder plus a name suffix** — no enchant, level, durability or data change.
  Tier-0 (unregistered) enchants are never hoisted.
- **decomposition:** `type: transmog-scroll` with `name-suffix` — a direct hit; the pack's
  `&r &d[&b&l&n{COUNT}&r&d]` reproduces the recorded suffix.
- **era:** `PAPER` era-stable.

### Randomization Scroll (`items/randomizer-scroll.yml`)

- **codex:** `08-enchant-economy-items.md` §10.1; `16-item-builders.md` (randomization
  scroll)
- **likeness — VERBATIM:** material `Material.PAPER`
  - name: `{rarityColor}§l{Rarity} Randomization Scroll`
  - lore, 5 lines, **line 1 is blank**:

    ```text
    (blank)
    §7Apply to a(n) {rarityColor}{Rarity}§7 enchantment book
    §7to reroll the success and destroy rates.
    (blank)
    §7Drag n' Drop onto §nenchantment book§7 to apply.
    ```

    On the wildcard (`Godly`) rung the lore reads `ANY` where the name still reads the
    rarity — one item, two descriptions of the same rung.
- **mechanics:** rerolls **both** numbers to fresh independent uniform `[1, 100]` draws —
  no floor, no ceiling, no keep-the-better rule; a 99 %/1 % book can become 1 %/100 %.
  Refused on stacked books (`§c§l(!) §cYou cannot apply scrolls to stacked books.`), on
  heroic books (`§c§l(!) §cYou cannot use Randomization Scrolls on Heroic Enchantment Books.`),
  and on a rarity mismatch
  (`§c§l(!) §cYou need a(n) {rarity} rarity scroll to reroll that book.`) unless the scroll
  is the wildcard rung.
- **decomposition:** `type: randomizer-scroll` with `min-percent`/`max-percent` — the
  mechanic is a hit (the engine's failure is `100 − success`, so rerolling success
  rerolls both, and the pack's lore says exactly that). The **rarity variants and the
  wildcard rung** have no home → `ITEM_RARITY_VARIANTS` (third consumer).
- **era:** `PAPER` era-stable. The pack's `REDSTONE` + "Randomizer Dust" naming is a
  deliberate likeness change with the type key preserved.

### Soul Gem (`items/soul-gem.yml`)

- **codex:** `07-enchants-mastery-soul.md` § Soul Mode (the soul API contract and the
  toggle listener); `16-item-builders.md` appendix (the mint delegate)
- **likeness:** **codex-silent** — material codex-silent, name codex-silent, lore
  codex-silent. The gem is minted by an external plugin that is absent from the corpus;
  only the call contract is observable.
- **contract — fully recorded, and it matches the port exactly:**
  | Capability | Recorded meaning |
  | --- | --- |
  | soul-mode flag | a per-player on/off state, toggled by the item |
  | "has N souls" | the player's **gems** hold ≥ N souls |
  | "all souls" | total across **all** gems |
  | "drain N" | drains N across gems |
  | "is a soul gem" | item identity test |
  | per-gem get/set | souls are stored **on the item** |
  | mint | a new gem item holding N souls |
- **toggle mechanics — VERBATIM:** right-click (air or block) while holding a soul gem,
  with **at least 1 soul**, toggles soul mode. Refused while soul-trapped:
  `§c§l(!) §cYou cannot enable soul mode while in a Soul Trap!`
  - ON: blank line, `§a§l** SOUL MODE: §nON§a§l **`,
    `§7Active soul enchantments will now drain soul gems.`, blank line, sound
    `ORB_PICKUP` `1.0F`/`1.2F`
  - OFF: blank line, `§c§l** SOUL MODE: §nOFF§c§l **`,
    `§7Soul enchantments will no longer drain soul gems.`, blank line, sound
    `BAT_TAKEOFF` `1.0F`/`1.2F`
  - spend feedback (as used by soul enchants): `§c§l- {N} Soul Gems` then
    `§7You have §n{total}§7 souls left.`; out of souls: `§c§l** OUT OF SOULS **`
- **decomposition:** `type: soul-gem` — right-click toggle, souls on the item, combine by
  stacking, split by command. Every recorded contract line is already implemented; the
  only codex-silent fields are the cosmetic ones, and the pack's `EMERALD` +
  `§c§lSoul Gem [{SOUL_COLOR}{AMOUNT}§c§l]` are port-choices that should stay marked as
  such.
- **numbers:** souls-per-kill is **codex-silent** (the accrual lives in the absent
  plugin); the only recorded soul *prices* are per-consumer (e.g. the EMP item's 300).

### Trak gems — block / mob / soul / fish (`items/*trak.yml`)

- **codex:** `16-item-builders.md` (kill tracker builder)
- **likeness:** **codex-silent on every field** — material, name and lore all live in an
  absent plugin. What *is* recorded: the tracker item is selected by matching a tracker
  rank to the item's rarity rung (1–7), falling back to the lowest rank when no tracker
  of that rank exists, and the fallback constant is mixed-case (`Simple`, not `SIMPLE`).
  Nothing else — no count format, no lore line, no per-trak split.
- **notes:** the port's four traks (block/mob/soul/fish) are a **finer split than the
  corpus records**; the jar has one "tracker" family with a rank ladder. Both the split
  and the likenesses are port-original and should stay marked as such.
- **decomposition:** four `type: *trak` likenesses with `count-format` and `applies-to`,
  already shipped in the signature pack; the cosmic pack carries none.

### Item Nametag (`items/nametag.yml`)

- **codex:** `16-item-builders.md` (item broker dispatch table)
- **likeness:** **codex-silent** — the nametag is fetched from an external plugin; only
  its existence and the fact that two dispatch constants resolve to the same factory are
  recorded. No material, no name, no lore, no blacklist.
- **notes:** the port's rename-with-anvil-GUI flow is entirely port-original.

### Holy White Scroll (`items/holy-white-scroll.yml`)

- **codex:** `16-item-builders.md` (item broker dispatch table); `11-masks.md` (one mask
  advertises `§c33% Holy White Scroll negation`)
- **likeness:** **codex-silent** — material, name and lore all live in an absent dungeons
  plugin. Two facts are recorded: it exists as a distinct item from the White Scroll, and
  one mask advertises a **33 % negation** of it (a headline that is **not implemented
  anywhere** in the corpus — matrix/11 already carries that as a mask-side note).
- **notes:** the keep-on-death semantics, the 7-protection corruption ladder and the
  `§e§l*§f§lHOLY§e§l* §f§lPROTECTED` line are all port-original. The one thing this pass
  adds is that a *mask* is supposed to counter it — so if the cosmic pack ever ships a
  holy scroll, the Monopoly mask's line becomes live and needs a decision.

### Godly Transmog (`items/godly-transmog.yml`)

- **codex:** no entry. **Codex-silent on every field**, and on the mechanic — the corpus
  records exactly one transmog item, the rarity-sorting scroll above. The manual reorder
  GUI is port-original (v3-directives §I/§K).

### Pet Food (`items/pet-food.yml`) ← Rare Candy

- **codex:** `12-pets.md` §C.2
- **likeness — VERBATIM:** material `Material.RED_MUSHROOM` (no data value)
  - name: `§c§lRare Candy`
  - lore, 8 lines:

    ```text
    §7Magical candy imbued with
    §7powerful growth hormones
    §7for inventory pets.
    (blank)
    §c§lAbility
    §7Apply to any inventory pet to instantly
    §7increase its level by +1 and trigger its
    §7ability cooldown.
    ```

- **mechanics:** cursor-drag onto a pet head. **+1 level, 100 %, no roll**; the pet's
  banked XP is **not** reset; the pet's cooldown stamp is refreshed (the "trigger its
  ability cooldown" the lore promises). Guards in order: max level (works) → pet on
  cooldown (**dead** — the same inverted arithmetic as every pet cooldown, matrix/12
  §A.6) → **once per pet item per 24 h** (`86 400 000 ms`, the only working rate limit,
  stored on the pet as `lastRareCandyUsed`) → dupe check.
- **strings — VERBATIM:**
  `§c§l(!) §cYou cannot use Rare Candy on a max level pet!` /
  `§c§l(!) §cYou cannot use Rare Candy on a pet on cooldown!` /
  `§c§l(!) §cYou cannot use another Rare Candy on this pet for: §n{time}` /
  `§c§l(!) §cRare Candy already used!` /
  success `§e§l(!) §e§l<Pet itemName>: §c§l+1 LEVEL! §7(Rare Candy)` + `LEVEL_UP`
  `3.0F`/`1.1F`
- **decomposition:** `type: pet-food` with `levels` and `{AMOUNT}` — a hit on the grant
  (the pack ships `levels: 10` against the jar's `+1`), and the cooldown-refresh side
  effect plus the 24 h per-item rate limit have no knob.
- **era:** `RED_MUSHROOM` era-stable.
- **gaps:** `ITEM_USE_RATE_LIMIT` (a per-target-item cooldown on an applied item, stored
  on the target, distinct from any ability cooldown), `APPLY_SIDE_EFFECT` (an apply that
  also stamps the target's ability cooldown).

### Weapon Reforge (`items/reforge.yml`)

- **codex:** no entry. **Codex-silent on every field and on the mechanic** — the corpus
  has an enchantment named `Reforged` (an axe/tool durability enchant, matrix/04) and
  nothing resembling a reforge *item* or a per-weapon signature-ability socket. The whole
  family is port-original (ADR-0070/0071) and must stay marked as such; the name collision
  with the enchant is the only thing worth noting so a future reader does not mistake one
  for the other.

### EMP Pulse (no pack likeness — noted, not ported)

- **codex:** `08-enchant-economy-items.md` §10.2
- **likeness — VERBATIM:** material `Material.REDSTONE_TORCH_ON`
  - name: `§3§lEMP Pulse §7(Right Click)`
  - lore, 8 lines:

    ```text
    §7Emits a large Electromagnetic Pulse,
    §7combat tagging ALL players within the
    §7device's radius. Players can only be
    §7affected by 1 EMP every 3 minutes.
    (blank)
    §3§nRadius:§b 7x128x7
    §3§nSoul Cost:§b 300 souls
    §3§nCooldown:§b 300s
    ```

- **mechanics:** right-click; **300 s** self cooldown, blocked within **7 s** of a
  teleport, blocked in PvP-disabled regions, costs **300 souls** (spent **before** the
  target scan, so a 0-target pulse still costs full). Scans a 7 × 128 × 7 half-extent box
  and combat-tags every visible survival-mode player not EMP'd within **180 s**, striking
  a cosmetic lightning effect on each.
- **strings — VERBATIM:** `§3§l** EMP Pulse **` (to each target) /
  `§3§l** EMP Pulse [§b{N} players affected§3§l] **` /
  `§c§l -300 SOULS (§f{remaining}§c§l)` /
  `§c§l *** NOT ENOUGH SOULS §c(REQ: 300)§l ***` /
  `You cannot use EMP Pulse for another §7{n}s` /
  `You cannot use this trinket so quickly after teleporting!` /
  `You cannot use this trinket in a PvP-disabled region.`
- **disposition:** the payload is **combat tagging**, an external server system with no
  engine analogue and no meaning off that server (same class as the outpost/faction
  gates). Recommend: note-only, no likeness, unless a use-item that costs souls and does
  nothing but tag is wanted as a curiosity. Recorded here so the §C sweep is complete.
- **era:** `REDSTONE_TORCH_ON` → `REDSTONE_TORCH`.

### Minor items with no pack home (noted, complete)

- **Experience Bottle** — `Material.EXP_BOTTLE`, name
  `§a§lExperience Bottle§7 (Throw)`, lore `§dValue §f{xp} XP` / `§dEnchanter §f{name}`.
  The XP value lives **only in lore text**. Pure economy (ruling R9), note-only.
- **Depth Strider Book** — `Material.ENCHANTED_BOOK` with a stored vanilla Depth Strider
  enchant and **no display name or lore at all** — the only item in the corpus built
  without a name. Vanilla passthrough, nothing to port.

## Gap declarations (unique to this doc; to be clustered in `proposed-primitives.md`)

- `BOOK_RESTRICTION_PAIR` — a gear-side "refuses ordinary enchant books" flag keyed by a
  family name, plus a book-side set-valued key field that lifts it for a matching family;
  the shard applies at 100 % into that set, one of each family, duplicates refused without
  consuming, and the refusal message is authored on the gear side. Params: gear
  `refuses-books: <family>`; item `grants-book-key: <family>`. **No half of this exists**
  — an authored shard alone unlocks a lock nobody can set. Consumers: `items/mastery-shard`
  (new), the four ability sets in matrix/10, the trader loop in §C.10.
- `PER_SLOT_HEROIC_STATS` — the heroic likeness's reduction, armour points and absolute
  max durability become per-slot maps rather than one uniform per-piece percent. Params:
  `percent-reduction: {helmet, chestplate, leggings, boots}`, `armor-points: {…}`,
  `max-durability: {…}`. Consumers: heroic armour (10/8/4.5/4.5 and 3/8/6/3 and
  810/1000/935/686), the M-Kit grade below.
- `ITEM_GRADES` — a second (third…) grade of an existing likeness type, each with its own
  material/name/lore and its own number band, selected at mint. Consumers: Heroic Black
  Scroll, Primal Dust, the M-Kit heroic tier (16/13/8/8 at halved armour points), the
  Anti-M-Kit crystal's own identity. **Absorbs** `SECOND_CRYSTAL_LIKENESS` and
  `HEROIC_GRADES`; this is the single highest-leverage gap in the doc — four items across
  three families want exactly one capability.
- `ITEM_RARITY_VARIANTS` — a likeness minted per rarity rung, taking the rung's name and
  colour as tokens, plus a `rarity-match` apply predicate (and a wildcard rung). Params:
  `variants: [<rung>…]`, `rarity-match: true|false`. Consumers: Magic Dust, Primal Dust,
  Secret Dust, Randomization Scroll, Mystery Book (already tier-scoped, would unify).
- `UNOPENED_ITEM` — right-click a container item to reveal a weighted random **minted
  item of any likeness type**, with per-tier weights and a per-mint numeric roll authored
  on the container. Params: `table: [{item, weight, roll}]`. Consumers: Random Heroic
  Upgrade (70/30 over six sets, success `1..100`), Secret Dust (75/23.75/1.25), the
  Mystery Pet Box (matrix/12 §C.1, out of scope by R3 but the same shape). **Generalises**
  the existing `type: unopened-book`.
- `ITEM_REFUSAL_PREDICATE` — an applied item may declare targets it **refuses**, distinct
  from the kinds it fits: refuse when the target carries a named family marker (a set
  piece), refuse when the target carries an enchant of a named class with a name
  whitelist. Params: `refuse-if: {family: …, enchant-class: …, except: [names]}`.
  Consumers: Armor Crystal (set pieces; mastery enchants except three), Heroic Upgrade
  (non-diamond, one named set), Black Scroll (equipped armour), Dust (heroic books,
  100 %-success books, stacked books), Randomization Scroll (heroic books, stacked books).
  Five items, one predicate.
- `APPLY_GAMBLE` — an applied item consumed **before** its roll, destroying itself on
  failure, with the chance carried per-item. Params: `consume-before-roll: true`,
  `success: <per-item double>`. Consumers: Armor Crystal (20 % default), Anti-M-Kit
  Crystal (1 % default), Heroic Upgrade (per-item). **Ruled out for crystals by ADR-0034 /
  R-QC53** — declared so the ledger row has a name, not because it should be built.
- `PER_ITEM_SLOT_CEILING` — an orb carries its own maximum-total-slots value rather than
  reading a pack constant, so orb grade is an item property. Params: `max-slots` on the
  minted orb. Consumers: Enchantment Orb (one consumer, low priority).
- `USABLE_ENCHANT_CAP` — a cap on how many of an item's enchants **function**, distinct
  from how many may be stored; the lowest tiers stay active, the rest are listed in a
  rate-limited lock notice. Params: `usable-cap`, `notice-cooldown`. Consumers: the book
  economy's rank ladder (one consumer; note the jar's ladder is permission-driven, so this
  may be R9 economy-adjacent — flag for the owner rather than assume).
- `INDEPENDENT_DESTROY_RATE` — a destroy chance decoupled from success. Consumers: every
  enchant book. **Recommend rejecting**: the engine's `100 − success` is a deliberate
  simplification and the jar's independent pair is what makes its `×1.15`/`−10` fudges
  invisible to players. Declared so the decision is recorded, not deferred.
- `ITEM_USE_RATE_LIMIT` — a per-**target-item** cooldown on an applied item, stored on the
  target. Params: `per-target-cooldown`. Consumers: Rare Candy (24 h per pet).
- `ITEM_HELP_HINT` — a right-click-in-hand help line on an applied item. Consumers:
  books, white scrolls, orbs, secret dust, EMP. Cosmetic; low priority.

## Proposed ledger rows (`deviations.md`, ids `D-13-n`)

| ID | Item | Measured jar behavior | Evidence | Proposed shipped behavior | Rationale |
| --- | --- | --- | --- | --- | --- |
| D-13-1 | `items/crystal` | The crystal is consumed **before** a default 20 % roll; four are burnt per piece landed | `10-armor-sets.md` §C.1 | Apply with no roll at all | ADR-0034 gesture rule, confirmed R-QC53; there is no success knob on the crystal likeness |
| D-13-2 | `items/crystal`, `items/heroic` | The overworld gate compares the **player's name** to the string `world_end`, so only a player literally named that gets the exemption | §C.1, §C.7 | No world gate (interaction-layer, off by default) | Typo-class infrastructure bug; world gates are off by default across the whole port |
| D-13-3 | `items/heroic` | The generic upgrade writes `ANY` while the wildcard branch tests `ALL`, so a generic upgrade is **unreachable** and fails with a doubled-space message | §C.7 | Heroic is not set-bound; every upgrade applies to any eligible gear | The port's heroic classification is gear-wide by design; the bug disappears with the concept |
| D-13-4 | `items/mastery-shard` (new) | Applying one shard clears the **entire cursor stack** (a stack of 64 is destroyed to apply one) | §C.9 | Decrement by one | Every sibling item decrements; this is a plain loss bug |
| D-13-5 | `items/heroic` | The heroic **weapon** bonus is a flat `+4` attack damage, and the axe's maxed Silence/Lifesteal grant is coupled to the cosmetic lore flag | §C.6 | `percent-damage` stays a percent (no codex value); no enchant grant on upgrade | Flat attack is not expressible on a percent knob; the enchant grant is a coupling bug and out of the likeness's scope |
| D-13-6 | `items/enchant-book` | Success is reduced by a flat `−10` and destroy multiplied by `×1.15` for everyone except two hard-coded account names; a third name is forced to 100 % | `08-…` §1.8 | Shipped rates are the authored rates | Hard-coded per-account special cases are never replicated (corpus-wide class) |
| D-13-7 | `items/enchant-book` | The success roll is `randInt(0..99) > success`, so 99 % and 100 % are both certain and 0 % is still 1 % | `08-…` §1.8 | Exact percent | Off-by-one; the orb's own roll in the same jar is already exact |
| D-13-8 | `items/enchant-book` | Destroy can exceed 100 (a 100-rolled book becomes 115) with no clamp | `08-…` §1.8 | Clamped | Missing clamp |
| D-13-9 | `items/white-scroll` | The scroll burns on **any** failed application, even when the destroy roll would not have fired | `08-…` §1.8 | **Keep as measured** — the pack already ships failure-insurance semantics | Measured-fidelity keep; recorded so the reading is deliberate, not accidental |
| D-13-10 | `items/slot-orb` | The stamped bonus baseline is **9** while the stored-enchant cap ladder tops out at **8**, and the apply gate is strictly-greater so a default player can add one past the cap | `08-…` §4.4, §1.11 | One slots ladder, `hard-cap` inclusive | Two ladders that disagree by one; the engine has a single source |
| D-13-11 | Secret Dust (if ported) | The primal branch multiplies the drawn bonus by 3 with a floor of **10**, so a `+0-1%` satchel can yield a 10 % dust | `08-…` §5.8 | Respect the advertised ceiling | The floor overrides the item's own printed band |
| D-13-12 | `items/pet-food` | The on-cooldown guard is dead (inverted arithmetic, matrix/12 §A.6); the 24 h per-pet limit is the only working one | `12-pets.md` §C.2 | No cooldown guard; the per-target limit is a gap, not shipped | Same dead-cooldown family as every pet entry; folded under D-12-* rather than re-litigated |
| D-13-13 | (crystals, family) | The multi-crystal name and the applied-gear line both leave a stray trailing colour code, and the lore's set line emits two consecutive colour sequences | §C.1 | **Keep verbatim** | Display strings never deviate (spec §6); recorded so nobody "fixes" it |

Cross-references, **not** new rows: the Infinite Luck negation reading the wrong player is
already **D-10-1**; the defensive double-fire is **D-001**; the heroic per-slot asymmetry
folded into one uniform percent is already carried as a pending note on
`items/heroic.yml` and becomes `PER_SLOT_HEROIC_STATS` above.

## Corrections this pass owes other documents

1. **`deferred-content.md` line 41 (`items/mastery-shard`)** — the claim "the matrix also
   records no verbatim name or lore for the item" is **wrong**: `10-armor-sets.md` §C.9
   records the material (`INK_SACK`), the per-set dye data (`7`/`2`/`6`/`6`), all four
   display names and the three-line lore verbatim. The row's *real* blocker stands
   unchanged and is the only one that matters: **the restriction it lifts has no
   surface**. Rewrite the row to cite `BOOK_RESTRICTION_PAIR`.
2. **`deferred-content.md` §C queue note (lines ~1207–1219)** — "§C records no verbatim
   name or lore for the crystal item, the upgrade item or the shard" is **wrong on all
   three**. The crystal (§C.1), the targeted upgrade (§C.7), the random upgrade (§C.7),
   the extractor (§C.8) and the shard (§C.9) are each recorded with material, name and
   full lore. Replace the note with a pointer to this doc.
3. **`packs-src/cosmic-pack/items/crystal.yml`** — drop the "(port-chosen)" note on
   `material`: `NETHER_STAR` is the recorded material, an exact hit. The `name` is exact.
   The `lore` and `lore-while-on-item` are not (the recorded forms are above, including
   the `§7` separator and the doubled colour codes).
4. **`packs-src/cosmic-pack/items/heroic.yml`** — "the matrix records no likeness for the
   upgrade item itself — no name, no material, no success rate" is settleable: material
   `INK_SACK:11` (`YELLOW_DYE`), the two name forms, the eight-line lore, and a per-item
   success that is uniform `1..100` on the random-upgrade path. `percent-damage` remains
   genuinely codex-silent **as a percent** (the jar's weapon bonus is flat `+4`).
5. **`packs-src/cosmic-pack/content/crystals/*.yml`** — every `description:` block is
   marked "not matrix-recorded (factual paraphrase)". The jar's own per-set crystal
   description lists **are** recorded (§A.2) and are reproduced above for all eight sets;
   the paraphrases can be replaced with verbatim text.
