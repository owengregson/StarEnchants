# Matrix 10 — Armor Sets

Source codex: `10-armor-sets.md` (8 plain sets + 4 ability sets = 12 entries, codex order).
Behavioral authority is the codex; this doc records the decomposition onto the engine
surface (`docs/reference/authoring-surface.txt` at HEAD).

Family-wide facts, recorded once (codex `10-armor-sets.md §A`):

- **Single pass.** The jar's set listener applies each set's attack/defense hook once per
  hit (attacker pass, then victim pass — §A.6). The defensive double-fire (deviations
  `D-001`) is an enchant-listener bug and never touches set hooks; all values below are
  single-pass measured values.
- **Application order on one hit (§A.6):** attacker set → attacker crystals → victim
  faction-upgrade (external, only if damage already moved) → victim set → victim
  crystals. All jar composition is multiplicative and order-dependent; the engine folds
  damage additively per the project-wide damage model (global ADR, not a per-item
  deviation).
- **Completion is all-or-nothing:** exactly 4 matching worn pieces (§A.4.2); no 2/3-piece
  tiers exist anywhere; two plain sets can never be active at once; plain + ability set is
  impossible. Set identity in the jar is forgeable lore matching (plain) / NBT (ability);
  the port uses engine PDC identity + WornState — the jar's 30 s stale-cache window and
  lore forgery are infrastructure-class bugs, not replicated (spec §6 class).
- **Crystal route (§A.7, §C.1):** an Armor Crystal on a worn non-set piece grants a
  per-piece fraction of a set's bonus; effect scales linearly with crystal-bearing pieces
  `n ∈ 1..4` (factor `1 ± pct × 0.01 × n`). Linear-in-n maps exactly onto one additive
  `DAMAGE_MOD` per crystal-bearing piece via the crystals family. One crystal (single or
  multi) per piece, max 4; refused on set pieces and on mastery-enchanted gear (whitelist:
  `Discombobulate`, `Explosives Expert`, `Lava Strider`); apply is a consumed-before-roll
  gamble (default 20 %).
- **Heroic armor interplay (§C.3–C.5):** heroic leather grants per-piece flat incoming
  reduction — chest `10 %`, legs `8 %`, boots/helm `4.5 %` (full set 27 %); ability-set
  (M-Kit) tier is chest `16 %`, legs `13 %`, boots/helm `8 %` (full set 45 %). Decomposes
  as per-piece `DAMAGE_MOD(side=defense, mode=add, amount=…)` component stats. Heroic
  pieces also negate Infinite Luck at `12.5 %` per piece (ledger: read from the wrong
  player in the jar) and amplify incoming damage from outpost-holding attackers
  (external server system; interaction-layer world/faction condition, off by default).
- **Outpost world gates** (TRAINEE/VANILLA/HERO block set bonuses, §A.8) → interaction-
  layer world conditions on `actor.world`; off by default in the port.
- **No cooldowns anywhere (§A.9):** every proc is an independent per-hit chance roll.

---

### Phantom (`armor-sets/phantom`)

- **codex:** `10-armor-sets.md § Phantom` (Part B), §A.6–A.7
- **activation:** set-complete gate (4/4 worn, engine WornState); triggers `ATTACK`
  (melee only — the jar never routes projectiles to this set), `DEFENSE`
- **decomposition:**
  1. `DEFENSE` → `REMOVE_POTION(effect=DAMAGE_RESISTANCE)` — unconditional; fires even
     in world-blocked regions (measured quirk, before any gate)
  2. `ATTACK` → `REMOVE_POTION(effect=DAMAGE_RESISTANCE)`
  3. `ATTACK` → `DAMAGE_MOD(side=attack, mode=add, amount=25)`
  4. `ATTACK` `[actor.helditem == armor-sets/phantom-scythe]` →
     `DAMAGE_MOD(side=attack, mode=add, amount=10)` — held-weapon rider; jar identity is
     an item tag that survives Heroic Sword conversion (retype to gold sword keeps the
     bonus); port: item identity survives reforge
- **interactions:** victim Infinite Luck `>= 3` suppresses the outgoing bonus
  (interaction-layer rule; highest threshold of any set); crystal grants
  `DAMAGE_MOD(side=attack, amount=5)` per crystal-bearing piece (n=1..4 → ×1.05..×1.20),
  no defensive crystal half; world-blocked in outpost tiers (attack side only);
  stacks with victim heroic reduction and faction upgrade multiplicatively in the jar
- **strings:**
  - pieces: `§c§lPhantom Hood`, `§c§lPhantom Shroud`, `§c§lPhantom Robeset`,
    `§c§lPhantom Sandals`
  - flavor (per piece): `§cThe fabled hood of the Phantom.` /
    `§cThe legendary shroud of the Phantom.` / `§cThe demonic robe of the Phantom.` /
    `§cThe silent sandals of the Phantom.`
  - shared bonus block: `§c§lPHANTOM SET BONUS` / `§cDeal +25% damage to all enemies.` /
    `§7(§oRequires all 4 phantom items.§7)`
  - weapon `§c§lPhantom Scythe`: `§7An eerie blade designed to` /
    `§7cut through the enemies.` / (blank) / `§c§lPHANTOM WEAPON BONUS` /
    `§cDeal +10% damage to all enemies.` / `§7(§7§oRequires all 4 phantom items.§7)`
  - equip message: `§c§lPHANTOM SET BONUS: §c+25% DMG`
- **numbers:** set ×1.25 outgoing (all victims, PvP+PvE, melee); scythe ×1.1 on top —
  combined ×1.375 measured (multiplicative, not the +35 % a player would infer);
  crystal +5.0 %/piece; Infinite Luck threshold 3; pieces: diamond, Durability III +
  Protection IV, nearly-maxed roll `min(M, max(1,M−2)+rand(3))` over rosters (hood:
  Deathbringer, Drunk, Enlighted, Implants, Tank, Armored, Angelic; shroud: Overload,
  Blood Lust, Enlighted, Armored, Tank, Dodge, Angelic; robeset: Deathbringer, Clarity,
  Enlighted, Obsidianshield, Tank, Armored, Angelic, Cactus; sandals: Gears, Enlighted,
  Deathbringer, Rocket Escape, Springs, Armored, Tank); scythe: Sharpness V,
  Unbreaking III, no custom enchants
- **era:** none beyond family norms (diamond armor, plain sounds absent); scythe is a
  diamond sword — fine on 1.8.9

---

### Yeti (`armor-sets/yeti`)

- **codex:** `10-armor-sets.md § Yeti`, §A.7
- **activation:** set-complete gate; triggers `ATTACK`, `DEFENSE` (melee only)
- **decomposition:**
  1. `DEFENSE` → `REMOVE_POTION(effect=DAMAGE_RESISTANCE)` →
     `DAMAGE_MOD(side=defense, mode=add, amount=10)` — −10 % incoming, all sources
     routed through entity-attack
  2. `ATTACK` → `REMOVE_POTION(effect=DAMAGE_RESISTANCE)` →
     `DAMAGE_MOD(side=attack, mode=add, amount=10)`
  3. `ATTACK` `[actor.helditem == armor-sets/yeti-maul && victim.type == PLAYER]`
     `[chance 75]` → `DURABILITY(target=armor, mode=damage, amount=1)` — jar rolls
     75 % per armor slot independently (expected strip 3/hit); engine expresses one
     whole-armor roll at the same expected value (distribution delta noted)
  4. `ATTACK` `[actor.helditem == armor-sets/yeti-maul && victim.type == PLAYER]` →
     `DAMAGE_MOD(side=attack, mode=add, amount=7.5)` — intended value (ledger)
  5. `ATTACK` `[actor.helditem == armor-sets/yeti-maul && victim.type == PLAYER]`
     `[chance 50]` → `IGNORE_HEROIC` — decomposition of the lore-intended
     "Bypass 50% of Heroic Armor" (ledger)
- **interactions:** victim Infinite Luck `>= 1` suppresses the outgoing bonus (lowest
  threshold, shared with Ranger); crystal grants `DAMAGE_MOD(side=attack, amount=2.5)`
  and `DAMAGE_MOD(side=defense, amount=2.5)` per crystal-bearing piece (4 → ×1.10 out /
  ×0.90 in); Maul identity accepts diamond axe ONLY in the jar — converting it to a
  Heroic Axe silently voids the branch (port: identity survives, record as intended
  survival with the reforge family); armor-strip feeds the durability/armor system
  (Repair Guard / Hardened interplay per the enchant docs); outpost world gates both
  hooks
- **strings:**
  - pieces: `§b§lYeti Facemask`, `§b§lBloody Yeti Torso`, `§b§lFuzzy Yeti Leggings`,
    `§b§lBig-Yeti Boots`
  - flavor: `§bThe savage mask of the Yeti.` /
    `§bThe impermeable chestplate of the Yeti.` /
    `§bThe cozy yet monstrous legs of the Yeti.` /
    `§bBigfoot has nothing on these boots.`
  - shared bonus block: `§b§lYETI SET BONUS` /
    `§b§l* §bDeal +10% damage to all enemies.` /
    `§b§l* §bEnemies deal 10% less damage to you.` /
    `§7(§oRequires all 4 yeti items.§7)`
  - weapon `§b§lYeti Maul`: `§7A penetrating axe used by a Yeti.` / (blank) /
    `§b§lYETI WEAPON BONUS` / `§bDeal +75% Durability Damage.` /
    `§bDeal +7.5% DMG to enemies` / `§bBypass 50% of Heroic Armor` /
    `§7(§7§oRequires all 4 yeti items.§7)`
  - equip message: `§f§lYETI SET BONUS: §f+10% ATK / -10% DMG` (white, not aqua —
    measured quirk, reproduce)
- **numbers:** set ×1.10 out / ×0.90 in; **Maul measured multiplier is
  `×(2.075 + 0.03 × victimHeroicPieces)` — codex-flagged bug** (a stray `+1.0` term more
  than doubles damage); as-intended ×1.075 (+7.5 % per lore) with 50 % heroic bypass —
  ledger row; measured table i=0..4 → ×2.075/×2.105/×2.135/×2.165/×2.195; strip: 75 %
  per slot, −1 durability, expected 3/hit; crystal ±2.5 %/piece; Infinite Luck
  threshold 1; helmet roster lists `Armored` twice (second roll wins — measured quirk);
  Maul: Sharpness V, Unbreaking III, Silence max, Lifesteal max, 25 % chance of Demonic
  Lifesteal at uniform 1..max; chest/legs/boots double-transmog vs helmet single
  (cosmetic sort quirk, engine renders lore from state so not replicated)
- **era:** none special; durability strip must route through the item-damage layer on
  both eras (synthetic `PlayerItemDamageEvent` on 1.8 harness per tester facts)

---

### Mother of Yijki (`armor-sets/mother-of-yijki`)

- **codex:** `10-armor-sets.md § Mother of Yijki`, §A.7, §A.16, §C.5
- **activation:** set-complete gate; triggers `DEFENSE` (main), `ATTACK`
  (held-weapon rider only); the jar's bow-support flag is inert for this set (measured)
- **decomposition:**
  1. `DEFENSE` → `DAMAGE_MOD(side=defense, mode=add, amount=30)` →
     `REMOVE_POTION(effect=DAMAGE_RESISTANCE)`
  2. `DEFENSE` `[chance 5]` → Revenge of Yijki (below); held World Ender
     (`[actor.helditem == armor-sets/yijki-world-ender]`) raises the chance to `6.25`
     (jar defensive check accepts the diamond form only — heroic-converted sword keeps
     the +20 % attack but loses the ×1.25 revenge chance; measured quirk, record)
  3. Revenge of Yijki — per strike-point payload chain (via gap `DELAYED_STRIKE_FIELD`):
     - phase 1, per point: `SOUND(sound=WITHER_SPAWN, volume=1.0, pitch=0.4)` +
       `PARTICLE(particle=SPELL_WITCH, count=32)` at ground and +1 Y
     - phase 1, target acquisition: `AOE(r=32, filter=ENEMIES)` around the wearer
       (jar box is 32/64/32 half-extents) → `MESSAGE(text=…, channel=chat)` warning
     - phase 2 (delay 20 t), per point: `LIGHTNING(damage=0)` (visual strike) +
       `SOUND(sound=WITHER_DEATH, volume=1.0, pitch=0.4)` +
       `PARTICLE(particle=EXPLOSION_LARGE, count=4)` at ground and +1 Y
     - phase 2, per victim within hit radius: `[victim.health > 17]` →
       `MODIFY_HEALTH(amount=16, mode=take)`; `[victim.health <= 17]` →
       `MODIFY_HEALTH(amount=1, mode=set)` — exact expression of the jar's
       raw-health floor ("can never kill")
  4. `ATTACK` → `REMOVE_POTION(effect=DAMAGE_RESISTANCE)`
  5. `ATTACK` `[actor.helditem == armor-sets/yijki-world-ender]` →
     `DAMAGE_MOD(side=attack, mode=add, amount=20)` — the set's ONLY offense
- **gaps:** `DELAYED_STRIKE_FIELD` — sample N ground points at independent per-axis
  offsets ±[min..max] from an origin (snap down to the highest block when below the
  origin Y), run a per-point cue chain immediately, acquire and warn targets in a range,
  then after delay-ticks run a per-point payload chain against entities within
  hit-radius of each stored point, re-validating target filters; points are independent
  (overlapping points multi-hit, measured); params: `points`, `offset-min`,
  `offset-max`, `delay`, `hit-radius`, `target-range`, phase-1/phase-2 effect chains;
  consumers: defensive strike-field procs in this family and the staff-form use-item
  of the same ability (`10-armor-sets.md` appendix). No existing primitive combination
  expresses "delayed one-shot execution at stored world points" — `REPEATING` is
  periodic, `MARK`/`MARKED` track entities not points, and no effect carries a delay.
- **interactions:** target filters are interaction-layer rules: victim Infinite Luck
  `>= 4` excluded; faction TRUCE-or-better excluded (engine `filter=ENEMIES`); PvP-deny
  regions excluded; victim Poltergeist level grants a `12.5 % × level` immunity roll per
  strike (ledger: the jar rolls the CASTER's level — ship target-side); Phoenix on the
  victim can absorb the strike; victim's external faction-upgrade tier reduces the 16
  base to 15/14/13/12 (external system, interaction-layer hook); World Ender lore
  `§fImmune to Rot and Decay` → interaction rule suppressing the `rot-and-decay`
  mastery enchant while held (cross-ref matrix/07); crystal: `DAMAGE_MOD(side=defense,
  amount=5)` per piece plus a revenge roll at `0.010000000000000002` per piece per hit
  (exact FP literal, = compile-time `0.05 × 0.2`; crystal lore advertises "20%" —
  measured value kept, advert mismatch recorded)
- **strings:**
  - pieces: `§f§lMask of Yijki the Destroyer of Worlds`,
    `§f§lMantel of Yijki the Destroyer of Worlds`,
    `§f§lRobeset of Yijki the Destroyer of Worlds`,
    `§f§lFootwraps of Yijki the Destroyer of Worlds`
  - flavor (verbatim incl. source typo `forebidden`):
    `§f§oThe god-mask of Yijki, imbued with` / `§f§othe power of absolute destruction.`;
    `§f§oThe forebidden mantel of Yijki,` / `§f§owoven from the dust of fallen stars.`;
    `§f§oThe fabled robes of Yijki,` / `§f§ohalf angelic, half demonic.`;
    `§f§oLight as a feather, heavy as` / `§f§oa mountain - the footwraps of Yijki.`
  - shared bonus block: `§f§lYIJKI SET BONUS` /
    `§f§l* §fEnemies deal 30% less damage to you.` /
    `§f§l* §fRevenge of Yijki Passive Ability` /
    `§7(§oRequires all 4 yijki items.§7)`
  - weapon `§f§lYijki's World Ender`: `§7An absolutely massive, corrupted` /
    `§7executioner blade imbued with` / `§7highly destructive dark magic.` / (blank) /
    `§f§lYIJKI WEAPON BONUS` / `§fDeal +20% damage to all enemies.` /
    `§f125% Revenge of Yijki Ability` / `§fImmune to Rot and Decay` /
    `§7(§7§oRequires all 4 yijki items.§7)`
  - equip message: `§c§lMOTHER OF YIJKI SET BONUS: §c-30% INCOMING DMG`
  - proc warning: `§5§l** REVENGE OF YIJKI (§c{caster} [1.5s]§5§l) **` — string says
    1.5 s, delay is 20 t = 1.0 s; strings never deviate, timing ships measured
  - immunity: `§4§l* POLTERGEIST [§7Immune: Mother of Yijki§4§l] *`
- **numbers:** −30 % incoming; revenge chance 5 % (6.25 % with sword held); 16 strike
  points, per-axis offset ±(2..9), +0.5/+0/+0.5, snap-down rule; delay 20 t; hit test
  `distanceSquared <= 2.0` (radius ≈ 1.414); damage 16 half-hearts raw, floor 1.0
  health, cannot kill, no de-duplication across overlapping points (measured); sword
  attack ×1.20; crystal −5 %/piece defense; Infinite Luck threshold 4; Poltergeist roll
  vs `[1.0, 101.0)` (a "100 %" level still fails ~1 % — measured); World Ender:
  Sharpness V, Unbreaking III, no custom enchants; piece rosters per codex (13/15/15/15
  entries, nearly-maxed rolls)
- **era:** sounds `WITHER_SPAWN`/`WITHER_DEATH` (1.8 names) → `ENTITY_WITHER_SPAWN` /
  `ENTITY_WITHER_DEATH` on modern; particle `WITCH_MAGIC` → `SPELL_WITCH` (1.8) /
  `WITCH` (modern registry); `LARGE_EXPLODE` → `EXPLOSION_LARGE`/`EXPLOSION_EMITTER`
  era split; visual-only lightning needs the no-fire/no-damage strike path on both eras

---

### Ranger (`armor-sets/ranger`)

- **codex:** `10-armor-sets.md § Ranger`, §A.7
- **activation:** set-complete gate; triggers `PASSIVE` (speed), `DEFENSE`
  (projectile-conditioned), `BOW` (held-bow rider); bow-support is real for this set
- **decomposition:**
  1. `PASSIVE` → `POTION(effect=SPEED, level=<gears-4 mapping>, duration=∞ while worn)`
     — "Gears IV"; speed value single-sourced from the `gears` enchant decomposition
     (matrix/01); permanent-while-worn passive-potion semantics
  2. `DEFENSE` `[damagecause == PROJECTILE]` →
     `DAMAGE_MOD(side=defense, mode=add, amount=25)` — ANY projectile damager (mob
     arrows, fireballs…), and the jar has no world gate on this path (measured quirk)
  3. `BOW` `[actor.helditem == armor-sets/ranger-bow]` →
     `DAMAGE_MOD(side=attack, mode=add, amount=30)`
  4. set-complete → `SUPPRESS(scope=KIND, key=TELEBLOCK, mode=timed)` maintained while
     worn — "Immune to Teleblock" (jar delivers this via an external flag; engine
     expresses it directly against the TELEBLOCK effect kind)
- **interactions:** victim Infinite Luck `>= 1` suppresses the bow bonus; crystal:
  `DAMAGE_MOD(side=defense, amount=5)` and `DAMAGE_MOD(side=attack, amount=6)` per
  piece, both projectile-conditioned (`[damagecause == PROJECTILE]` interaction
  condition); crystal-advertised `20% Immune to Teleblock` is absent from the jar —
  ledger row, shipped as a 20 %-per-piece teleblock-immunity chance; jar bow identity
  is a display-name prefix (any renamed bow qualifies — forgeable; port uses item
  identity, infrastructure-class fix); unequip strips the speed passive even when equip
  was world-blocked (jar quirk; engine WornState handles symmetric equip/unequip)
- **strings:**
  - pieces: `§a§lRanger Hood`, `§a§lRanger Cuirass`, `§a§lRanger Greaves`,
    `§a§lRanger Slippers`
  - flavor (verbatim incl. source typos — stray comma, `a ancient`):
    `§a§oThe hood of a legendary` / `§a§oRanger lost with time itself.`;
    `§a§oThe lost cuirass of the,` / `§a§olast known legendary archer.`;
    `§a§oThe fabled shin protectors,` / `§a§oof an ancient agile Ranger.`;
    `§a§oLightweight footgear for` / `§a§oa ancient nimble Ranger.`
  - shared bonus block: `§a§lRANGER SET BONUS` /
    `§a§l* §aEnemies bows deal 25% less damage to you.` /
    `§a§l* §aRanger Bow grants 30% increased bow damage.` /
    `§a§l* §aImmune to Teleblock` / `§a§l* §aGears IV` /
    `§7(§oRequires all 4 ranger items.§7)`
  - weapon `§a§lRanger Bow`: `§aA finely crafted bow imbued` /
    `§awith the energy of the Sun.` / (blank) / `§a§lRANGER WEAPON BONUS` /
    `§a§l* §aRanger Bow grants 30% increased bow damage.` /
    `§a§l* §aEnemies deal 25% less damage to you.` /
    `§7§o(Requires all 4 ranger armor items.)`
  - equip message: `§c§lRANGER SET BONUS: §c-25% INCOMING BOW DMG, +30% RANGER BOW DMG,
    Immune to Teleblock, Gears IV`
- **numbers:** −25 % incoming projectiles (unconditional, no world/luck gate — measured);
  +30 % with Ranger Bow (Infinite Luck threshold 1); crystal −5 / +6 %/piece
  (projectile-only), 4 pieces → ×0.80 in / ×1.24 out; combined 4-crystal + bow
  ×1.612 measured; bow enchants: Power V, Unbreaking III, Flame II, Infinity I,
  Sniper max, Lethal Sniper uniform 2–5 (hard-coded range — measured, can over-level),
  then nearly-maxed Silence, Rage, Eagle Eye, Arrow Lifesteal, Hellfire, Piercing,
  Blind, Venom, Snare, Virus, Healing; piece rosters 12 each per codex
- **era:** Flame II / Infinity are legal unsafe levels on 1.8; permanent Speed via
  potion re-application on 1.8 (no infinite-duration potions pre-1.19.4 — engine
  passive-potion path already era-splits)

---

### Supreme (`armor-sets/supreme`)

- **codex:** `10-armor-sets.md § Supreme`, §A.7, §A.13
- **activation:** set-complete gate; triggers `PASSIVE` (speed), `FALL`, `REPEATING`
  (food), `ATTACK`, `DEFENSE`
- **decomposition:**
  1. `PASSIVE` → `POTION(effect=SPEED, level=<gears-4 mapping>, duration=∞ while worn)`
     — Gears IV, same sourcing as Ranger
  2. `FALL` → `CANCEL` — no fall damage
  3. `REPEATING` (period 20 t) → `MODIFY_FOOD(amount=20, mode=give)` — "No Food Loss";
     the jar cancels food-level decreases outright; pinning food full at 1 s cadence
     delivers the same felt unit (brief dip-and-snap possible between ticks — recorded
     approximation, not a gap)
  4. `DEFENSE` `[damagecause == PROJECTILE]` →
     `DAMAGE_MOD(side=defense, mode=add, amount=-10)` — +10 % incoming arrow damage,
     the set's built-in drawback (negative defense amount = more damage taken); the
     jar gates on the damager being an ARROW specifically — `damagecause ==
     PROJECTILE` is wider (snowballs, fireballs, thrown potions), see gaps
  5. `DEFENSE` `[actor.helditem == armor-sets/supreme-fanny-pack]` →
     `DAMAGE_MOD(side=defense, mode=add, amount=10)` — −10 % incoming while HOLDING the
     weapon as you are hit (measured: defender's held item)
  6. `ATTACK` `[actor.helditem == armor-sets/supreme-fanny-pack]` →
     `DAMAGE_MOD(side=attack, mode=add, amount=20)` — intended value (ledger; measured
     jar multiplies the wielder's own outgoing damage by ×0.9 instead)
  7. `ATTACK` → `DAMAGE_MOD(side=attack, mode=add, amount=15)`
- **gaps:** `PROJECTILE_KIND_VAR — a comparison var discriminating the damaging
  projectile's kind (arrow | fireball | thrown | other); needed because damagecause
  exposes only PROJECTILE and cannot express arrow-only gates; consumers:
  armor-sets/supreme (arrow-only drawback); candidate consumers in the bow family
  (matrix/05) — consolidate at clustering, else record as an approximation + ledger row`
- **interactions:** victim Infinite Luck `>= 2` suppresses the outgoing bonus; crystal:
  `DAMAGE_MOD(side=attack, amount=3)` per piece (4 → ×1.12), no defensive half;
  `+200% Clout (Flight Enabled)` is implemented by an external economy plugin (codex
  UNRESOLVED) — lore ships verbatim, flight/clout ruling deferred to the owner
  (`FLY_MODE` exists if ruled in); the jar strips the speed passive from EVERY player
  crossing a world border regardless of set (measured infrastructure bug — engine
  WornState scopes unequip correctly, not replicated); fall/food immunities have no
  world gate in the jar (measured)
- **strings:**
  - pieces: `§4§lSupreme Headgear`, `§4§lSupreme Vest`, `§4§lSupreme Chaps`,
    `§4§lSupreme Thruster Boots`
  - flavor: `§4§oA lightweight headpiece` / `§4§ofit for take off.`;
    `§4§oAn aerodynamic vest that` / `§4§ois capable of sustaining flight.`;
    `§4§oLightweight and clout powered,` / `§4§oprovides enough thrust to boost` /
    `§4§oeven the most feeble into the skies.`;
    `§4§oSupreme boots capable of` / `§4§oconverting clout into flight.`
  - shared bonus block: `§4§lSUPREME SET BONUS` / `§4§l* §4Gears IV` /
    `§4§l* §4No Fall Damage / Food Loss` / `§4§l* §4Deal +15% damage to all enemies` /
    `§4§l* §4Enemy arrows deal 10% more damage to you.` / `§4§l* §4+200% Clout` /
    `§7(§oRequires all 4 supreme items.§7)`
  - weapon `§4§lSupreme Fanny Pack`: `§7They laughed at you, they` /
    `§7told you a fanny pack is not` / `§7a practical piece of clothing...` /
    `§7now they will pay for their ignorance!` / (blank) /
    `§4§lSUPREME WEAPON BONUS` / `§4Deal +20% damage to all enemies` /
    `§4Enemies deal 10% less damage to you.` /
    `§7(§7§oRequires all 4 supreme items.§7)`
  - equip message (7 lines): `§4§lSUPREME SET BONUS:` / `§4§l * §4+15% OUTGOING DAMAGE`
    / `§4§l * §4+10% INCOMING ARROW DAMAGE` / `§4§l * §4NO FALL DAMAGE` /
    `§4§l * §4NO FOOD LOSS` / `§4§l * §4GEARS IV` /
    `§4§l * §4+200% CLOUT (Flight Enabled)`
- **numbers:** +15 % out; +10 % incoming arrows (drawback); −10 % incoming with weapon
  held; **weapon-held attack measured ×0.9 — codex-flagged copy-paste bug** (net
  ×1.035 instead of the lore's ×1.38); as-intended ×1.2 — ledger row; crystal
  +3.0 %/piece; Infinite Luck threshold 2; item rolls: helmet 10 % Phoenix else second
  Marksman; chest Enlighted max always + 10 % Divine Enlighted; legs 10 % Nature Wrath
  else Protection; boots Springs fixed level 2 (only fixed-level roll in the family),
  10 % Deathbringer-max + Planetary Deathbringer else Deathbringer + Ghost, Depth
  Strider uniform 1–3; weapon: Sharpness V, Unbreaking III, Block max,
  Reflective Block max
- **era:** Depth Strider exists on 1.8.9; food pinning must use the food-level API
  (no saturation-effect shortcut — behavior differs); set color `§4` renders fine

---

### Dimensional Traveler (`armor-sets/dimensional-traveler`)

- **codex:** `10-armor-sets.md § Dimensional Traveler`, §A.7, §A.13
- **activation:** set-complete gate; triggers `ATTACK`, `DEFENSE` (proc source)
- **decomposition:**
  1. `ATTACK` → `DAMAGE_MOD(side=attack, mode=add, amount=30)` — highest unconditional
     multiplier in the family; NO Infinite Luck check (unique among damage sets,
     measured)
  2. `DEFENSE` `[actor.world contains "dungeon"]` → `CANCEL`-of-proc (flow `stop` on
     this set's defensive rules; world-substring gate, measured case-sensitive)
  3. `DEFENSE` → `REMOVE_POTION(effect=DAMAGE_RESISTANCE)`
  4. `DEFENSE` `[chance 1]` → Dimensional Shift (below); the jar also procs
     unconditionally for one hard-coded sneaking developer name — removed (ledger)
  5. Dimensional Shift, per target from `AOE(r=25, filter=ENEMIES)` (jar box 25/32/25):
     - `FREEZE(duration=80, slow=100, dot=0)` — full walk-speed immobilization, 4 s
     - `POTION(effect=BLINDNESS, level=1, duration=60)` — gated by the victim's
       Clarity enchant (interaction rule)
     - `POTION(effect=SLOW, level=1, duration=80)` — NOT Clarity-gated (measured)
     - `SOUND(sound=ENDERMAN_TELEPORT, volume=1.0, pitch=1.1)` +
       `SOUND(sound=ANVIL_LAND, volume=1.0, pitch=1.1)` at target +4 Y
     - `MESSAGE(text=…, channel=chat)` to the target
     - block field: `FALLING_BLOCK(material=END_STONE, radius=4, height=10, ttl=…,
       carry=…)` + `FALLING_BLOCK(material=NETHERRACK, radius=4, height=10, …)` with
       the field profile below (gap)
- **gaps:** `BLOCK_FIELD_PROFILE` — extended falling-block field parameterization the
  single-shape `FALLING_BLOCK` lacks: layer count with randomized per-layer Y offsets
  (jar: 3–4 layers, step `(12..19) × layerIndex` above +10 Y, skip outside world
  bounds), per-position spawn probability (50 %), multi-material palette (uniform
  END_STONE/NETHERRACK), victim-scaled carry damage (`percent-of-capped-max`:
  `0.15 × min(victim.maxhealth, 44)`), per-victim re-hit cap (max 4 hits per 10 000 ms
  rolling bucket, shared across all wearers), on-hit victim hooks (unfreeze + per-hit
  sound), and a block-kill counterplay material (cobweb check every 20 t); params:
  `layers-min/max`, `layer-step-min/max`, `density`, `materials[]`, `damage-percent`,
  `health-cap`, `rehit-max`, `rehit-window`, `kill-material`; consumers: area-denial
  summon fields in this family (and any future block-rain ability). Plain
  `FALLING_BLOCK` cannot express the re-hit cap or capped-percent damage, which set
  the ability's lethality ceiling (26.4 health per 10 s).
- **interactions:** target filters as interaction-layer rules: faction TRUCE+,
  god-mode, spectator/NPC, PvP-deny regions, victim Infinite Luck `>= 5` (highest
  threshold in the plugin); victim Poltergeist level rolls `12.5 % × level` vs an
  integer 1..100 — on success the target gains an 8 000 ms immunity window that the
  falling blocks also honor, plus the immunity message; Phoenix can absorb a block hit
  (block dies without damaging); blocks damage ANY qualifying player (area denial),
  wearer excluded; blocks never place terrain (engine FALLING_BLOCK is never-place by
  design — matches); crystal: `DAMAGE_MOD(side=attack, amount=7.5)` per piece
  (4 → ×1.30) and a shift roll at `0.002 × pieces` per incoming hit (crystal lore
  advertises "20%" vs measured 0.2 %/piece — measured kept, mismatch recorded);
  on-hit unfreeze is part of the field profile; frozen players stay frozen through
  teleports in the jar (dead code — engine FREEZE lifecycle handles teleport
  correctly, infrastructure-class)
- **strings:**
  - pieces: `§5§lInterdimensional Hood`, `§5§lChestplate of Ad Infinitum`,
    `§5§lTimeless Robes`, `§5§lWarp Speed Sandals`
  - flavor (verbatim incl. source typo `barer`):
    `§7A hood that fades in and out of` /
    `§7existence inside the dimension of the owner.`;
    `§7A cross-dimensional chestplate` / `§7with impossibly infinite density` /
    `§7and defensive properties.`;
    `§7Robes that evade the 4th dimension and` /
    `§7are in a state of constant existence` / `§7across all planets of reality.`;
    `§7Sandals capable of breaking through` /
    `§7dimensional barriers to allow the barer` / `§7to perform impossible feats.`
  - shared bonus block: `§5§lTRAVELER SET BONUS` /
    `§5§l* §5You deal 30% more damage.` /
    `§5§l* §5Dimensional Shift Passive Ability` /
    `§7(§oRequires all 4 dimensional traveler items.§7)`
  - equip message: `§c§lDIMENSIONAL TRAVELER SET BONUS: §c+30% DMG`
  - freeze message: `§5§l** DIMENSIONAL SHIFT (§c{relation-color}{caster} [4s]§5§l) **`
    (the `§c` is always overridden by the relation color in the jar — reproduce the
    redundant code point for byte-identical output)
  - immunity: `§4§l* POLTERGEIST [§7Immune: Dimensional Traveler§4§l] *`
- **numbers:** +30 % out, no incoming reduction; shift proc 1 %/incoming hit; freeze
  80 t, blind 60 t, slow 80 t; field: origin +10 Y, 3–4 layers (50/50), 9×9 grid,
  50 % density, ≈142 expected blocks/target; damage `0.15 × min(maxhealth, 44)` —
  20 HP → 3.0, 44+ HP → 6.6 flat; raw health subtraction, CAN kill (unlike Revenge);
  re-hit cap 4 per 10 s per victim (fixed bucket anchored at first check, measured);
  crystal +7.5 %/piece attack, shift `0.002 × n`; unfreeze restores stored walk speed
  or 0.2 default; no weapon (only weaponless plain set besides Dragon Slayer/KOTH)
- **era:** `ENDER_STONE` → `END_STONE` on modern (legacy alias needed both ways);
  sounds `ENDERMAN_TELEPORT`/`ANVIL_LAND`/`ZOMBIE_WOODBREAK` are 1.8 names →
  `ENTITY_ENDERMAN_TELEPORT`/`BLOCK_ANVIL_LAND`/`ENTITY_ZOMBIE_BREAK_WOODEN_DOOR`;
  falling-block entities differ per era (engine FALLING_BLOCK owns the NMS seam);
  walk-speed 0 freeze is era-safe (1.8 `setWalkSpeed`)

---

### Dragon Slayer (`armor-sets/dragon-slayer`)

- **codex:** `10-armor-sets.md § Dragon Slayer`, §A.7
- **activation:** set-complete gate; triggers `ATTACK` (PvP-conditioned), `DEFENSE`;
  passive suppression grants while worn
- **decomposition:**
  1. `DEFENSE` → `DAMAGE_MOD(side=defense, mode=add, amount=20)` — all entity-routed
     sources
  2. `ATTACK` `[victim.type == PLAYER]` → `DAMAGE_MOD(side=attack, mode=add, amount=15)`
  3. set-complete → `SUPPRESS(scope=KIND, key=FREEZE, mode=timed)` maintained while
     worn — "Immune to Freezes"
  4. set-complete → 75 % Silence negation: `[chance 75]`
     `SUPPRESS(scope=ENCHANT, key=silence, mode=next-hit)` authored as an
     interaction-layer rule against the Silence proc (jar delivers this via an external
     flag; exact external semantics unresolved — lore value is the contract)
  5. set-complete → 25 % mastery negation: `[chance 25]`
     `SUPPRESS(scope=GROUP, key=mastery, mode=next-hit)` — "25% Mastery Enchant
     Negation" (same sourcing caveat)
- **interactions:** NO Infinite Luck check on either hook (shared with KOTH — the only
  two damage-modifying sets without one; measured); "Mastery Enchant Reflect X" → the
  set contributes reflect level 10 to the mastery reflect ladder (integer-division
  chance table: level 10 → 5 % per enchant proc; cross-ref `00-MECHANICS.md §3.3` and
  the mastery doc); crystal: `DAMAGE_MOD(side=defense, amount=5)` per piece and
  `DAMAGE_MOD(side=attack, amount=3)` per piece (PvP-only condition on the attack
  half), PLUS a `10 % × pieces` chance to cancel an incoming `Silence` or `Ice Aspect`
  proc outright with the block message — the only crystal whose advertised numbers all
  match measured behavior at 1 piece; the full-set flag path and the crystal roll path
  are independent mechanisms and can both be active (measured); world-blocked in
  outpost tiers
- **strings:**
  - pieces (names end `§r` — reproduce; `Firey` is a source typo):
    `§e§lDecapitated Dragon Skull§r`, `§e§lFirey Chestplate of Dragons§r`,
    `§e§lScorched Leggings of Dragons§r`, `§e§lDragon Slayer Battle Boots§r`
  - flavor: `§e§oThe mythical {piece-type} of a Slayer of Dragons.` where
    `{piece-type}` ∈ helmet/chestplate/leggings/boots
  - shared bonus block (no full stop after "items" — verbatim):
    `§e§lDRAGON SLAYER SET BONUS` / `§e§l* §e+15% PvP Damage` /
    `§e§l* §e-20% Incoming Damage` / `§e§l* §eImmune to Freezes` /
    `§e§l* §eMastery Enchant Reflect X` / `§e§l* §eNegate 75% of Enemy Silence` /
    `§e§l* §e25% Mastery Enchant Negation` /
    `§7(§7§oRequires all 4 dragon slayer items§7)`
  - equip message (7 lines): `§e§lDRAGON SLAYER SET BONUS:` /
    `§e§l * §e+15% OUTGOING DAMAGE` / `§e§l * §e-20% INCOMING DAMAGE` /
    `§e§l * §eImmune to Freezes` / `§e§l * §eMastery Enchant Reflect X` /
    `§e§l * §eNegate 75% of Enemy Silence` / `§e§l * §e25% Mastery Enchant Negation`
  - crystal block messages: `§e§l* DRAGON SLAYER CRYSTAL [§7Silence blocked§e§l] *` /
    `§e§l* DRAGON SLAYER CRYSTAL [§7Ice Aspect blocked§e§l] *`
- **numbers:** −20 % in (all sources), +15 % PvP out; Protection V + Unbreaking III on
  every piece (Protection V is shared with KOTH; every other set is Protection IV);
  heroic-enchant roster gate 40 % per heroic entry; bonus rolls: helmet 30 % Immortal,
  chest 5 % Enchant Reflect max + Heroic Enchant Reflect, legs 7.5 % Nature Wrath max,
  boots 7.5 % Phoenix; **boots roster
  contains the misspelling `Gaurdians` — silently never applies (codex-flagged bug);
  as-intended: Guardians rolls on boots** — ledger row; crystal −5 / +3 (PvP) %/piece,
  10 %/piece Silence + Ice Aspect cancel; no weapon (help menu shows the external
  Dragon Mask in its slot — masks family, matrix/11)
- **era:** none special; suppression flags are engine-level (era-agnostic)

---

### KOTH (`armor-sets/koth`)

- **codex:** `10-armor-sets.md § KOTH`, §A.7, §C.3
- **activation:** set-complete gate; triggers `ATTACK`, `FALL`, `REPEATING` (auto-bless)
- **decomposition:**
  1. `ATTACK` `[victim.type == PLAYER]` → `DAMAGE_MOD(side=attack, mode=add, amount=20)`
  2. `ATTACK` `[victim.type != PLAYER]` → `DAMAGE_MOD(side=attack, mode=add, amount=50)`
  3. `ATTACK` `[actor.helditem == armor-sets/koth-sword && victim.type == PLAYER]` →
     `DAMAGE_MOD(side=attack, mode=add, amount=7.5)`
  4. `ATTACK` `[actor.helditem == armor-sets/koth-axe && victim.type == PLAYER]` `[chance 50]` →
     `DURABILITY(target=armor, mode=damage, amount=2)`, plus `[chance 25]` →
     `DURABILITY(target=armor, mode=damage, amount=1)` — jar rolls 50 % per slot for
     −2 or −3 (50/50); expected strip 5/hit; engine expresses whole-armor rolls at the
     same expected value per slot (1.25)
  5. `FALL` → `CANCEL` — no fall damage
  6. `REPEATING` (period 20 t, initial delay 100 t) → `CURE(category=HARMFUL)` —
     "Auto Bless" once per second; cleanse semantics single-sourced from the Blessed
     axe-enchant decomposition (matrix/04)
- **interactions:** no defensive component at all (glass cannon — measured); pieces are
  minted as Heroic red leather out of the box (armor 3/8/6/3 NOT halved, durability
  810/1000/935/686) → they feed the non-M-Kit heroic reduction (10/8/4.5/4.5 %, full
  set 27 %) and the outpost-holder incoming amplification drawback (external system);
  the KOTH axe/sword items are minted by an external plugin (codex UNRESOLVED — only
  their identity tags are read; port must define the weapons or gate the riders on the
  reforge-era heroic weapons); KOTH is excluded from Heroic Upgrade minting (a KOTH
  upgrade silently mints Ranger — measured, support-item rule); crystal:
  `DAMAGE_MOD(side=attack, amount=5)` per piece vs players, `amount=12.5` per piece vs
  non-players (4 pieces reproduce the full set's own multipliers); no Infinite Luck
  check
- **strings:**
  - piece name pattern:
    `§f§l§k!§r §c§lK§6§l.§e§lO§a§l.§b§lT§5§l.§d§lH §f§l§k!§r §f§l§n{Piece}§r` with
    `{Piece}` ∈ Helmet/Chestplate/Leggings/Boots (the enum-level banner variant uses
    `;` bookends — two different KOTH banners exist; reproduce both where each appears)
  - lore block: (blank) / `§d§lKOTH SET BONUS` / `§d§l* §d+20% PvP Damage` /
    `§d§l* §d+50% PvE Damage` / `§d§l* §dAuto Bless` / `§d§l* §dNo Fall Damage` /
    `§7(§7§oRequires all 4 koth items§7)` / (blank) /
    `§b§l(!) §bClaimed by §d§l{player}§b on §d§n{date}` — `by …` segment only when a
    claimant exists; unclaimed reads `§b§l(!) §bClaimed on §d§n{date}`; date format
    `EEE MM/dd/yy`
  - heroic appended lore: (blank) / `§7+{armor} Armor Value` / `§7{durability}
    Durability` / `§4This armor is stronger than diamond.`
  - equip message (5 lines): `§d§lKOTH SET BONUS` / `§d§l * §d+20% PvP DMG` /
    `§d§l * §d+50% PvE damage` / `§d§l * §dAuto Bless` / `§d§l * §dNo Fall Damage`
    (lore says `PvE Damage`, equip says `PvE damage` — both verbatim)
- **numbers:** ×1.20 PvP / ×1.50 PvE (highest PvE in the family); sword rider ×1.075
  PvP (combined ×1.29 measured); axe strip 50 %/slot of −2 or −3 (expected 5/hit;
  different formula from the Yeti Maul's 75 %/−1 — measured); bless cadence 20 t
  (initial 100 t); all piece enchants at MAX level, no random rolls (helm 7: Drunk,
  Deathbringer, Enlighted, Armored, Tank, Angelic, Implants; chest 8: Blood Lust,
  Overload, Deathbringer, Enlighted, Armored, Angelic, Tank, Dodge; legs 8: Clarity,
  Deathbringer, Enlighted, Armored, Tank, Angelic, Valor, Cactus + 7.5 % Nature Wrath
  max; boots 7: Deathbringer, Enlighted, Gears, Armored, Tank, Angelic, Rocket
  Escape); Protection V + Unbreaking III; crystal +5 / +12.5 %/piece
- **era:** `§k` magic text renders on both eras; red-dyed leather via LeatherArmorMeta
  is era-safe; extra armor value beyond the leather base needs the engine's heroic
  defense mods on 1.8.9 (no `generic.armor` attribute pre-1.9); gold-tool weapon
  identities are `GOLD_AXE`/`GOLD_SWORD` on 1.8 vs `GOLDEN_*` on modern

---

### Ghost (`armor-sets/ghost`)

- **codex:** `10-armor-sets.md § Ghost`, §A.10–A.15, §C.3–C.4
- **activation:** set-complete gate (NBT/PDC identity, 4/4 worn); ability sets have NO
  actives in this plugin — every "Ghost ability" is a mastery enchant rolled onto the
  pieces (pool below), decomposed in `matrix/07-enchants-mastery-soul.md`. Equipping is
  silent (empty equip description — measured).
- **decomposition:**
  1. per-piece component stats (heroic M-Kit tier): chest
     `DAMAGE_MOD(side=defense, mode=add, amount=16)`, legs `amount=13`, boots
     `amount=8`, helm `amount=8` — full set 45 % flat reduction, additive-then-fold
     exactly as measured
  2. armor values halved by the M-Kit rule: 2/4/3/2 (total 11 vs 20 non-M-Kit) —
     item attribute data
  3. boots: `Gears` at MAX level, guaranteed (cross-ref matrix/01 for the enchant)
- **interactions:** piece identity is data (a per-set NBT tag on the item → engine PDC
  component); pieces carry the no-normal-enchant-books restriction — lifted only by a
  matching Mastery Shard on the book (support-item rule, §C.9: shard applies 100 %, one
  of each set per book, whole-cursor-stack-loss jar bug not replicated); Anti-M-Kit
  Crystal (Ghost) on ANY single worn piece of a victim cancels this set's mastery-pool
  procs outright — 100 %, no roll (§C.6 as the codex's own prose numbers it; the
  codex heading is `### C.2` — source-side numbering drift; the crystal's percent is only its apply
  chance); Armor Crystals CAN be applied to M-Kit gear that carries no blocked mastery
  enchant (§A.15); mastery pool ties: Ghost is the only ability set that can roll
  `Infinite Luck` (12.5 % legs) — the same mechanic that negates plain-set bonuses
- **strings:**
  - pieces: `§3§lGhostly Hood`, `§3§lGhostly Shroud`, `§3§lGhostly Robes`,
    `§3§lGhostly Whisp`
  - flavor (verbatim incl. `Ghost don't have feet.`):
    `§7§oA tattered hood haunted by` / `§7§othe echoes of all lost souls.`;
    `§7§oA thin, sheer shroud that seems` / `§7§oto defy gravity in its movements.`;
    `§7§oA darkly woven silk robe imbued` / `§7§owith onyx gemstones that` /
    `§7§oemanate evil power.`; `§7§oGhost don't have feet.`
  - heroic appended lore: (blank) / `§7+2 Armor Value` (helm; +4 chest, +3 legs,
    +2 boots) / `§7810 Durability` (1000/935/686) /
    `§4This armor is stronger than diamond.`
  - no equip message (measured — empty)
- **numbers:** leather, armor color `#808080` (Bukkit GRAY); durability
  810/1000/935/686; Unbreaking III + Protection IV; base rosters and probability rolls
  per codex (helm 8 base + 60 % Valor/Angelic/Tank + 17.5 % Paladin Armored +
  17.5 % Alien Implants; chest 9 base + 60 %×3 + 20 % Paladin Armored + 17.5 % Godly
  Overload + 20 % Planetary Deathbringer + 20 % Divine Enlighted + 15 % Vengeful
  Diminish; legs 7 base + 60 %×3 + 15 % Paladin Armored + 12.5 % Infinite Luck; boots
  8 base + Gears max + 60 %×3 + 25 % Paladin Armored + 30 % Ethereal Dodge + 45 %
  Phoenix re-roll [overwrites the base roll — measured]); mastery pool (2 drawn
  without replacement, 50 % each): `Horrify`, `Feign Death`, `Poltergeist`,
  `Mark of the Beast`
- **era:** leather color era-safe; extra armor value needs defense mods on 1.8.9 (no
  armor attribute); nearly-maxed roll distribution reproduced by the pack minter

---

### Necromancer (`armor-sets/necromancer`)

- **codex:** `10-armor-sets.md § Necromancer`
- **activation:** identical frame to Ghost — set-complete NBT identity, no actives, no
  equip message; abilities are the mastery pool (matrix/07)
- **decomposition:** identical to Ghost items 1–3 (per-piece defense mods 16/13/8/8 =
  45 %; halved armor 2/4/3/2; boots `Gears` max guaranteed)
- **interactions:** as Ghost; additionally `Rot and Decay` (this set's pool) is the
  enchant Yijki's World Ender claims immunity to — the suppression rule is recorded on
  the Mother of Yijki entry; Anti-M-Kit Crystal (Necromancer) cancels all four pool
  enchants at 100 %
- **strings:**
  - pieces: `§2§lSkull of Souls`, `§2§lRobe of the Necromancer`,
    `§2§lTattered Wrappings of Death`, `§2§lDemon Foot`
  - flavor (verbatim incl. `magick` and the missing full stop):
    `§7§oA floating skull imbued with` / `§7§oevil enchantments and demonic energy.`;
    `§7§oSpider Silk woven robes that camouflage` /
    `§7§ointo the darkness of the night`;
    `§7§oOnce tormented by death, it now` / `§7§oloyally serves and protects you.`;
    `§7§oA good luck charm for those who are` /
    `§7§omasters of the dark magick of necromancy.`
  - heroic appended lore: same pattern as Ghost
- **numbers:** leather, armor color `#00FF00` (Bukkit GREEN — brighter than the shard
  dye, measured mismatch); rolls per codex (helm 8 base + 60 %×3 + 20 % Paladin
  Armored + 17.5 % Alien Implants; chest 9 base + 60 %×3 + 20 % + 17.5 % Godly
  Overload + 22.5 % Planetary Deathbringer + 22.5 % Divine Enlighted + 15 % Vengeful
  Diminish; legs 8 base [adds `Spirits` vs Ghost] + 60 %×3 + 20 % + 30 % Immortal;
  boots 8 base + Gears max + 60 %×3 + 20 % + 22.5 % Ethereal Dodge + 35 % Phoenix
  re-roll + 12.5 % Guided Rocket Escape); mastery pool: `Mortal Coil`, `Soul Siphon`,
  `Rot and Decay`, `Demonic Gateway`
- **era:** as Ghost

---

### Death Knight (`armor-sets/death-knight`)

- **codex:** `10-armor-sets.md § Death Knight`
- **activation:** identical frame to Ghost — set-complete NBT identity, no actives, no
  equip message; abilities are the mastery pool (matrix/07)
- **decomposition:** identical to Ghost items 1–3
- **interactions:** as Ghost; Anti-M-Kit Crystal (Death Knight) cancels the pool at
  100 %; shares its shard dye AND its entire pool with Architect (see that entry)
- **strings:**
  - pieces: `§9§lCrown of the Lich King`, `§9§lDeath Knight Platebody`,
    `§9§lDeath Knight Leggings`, `§9§lBoots of the Scourge`
  - NO flavor lore on any piece (built empty — measured); only the heroic appended
    block: (blank) / `§7+2 Armor Value` (per-piece values as Ghost) /
    `§7810 Durability` (1000/935/686) / `§4This armor is stronger than diamond.`
- **numbers:** leather, armor color `#2239B9` (decimal 2243001 — deep royal blue);
  rolls per codex (helm 9 base + 60 %×3 + 20 % Paladin Armored + 17.5 % Alien
  Implants + 35 % Immortal; chest 9 base + 60 %×3 + 20 % + 17.5 % Godly Overload +
  22.5 % Planetary Deathbringer + 22.5 % Divine Enlighted + 15 % Vengeful Diminish;
  legs 8 base + 60 %×3 + 20 % + 30 % Immortal; boots 8 base + Gears max + 60 %
  Valor/Angelic ONLY — the 60 % Tank roll is omitted on boots, the only such omission
  in the family [measured] — + 20 % + 22.5 % Ethereal Dodge + 12.5 % Guided Rocket
  Escape, and no extra Phoenix roll); the only ability set that can roll Immortal on
  two pieces (35 % helm, 30 % legs); mastery pool: `Chain Lifesteal`, `Death Pact`,
  `Tombstone`, `Permafrost`
- **era:** as Ghost

---

### Architect (`armor-sets/architect`)

- **codex:** `10-armor-sets.md § Architect`
- **activation:** identical frame to Ghost; set identity is the ONLY runtime
  distinction from Death Knight (measured — the class is a line-for-line copy)
- **decomposition:** identical to Death Knight in every particular — names, empty
  lore, rosters, probabilities, armor color `#2239B9`, mastery pool
- **interactions:** as Ghost/Death Knight; the set's display identity is
  `§b§lArchitect` (aqua) while its ITEMS are blue Death Knight branding — measured,
  reproduce; Anti-M-Kit interplay is codex-UNRESOLVED: the pool enchants almost
  certainly tag as Death Knight's, which would make an Architect Anti-M-Kit Crystal
  inert against them — owner ruling needed on whether the port tags the shared pool
  per-set (making both crystals live) or replicates the likely-inert measured wiring;
  shard item is visually identical to Death Knight's (same dye) apart from its name
- **strings:** pieces identical to Death Knight verbatim: `§9§lCrown of the Lich
  King`, `§9§lDeath Knight Platebody`, `§9§lDeath Knight Leggings`,
  `§9§lBoots of the Scourge`; no flavor lore; heroic appended block as Death Knight;
  set display name `§b§lArchitect`
- **numbers:** byte-identical to Death Knight (armor 2/4/3/2, durability
  810/1000/935/686, 45 % M-Kit reduction, same rolls incl. the missing boots Tank
  roll); mastery pool identical: `Chain Lifesteal`, `Death Pact`, `Tombstone`,
  `Permafrost`
- **era:** as Ghost
