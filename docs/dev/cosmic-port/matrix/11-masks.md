# Matrix 11 — masks (27 items)

Source codex: `11-masks.md` (CosmicMasks jar). Entry format per `../README.md`; only
primitives in `docs/reference/authoring-surface.txt` at HEAD appear in decompositions.

## Family preamble (shared facts, stated once)

- **Model.** Every cosmic mask is a helmet-attached visual override, passive while
  attached — no cooldowns, no durations, no activations anywhere in the family
  (`11-masks.md` Part B shared facts). Ports onto the StarEnchants masks family
  (ADR-0053): worn-source abilities with the §B maintained lifecycle; the jar's 60 t
  rescan heartbeat and 20 t Spectral task become PASSIVE/REPEATING re-arm.
- **Single-pass.** Mask damage multipliers live in one `EntityDamageByEntityEvent`
  handler with no `EntityDamageEvent` twin, so the defensive double-fire
  (`00-MECHANICS.md` §3, deviations row D-001) does NOT apply to masks — every number
  below is already a single-pass value.
- **Composition.** The jar multiplies the shared damage scalar (a fourth independent
  `setDamage` channel at HIGHEST, `00-MECHANICS.md` §4.3); the port contributes the
  same percentages to the ONE additive fold (ADR-0012). Amounts below are the intended
  FINAL percents; attack-side authored amounts are divided by the pack's
  `combat.attack-scale` knob at authoring time (ADR-0050 R2); defense-side amounts are
  not scaled.
- **Multi-Mask routing split (family bug).** Abilities routed through `fromPlayer()`
  (Purge, Turkey, Monopoly −5%, Dragon +5%, Party Hat, Death Knight +2.5%) are dead
  inside a Multi-Mask; `hasEquipped()`-routed abilities work (`11-masks.md` §A.3).
  Port intent: every folded child stays active (deviation ledger D-11-1; see
  Multi-Mask entry).
- **Generator pools.** `Math.random() <= 0.1` picks the rare branch. Common pool
  (11): Pilgrim, Turkey, Scarecrow, Purge, Santa, Necromancer, Rift, Zeus, Glitch,
  Reindeer, Dungeon — 90%/11 = **8.1818%** each. Rare pool (9): Monopoly, Party Hat,
  Death Knight, Lover, Joker, Bunny, Boss, Stronghold, Outpost — 10%/9 = **1.1111%**
  each. Never generated: Headless, Pumpkin Monster, Ghost, Dragon, Beanie, Spectral,
  Multi-Mask (`11-masks.md` §A.7).
- **Anti-dupe (gameplay-affecting framework note).** The Mask Generator is a one-shot
  claim: minting stamps a unique item id, claiming blacklists it, and a later claim of
  a blacklisted copy is confiscated from the hand with
  `§c§l(!) §cIt seems you tried to claim a duplicate Random Mask, it has been removed from your inventory.`
  (`11-masks.md` §C.7). Maps to the port's mint-once/consume-once item identity; no
  per-mask entry repeats this.
- **Shared trailing lore** appended to every mask (verbatim, after the declared lore):
  `(empty)` / `§7§oAttach this mask to any helmet` / `§7§oto give it a visual override!`
  / `(empty)` / `§7To equip, place this mask on a helmet.` /
  `§7To remove, right-click helmet while attached.`
- **ATTACHED stamp** on the helmet (verbatim): non-multi
  `§7§lATTACHED: {display}§f ({ability}§f)` (ability parens omitted when the mask has
  no ability line); multi
  `§7§lATTACHED: §f§lMulti-Mask§f ({child1}§f, {child2}§f)`.
- **Render-layer facts** (masks-family render, not per entry): while the wearer is
  combat-tagged they see their REAL helmet in their own inventory while others still
  see the mask; Creative disables the self-override entirely; the override appends
  `§7Durability: {current} / {max} ({percent}%)` (percent formatted `#,###.##`)
  (`11-masks.md` §A.6).
- **Era (family-wide).** Loose masks are `SKULL_ITEM:3` on 1.8.9 vs `PLAYER_HEAD`
  modern — the masks repaint era seam (ADR-0053) already owns this; per-entry era
  lines list only hazards beyond the head seam.

---

### Headless Mask (`masks/headless`)

- **codex:** `11-masks.md` § Headless Mask
- **activation:** none — cosmetic-only worn head (no listener references it)
- **decomposition:** item definition only (display, head texture, lore); no abilities
- **interactions:** none
- **strings:** name `§6§lHeadless Mask`; lore `§7A terrifying Mask from the` /
  `§7grave of the Headless Horseman.` / `§6§l * §6October 2018`
- **numbers:** active in `/mask help`: yes; generator pool: none. Codex quirk: the
  only mask with a populated `base64` field (a 62-hex-char garbage value, never read)
  — no gameplay effect, not ported.
- **era:** head seam only (preamble)

### Purge Mask (`masks/purge`)

- **codex:** `11-masks.md` § Purge Mask
- **activation:** ATTACK (wearer as damager, melee/projectile), no condition
- **decomposition:** `[ATTACK] DAMAGE_MOD(side=attack, mode=add, amount=2.5)`
- **interactions:** contributes to the additive outgoing fold (preamble); jar-measured
  dead inside a Multi-Mask (`fromPlayer()` routing) — port keeps it active there
  (ledger D-11-1, Multi-Mask entry)
- **strings:** name `§c§lPurge Mask`; ability `§c+2.5% DMG`; lore `§c+2.5% DMG` /
  `§7A great evil is contained within this` / `§7horrifying mask. Who knows what inner` /
  `§7demons it will unleash.` / `§6§l * §6Halloween 2018`
- **numbers:** measured `setDamage(damage * 1.025)` = **+2.5%** outgoing; pool common
  (8.1818%); active: yes
- **era:** none beyond the head seam

### Pumpkin Monster (`masks/pumpkin-monster`)

- **codex:** `11-masks.md` § Pumpkin Monster
- **activation:** none — cosmetic only
- **decomposition:** item definition only; hidden from the mask help menu
  (`active = false`)
- **interactions:** none
- **strings:** name `§6§lPumpkin Monster`; lore `§7The severed head of a fabled Halloween`
  / `§7nightmare known as the Pumpkin Monster` / `§6§l * §6Halloween Quests`
- **numbers:** active: **no** (hidden); pool: none
- **era:** head seam only

### Ghost Mask (`masks/ghost`)

- **codex:** `11-masks.md` § Ghost Mask
- **activation:** none — cosmetic only (no effect anywhere in the suite)
- **decomposition:** item definition only
- **interactions:** none. (Distinct item from the signature-pack `masks/ghost`; the
  packs are separate namespaces.)
- **strings:** name `§3§lGhost Mask`; lore `§7The deviant mask of the spectre,` /
  `§7a metaphysical monstrosity.` / `§6§l * §6Ghost Mastery Kit`
- **numbers:** active: yes; pool: none
- **era:** head seam only

### Scarecrow Mask (`masks/scarecrow`)

- **codex:** `11-masks.md` § Scarecrow Mask
- **activation:** maintained while worn — REPEATING re-arm (the jar cancels
  `FoodLevelChangeEvent` losses; gains pass through)
- **decomposition:** `[REPEATING repeat=20] MODIFY_FOOD(amount=20, mode=give, who=@Self)`
  — MODIFY_FOOD gives clamped to 20, so the wearer's hunger is held full
- **interactions:** a `SUPPRESS(scope=KIND, key=MODIFY_FOOD)` window on the wearer
  (Chef-mask idiom, ADR-0053 §7) silences this top-up at gate 5 — an interaction the
  1.7 jar could not have; works inside a Multi-Mask in the jar (`hasEquipped` routing)
- **strings:** name `§e§lScarecrow Mask`; ability `§cInfinite Food`; lore
  `§cInfinite Food` / `§7An empty husk of a` / `§7once tortured soul left to rot.` /
  `§6§l * §6November 2018 CC`
- **numbers:** measured: only food-level LOSSES cancelled (freeze at equip-time
  level); ported: hunger topped to 20 every 20 t — as-intended "Infinite Food"
  (deviation ledger D-11-2). Pool common (8.1818%); active: yes.
- **era:** none beyond the head seam

### Turkey Mask (`masks/turkey`)

- **codex:** `11-masks.md` § Turkey Mask
- **activation:** DEFENSE (entity-attack/projectile damage against the wearer only —
  no dodge vs fall/fire, matching the jar's EDBEE-only scope), 2% chance gate
- **decomposition:** `[DEFENSE chance=2] CANCEL()`,
  `MESSAGE(text="§e§l* DODGED [§7Turkey Mask§e§l]", channel=chat, who=@Self)`,
  `PARTICLE(particle=CLOUD, count=10, spread=0.2, who=@Self)`
- **interactions:** CANCEL discards the whole hit (every fold contribution with it);
  jar-measured dead inside a Multi-Mask (`fromPlayer()` routing) — port keeps it
  active (ledger D-11-1, Multi-Mask entry)
- **strings:** name `§e§lTurkey Mask`; ability `§c+2% Dodge`; dodge message verbatim
  `§e§l* DODGED [§7Turkey Mask§e§l]` (no trailing `*`, unlike Zeus); lore
  `§c+2% Dodge` / `§7Stay nimble and fast, or the` /
  `§7Pilgrims will catch and cook you!` / `§6§l * §6Thanksgiving 2018`
- **numbers:** chance `0.02` = **2%** (inclusive `<=` in the jar); particle: CLOUD,
  offsets 0.2/0.2/0.2, speed 0.1, count 10, at +0.5 y (offset/speed are cue-level —
  PARTICLE has no speed/offset params; spread=0.2, count=10 is the port cue). Pool
  common (8.1818%); active: yes.
- **era:** CLOUD particle exists on 1.8.9 — no hazard beyond the head seam

### Pilgrim Mask (`masks/pilgrim`)

- **codex:** `11-masks.md` § Pilgrim Mask
- **activation:** EXP_GAIN (wearer gains XP), no condition
- **decomposition:** `[EXP_GAIN] EXP_MULTIPLY(factor=1.25)`
- **interactions:** stacks with other EXP_MULTIPLY sources per the engine's XP fold;
  works inside a Multi-Mask in the jar (`hasEquipped` routing)
- **strings:** name `§e§lPilgrim Mask`; ability `§c+25% XP/Drops`; lore
  `§c+25% XP/Drops` / `§7tHiS iS oUr lAnD nOw!` / `§6§l * §6Thanksgiving 2018`
- **numbers:** measured `droppedExp * 1.25` truncated to int, mob deaths only, killer
  wears mask = **+25%**. The lore's "/Drops" half is XP-only in the jar (item-drop
  multiplier UNRESOLVED/absent — not ported; lore stays verbatim). Ported scope shift:
  the jar boosts the mob's dropped orbs (any collector benefits); the port multiplies
  the wearer's own EXP_GAIN (deviation ledger D-11-3). Pool common (8.1818%); active: yes.
- **era:** none beyond the head seam

### Monopoly Mask (`masks/monopoly`)

- **codex:** `11-masks.md` § Monopoly Mask
- **activation:** DEFENSE (incoming hit, −5%); ATTACK lethal-hit conditioned (scroll
  negation, as-intended)
- **decomposition:**
  1. `[DEFENSE] DAMAGE_MOD(side=defense, mode=add, amount=5)`
  2. as-intended scroll line (absent in the jar — ships per deviation ledger D-11-4):
     `[ATTACK condition="%victim.health% <= %damage%" chance=33]
     STRIP_SCROLL(scroll=HOLY, hand=true, who=@Victim)` — the lethal-hit gate makes
     the 33% roll fire once per kill, stripping one Holy White Scroll marker before
     death-drop resolution
- **interactions:** the strip must resolve before the death-drop keep check (apply
  order inside the kill event — interaction-layer ordering, same seam as the Anubis
  pet's STRIP_SCROLL); −5% joins the additive reduction bucket; jar-measured
  −5% dead inside a Multi-Mask (`fromPlayer()`) — port keeps it active (ledger D-11-1)
- **strings:** name `§b§lMonopoly Mask`; ability `§c33% Holy White Scroll negation`;
  lore `§c33% Holy White Scroll negation` / `§c-5% ENEMY DMG` /
  `§7The mask of a man who has it all,` / `§7a truly powerful entity to contest with.`
  / `§6§l * §6Black Friday 2018`
- **numbers:** measured `setDamage(damage * 0.95)` = **−5%** incoming (shared branch
  with Party Hat). Headline **33%** (`0.33`) scroll negation is NOT implemented
  anywhere in the jar (codex UNRESOLVED — advertised only); as-intended value 33% per
  kill (deviation ledger D-11-4). Pool rare (1.1111%); active: yes.
- **era:** none beyond the head seam (scrolls are engine features)

### Necromancer Mask (`masks/necromancer`)

- **codex:** `11-masks.md` § Necromancer Mask
- **activation:** immunity while worn — incoming lifesteal-family procs against the
  wearer are negated (jar: 5 external call sites abort the attacker's heal/chain)
- **decomposition:** best available today:
  `[DEFENSE] SUPPRESS(scope=GROUP, key=lifesteal, duration=100, who=@Attacker)`
  (re-armed each incoming hit; the pack must declare the lifesteal family —
  `enchants/lifesteal`, `enchants/vampire`, `heroic/demonic-lifesteal`,
  `mastery/chain-lifesteal` — under one `lifesteal` group, else one
  ENCHANT-scope SUPPRESS per key). Exact semantics need the gap below.
- **gaps:** `DEFENDER_KEYED_SUPPRESSION` — a maintained suppression window stored on
  the WEARER that gate 5 consults for abilities activating AGAINST the holder
  (incoming direction), complementing the activator-keyed SuppressionStore; params:
  scope (ENCHANT|GROUP|TYPE|KIND), key, duration (maintained re-arm), chance 0–100
  per-incoming-activation roll (default 100, mirroring SUPPRESS_IMMUNE.chance);
  consumers: necromancer, lover, glitch, zeus, rift (chance=50). Why no existing
  combination: gate 5 checks only the ACTIVATOR's store, and a `who=@Attacker`
  SUPPRESS armed from DEFENSE always misses the arming hit (the attack pass resolves
  before the defense pass), so "Immune to X" leaks the opening proc of every
  engagement; a per-application chance is inexpressible because ability chance gates
  roll at arm time.
- **interactions:** the jar also skips the wearer as a Chain Lifesteal CHAIN target
  (checked per chain hop) — defender-keyed suppression evaluated per target
  application reproduces this; works inside a Multi-Mask in the jar
- **strings:** name `§2§lNecromancer Mask`; ability `§cImmune to Lifesteal`; lore
  `§cImmune to Lifesteal` / `§7An enchanted skull conjured from` /
  `§7the depths of the underworld.` / `§6§l * §6Necromancer Mastery Kit`
- **numbers:** immunity is absolute (100%) in the jar across Lifesteal, Vampire,
  Demonic Lifesteal, Chain Lifesteal (primary + chain). Pool common (8.1818%);
  active: yes.
- **era:** none beyond the head seam

### Dragon Mask (`masks/dragon`)

- **codex:** `11-masks.md` § Dragon Mask
- **activation:** ATTACK (+5% outgoing); FIRE (environmental fire/lava damage against
  the wearer)
- **decomposition:**
  1. `[ATTACK] DAMAGE_MOD(side=attack, mode=add, amount=5)`
  2. `[FIRE] CANCEL()` — the jar cancels and zeroes FIRE / FIRE_TICK / LAVA damage
- **interactions:** the jar's two abilities use DIFFERENT lookups — +5% via
  `fromPlayer()` (dead in a Multi-Mask), fire immunity via `hasEquipped()` (works) —
  port: both active in a Multi-Mask (ledger D-11-1, Multi-Mask entry)
- **strings:** name `§4§lDragon Mask`; ability `§c+5% DMG`; lore `§c+5% DMG` /
  `§cImmune to Fire and Lava damage` / `§7The decapitated skull of a slain` /
  `§7Timeless Dragon from the Ender Dimension.` / `§6§l * §6Timeless Dragon Update`
- **numbers:** measured `setDamage(damage * 1.05)` = **+5%** outgoing; fire causes
  covered: FIRE, FIRE_TICK, LAVA (the 1.7 server has no HOT_FLOOR). Port: the FIRE
  trigger's cause set decides HOT_FLOOR on modern — keep the three measured causes
  plus HOT_FLOOR only if the FIRE trigger already includes it engine-wide. Pool:
  none (not generated); active: yes.
- **era:** HOT_FLOOR damage cause absent before 1.12 — the three measured causes
  exist on 1.8.9; no other hazard

### Santa (`masks/santa`)

- **codex:** `11-masks.md` § Santa
- **activation:** PASSIVE maintained while worn
- **decomposition:** `[PASSIVE] HEALTH(amount=4)` — the reconciled worn max-health
  bonus (+4 health = +2 hearts), removed on unequip by the §B lifecycle
- **interactions:** the jar suppresses the Santa bonus entirely while the wearer is
  Mortal-Coiled (`SantaMask.onUpdate` early-return) — in the port the mastery
  mortal-coil's max-health attack and this maintained bonus reconcile through the ONE
  max-health driver (interaction-layer rule, authored on the mortal-coil side; see
  also Lover, which shields it). Jar quirk: an existing enchant HEALTH_BOOST is
  UPGRADED +1 level instead of stacking — port stacks additively (ledger D-11-5).
- **strings:** name `§b§lSanta`; ability `§c+2 Max Hearts`; lore `§c+2 Max Hearts` /
  `§7An eerie mask imbued with` / `§7Christmas Joy that knows` /
  `§7who is naughty or nice.` / `§6§l* §6Christmas 2018` (credit line has NO leading
  space, unlike the Halloween/Thanksgiving masks — transcribe as-is)
- **numbers:** measured HEALTH_BOOST amplifier 0 (level I = +4 health), 200 t effect
  refreshed by the 60 t heartbeat; ported: flat +4 max health maintained. Pool common
  (8.1818%); active: yes.
- **era:** 1.8.9 max-health attribute (`generic.maxHealth`) exists — the HEALTH
  worn-bonus reconciler already spans the seam (ADR-0053); no extra hazard

### Reindeer (`masks/reindeer`)

- **codex:** `11-masks.md` § Reindeer
- **activation:** PASSIVE maintained while worn (+ REPEATING for the flight flag)
- **decomposition:**
  1. `[PASSIVE] POTION(effect=SPEED, level=4, duration=200)` — Speed IV maintained
     (jar amplifier 3), re-applied while worn, removed on unequip (§B lifecycle)
  2. as-intended flight line (absent in the jar — ships per deviation ledger D-11-6):
     `[REPEATING repeat=40] FLY_MODE(who=@Self)` — flight while not in combat,
     revoked in combat, lapses on unequip
- **interactions:** jar disables the speed inside non-Cosmonaut outpost worlds
  (external CosmicOutposts) — no StarEnchants outpost subsystem, rule not ported.
  Jar quirk: a tracked enchant SPEED effect is upgraded-in-place rather than stacked
  — port uses vanilla potion-pool semantics (highest amplifier shows).
- **strings:** name `§a§lReindeer`; ability `§cSPEED IV`; lore `§cSPEED IV` /
  `§cFlight regardless of rank` / `§7The decapitated and stuffed` /
  `§7head of one of Santa's` / `§7magical reindeer... yikes!` /
  `§6§l* §6Christmas 2018` (no leading space)
- **numbers:** SPEED amplifier 3 = **Speed IV**; jar `onEquip` grants
  `Integer.MAX_VALUE` ticks then the first heartbeat silently downgrades to a 200 t
  rolling refresh (and grants NOTHING until the first heartbeat when no tracked
  enchant speed exists) — as-intended: uniform maintained Speed IV while worn
  (deviation ledger D-11-7). "Flight regardless of rank" is advertised but absent in the jar
  (deviation ledger D-11-6). Pool common (8.1818%); active: yes.
- **era:** SPEED potion and allow-flight API stable on 1.8.9 — no hazard beyond the
  head seam

### Party Hat (`masks/party-hat`)

- **codex:** `11-masks.md` § Party Hat
- **activation:** ATTACK (+4% outgoing); DEFENSE (−5% incoming)
- **decomposition:**
  1. `[ATTACK] DAMAGE_MOD(side=attack, mode=add, amount=4)`
  2. `[DEFENSE] DAMAGE_MOD(side=defense, mode=add, amount=5)`
- **interactions:** the only mask with both an offensive and defensive multiplier;
  both jar-measured dead inside a Multi-Mask (`fromPlayer()`) — port keeps both
  active (ledger D-11-1, Multi-Mask entry); both join the additive fold buckets
- **strings:** name `§f§lParty Hat`; ability `§c-5% ENEMY DMG`; lore
  `§c-5% ENEMY DMG` / `§c+4% DMG` / `§7Everywhere you are is a party.` /
  `§6§l* §6New Years 2018` (no leading space)
- **numbers:** measured ×1.04 = **+4%** outgoing, ×0.95 = **−5%** incoming (victim
  branch shared with Monopoly). Pool rare (1.1111%); active: yes.
- **era:** none beyond the head seam

### Death Knight (`masks/death-knight`)

- **codex:** `11-masks.md` § Death Knight
- **activation:** ATTACK (+2.5% outgoing; 50% Phoenix negation per hit)
- **decomposition:**
  1. `[ATTACK] DAMAGE_MOD(side=attack, mode=add, amount=2.5)`
  2. `[ATTACK chance=50] SUPPRESS(scope=ENCHANT, key=enchants/phoenix,
     mode=next-hit, charges=1, who=@Victim)` — the attack pass resolves before the
     victim's defense pass, so the one-shot armed on this hit suppresses a Phoenix
     proc on this same hit (the corrupt-vs-inversion idiom); per-hit 50% arming ≡
     the jar's per-proc 50% roll on the lethal hit
- **gaps:** `SUPPRESS_CONSUME_CUE` — feedback (per-party messages, optional
  sound/particle) emitted at the moment a suppression window/one-shot actually blocks
  an activation; params: actor-message, victim-message, sound; consumers:
  death-knight, zeus. Why no existing combination: MESSAGE fires when the ARMING
  ability activates (would spam on every 50% roll), not at the later consumption;
  nothing on the surface observes a gate-5 block (`/se why` is diagnostic, not
  player-facing).
- **interactions:** jar quirk — a BLOCKED Phoenix still stamps the victim's
  `last_phoenix` cooldown (the victim loses the proc AND the cooldown window);
  as-intended: a gate-5-suppressed proc leaves its cooldown unspent (deviation
  ledger D-11-8). +2.5% jar-measured dead inside a Multi-Mask, Phoenix block works there —
  port: both active (ledger D-11-1, Multi-Mask entry).
- **strings:** name `§9§lDeath Knight`; ability
  `§c50% chance to negate enemy's Phoenix`; block messages verbatim — victim:
  `§c§l* PHOENIX BLOCKED [§7{damager}§c§l] *`, wearer:
  `§c§l* DEATH KNIGHT MASK [§7{victim}'s Phoenix Blocked§c§l] *`; lore
  `§c50% chance to negate enemy's Phoenix` / `§c+2.5% DMG` /
  `§7The cursed mask of runeforged` / `§7Death Knight armor.` /
  `§6§l* §6Death Knight Mastery Kit` (no leading space)
- **numbers:** ×1.025 = **+2.5%** outgoing; Phoenix negation `Math.random() <= 0.5` =
  **50%**. Pool rare (1.1111%); active: yes.
- **era:** none beyond the head seam

### Beanie__ Mask (`masks/beanie`)

- **codex:** `11-masks.md` § Beanie__ Mask
- **activation:** none — cosmetic staff/personal mask
- **decomposition:** item definition only; hidden from the mask help menu
  (`active = false`)
- **interactions:** none
- **strings:** name `§b§lBeanie__ Mask` (double underscore is part of the name);
  lore `§7A beanie to keep your head warm.` / `§6§l * §6Snow Day Lootbox Release`
- **numbers:** active: **no** (hidden); pool: none
- **era:** head seam only

### Rift Mask (`masks/rift`)

- **codex:** `11-masks.md` § Rift Mask
- **activation:** immunity-style while worn — each incoming mastery-enchant
  application onto the wearer is negated 50%
- **decomposition:** best available today (lagged, correlated — approximation only):
  `[DEFENSE chance=50] SUPPRESS(scope=TYPE, key=mastery, duration=20, who=@Attacker)`
  — 50% of incoming hits silence the attacker's mastery type for the following
  second. Exact iid per-application semantics need the chance param on
  `DEFENDER_KEYED_SUPPRESSION` (see Necromancer): a maintained defender-keyed window
  with `scope=TYPE, key=mastery, chance=50`. The pack must lower mastery-tier
  enchants with cooldown-scope type `mastery` for the TYPE key to bind.
- **gaps:** `DEFENDER_KEYED_SUPPRESSION` (chance=50 consumer) — definition at the
  Necromancer entry
- **interactions:** jar scope is the mastery-REFLECT application path (per-level
  50% skip inside the reflect gate `0.02 + 0.0267 * (level / 3)`); works inside a
  Multi-Mask in the jar
- **strings:** name `§5§lRift Mask`; ability `§c50% Mastery Enchant Negation.`
  (trailing full stop — the ATTACHED stamp reads `…Negation.` while lore line 1 has
  no stop; transcribe both as-is); lore `§c50% Mastery Enchant Negation` /
  `§7A mysterious mask with a strange` / `§7aura of power found scattered among` /
  `§7a select few random lootboxes.` / `§6§l * §6Snow Day Lootbox Release`
- **numbers:** `Math.random() <= 0.5` = **50%** per mastery application. Codex data
  bugs (310-char over-padded texture base64; 62-hex-char truncated texture hash) are
  item-asset defects — the port uses a valid re-encoded texture, display strings
  unchanged. Pool common (8.1818%); active: yes.
- **era:** none beyond the head seam

### Lover Mask (`masks/lover`)

- **codex:** `11-masks.md` § Lover Mask
- **activation:** immunity while worn — Mortal Coil's apply no-ops entirely against
  the wearer (no message, no timer)
- **decomposition:** best available today:
  `[DEFENSE] SUPPRESS(scope=ENCHANT, key=mastery/mortal-coil, duration=100,
  who=@Attacker)` (re-armed per incoming hit; opening-hit leak). Exact always-on
  semantics via `DEFENDER_KEYED_SUPPRESSION` (definition at Necromancer):
  `scope=ENCHANT, key=mastery/mortal-coil, chance=100` maintained while worn.
- **gaps:** `DEFENDER_KEYED_SUPPRESSION` (consumer) — definition at the Necromancer
  entry
- **interactions:** synergy — Mortal Coil also suppresses the Santa max-health bonus,
  so Lover indirectly protects a Santa+Lover Multi-Mask wearer's +2 hearts (emergent
  from the two rules; nothing extra to author); works inside a Multi-Mask in the jar
- **strings:** name `§c§lLover Mask`; ability `§cImmune to Mortal Coil`; lore
  `§cImmune to Mortal Coil` / `§7Make love, not Minecraft...` /
  `§7or maybe its the other way around?` / `§6§l * §6Valentine's Day 2019`
- **numbers:** immunity absolute (100%). Pool rare (1.1111%); active: yes.
- **era:** none beyond the head seam

### Spectral Mask (`masks/spectral`)

- **codex:** `11-masks.md` § Spectral Mask
- **activation:** REPEATING poll (jar: every 20 t), altitude-gated maintained
  disguise
- **decomposition:** `[REPEATING repeat=20, condition="%actor.y% > 200"]
  MOB_DISGUISE(type=ZOMBIE, adult=true)` — both the condition fact and the effect are
  gaps (below); the maintained flag lapses (undisguise + message) when the condition
  fails or the mask leaves the helmet
- **gaps:**
  - `MOB_DISGUISE` — render a player as a configured mob to OTHER clients while a
    maintained flag holds (self-view unchanged), with transition messages on
    apply/remove; params: entity-type, adult/baby, apply-messages, remove-message;
    consumers: spectral. Why no existing combination: no primitive alters how other
    clients render a player — EQUIP_SWAP swaps real armour, and the masks render
    override only retextures the helmet slot, not the whole entity.
  - `POSITION_VARS` — absolute position condition facts (`%actor.y%` at minimum);
    consumers: spectral (y > 200 gate). Why: the var surface exposes only relative
    position facts (`actor.belowvictim`, `distance`), never coordinates.
- **interactions:** jar guards: never disguises a player already disguised by other
  means, and only undisguises while the active disguise is still the zombie —
  irrelevant once the disguise is engine-owned state; the "(in combat)" lore claim
  has NO combat check in the jar (unconditional at y>200) — port matches measured,
  lore stays verbatim
- **strings:** name `§3§lSpectral Mask`; ability
  `§cZombie Auto-disguise at y>200 (in combat)`; messages verbatim — apply:
  `§3§l(SPECTRAL) §3You have been auto-disguised as a Zombie.` +
  `§7This disguise will remain as long as you stay above y:200`; remove (both
  branches): `§c§l(SPECTRAL) §cYour spectral-zombie disguise has been unequipped!`;
  lore `§cZombie Auto-disguise at y>200 (in combat)` /
  `§7As silent as the night, as mystic` / `§7as the full moon: The Spectre` /
  `§6§l * §6Baked Lootbox Release`
- **numbers:** threshold **200.0** — jar uses strict `>` to disguise and strict `<`
  to undisguise, so `y == 200.0` exactly is a dead zone that freezes state
  (known bug); as-intended: disguise at `> 200`, undisguise at `<= 200` (deviation
  ledger D-11-9). Poll period 20 t (1.0 s). Pool: **none**; active: yes.
- **era:** MAJOR — client-bound disguise packets diverge hard on 1.8.9 (spawn/
  metadata format, 1.8 datawatcher layout); the MOB_DISGUISE capability must sit
  behind a version resolver, and the legacy sweep owns the 1.8 packet shapes

### Glitch Mask (`masks/glitch`)

- **codex:** `11-masks.md` § Glitch Mask
- **activation:** immunity while worn — the wearer (as VICTIM) is immune to Teleblock
  and to Bidirectional Teleportation's grapple/trap ("Bidirectional Teleport")
- **decomposition:** best available today:
  `[DEFENSE] SUPPRESS(scope=ENCHANT, key=enchants/teleblock, duration=100,
  who=@Attacker)`,
  `SUPPRESS(scope=ENCHANT, key=heroic/bidirectional-teleportation, duration=100,
  who=@Attacker)` (re-armed per incoming hit — covers the bow-shot delivery since
  DEFENSE fires on projectile hits; opening-hit leak). Exact always-on semantics via
  `DEFENDER_KEYED_SUPPRESSION` (definition at Necromancer) with the two ENCHANT keys.
- **gaps:** `DEFENDER_KEYED_SUPPRESSION` (consumer) — definition at the Necromancer
  entry
- **interactions:** in the jar the Ranger armour-set Teleblock immunity is checked
  BEFORE the Glitch check (ordering invisible to gameplay — both are early returns);
  in the port both are idempotent suppressions, order irrelevant. Works inside a
  Multi-Mask in the jar. The engine's own TELEBLOCK effect (pearl/chorus denial) is
  what the suppression prevents from arming.
- **strings:** name `§f§lGlitch Mask`; ability
  `§cImmune to Teleblock, Bidirectional Teleport`; lore
  `§cImmune to Teleblock, Bidirectional Teleport` / `§7The aura around this mask` /
  `§7is electrified and encoded.` / `§6§l * §6St. Patrick's Day 2019`
- **numbers:** both immunities absolute (100%); the Bidirectional Teleportation roll
  bypassed is `Math.random() <= 0.066 * level`. Pool common (8.1818%); active: yes.
- **era:** chorus fruit does not exist on 1.8.9 — TELEBLOCK's denial set is pearls
  only there (already an engine-side era fact)

### Zeus Mask (`masks/zeus`)

- **codex:** `11-masks.md` § Zeus Mask
- **activation:** immunity while worn — a Natures Wrath proc targeting the wearer is
  cancelled, with a dodge message
- **decomposition:** best available today:
  `[DEFENSE] SUPPRESS(scope=ENCHANT, key=enchants/natures-wrath, duration=100,
  who=@Attacker)` (re-armed per incoming hit; opening-hit leak). Exact always-on
  semantics via `DEFENDER_KEYED_SUPPRESSION` (definition at Necromancer),
  `scope=ENCHANT, key=enchants/natures-wrath`. Dodge message fires on consumption via
  `SUPPRESS_CONSUME_CUE` (definition at Death Knight).
- **gaps:** `DEFENDER_KEYED_SUPPRESSION`, `SUPPRESS_CONSUME_CUE` (consumers) —
  definitions at Necromancer / Death Knight
- **interactions:** jar handler also nulls the wrapped damage event
  (`setEvent(null)`), which could strip the event from downstream readers — a jar
  artifact, not ported (gate-5 suppression never mutates the event); works inside a
  Multi-Mask in the jar
- **strings:** name `§b§lZeus Mask`; ability `§cImmune to Natures Wrath`; dodge
  message verbatim `§d§l* ZEUS MASK [§7Natures Wrath Dodged§d§l] *`; lore
  `§cImmune to Natures Wrath` / `§7Channel the powers of the` /
  `§7King of the Greek Gods` / `§6§l * §6April Showers Lootbox Release`
- **numbers:** immunity absolute (100%). Pool common (8.1818%); active: yes.
- **era:** none beyond the head seam

### Bunny Mask (`masks/bunny`)

- **codex:** `11-masks.md` § Bunny Mask
- **activation:** passive presence — spawner spawns in the wearer's chunk roll extra
  output
- **decomposition:** none expressible — no spawner-spawn trigger, no chunk-scoped
  selector, and SPAWN_ENTITY cannot observe or piggyback a natural spawner spawn;
  fully a gap
- **gaps:** `SPAWNER_YIELD` — extra spawner output while a qualifying wearer is in
  scope: each spawner spawn rolls chance% to add extra copies of the spawned mob;
  params: chance (65), extra (1), scope (chunk|radius); consumers: bunny. Why no
  existing combination: the trigger vocabulary has no spawner event, selectors have
  no chunk scope, and no effect multiplies a spawn that the engine did not create.
- **interactions:** jar writes `monsterAmount` metadata (value 2, or +2 stacking)
  consumed by an ABSENT external plugin (Arkkit) — the multiplication itself is
  UNRESOLVED in the corpus; the port implements the advertised expectation directly
  (65% → one extra copy = 1.65x expected). Jar-measured 5 s per-chunk cache with the
  priming spawn granting nothing — as-intended: per-spawn wearer check (deviation
  ledger D-11-10). Works inside a Multi-Mask in the jar (`MaskUtils` routing).
- **strings:** name `§d§lBunny Mask`; ability `§c1.65x Mobs from Spawners in Chunk`;
  lore `§c1.65x Mobs from Spawners in Chunk` / `§7And so the gods declared to all` /
  `§7the easter bunnies: be fruitful` / `§7and multiply.` / `§6§l * §6Easter 2019`
- **numbers:** chance `0.65` = **65%** (strict `<`); metadata grant `2` (or existing
  +2); marketing **1.65x** = 1×0.35 + 2×0.65; jar cache TTL 5 s (bug, ledger D-11-10).
  Pool rare (1.1111%); active: yes.
- **era:** `CreatureSpawnEvent` + SpawnReason.SPAWNER exist on 1.8.9 — no hazard
  beyond the head seam

### Joker Mask (`masks/joker`)

- **codex:** `11-masks.md` § Joker Mask
- **activation:** none in the jar — no combat-tag code exists in CosmicMasks and no
  external call site was found (codex UNRESOLVED: absent combat-tag plugin, or never
  implemented)
- **decomposition:** item definition only — ships cosmetic with verbatim lore.
  StarEnchants has no combat-tag subsystem, so this is NOT recorded as a surface gap:
  the consuming subsystem itself has no counterpart (a gap would demand a combat-tag
  engine, out of the port's primitive bar). Revisit only if a combat-tag system ever
  lands.
- **interactions:** none (see above)
- **strings:** name `§5§lJoker Mask`; ability
  `§cIncrease Combat Tag on players by 4s` (differs from lore line 1 — the ATTACHED
  stamp reads the ability text; transcribe both as-is); lore
  `§c+4s Combat Tag on enemy players` / `§c-3s Combat Tag on you` /
  `§7Everyone takes everything so` / `§7very seriously, you're just trying` /
  `§7to.. haHahaHahaHahaHahaHahaHa!` / `§6§l * §6Summer Savage Lootbox Release`
- **numbers:** advertised **+4 s** enemy tag, **−3 s** own tag — advertised-only,
  nothing measured. Pool rare (1.1111%); active: yes.
- **era:** head seam only

### Dungeon Mask (`masks/dungeon`)

- **codex:** `11-masks.md` § Dungeon Mask
- **activation:** none in the jar — key-refund logic lives in the absent dungeons
  plugin (codex UNRESOLVED)
- **decomposition:** item definition only — ships cosmetic with verbatim lore; no
  StarEnchants dungeon subsystem, so no surface gap recorded (same reasoning as
  Joker)
- **interactions:** none
- **strings:** name `§6§lDungeon Mask`; ability
  `§c10% chance to not use /dungeon key`; lore `§c10% chance to not use /dungeon key`
  / `§7You take dungeon running extremely` / `§7seriously, you solo most dungeons` /
  `§7faster than other groups start them.` / `§6§l * §6Lit Lootbox Release`
- **numbers:** advertised **10%** (`0.10`) key refund — advertised-only. Pool common
  (8.1818%); active: yes. (Jar quirk: the skull GameProfile username contains a space
  — harmless, texture embedded; not ported.)
- **era:** head seam only

### Outpost Mask (`masks/outpost`)

- **codex:** `11-masks.md` § Outpost Mask
- **activation:** none in the jar — capture logic lives in the absent CosmicOutposts
  plugin (codex UNRESOLVED)
- **decomposition:** item definition only — ships cosmetic with verbatim lore; no
  StarEnchants outpost subsystem, so no surface gap recorded
- **interactions:** none
- **strings:** name `§b§lOutpost Mask`; ability
  `§cCapture, destroy /outpost caps 2x faster`; lore
  `§cCapture, destroy /outpost caps 2x faster` /
  `§7Your very presence commands unquestioning,` /
  `§7unwaivering admiration and respect.` (typo "unwaivering" is in the source —
  transcribe as-is) / `§6§l * §6July 2019`
- **numbers:** advertised **2x** capture/destroy rate — advertised-only. Pool rare
  (1.1111%); active: yes.
- **era:** head seam only

### Multi-Mask (`masks/multi-mask`)

- **codex:** `11-masks.md` § Multi-Mask + §A.9
- **activation:** container — no abilities of its own; every folded child mask's
  ability set resolves as if that child were worn
- **decomposition:** the child abilities are the children's own matrix entries; the
  compound worn identity is a gap
- **gaps:** `WORN_COMPOSITE` — one worn mask carrying an ordered list of child mask
  identities whose ability sets ALL resolve as if each child were the worn mask, and
  whose display/lore render from the child list; params: children (list of mask
  keys), optional child cap; consumers: multi-mask. Why no existing combination: the
  WornState resolver binds ONE mask identity per helmet; no primitive merges N
  ability sets under a single worn source or renders the compound lore.
- **interactions:** THE family bug — in the jar only `hasEquipped()`-routed child
  abilities work inside the compound; `fromPlayer()`-routed ones (Purge, Turkey,
  Monopoly −5%, Dragon +5%, Party Hat, Death Knight +2.5%) silently do nothing.
  As-intended: every folded child stays active (deviation ledger D-11-1). Jar NBT quirk:
  the child list survives detachment on the helmet forever — port state is
  item-data-model owned, cleaned on detach. Minted via the custom-item command with
  explicit child names; the codex-documented crash edges (empty child list, unknown
  child name, `valueOf` throw) become compile-time pack/mint validation.
- **strings:** base name `§f§lMulti-Mask`; generated compound name
  `§f§lMulti-Mask (§r{child1}, {child2}§f§l)`; compound lore
  `§7This mask contains the powers of:` then per child `§f§l* {child display}` /
  `§f§l({child ability}§f§l)`; ATTACHED stamp
  `§7§lATTACHED: §f§lMulti-Mask§f ({child1}§f, {child2}§f)`; declared base lore is a
  single empty line + the shared trailing block. Jar bug: children with no ability
  line render the literal text `null` — as-intended: omit the ability parens for
  ability-less children (deviation ledger D-11-11).
- **numbers:** no numeric behavior of its own; active: **no** (hidden); pool: none
- **era:** head seam only

### Stronghold Mask (`masks/stronghold`)

- **codex:** `11-masks.md` § Stronghold Mask
- **activation:** none in the jar — capture logic lives in the absent stronghold
  plugin (codex UNRESOLVED)
- **decomposition:** item definition only — ships cosmetic with verbatim lore; no
  StarEnchants stronghold subsystem, so no surface gap recorded
- **interactions:** none
- **strings:** name `§3§lStronghold Mask`; ability
  `§cCapture, destroy /stronghold caps 2x faster`; lore
  `§cCapture, destroy /stronghold caps 2x faster` /
  `§7Your very presence terrifies all,` / `§7who dare stand before you.` /
  `§6§l * §6Sugar Daddy Lootbox Release`
- **numbers:** advertised **2x** — advertised-only. Pool rare (1.1111%); active: yes.
- **era:** head seam only

### Boss Mask (`masks/boss`)

- **codex:** `11-masks.md` § Boss Mask
- **activation:** ATTACK vs a boss-flagged entity (+10%); DEFENSE from a boss-flagged
  attacker (−25%); projectile hits resolve to the shooter (both in the jar and in the
  engine's combat dispatch)
- **decomposition:**
  1. `[ATTACK condition="<boss designation>"]
     DAMAGE_MOD(side=attack, mode=add, amount=10)`
  2. `[DEFENSE condition="<boss designation>"]
     DAMAGE_MOD(side=defense, mode=add, amount=25)` — on the DEFENSE pass the
     `%victim.*%` facts read the ATTACKER, so the same condition gates the incoming
     side. The designation is a `%victim.type%` list over the vanilla bosses —
     `%victim.type% == "ENDER_DRAGON" || %victim.type% == "WITHER"` (both resolve on
     1.8.9). NOT `%victim.mobtype%`, which is the MythicMobs soft hook (ADR-0027):
     with no integration installed it resolves to the empty string for every entity,
     so the mask would ship as inert as the absent-plugin metadata flag it replaces.
     Servers running MythicMobs widen the list with `%victim.mobtype%` entries, which
     `masks/boss.yml` documents. Deliberately the same list `matrix/04` § Boss Slayer
     carries, so the pack has ONE boss designation rather than two
- **interactions:** the jar keys on `"boss"` metadata stamped by an ABSENT external
  plugin (UNRESOLVED) — the port re-keys on the pack's boss-id condition list, which
  is the interaction-layer decision (which mobs count as bosses is config, not
  engine); works inside a Multi-Mask in the jar (`MaskUtils` routing)
- **strings:** name `§c§lBoss Mask`; ability
  `§c-25% incoming Boss DMG, +10% outgoing Boss DMG` (wording differs from lore line
  1 — transcribe both as-is); lore `§c-25% incoming DMG, +10% outgoing DMG to Bosses`
  / `§7A seasoned monster hunter, you pride` /
  `§7yourself on your boss slaying record.` / `§6§l * §6Sugar Daddy Lootbox Release`
- **numbers:** measured ×1.1 = **+10%** outgoing vs bosses; ×0.75 = **−25%** incoming
  from bosses. Pool rare (1.1111%); active: yes.
- **era:** none beyond the head seam (boss-id list is pack config on every era)
