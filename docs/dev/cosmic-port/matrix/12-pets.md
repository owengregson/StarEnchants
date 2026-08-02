# 12 — Inventory Pets

Source codex: `12-pets.md` (17 items, codex order). Behavioral authority is the
local-only codex; no decompiled code is quoted here.

## Family-level facts (shared by all 17 entries)

- **Item shape:** player-head item (legacy `SKULL_ITEM:3`), display name
  `{itemName}§7 [LVL {level}]`, per-type skull texture. Held in the hotbar;
  the ability fires on right-click. The jar's 1 Hz hotbar ticker is a no-op for
  every pet (codex §A.2) — nothing passive is ported.
- **Engine mapping:** pets are use-items — trigger `USE` on the held pet item.
  The jar has **no action filter** (left-click and physical interactions fire
  too, codex §A.2 quirk); as intended this is right-click only → `USE`
  (ledger: pending).
- **Spectator guard (all pets):** jar checks a spectator-mode flag on the
  player; engine mapping is `actor.gamemode == "SPECTATOR" → MESSAGE, stop`.
  String verbatim:
  `§c§lPET: §cYou cannot use that ability while spectating!`
- **Cooldown template (all pets):** the jar computes remaining time as
  `cooldown − now − last-used`, which is always a large negative number, so
  **no pet cooldown ever blocks and the on-cooldown branch is dead code** (codex
  §A.6 — the single most consequential bug in the plugin). The matrix records
  the **as-intended** cooldown per entry (spec §6 analogue: intended values);
  every entry has a ledger row. Engine expression, fully on-surface:
  1. `when var pet-cd-<slug> set → MESSAGE(text="§c§lPET: §cAbility on cooldown: §c§n{time}", channel=chat), stop`
  2. …payload…
  3. `SET_VAR(name=pet-cd-<slug>, ttl=<per-level cooldown ticks>)`
  Jar cooldown state is a per-item NBT timestamp; the engine window is
  per-player. Unobservable in the measured jar (cooldowns never applied), so
  not a ledger row. The jar's op-while-sneaking bypass is unreachable dead
  code and is not ported.
- **Levelling/XP framework (all pets):** XP comes **only** from using the pet's
  own ability (never from kills); each entry lists its grant and curve.
  Each per-level threshold N is the cost from level N−1 → N. At most one
  level-up per grant, remainder banked; at cap, XP accumulates unbounded. The
  EXP lore bar
  is 50 segments but the jar's integer division renders it **always empty below
  the cap and overflowing green past 50 segments at the cap** (codex §A.5);
  intended: proportional fill clamped at full (ledger: pending). This whole
  track is the `ITEM_XP_TRACK` gap (declared once, consumed by every entry).
- **Shared strings (verbatim, placeholders as brace tokens):**
  - XP gain (14 pets): `§a§l+ §a{xp} Pet EXP §a§l[§7{exp}/{needed}§a§l]`
    (jar quirk kept: `{exp}` is post-grant, `{needed}` is the post-level-up
    requirement on the activation that levels you)
  - Level-up: `§a§l*** {item-name-with-old-level} §a has increased to level §a§n{level}§a§l ***`
    — jar reads the pre-rebuild display name, so the name shows the *old*
    level; rendered example:
    `§a§l*** §e§lLava Elemental Pet§7 [LVL 1]§a has increased to level §a§n2§a§l ***`
  - Message prefix pattern: `{c}§lPET ({c}{Pet Name}{c}§l){c}:§f {msg}` where
    `{c}` is `§c` (fail) or `§a` (success)
  - Corrupt pet: `§c§l(!) §cCorrupted Inventory Pet found?`
  - Lore tail (every pet, after the description block):
    `§7` / `§f§lLevel` / `§7 {level}` / `§7` / `§f§lEXP` / `{bar}` / `§7 ({exp}/{needed})`
- **Out of scope (ruling R3, delivery items — existence noted, not
  decomposed):** Mystery Pet Box (ender chest; 20 % rare tier / 80 % common;
  five pets — Stronghold Sell, Raid Creeper, Vile Creeper, Smite, World
  Destroyer — are listed in its lore at **0 %** actual chance, codex §C.1),
  Rare Candy (+1 level, no XP, exp not reset, real 24 h per-item gate, codex
  §C.2), and the Evolution-Kits `/vkit` flows (codex §C.3).
- **Cross-feature (family-wide):** pet skulls are invalid helmet items (the
  masks family rejects wearing any pet head: `§c§l(!) §cYou cannot wear this
  item!`) — authored as an interaction-layer rule against the masks/helmet
  slot, not folded into any gap. Defensive double-fire (00-MECHANICS §3) does
  not touch this family directly — pets are activation items, not defensive
  procs; where a pet triggers a shared enchant routine (Feign Death, Gaia),
  the values here are single-pass intended values consistent with D-001.
- **Era (family-wide 1.8.9 notes):** every sound name in this codex is a
  legacy 1.7/1.8 name (`LAVA_POP`, `SPLASH`, `VILLAGER_NO`, `DIG_SNOW`,
  `WITHER_SHOOT`, `ENDERDRAGON_GROWL`, `GHAST_SCREAM2`, `ANVIL_LAND`,
  `NOTE_PLING`, `ORB_PICKUP`, `LEVEL_UP`, `ITEM_PICKUP`, `VILLAGER_YES`,
  `CHICKEN_EGG_POP`) — route through the cross-version sound resolver both
  ways. `SKULL_ITEM:3` → `PLAYER_HEAD` on modern; skull textures need the
  profile-based head codec on both trees. Titles are fine on 1.8.9.

## Gap declarations (unique to this doc; clustered in `proposed-primitives.md`)

- `ITEM_XP_TRACK` — per-item experience/level progression held in the item's
  component store: an effect-side grant (`amount`, optional `window` ms gate
  that limits grants to once per window), config-side per-level thresholds and
  a level cap, one level-up per grant with remainder banking, unbounded banking
  at cap, and the item's level selecting its per-level param tier; level and
  EXP-bar lore rendered from state. Params: `amount`, `window`. No existing
  primitive touches item-attached progression (MODIFY_EXP is vanilla player
  XP). Consumers: all 17 entries here (and the out-of-scope Rare Candy flow).
- `INVENTORY_CONVERT` — replace up to `limit` items of material `from`
  (meta-less only when `plain=true`) with material `to` across the whole
  inventory, returning overflow from a cap-straddling stack as `from` with
  drop-protection; exposes the converted count to the activation (for dynamic
  XP and the zero-converted failure branch). Params: `from`, `to`, `limit`,
  `plain`, `protect-seconds`. REMOVE_ITEM/GIVE_ITEM cannot express "up to N",
  the count-dependent XP, or the zero-count failure. Consumers:
  `pets/lava-elemental`, `pets/water-elemental`.
- `VANISH_DECOY` — hide the actor from all players, spawn a static decoy
  corpse at the actor's position for `corpse-ticks`, then un-hide after
  `duration` **or** after `max-hits` outgoing hits by the vanished actor,
  whichever first; emits vanish/unvanish feedback hooks. Params: `duration`,
  `corpse-ticks`, `max-hits`. No combination of POTION(INVISIBILITY)/SPAWN_ENTITY
  gives true hide-player semantics or a hit-capped early end. Consumers:
  `pets/feign-death` (and the GHOST-set Feign Death mastery in matrix/07 —
  same capability, shared window).
- `STATUS_CLEAR` — remove an active engine status window of `kind` from the
  selected entity (first kind: TELEBLOCK), with a paired condition var
  (`status.teleblock BOOL`) for guards. Params: `kind`. CURE/REMOVE_POTION
  only touch potion effects; TELEBLOCK is an engine status with no clear
  primitive and no queryable var. Consumers: `pets/anti-teleblock`.
- `SOUL_COST_EXEMPT` — for `duration`, actions that would deduct souls from
  the actor deduct none; exposes the per-cancel amount so feedback can be
  thresholded (jar: message only above 10 souls). Params: `duration`,
  `feedback-threshold`. Souls are an engine economy with no exemption
  primitive (REMOVE_SOULS only spends). Consumers: `pets/tesla`.
- `BOOK_RATE_MODIFIER` — arm a one-shot additive percent modifier on the next
  enchant-book-economy roll at `site` (`generate` = scroll strips gear and
  mints a book; `apply` = a book is applied to gear), consumed on the next
  roll regardless of outcome; paired armed-condition var per site so a second
  arm can be refused. Params: `site`, `percent`. The book economy's success
  rolls are engine-internal; no var/SET_VAR combination can inject into them.
  Consumers: `pets/blackscroll` (generate), `pets/enchanter` (apply).

---

### Lava Elemental Pet (`pets/lava-elemental`)

- **codex:** `12-pets.md § Lava Elemental Pet` (+ §A.5, §A.6)
- **activation:** `USE` (held pet item); conditions: spectator guard, cooldown
  var gate (family template)
- **decomposition:**
  1. `when var pet-cd-lava set → MESSAGE(cooldown), stop`
  2. `INVENTORY_CONVERT(from=BUCKET, to=LAVA_BUCKET, limit=<maxFill(level)>, plain=true, protect-seconds=60)` *(gap)*
  3. fat-bucket top-up: +`<fatFill(level)>` charges to every Fat Bucket, hard
     cap 256 per bucket — Fat Buckets are an external item (Arkkit patch,
     codex-UNRESOLVED); numbers recorded here, decomposition deferred until a
     fat-bucket item exists in the port (see interactions)
  4. `when converted == 0 → SOUND(sound=VILLAGER_NO, volume=3.0, pitch=0.7), MESSAGE(text="§c§lPET (§cLava Pet§c§l)§c:§f No empty buckets in your inventory!"), stop`
     (jar failure path also un-stamps the cooldown — intended: no cooldown
     burned on failure; expressed by ordering SET_VAR after this gate)
  5. `SOUND(sound=LAVA_POP, volume=1.1, pitch=1.0)`
  6. `ITEM_XP_TRACK(amount = converted + fat-bucket charges added × stack size)` *(gap)* + XP-gain MESSAGE
  7. `SET_VAR(name=pet-cd-lava, ttl=6000)`
- **gaps:** `INVENTORY_CONVERT`; `ITEM_XP_TRACK`
- **interactions:** Fat Buckets (external Arkkit patch; cap 256 enforced in
  pet code and exact). Fat-bucket branch is not limited by the bucket budget,
  and XP credit multiplies charges by stack size (a stack of 64 at L10 credits
  256 × 64 = 16 384 XP from one slot) — measured and kept: the credit equals
  the charges actually added. No enchant/set/mask interaction.
- **strings:** name `§e§lLava Elemental Pet§7 [LVL {level}]`; lore body
  (rendered) `§7§7A fiery pet that is` / `§7§7capable of conjuring lava` /
  `§7§7and giving it to its owner.` / `§7` / `§7§f§lAbility` /
  `§7§7Fills up to {maxFill}x` (L10: `§7§7Fills up to ALL`) /
  `§7§7empty buckets in your` / `§7§7inventory into lava!` /
  `§7§f§l      +` / `§7§7Adds {fatFill}x Fat Bucket` (L10: `256x`) /
  `§7§7charges to all Fat Buckets!` / `§7` / `§7§f§lCooldown` /
  `§7§7 5 Minutes`; failure and XP strings as above.
- **numbers:** maxFill = `level × 32` (32…288), **L10 = 1152** (lore `ALL`);
  fatFill = `level × 25` (25…225), **L10 = 256**; XP/use dynamic (buckets
  converted + weighted fat charges); curve `level × 1000` (L2 2000 … L10
  10000), cap 10, total 54 000. Cooldown: measured **0 s** (inverted-cooldown
  bug) → intended **300 s** (6000 t) (ledger: pending). Known bug: the
  cap-straddling stack is inverted — the jar converts the *overflow* and hands
  back the *fitting* portion; intended: convert up to the cap, return the
  overflow (ledger: pending).
- **era:** `LAVA_POP`/`VILLAGER_NO` legacy sound names; buckets identical on
  both trees; player-head codec.

### Water Elemental Pet (`pets/water-elemental`)

- **codex:** `12-pets.md § Water Elemental Pet`
- **activation:** `USE`; family guards
- **decomposition:**
  1. `when var pet-cd-water set → MESSAGE(cooldown), stop`
  2. `INVENTORY_CONVERT(from=BUCKET, to=WATER_BUCKET, limit=<maxFill(level)>, plain=true, protect-seconds=60)` *(gap)*
  3. `when converted == 0 → SOUND(sound=VILLAGER_NO, volume=3.0, pitch=0.7), MESSAGE(text="§c§lPET (§cWater Pet§c§l)§c:§f No empty buckets in your inventory!"), stop`
  4. `SOUND(sound=SPLASH, volume=1.1, pitch=1.0)`
  5. `ITEM_XP_TRACK(amount = converted)` *(gap)* + XP-gain MESSAGE
  6. `SET_VAR(name=pet-cd-water, ttl=6000)`
- **gaps:** `INVENTORY_CONVERT`; `ITEM_XP_TRACK`
- **interactions:** none with enchants/sets/masks.
- **strings:** name `§e§lWater Elemental Pet§7 [LVL {level}]`; lore body
  `§7§7A watery pet that is` / `§7§7capable of extracting water` /
  `§7§7from the air and condensing it.` / `§7` / `§7§f§lAbility` /
  `§7§7Fills up to {maxFill}x` (L10 `ALL`) / `§7§7empty buckets in your` /
  `§7§7inventory into water!` / `§7` / `§7§f§lCooldown` / `§7§7 5 Minutes`.
- **numbers:** maxFill identical to Lava (`level × 32`, L10 = 1152); XP/use =
  buckets converted; curve `level × 500` (L2 1000 … L10 5000), cap 10, total
  27 000 — exactly half Lava's for identical work. Cooldown: measured 0 s →
  intended **300 s** (6000 t) (ledger: pending). Same inverted partial-fill
  bug as Lava; intended: convert up to cap, return overflow (ledger: pending).
- **era:** `SPLASH` legacy sound name.

### Feign Death Pet (`pets/feign-death`)

- **codex:** `12-pets.md § Feign Death Pet` (+ §A.6; mastery coupling)
- **activation:** `USE`; conditions: spectator guard, shared 10 s feign
  window, cooldown gate
- **decomposition:**
  1. `when var feign-window set → MESSAGE(text="§c§l(!) §cYou must wait at least 10s in between feign death use!"), stop`
     — `feign-window` is the same var the GHOST-mastery Feign Death sets; the
     jar's shared 10 s expire-after-write window is the **only functioning
     cooldown any pet has**
  2. `when var pet-cd-feign set → MESSAGE(cooldown), stop`
  3. `SET_VAR(name=feign-window, ttl=200)`
  4. `VANISH_DECOY(duration=<level × 30 ticks>, corpse-ticks=30, max-hits=<level>)` *(gap)*
  5. `MESSAGE(channel=title, text="§c§lFeign Death", subtitle="§c{seconds}")`
  6. `MESSAGE(text="§4§l* Feign Death - VANISHED [{seconds}s] *")`
  7. `SOUND(sound=DIG_SNOW, volume=1.1, pitch=3.0)`, `SOUND(sound=WITHER_SHOOT, volume=1.1, pitch=3.0)`, `SOUND(sound=WITHER_SHOOT, volume=3.0, pitch=0.9)`
  8. XP: `when recentattackers > 0 → ITEM_XP_TRACK(amount=5)` else
     `ITEM_XP_TRACK(amount=1)` *(gap; in-combat mapped to `recentattackers`;
     warzone tier collapses — see numbers)* + XP-gain MESSAGE
  9. `SET_VAR(name=pet-cd-feign, ttl=12000)`
  - un-vanish (timer or hit-cap) emits `§4§l* Feign Death - UNVANISHED *`
- **gaps:** `VANISH_DECOY`; `ITEM_XP_TRACK`
- **interactions:** shares the 10 s window, vanish state, and hit counters
  with the GHOST-set Feign Death mastery (matrix/07): using the pet consumes
  the mastery's window and vice-versa; no stacking with a natural proc —
  authored as the shared `feign-window` var + condition, interaction layer.
  The pet bypasses the mastery's proc roll (`level + (1 − hp%) × 5`, × 0.01)
  and guarantees the vanish. Un-vanish does not re-show spectating players.
- **strings:** name `§e§lFeign Death Pet§7 [LVL {level}]`; lore body
  `§7§7A ghastly pet that can` / `§7§7control death itself.` / `§7` /
  `§7§f§lAbility` / `§7§7Fake your own death.` / `§7§7[§4Feign Death§7]` /
  `§7` / `§7§f§lCooldown` / `§7§7 10 Minutes`; window/vanish/unvanish strings
  above; title `§c§lFeign Death` / subtitle `§c{seconds}`.
- **numbers:** vanish = `level × 1.5 s` → 1.5/3.0/4.5/6.0 s (30/60/90/120 t);
  max-hits before early un-vanish = level (1–4); corpse despawn 30 t; XP 1
  (out of combat) / 5 (in combat) / 50 (in combat + warzone — jar quirk: an
  out-of-combat warzone use still yields 1); the warzone tier depends on
  faction territory, which has no engine analogue → collapses to the
  in-combat 5 (ledger: pending). Curve `level × 750` (1500/2250/3000), cap 4,
  total 6750. Cooldown: measured 0 s (only the 10 s window blocks) → intended
  **600 s** (12 000 t) + the shared 10 s window (ledger: pending).
- **era:** hide/show-player and packet decoy differ per era — VANISH_DECOY
  implementation hazard (1.8 datawatcher vs modern metadata); titles OK on
  1.8.9; legacy sound names.

### Evolution Pet (`pets/evolution`)

- **codex:** `12-pets.md § Evolution Pet` (+ §C.3, §A.8)
- **activation:** `USE`; conditions: spectator guard, daily token, cooldown
  gate
- **decomposition:**
  1. `when var evo-token set → MESSAGE(text="§c§l(!) §cYou can only use 1 /vkit pet per account, per day."), stop`
  2. `when var pet-cd-evolution set → MESSAGE(cooldown), stop`
  3. payload: raise the player's lowest-level eligible Evolution Kit by 1
     (selection: kits with level `> 0` and `< 10` the player has access to;
     min level; uniform pick among ties) — **Evolution-Kits flow, OUT OF SCOPE
     (ruling R3)**; existence recorded, not decomposed
  4. bonus double proc: `chance (level × 1%) →` repeat payload (no XP, no
     success message; jar prints a raw unprefixed `No /vkit found to level
     up!` if the bonus fails)
  5. `SET_VAR(name=evo-token, ttl=1728000)` (24 h)
  6. `SOUND(sound=LAVA_POP, volume=1.1, pitch=1.0)` per successful level-up;
     failure: `SOUND(sound=VILLAGER_NO, volume=3.0, pitch=0.7)`
  7. `ITEM_XP_TRACK(amount=500)` *(gap; primary proc only)* + XP-gain MESSAGE
  8. `SET_VAR(name=pet-cd-evolution, ttl=3456000)` (48 h)
- **gaps:** `ITEM_XP_TRACK` (payload is out-of-scope, not a gap)
- **interactions:** CosmicEvolutionKits (external hard dependency; out of
  scope R3). No enchant/set/mask interaction.
- **strings:** name `§e§lEvolution Pet§7 [LVL {level}]`; lore body
  `§7§7One of the more helpful members` / `§7§7of the mystical Mimic race,` /
  `§7§7this object has the power to` / `§7§7grant its owner extreme power!` /
  `§7` / `§7§f§lAbility` / `§7§7Increases level of lowest /vkit by 1,` /
  `§7§7and a {level}% chance to double level up.` / `§7` / `§7§f§lCooldown` /
  `§7§7 48 Hours`; success
  `§a§lPET (§aEvolution Pet§a§l)§a:§f §a"{kit}§a" leveled to Level §a§n{level}§a!`;
  failures `§c§lPET (§cEvolution Pet§c§l)§c:§f No /vkit found to level up!` /
  `… Invalid player data!`; bonus-proc failure raw `No /vkit found to level up!`.
- **numbers:** double-proc chance `0.01 × level` (1 %…10 %); XP 500/use;
  curve flat 1000, cap 10, total 9000 (18 uses to cap). Cooldown: measured
  0 s → intended **172 800 s / 48 h** (3 456 000 t) (ledger: pending). Token:
  measured once per server session (metadata, documented "per day") →
  intended 24 h (ledger: pending); jar burns the token even when the primary
  use fails → intended: burned only on success (ledger: pending).
- **era:** none beyond family notes.

### Anti Teleblock Pet (`pets/anti-teleblock`)

- **codex:** `12-pets.md § Anti Teleblock Pet`
- **activation:** `USE`; conditions: spectator guard, teleblock-active guard,
  cooldown gate
- **decomposition:**
  1. `when NOT status.teleblock → SOUND(sound=VILLAGER_NO, volume=3.0, pitch=0.7), MESSAGE(text="§c§l(!) §cYou must be affected by Teleblock to activate this pet!"), stop`
     *(condition var provided by the STATUS_CLEAR gap)*
  2. `when var pet-cd-antitb set → MESSAGE(cooldown), stop`
  3. `STATUS_CLEAR(kind=TELEBLOCK)` *(gap)*
  4. `SOUND(sound=LAVA_POP, volume=1.1, pitch=1.0)`
  5. `ITEM_XP_TRACK(amount=10)` *(gap)* + XP-gain MESSAGE
  6. `MESSAGE(text="§a§lPET (§aAnti Teleblock Pet§a§l)§a:§f Teleblock removed!")`
  7. `SET_VAR(name=pet-cd-antitb, ttl=<per level: 2400/2280/2160/2040/1920/1800/1680/1560/1440/1320>)`
- **gaps:** `STATUS_CLEAR`; `ITEM_XP_TRACK`
- **interactions:** the TELEBLOCK effect (bow family, matrix/05). This pet is
  the only *removal*; the three preventions (RANGER set immunity, GLITCH mask
  immunity, RANGER bonus crystals at `0.2 × crystals`) are recorded with
  their owners (matrix/10, matrix/11) — interaction-layer rules, not folded
  here. Upstream teleblock quirks (ms-vs-seconds duration metadata, `[-0ep]`
  message) belong to the bow entry.
- **strings:** name `§e§lAnti Teleblock Pet§7 [LVL {level}]`; lore body
  `§7§7An elusive creature that` / `§7§7has the power to negate` /
  `§7§7curses that prevent the use` / `§7§7of enderpearl teleportation.` /
  `§7` / `§7§f§lAbility` / `§7§7Removes [§cTeleblock§7]` / `§7` /
  `§7§f§lCooldown` / `§7§7 {cooldown}` (TimeUtils format, L1 `2m` → L10
  `1m 6s`); guard/success strings above.
- **numbers:** cooldown `max(30, 120 − 6 × (level − 1))` s = 120/114/108/102/
  96/90/84/78/72/66 (floor 30 unreachable); measured 0 s → intended per-level
  values (ledger: pending). XP 10/use; curve flat 1000, cap 10, total 9000.
  Known bug: the jar fires on *lapsed* teleblock metadata for free XP →
  intended: requires an active teleblock window (the engine's expiring status
  makes this automatic) (ledger: pending).
- **era:** none beyond family notes.

### Banner Pet (`pets/banner`)

- **codex:** `12-pets.md § Banner Pet`
- **activation:** `USE`; conditions: spectator guard, faction membership
  (external), cooldown gate
- **decomposition:**
  1. `when var pet-cd-banner set → MESSAGE(cooldown), stop`
  2. faction-membership guard and the external faction-banner custom item
     payload (built no-soul-cost, attributed "Banner Pet") are **external**
     (a factions plugin + a custom-items build absent from the tree;
     codex-UNRESOLVED). No engine analogue — port ruling required; recorded,
     not decomposed.
  3. `SOUND(sound=LAVA_POP, volume=1.1, pitch=1.0)`
  4. `ITEM_XP_TRACK(amount=500, window=86400000)` *(gap — the 24 h XP gate is
     an item-NBT timestamp, carried by the item across trades)* +
     XP-gain MESSAGE when granted
  5. `SOUND(sound=ITEM_PICKUP, volume=1.0, pitch=1.1)`
  6. `MESSAGE(text="§a§lPET (§aBanner Pet§a§l)§a:§f Banner added to inventory!")`
  7. `SET_VAR(name=pet-cd-banner, ttl=<per level: 14400/13200/12000/10800/9600/8400/7200/6000/4800/3600>)`
- **gaps:** `ITEM_XP_TRACK` (with `window`)
- **interactions:** an external factions plugin + its faction-banner custom
  item (codex-UNRESOLVED: banner material/name/lore). Bypasses the banner's soul
  cost. Lore says "Spawn, Place" but the jar only *gives* the item.
- **strings:** name `§e§lBanner Pet§7 [LVL {level}]`; lore body
  `§7§7A rallying entity that` / `§7§7has the magical power to` /
  `§7§7summon /f banners at its` / `§7§7owner's location at will.` / `§7` /
  `§7§f§lAbility` / `§7§7Spawn, Place /f banner` / `§7§7at current location.` /
  `§7§7(no soul cost)` / `§7` / `§7§f§lCooldown` / `§7§7 {cooldown}` (L1
  `12m` → L10 `3m`); guards `§c§l(!) §cYou must be in a faction to use this
  pet!` and `§cNo banner found!`; success string above.
- **numbers:** cooldown `max(120, 720 − 60 × (level − 1))` s = 720/660/600/
  540/480/420/360/300/240/180 (floor 120 unreachable); measured 0 s →
  intended per-level values (ledger: pending). XP 500 gated to once per
  **86 400 000 ms** (24 h); curve flat 1000, cap 10, total 9000. A freshly
  minted pet has no gate timestamp and earns XP immediately.
- **era:** none beyond family notes.

### XP Booster Pet (`pets/xp-booster`)

- **codex:** `12-pets.md § XP Booster Pet` (+ §A.7)
- **activation:** `USE` (arm); companion ability on `EXP_GAIN` (apply while
  armed)
- **decomposition:**
  - `USE` ability:
    1. `when var xp-boost set → MESSAGE(text="§c§l(!) §cYou must wait for your previous buff to expire!"), stop`
    2. `when var pet-cd-xpb set → MESSAGE(cooldown), stop`
    3. `SET_VAR(name=xp-boost, ttl=<duration ticks per level>)`
    4. `ITEM_XP_TRACK(amount=15)` *(gap)* + XP-gain MESSAGE
    5. `MESSAGE(text="§a§lPET (§aXP Booster§a§l)§a:§f§a§l [§7XP Booster Pet (§a+{multiplier}§7x XP)§a§l]")`
    6. `SOUND(sound=ORB_PICKUP, volume=1.0, pitch=3.0)`, `SOUND(sound=NOTE_PLING, volume=1.0, pitch=1.0)`, `SOUND(sound=VILLAGER_YES, volume=3.0, pitch=1.1)`
    7. `SET_VAR(name=pet-cd-xpb, ttl=72000)`
  - `EXP_GAIN` ability: `when var xp-boost set → EXP_MULTIPLY(factor=<multiplier per level>)`
- **gaps:** `ITEM_XP_TRACK` (buff itself fully on-surface: SET_VAR +
  EXP_GAIN + EXP_MULTIPLY)
- **interactions:** jar multiplies **vanilla XP orbs** dropped on any
  `EntityDeathEvent` with a player killer (mobs and players), truncating to
  int; engine mapping is the `EXP_GAIN` trigger — note the site difference
  (orb drop vs player gain). The `EXP_GAIN` ability rides the pet item, so
  the buff applies while the pet is in the hotbar (jar buff is player-keyed
  regardless of item location — fidelity note).
- **strings:** name `§e§lXP Booster Pet§7 [LVL {level}]`; lore body
  `§7§7A mystical sprite that has` / `§7§7the unique ability to increase` /
  `§7§7the rate at which cosmonauts gain` /
  `§7§7experience and knowledge from combat.` / `§7` / `§7§f§lAbility` /
  `§7§7Gain a {multiplier}x Vanilla XP` / `§7§7increase for {duration}!` /
  `§7` / `§7§f§lCooldown` / `§7§7 1 hour`; wait/success strings above;
  OP-only debug (verbatim, jar leftover name): `§cBunny Buff: {before} -> {after}`.
- **numbers:** multiplier `min(3.0, 1.25 + (level − 1) × 0.2)` →
  1.25/1.45/1.65/1.85/2.05/2.25/2.45/2.65/2.85/**3.0** (cap hit only at L10,
  raw 3.05); duration `min(1200, 300 + (level − 1) × 90)` s → 300…1110 s
  (6000…22200 t; cap unreachable); XP 15/use; curve `level × 100` (200…1000),
  cap 10, total 5400. Cooldown: measured 0 s → intended **3600 s** (72 000 t)
  (ledger: pending). Known bug: buff key cleared only by a kill made after
  expiry, so the pet can be stranded for the session → intended: gate expires
  with the buff (SET_VAR ttl gives this for free) (ledger: pending).
- **era:** legacy sound names; XP-orb mechanics identical on 1.8.9.

### Tesla Pet (`pets/tesla`)

- **codex:** `12-pets.md § Tesla Pet` (+ §A.7)
- **activation:** `USE`
- **decomposition:**
  1. `when var tesla-armed set → MESSAGE(text="§c§l(!) §cYou must wait for your previous buff to expire!"), stop`
  2. `when var pet-cd-tesla set → MESSAGE(cooldown), stop`
  3. `SET_VAR(name=tesla-armed, ttl=<duration ticks per level>)`
  4. `SOUL_COST_EXEMPT(duration=<same ticks>, feedback-threshold=10)` *(gap)*
     — refund feedback verbatim: `§a§lPET (§aTesla§a§l): §a+{souls} souls!`
     (hand-built prefix; punctuation differs from the standard pattern)
  5. XP: `when recentattackers > 0 → ITEM_XP_TRACK(amount=10)` else
     `ITEM_XP_TRACK(amount=4)` *(gap; measured tier is in-combat AND warzone
     — see numbers)* + XP-gain MESSAGE
  6. `MESSAGE(text="§a§lPET (§aTesla§a§l)§a:§f§a§l [§7Tesla Pet Boost (§aNo Soul Costs§7)§a§l]")`
  7. `SOUND(sound=ORB_PICKUP, volume=1.0, pitch=3.0)`, `SOUND(sound=NOTE_PLING, volume=1.0, pitch=1.0)`, `SOUND(sound=VILLAGER_YES, volume=3.0, pitch=1.1)`
  8. `SET_VAR(name=pet-cd-tesla, ttl=6000)`
- **gaps:** `SOUL_COST_EXEMPT`; `ITEM_XP_TRACK`
- **interactions:** the souls economy (SoulPool): while exempt, every
  soul-costed action deducts nothing — notably soul-gated enchant procs
  (their `** OUT OF SOULS **` refusal path never triggers) and any other
  soul sink. Authored against the souls system via the gap, with the
  interaction recorded here.
- **strings:** name `§e§lTesla Pet§7 [LVL {level}]`; lore body
  `§7§7A legendary creature of myth` / `§7§7that acts as a temporarily` /
  `§7§7infinite source of player souls.` / `§7` / `§7§f§lAbility` /
  `§7§7Remove all soul costs from` / `§7§7any action for {duration}` / `§7` /
  `§7§f§lCooldown` / `§7§7 5 minutes`; wait/success/refund strings above.
- **numbers:** duration `(int) min(30.0, 5.0 + (level − 1) × 2.5)` s →
  5/7/10/12/15/17/20/22/25/27 (truncation loses 0.5 s on even levels; cap 30
  unreachable) = 100…540 t; XP 10 (in combat + warzone) / 4 (else) — warzone
  needs faction territory, no engine analogue → collapses to 4 flat (ledger:
  pending); curve `level × 1500` (3000…15000), cap 10, total 81 000 (the
  most expensive pet). Cooldown: measured 0 s → intended **300 s** (6000 t)
  (ledger: pending). Known bug: the armed key is never removed — one use per
  server boot → intended: re-usable once the buff expires (ledger: pending).
- **era:** none beyond family notes.

### Blackscroll Pet (`pets/blackscroll`)

- **codex:** `12-pets.md § Blackscroll Pet`
- **activation:** `USE`
- **decomposition:**
  1. `when book-rate-armed(site=generate) → MESSAGE(text="§c§lPET: §cYou already have an active Blackscroll Pet applied. Use a blackscroll before attempting to use another blackscroll pet."), stop`
     *(armed-condition var provided by the gap)*
  2. `when var pet-cd-blackscroll set → MESSAGE(cooldown), stop`
  3. `BOOK_RATE_MODIFIER(site=generate, percent=<level>)` *(gap)*
  4. `SOUND(sound=LAVA_POP, volume=1.1, pitch=1.0)`
  5. `ITEM_XP_TRACK(amount=50)` *(gap)* + XP-gain MESSAGE
  6. `MESSAGE(text="§a§lPET: §aThe next blackscroll or heroic blackscroll you use will have a +{level}% modifier applied to the success rate of the book it generates.")`
  7. `SOUND(sound=ITEM_PICKUP, volume=1.0, pitch=1.1)`
  8. `SET_VAR(name=pet-cd-blackscroll, ttl=18000)`
- **gaps:** `BOOK_RATE_MODIFIER`; `ITEM_XP_TRACK`
- **interactions:** book economy, generate site — adds on top of whichever
  base success the (heroic) blackscroll rolled; the generated book's
  destruction rate stays 100. Independent of the Enchanter charge (different
  site): **both can be armed at once**. Jar quirk: charge is session-only
  (player metadata, lost on relog) — the engine var/gap should persist per
  the interaction layer's normal var scoping; measured jar loses it silently.
- **strings:** name `§e§lBlackscroll Pet§7 [LVL {level}]`; lore body
  `§7§7A strange organic box` / `§7§7that eminates dark magical` (source
  typo "eminates" kept verbatim) / `§7§7power which can be harnessed` /
  `§7§7to enhance disenchanting.` / `§7` / `§7§f§lAbility` /
  `§7§7Increase the % success rate of` / `§7§7enchantment books generated by` /
  `§7§7the next blackscroll or heroic` / `§7§7blackscroll you use by §f{level}%` /
  `§7` / `§7§f§lCooldown` / `§7§7 {cooldown}` (`15m`); guard/success above.
- **numbers:** bonus `+level %` (1…10); XP 50/use; curve `250 + 1000 × level`
  (2250…10250), cap 10, total 56 250. Cooldown: measured 0 s → intended
  **900 s** (18 000 t) (ledger: pending). Jar stamps its cooldown timestamp
  before the guard — inert (rebuild discarded on throw); not ported.
- **era:** none beyond family notes.

### Alchemist Pet (`pets/alchemist`)

- **codex:** `12-pets.md § Alchemist Pet`
- **activation:** `USE`; conditions: 3-empty-slot guard (external payload
  precondition), cooldown gate
- **decomposition:**
  1. `when var pet-cd-alchemist set → MESSAGE(cooldown), stop`
  2. guard `< 3` empty inventory slots →
     `MESSAGE(text="§c§lPET: §cNot enough space (3 slots) in inventory!"), stop`
     — no inventory-space var exists, but the guard belongs to the external
     payload below and travels with it
  3. payload: roll `<delivered(level)>` items from the ALCHEMIST Evolution-Kit
     loot table at kit level `max(3, petLevel)` — **Evolution-Kits flow, OUT
     OF SCOPE (ruling R3)**; table contents codex-UNRESOLVED; recorded, not
     decomposed. Per-item feedback verbatim: `§7+ {amount}x {item}`
  4. `SOUND(sound=LAVA_POP, volume=1.1, pitch=1.0)`
  5. `ITEM_XP_TRACK(amount=100)` *(gap)* + XP-gain MESSAGE
  6. `SOUND(sound=ITEM_PICKUP, volume=1.0, pitch=1.1)`
  7. `SET_VAR(name=pet-cd-alchemist, ttl=864000)` (12 h)
- **gaps:** `ITEM_XP_TRACK`
- **interactions:** CosmicEvolutionKits (external; out of scope R3).
  Enchant economy only indirectly via kit drops.
- **strings:** name `§e§lAlchemist Pet§7 [LVL {level}]`; lore body
  `§7§7A mystical little imp creature` / `§7§7that pulls random items out of` /
  `§7§7other dimensions and gives them` / `§7§7to its owner!` / `§7` /
  `§7§f§lAbility` / `§7§7Spawns up to §6{items}x items§7 from the` /
  `§7§6Alchemist /vkit (LVL: {level})§7 loot table` / `§7` / `§7§f§lCooldown` /
  `§7§7 12h`; guard/grant strings above.
- **numbers:** advertised item count = 3 (L1–4) / 4 (L5–10) with a
  **dead** `level == 10 → 5` arm (short-circuited by `>= 5`); delivered =
  advertised − 1 (off-by-one) → measured 2/2/2/2/3/3/3/3/3/3; intended:
  advertised counts 3 (L1–4), 4 (L5–9), 5 (L10) (ledger: pending). Kit level
  rolled `max(3, level)` — L1–3 identical in quality and count. XP 100/use;
  curve `1000 + level × 250` (1500…3500), cap 10, total 22 500. Cooldown:
  measured 0 s → intended **43 200 s / 12 h** (864 000 t) (ledger: pending).
- **era:** none beyond family notes.

### Gaia Pet (`pets/gaia`)

- **codex:** `12-pets.md § Gaia Pet`
- **activation:** `USE`; the whole payload is the Nature's Wrath armour-enchant
  package at **50 % duration** (the jar's half-duration modifier), free of its
  soul cost
- **decomposition:** (order preserves the jar: mob wipe happens even when no
  player is hit)
  1. `when var pet-cd-gaia set → MESSAGE(cooldown), stop`
  2. mob wipe — `AOE(r=<8 + 5 × level>, filter=MOBS)`:
     `LIGHTNING(damage=0)`, `PARTICLE(particle=LARGE_EXPLODE, count=10, spread=0.6)`,
     `PARTICLE(particle=SPELL, count=35, spread=0.4)`, `KILL`
     (jar deletes via `remove()` — no drops/XP; KILL yields vanilla deaths →
     ledger: pending)
  3. `when nearbyenemies == 0 → MESSAGE(text="§c§lPET: §cNo valid enemy players nearby!"), stop`
     (no XP, no cooldown stamp — matches the jar's post-proc throw)
  4. player package — `AOE(r=<8 + 5 × level>, filter=ENEMIES)`, skipping
     targets with `wrath-active` set:
     `SET_VAR(name=wrath-active, ttl=<(7 + level) × 10>)` on the victim,
     `LIGHTNING(damage=0)`,
     `POTION(effect=JUMP, level=129, duration=<(7 + level) × 20>)`,
     `POTION(effect=SLOW, level=129, duration=<(7 + level) × 20>)`,
     `POTION(effect=WEAKNESS, level=3, duration=<(7 + level) × 20>)`,
     `FREEZE(duration=<(7 + level) × 10>, dot=<level>, dot-period=20, slow=100, neutralize-frost-slow=false)`
     (walk-lock released at half the potion window, exactly the jar's
     `× 0.5` release; DoT ticks landed 4/4/5/5 match),
     `SOUND(sound=ENDERDRAGON_GROWL, volume=2.0, pitch=2.0)` at the victim,
     victim message `§c§l… (per-tick)` — see fidelity note below
  5. `MESSAGE(text="§a§lPET: ** NATURE'S WRATH **")`
  6. `SOUND(sound=DIG_SNOW, volume=1.1, pitch=3.0)`, `SOUND(sound=WITHER_SHOOT, volume=1.1, pitch=3.0)`
  7. `ITEM_XP_TRACK(amount=5)` *(gap; warzone tier collapses — numbers)* +
     XP-gain MESSAGE
  8. `SET_VAR(name=pet-cd-gaia, ttl=12000)`
  - fidelity note: the jar emits `§2§l** NATURE'S WRATH **` +
    `GHAST_SCREAM2 2.0/2.0` + SPELL particles at the victim **per DoT tick**;
    FREEZE has no per-tick feedback hook. Recorded for the shared enchant's
    entry (matrix/02); not redeclared as a gap here.
- **gaps:** `ITEM_XP_TRACK`
- **interactions:** direct free proc of the Nature's Wrath armour enchant
  (matrix/02) at 50 % duration, bypassing its soul cost. Poltergeist mastery
  immunity skips the freeze/potion package but the victim is still struck and
  **still takes the DoT** (immunity string
  `§4§l* POLTERGEIST [§7Immune: Nature's Wrath§4§l] *`) — interaction-layer
  rule with the mastery (matrix/07). Victim re-proc blocked by
  `wrath-active` for the window. Jar exclusions (WorldGuard PvP, spectator,
  gamemode, god/staff mode, factions truce/ally, protection-flagged mobs,
  ender crystals) map to the engine's ENEMIES filter + global PvP gates; the
  koth-world short release (`(7 + level) × 5 × 0.5` t = 20/22/25/27) depends
  on a named external world — recorded, not ported.
- **strings:** name `§e§lGaia Pet§7 [LVL {level}]`; lore body
  `§7§7A segment of the goddess` / `§7§7of the earth and nature itself.` /
  `§7` / `§7§f§lAbility` / `§7§7Call upon the wrath of Nature.` /
  `§7§7[§cNature Wrath {roman}§7] (50% duration)` / `§7` / `§7§f§lCooldown` /
  `§7§7 10 Minutes`; caster success `§a§lPET: ** NATURE'S WRATH **`; no-target
  `§c§lPET: §cNo valid enemy players nearby!`; victim per-tick
  `§2§l** NATURE'S WRATH **`.
- **numbers:** radius `8 + 5 × level` = 13/18/23/28; potion window
  `(7 + level) × 20` t = 160/180/200/220 (8–11 s); walk-lock/DoT window
  (× 0.5) = 80/90/100/110 t; DoT `level`/s, ticks landed 4/4/5/5, totals
  4/8/15/20; JUMP/SLOW amplifier 128 (level 129, the no-jump wrap trick),
  WEAKNESS amplifier 2 (level 3); XP 50 (warzone) / 5 (else) → collapses to
  5 flat, no faction territory in engine (ledger: pending); curve
  `1000 + level × 500` (2000/2500/3000), cap 4, total 7500. Cooldown:
  measured 0 s → intended **600 s** (12 000 t) (ledger: pending).
- **era:** amplifier-128 JUMP wrap is version-sensitive — FREEZE absorbs the
  walk-lock so the potion is cosmetic-redundant on modern; legacy particle
  names (`LARGE_EXPLODE`, `SPELL`); legacy sounds.

### Enchanter Pet (`pets/enchanter`)

- **codex:** `12-pets.md § Enchanter Pet`
- **activation:** `USE`
- **decomposition:**
  1. `when book-rate-armed(site=apply) → MESSAGE(text="§c§lPET: §cYou already have an active Enchanter Pet applied. Use an Enchantment Book before attempting to use another Enchanter Pet."), stop`
  2. `when var pet-cd-enchanter set → MESSAGE(cooldown), stop`
  3. `BOOK_RATE_MODIFIER(site=apply, percent=<level>)` *(gap)*
  4. `SOUND(sound=LAVA_POP, volume=1.1, pitch=1.0)`
  5. `ITEM_XP_TRACK(amount=50)` *(gap)* + XP-gain MESSAGE
  6. `MESSAGE(text="§a§lPET: §aThe next Enchantment Book you use will have a +{level}% modifier applied to its success rate.")`
  7. `SOUND(sound=ITEM_PICKUP, volume=1.0, pitch=1.1)`
  8. `SET_VAR(name=pet-cd-enchanter, ttl=72000)`
- **gaps:** `BOOK_RATE_MODIFIER`; `ITEM_XP_TRACK`
- **interactions:** book economy, apply site — additive with the jar's other
  apply-site modifiers (hero-outpost `+10`, `cosmicpvp.heroic` permission
  `+5`, then the global `−10` under-100 penalty floored at 1 and destruction
  `× 1.15`; those live with the book-economy decomposition, not here; the two
  hard-coded username exemptions are not ported). Different site from the
  Blackscroll charge — both armable at once. Jar quirk: the charge is
  consumed **before** the apply preconditions, so a failed precondition burns
  it with no effect — measured and recorded; the gap's consume-on-next-roll
  semantics should match the port's book-apply gate order (deviation decision
  belongs to the book-economy doc).
- **strings:** name `§e§lEnchanter Pet§7 [LVL {level}]`; lore body
  `§7§7A powerful, magical creature` / `§7§7skilled in the many forms of` /
  `§7§7alchemy and enchantment.` / `§7` / `§7§f§lAbility` /
  `§7§7Increase the % success rate of` / `§7§7the next enchantment book you` /
  `§7§7attempt to apply by §f{level}%` / `§7` / `§7§f§lCooldown` /
  `§7§7 {cooldown}` (`1h`); consumption-side (book flow) string
  `§7Applying enchantment book... §e{rate}§6(+{bonus})§e% success chance...`.
- **numbers:** bonus `+level %` (1…10); XP 50/use; curve `250 + 1000 × level`
  (identical to Blackscroll), cap 10, total 56 250. Cooldown: measured 0 s →
  intended **3600 s** (72 000 t) (ledger: pending).
- **era:** none beyond family notes.

### Stronghold Sell Pet (`pets/stronghold-sell`)

- **codex:** `12-pets.md § Stronghold Sell Pet`
- **activation:** `USE`
- **decomposition:**
  1. `when var sh-sell set → MESSAGE(text="§c§lPET: §cYou already have an active /sh sell effect!"), stop`
     (the jar's only deadline-comparing guard — a genuine working duration
     lock; SET_VAR ttl reproduces it exactly)
  2. `when var pet-cd-stronghold set → MESSAGE(cooldown), stop`
  3. `SET_VAR(name=sh-sell, ttl=<(10 + 5 × level) × 20>)`
  4. `MESSAGE(text="§a§lPET: /sh sell enabled for: {duration}s!")`
  5. `SOUND(sound=DIG_SNOW, volume=1.1, pitch=3.0)`, `SOUND(sound=WITHER_SHOOT, volume=1.1, pitch=3.0)`
  6. `ITEM_XP_TRACK(amount=5)` *(gap)* + XP-gain MESSAGE
  7. `SET_VAR(name=pet-cd-stronghold, ttl=36000)`
  - payload: the `sh-sell` window is read by an external `/sh sell` system —
    **no reader exists anywhere in the decompiled tree (codex-UNRESOLVED)**;
    port ruling required. The jar's scheduled expiry line
    `§c§lPET: /sh sell ability expired!` (fires even after re-arm — quirk)
    has no delayed-message primitive; it belongs to whatever consumes the
    window.
- **gaps:** `ITEM_XP_TRACK`
- **interactions:** external stronghold/outpost system (unresolved).
- **strings:** name `§e§lStronghold Sell Pet§7 [LVL {level}]`; lore body
  `§7§7A beast born from the chaos of` /
  `§7§7the contested strongholds of this planet.` / `§7` / `§7§f§lAbility` /
  `§7§7Enable /sh sell for: §f{duration}s` / `§7` / `§7§f§lCooldown` /
  `§7§7 30 Minutes`; guard/enable/expired strings above.
- **numbers:** duration `10 + 5 × level` s = 15…60 (300…1200 t); XP 5/use;
  curve flat 500, cap 10, total 4500 (cheapest cap in the family, 900 uses).
  Cooldown: measured 0 s → intended **1800 s** (36 000 t) (ledger: pending).
- **era:** none beyond family notes.

### Raid Creeper Pet (`pets/raid-creeper`)

- **codex:** `12-pets.md § Raid Creeper Pet`
- **activation:** `USE`; targets the clicked/looked-at block
- **decomposition:**
  1. `when var pet-cd-raidcreeper set → MESSAGE(cooldown), stop`
  2. `SPAWN_ENTITY(type=CREEPER, count=1)` at `BLOCK(distance=5)` — the jar
     hands the raw interact event to an external custom-creeper system
     (`Raid` type: "Lucky + Tactical", "100% Spawner Drop Rate") that is
     **codex-UNRESOLVED** (stats, AI, explosion, drop mechanics not in the
     tree); the vanilla spawn is the on-surface skeleton, the custom behavior
     needs a port ruling
  3. spawn failure → `MESSAGE(text="§c§lPET: §cUnable to spawn Raid Creeper!"), stop`
     (jar stamps its cooldown timestamp before the null check — inert;
     intended: no cooldown burned on failure, expressed by ordering)
  4. `SOUND(sound=DIG_SNOW, volume=1.1, pitch=3.0)`, `SOUND(sound=WITHER_SHOOT, volume=1.1, pitch=3.0)`
  5. `ITEM_XP_TRACK(amount=10)` *(gap)* + XP-gain MESSAGE
  6. `SET_VAR(name=pet-cd-raidcreeper, ttl=<per level: 9600/8400/7200/6000/4800>)`
- **gaps:** `ITEM_XP_TRACK`
- **interactions:** external custom-creeper system (undeclared dependency in
  the jar's own plugin.yml — hard-referenced anyway). Not obtainable from the
  Mystery Pet Box (0 %, despite being listed in its lore).
- **strings:** name `§e§lRaid Creeper Pet§7 [LVL {level}]`; lore body
  `§7§7A mythical hybrid creeper,` / `§7§7a rare cross between the` /
  `§7§7Lucky and Tactical species.` / `§7` / `§7§f§lAbility` /
  `§7§7Spawn a §4Raid Creeper` / `§7§7(Lucky + Tactical)` /
  `§7§7(100% Spawner Drop Rate)` / `§7` / `§7§f§lCooldown` /
  `§7§7{cooldown} Minutes` (no leading space — jar quirk, verbatim); failure
  string above.
- **numbers:** cooldown `(10 − level − 1)` min = 8/7/6/5/4 min (480…240 s,
  9600…4800 t; no floor of its own — the cap of 5 prevents the negative
  values the formula would reach at L10); measured 0 s → intended per-level
  values (ledger: pending). XP 10/use; curve `2500 × level`
  (5000/7500/10000/12500), cap 5, total 35 000 (3500 uses). Nothing about
  the creeper scales with level.
- **era:** creeper spawning identical; custom stats are external either way.

### Vile Creeper Pet (`pets/vile-creeper`)

- **codex:** `12-pets.md § Vile Creeper Pet`
- **activation:** `USE`; identical skeleton to Raid Creeper
- **decomposition:** as Raid Creeper with:
  2′. `SPAWN_ENTITY(type=CREEPER, count=1)` at `BLOCK(distance=5)` — external
  `Vile` type: corrupts its explosion area, applying `Rot and Decay V` for a
  5 s duration per the lore; the mechanism lives in the external
  creeper system (**codex-UNRESOLVED**) and the `V` / `5 second` figures are
  **lore literals** in the pet, not read from the enchant
  3′. failure → `MESSAGE(text="§c§lPET: §cUnable to spawn Vile Creeper!"), stop`
  6′. `SET_VAR(name=pet-cd-vilecreeper, ttl=<per level: 9600/8400/7200/6000/4800>)`
- **gaps:** `ITEM_XP_TRACK`
- **interactions:** external custom-creeper system; advertises the Rot and
  Decay mastery (matrix/07) as payload but never touches it directly. Not
  obtainable from the Mystery Pet Box (0 %).
- **strings:** name `§e§lVile Creeper Pet§7 [LVL {level}]`; lore body
  `§7§7An extremely dangerous creeper,` / `§7§7species, famous for its vicious`
  (grammatically broken source text, verbatim) /
  `§7§7modern warfare deployments.` / `§7` / `§7§f§lAbility` /
  `§7§7Spawn a §5Vile Creeper` / `§7§7Corrupts the area it explodes` /
  `§7§7in, applying [§cRot and Decay V§7]` / `§7§7for a 5 second duration.` /
  `§7` / `§7§f§lCooldown` / `§7§7{cooldown} Minutes`; failure string above.
- **numbers:** identical to Raid Creeper: cooldown 8/7/6/5/4 min measured
  0 s → intended per-level values (ledger: pending); XP 10/use; curve
  `2500 × level`, cap 5, total 35 000. Rot and Decay level V / 5 s (100 t)
  fixed at all pet levels (lore literals).
- **era:** as Raid Creeper.

### Smite Pet (`pets/smite`)

- **codex:** `12-pets.md § Smite Pet`
- **activation:** `USE`; target = the living entity nearest the looked-at
  block (jar: target block within 8, entity within 2 blocks of it, nearest
  wins) → engine mapping `ENTITYINSIGHT(r=8)` (approximation of the
  block-anchored search; recorded)
- **decomposition:**
  1. `when var pet-cd-smite set → MESSAGE(cooldown), stop`
  2. select `ENTITYINSIGHT(r=8)`; none →
     `MESSAGE(text="§c§lPET: §cNo target found!"), stop`
  3. player target inside a PvP-denied region →
     `MESSAGE(text="§c§lPET: §cNo valid target found!"), stop`
     (engine's own PvP gate; jar checks WorldGuard for **players only** —
     mobs are smitable in protected regions. Jar grants XP before this check
     but discards the write on throw — net nothing; the port validates first)
  4. `LIGHTNING(damage=5)` at the target (real, damaging strike + fire)
  5. `when victim.type == PLAYER →`
     `FREEZE(duration=<(1.0 + 0.25 × level) × 20 ticks>, dot=0, slow=100, neutralize-frost-slow=false)`,
     `MESSAGE(text="§c§l(!) §cFrozen by {player}'s Smite Pet [{seconds}s]!")` at `VICTIM`
     (freeze correctly releases the **victim** — the codex's earlier
     "permanent freeze / caster clobbered" claim is RETRACTED after bytecode
     verification; do not re-introduce it)
  6. `ITEM_XP_TRACK(amount=1)` *(gap)* + XP-gain MESSAGE
  7. `SET_VAR(name=pet-cd-smite, ttl=2400)`
- **gaps:** `ITEM_XP_TRACK`
- **interactions:** PvP-region gating (players only). No set/mask/enchant
  interaction. The jar's unused ray-cast targeting alternative is dead code —
  not ported.
- **strings:** name `§e§lSmite Pet§7 [LVL {level}]`; lore body
  `§7§7An electrified skeleton totem` / `§7§7that contains powerful elemental` /
  `§7§7forces of nature.` / `§7` / `§7§f§lAbility` /
  `§7§7Casts a powerful §f/smite` / `§7§7at the target entity. If affecting a player,` /
  `§7§7they will also be §nfrozen in place for {seconds}s` (raw double
  formatting: `1.25`, `1.5`, `1.75`, `2.0`, `2.25`) / `§7` / `§7§f§lCooldown` /
  `§7§72 Minutes` (no leading space); target/victim strings above. Lore says
  `/smite` but no command is dispatched — raw lightning, verbatim lore kept.
- **numbers:** freeze `1.0 + 0.25 × level` s = 1.25/1.5/1.75/2.0/2.25
  (25/30/35/40/45 t); lightning = vanilla damaging strike (5.0 + fire),
  level-independent; XP 1/use; curve `1000 × level` (2000…5000), cap 5,
  total 14 000 — the slowest curve in the plugin (14 000 uses). Cooldown:
  measured 0 s → intended **120 s** (2400 t) (ledger: pending).
- **era:** vanilla lightning sound/effect naming differs per era; walk-lock
  fine on 1.8.9.

### World Destroyer Pet (`pets/world-destroyer`)

- **codex:** `12-pets.md § World Destroyer Pet`
- **activation:** `USE`; targets every non-truce player in a 30-block box
  (61×61×61 AABB) → engine mapping `AOE(r=30, filter=ENEMIES)` (sphere vs box
  — recorded); jar applies **no** WorldGuard/gamemode/spectator checks here
  (unlike Smite); the engine's global PvP gates will apply — recorded
- **decomposition:**
  1. `when var pet-cd-wd set → MESSAGE(cooldown), stop`
  2. `when nearbyenemies == 0 → MESSAGE(text="§c§nPET: No valid enemy players nearby! (30x30)"), stop`
  3. per victim — `AOE(r=30, filter=ENEMIES)`:
     `CAGE(floor=OBSIDIAN, walls=<port pick — see ledger>, roof=OBSIDIAN, width=3, depth=3, height=3, rise=0, ticks=100)`
     (3×3 footprint, walls 3 high at y…y+2, obsidian floor y−1 and roof y+3,
     up to 44 blocks; the engine's temp-block ledger restores originals
     exactly — the jar's two restore sweeps at 100 t and 101 t delete
     recorded obsidian to air, a grief bug not replicated),
     `MESSAGE(text="§5§l** DIMENSIONAL CAGE §7[§c3s§7]§5§l **")` at `VICTIM`,
     `SOUND(sound=ANVIL_LAND, volume=2.0, pitch=2.0)` at `VICTIM`,
     `POTION(effect=BLINDNESS, level=101, duration=200)`,
     `POTION(effect=POISON, level=19, duration=100)`,
     `POTION(effect=WITHER, level=19, duration=100)`,
     `FREEZE(duration=55, dot=7, dot-period=5, slow=0, neutralize-frost-slow=false)`
     (11 DoT applications at 0.25 s period over 2.75 s; the jar rolls uniform
     5–9 per application — flattened to the expected 7, see ledger; `slow=0`
     because the jar does not slow the victim)
  4. `ITEM_XP_TRACK(amount=5)` *(gap)* — the one pet with **no** XP-gain chat
     line (verbatim: silent)
  5. `MESSAGE(text="§a§lPET: ** DIMENSIONAL CAGE **")`
  6. `SOUND(sound=WITHER_SHOOT, volume=10.0, pitch=2.0)` (volume 10.0 —
     deliberately far-audible, verbatim)
  7. `SET_VAR(name=pet-cd-wd, ttl=12000)`
- **gaps:** `ITEM_XP_TRACK`
- **interactions:** factions truce gating in the jar (allies/members/truce
  spared; either side flagged as duelling counts as not-truce) →
  engine ENEMIES filter. Named after the Mother-of-Yijki set's Dimensional
  Cage (matrix/10) but is a standalone reimplementation — no coupling, no
  stacking rule needed. Not obtainable from the Mystery Pet Box (0 %).
- **strings:** name `§e§lWorld Destroyer Pet§7 [LVL {level}]`; lore body
  `§7§7A fragment of the legendary` / `§7§7World Destroyer, Yijki.` / `§7` /
  `§7§f§lAbility` / `§7§7Summon the power of Yijki's` /
  `§7§7[§5Dimensional Cage§7] ability (3s)` / `§7` / `§7§f§lCooldown` /
  `§7§75 minutes`; no-target / caster / victim strings above. Lore and the
  victim message say `3s` while the cage stands 100 t (5.0 s) — strings kept
  verbatim, behavior ships the measured 100 t.
- **numbers:** **nothing scales with pet level** (level is cosmetic). Cage
  100 t; potions BLINDNESS 200 t amp 100 (level 101), POISON 100 t amp 18
  (level 19), WITHER 100 t amp 18 (level 19); DoT 11 × uniform [5, 9] at 5 t
  period (min 55 / max 99 / expected 77 raw damage over 2.5 s) → flat 7 × 11
  (ledger: pending); walls measured block id 417 (`IRON_BARDING`, an item,
  not a block — renders invalid/air-like) → intended a solid wall material,
  port pick (ledger: pending); restore grief bug → exact restore (ledger:
  pending). XP 5/use; curve `1000 + level × 500` (2000/2500/3000), cap 4,
  total 7500 (1500 uses). Cooldown: measured 0 s — the only pet that never
  stamps a cooldown timestamp at all, so even a fixed formula would never block;
  lore
  says 5 minutes, code says 10 → intended **600 s** (12 000 t) (ledger:
  pending).
- **era:** `IRON_BARDING` exists only pre-1.13 (and never as a block) — the
  wall material must be a real block on both trees; WITHER/BLINDNESS/POISON
  fine on 1.8.9; `ANVIL_LAND` legacy sound name.
