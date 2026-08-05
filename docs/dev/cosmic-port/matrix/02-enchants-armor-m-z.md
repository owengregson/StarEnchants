# Matrix 02 — armor enchants M–Z (31 items)

Source codex: `02-enchants-armor-m-z.md` (order preserved: alphabetical by display
name; Enchant Reflect sorts first). Defensive numbers are **single-pass intended
values** per `deviations.md` D-001 (`00-MECHANICS.md` §3 double-fire is never
replicated). Shared context applied throughout, recorded once here:

- **Environment gates** (`world_koth`, `world_duels*`, The End tier cap,
  Outposts tiers, staff/god/spectator/duel exemptions): cosmic-infra rules. All are
  expressible as `%actor.world%` / interaction-layer conditions if ever wanted; the
  port ships without them (no such worlds/plugins). Noted per entry only where they
  change numbers.
- **Non-stacking enchants** (everything below not explicitly marked stackable):
  jar keeps the highest-level piece only, one activation per event — authored as the
  interaction layer's highest-only rule. The jar's last-writer-wins equip-marker bug
  (level of last piece equipped wins; one unequip wipes all) is never replicated.
- **Era**: jar sound/particle/potion handles are 1.8-era names (`LEVEL_UP`,
  `RED_DUST`, `SLOW_DIGGING`, …). Entries cite the jar name; the boot-time resolver
  maps modern↔legacy. Only genuinely hazardous 1.8.9 items get an era line.

## Entries

### Enchant Reflect (`enchants/enchant-reflect`)

- **codex:** `02-enchants-armor-m-z.md § Enchant Reflect`
- **activation:** PASSIVE (worn state only) — the roll happens when a hostile
  weapon-enchant activation lands on the wearer; no trigger of its own.
- **decomposition:** `1. ACTIVATION_REBOUND(chance=<per-level ladder>, max-tier=5,
  level-cap=own-level)` (gap) — armed/lifted with the worn piece. Nothing else:
  the enchant has no effects, sounds, or strings of its own.
- **gaps:** `ACTIVATION_REBOUND — with per-grade chance, re-execute an incoming
  weapon-enchant ability with actor/victim swapped (the attacker eats their own
  proc); params: chance, max-tier, level-cap (rebound level must be >= the incoming
  enchant's level), grade (normal|heroic|mastery); consumers: worn rebound enchants
  at three grades. Why no combination works: SUPPRESS can only block an activation,
  REFLECT only mirrors damage — nothing can re-run another item's ability with
  swapped roles.`
  **SHIPPED as `PROC_REBOUND`** (wave 2b).
- **interactions:** grade precedence mastery (tier-8 incomings only) → heroic
  (tier ≤ 7) → normal (tier ≤ 5), first match wins (a mastery-grade source shadows
  this one for tier-8 procs only); rebound applies only when rebound level >= the
  incoming enchant level; jar quirk — a successful rebound against a non-player
  victim drops the proc for both parties (record as the rebound rule's
  non-player-victim outcome, decide at authoring); highest-only across pieces.
- **strings:** none.
- **numbers:** max 10, weight 3, table thresholds 25..115 step 10, tier 5. Chance
  by level: 2/2/3/3/3/4/4/4/5/5 % — the jar's `level/3` integer division yields only
  4 distinct values; shipped as measured (functional ladder, not a no-op).
- **era:** none.

### Marksman (`enchants/marksman`)

- **codex:** `02-enchants-armor-m-z.md § Marksman`
- **activation:** trigger `BOW` (projectile damage dealt while the armor is worn).
- **decomposition:** `1. DAMAGE_MOD(side=attack, mode=add, amount=1.5625·L)` —
  per-level amounts 1.5625 / 3.125 / 4.6875 / 6.25 (percent), contributed per worn
  piece into the additive attack fold.
- **interactions:** stackable-by-formula: the jar sums levels across all four
  pieces then applies one multiplier `1 + 0.015625·Σlevels` — exactly equivalent to
  additive per-piece DAMAGE_MOD contributions (full L4 set = +25%). Weak-bow gate
  (draw force < 0.75 does not proc) is the engine's bow-force gate.
- **strings:** none.
- **numbers:** max 4, weight 4, thresholds 20/25/30/35, tier 4. **Known bug
  (fatal):** the jar hook is unreachable for player archers — the enchant applies
  to armor only, but offensive procs read the held item, so measured behavior is a
  total no-op. Shipped: the intended values above (ledger D-02-1).
- **era:** none.

### Metaphysical (`enchants/metaphysical`)

- **codex:** `02-enchants-armor-m-z.md § Metaphysical`
- **activation:** PASSIVE (boots-only worn marker); consumed by the Trap, Heroic
  Trap, Snare and Pummel rolls (docs 03/05/06).
- **decomposition:** `1. PASSIVE marker` — all behavior is interaction-layer
  chance-deltas on the attacking abilities (below). The jar's post-roll negation of
  an already-successful proc is distribution-identical to a pre-roll chance
  subtraction, so the flowmod `-N %chance%` clause expresses it exactly.
- **interactions:** per Metaphysical level L, subtract from the attacker's proc
  roll: Trap −2.5 pp·L; Heroic Trap −1.25 pp·L (floored at 1 pp by the consumer);
  Snare −7 pp·L; Pummel −4 pp·L. Trap/Snare/Pummel have **no floor** — the chance
  goes negative and becomes absolute immunity (Metaphysical 2 already hard-blocks
  Snare 1). Heroic Metaphysical writes the same worn state — highest-only rule, the
  two grades never sum.
- **strings:** emitted by the blocked attacker-side rule, verbatim:
  `§8§l** METAPHYSICAL (§8Trap blocked!§l) **`,
  `§8§l** METAPHYSICAL (§8Snare blocked!§l) **`,
  `§8§l** METAPHYSICAL (§8Pummel blocked!§l) **`.
- **numbers:** max 4, weight 2, thresholds 17/21/25/29, tier 4. Reduction table at
  L1–4 as above (−2.5/−5/−7.5/−10 pp vs Trap, etc.).
- **era:** none.

### Molten (`enchants/molten`)

- **codex:** `02-enchants-armor-m-z.md § Molten`
- **activation:** trigger `DEFENSE`; condition
  `%damagecause% == "ENTITY_ATTACK"`; chance `10·L` %.
- **decomposition:** `1. IGNITE(duration=20·L, who=@Attacker)`.
- **interactions:** none. Non-stacking (highest piece only).
- **strings:** none.
- **numbers:** max 4, weight 2, thresholds 15/18/21/24, tier 2. Chance
  10/20/30/40 %; fire 20/40/60/80 t (1–4 s). Jar quirk: fire ticks are an absolute
  set (a re-proc can shorten a longer burn); IGNITE's refresh semantics are the
  shipped behavior — benign divergence, noted only.
- **era:** none.

### Nature Wrath (`enchants/nature-wrath`)

- **codex:** `02-enchants-armor-m-z.md § Nature Wrath`
- **activation:** trigger `DEFENSE`; condition
  `%damagecause% == "ENTITY_ATTACK" || %damagecause% == "PROJECTILE"`; chance
  `0.4·L` % (0.4/0.8/1.2/1.6); `soul-cost: 75` (fires only in soul mode with a
  payable pool — the global soul-enable toggle and out-of-souls feedback are the
  soul system's own).
- **decomposition:**
  1. `LIGHTNING(damage=0, who=@Aoe{r=8+5·L, filter=ENEMIES})` — visual bolt per victim
  2. `FREEZE(duration=(7+L)·20, dot=L, dot-period=20, slow=100,
     who=@Aoe{r=8+5·L, filter=ENEMIES})` — full root + DoT attributed to the wearer
     (jar: walk-speed 0 + Jump/Slowness 129 + L damage every 20 t)
  3. `POTION(effect=WEAKNESS, level=3, duration=(7+L)·20, who=@Aoe{…})`
  4. `DESPAWN(who=@Aoe{r=8+5·L, filter=MOBS})` (gap) + per-mob
     `PARTICLE(LARGE_EXPLODE, count=10)` and `PARTICLE(SPELL, count=35)`
  5. `SOUND(LEVEL_UP, volume=1.0, pitch=0.65, who=@Self)`; per frozen victim
     `SOUND(ENDERDRAGON_GROWL, volume=2.0, pitch=2.0)`; per DoT pulse
     `SOUND(GHAST_SCREAM2, volume=2.0, pitch=2.0)` + `PARTICLE(SPELL, count=35)`
  6. `MESSAGE` lines (strings below)
- **gaps:** `FREEZE_BREAKOUT — per-blocked-action chance for a frozen target to
  shatter the root early; params: chance; consumers: struggle-out roots (here
  30 − 7.5·L %). Why: FREEZE's window is fixed, no struggle mechanic exists.`
  **SHIPPED as `FREEZE.breakout-chance`.**
  `DESPAWN — silently remove target non-player entities (no drops, no XP, no death
  event); params: none beyond targeting; consumers: AoE mob-clear procs. Why: KILL
  fires a real death (drops/XP), no primitive removes silently.`
  **SHIPPED as the `DESPAWN` effect** (wave 1d.2).
  `FILTER_COMPOSE — conjunction of selector filters (e.g. ENEMIES ∧ PLAYERS);
  params: filter list; consumers: player-only hostile payloads here and in Plague
  Carrier / Smoke Bomb. Why: AOE/NEAREST accept exactly one filter.`
  **SHIPPED as the filter `+` conjunction on AOE/NEAREST.**
- **interactions:** blocked while a Soul Trap window (axes doc) is active on the
  wearer; Poltergeist (mastery doc) grants freeze immunity — jar always-100%
  (bug), intended a 12.5%·level roll, and an immune victim still takes the bolt
  and DoT; frozen players cannot melee or shoot (engine freeze-lock); the jar's
  root enforcement was global (any walk-speed-0 source disarmed) — engine FREEZE
  scopes per-target; KOTH double cost (150) and short durations `(7+L)·5` t are
  environment gates (not shipped); the jar's die-while-rooted walk-speed leak is
  engine hygiene, never replicated.
- **strings:** wearer on proc: blank line, `§a§l** NATURE'S WRATH **`,
  `§c§l- {cost} Soul Gems`, `§7You have §n{souls}§7 souls left.`, blank line.
  Out of souls: `§c§l** OUT OF SOULS **` (+ `PARTICLE(LAVA, count=20)` at eye
  height, `SOUND(ITEM_BREAK, volume=0.7, pitch=0.4)`). Poltergeist-immune victim:
  `§4§l* POLTERGEIST [§7Immune: Nature's Wrath§4§l] *`. Frozen victim, every DoT
  pulse: `§2§l** NATURE'S WRATH **` (rides the FREEZE pulse — cosmetic attach).
- **numbers:** max 4, weight 2, thresholds 15/18/21/24, tier 6. Chance
  0.4/0.8/1.2/1.6 %; radius 13/18/23/28; root+DoT window 160/180/200/220 t; DoT
  L HP per 20 t (totals 8/18/30/44 HP); Weakness III for the window; break-free
  22.5/15/7.5/0 % (the exact-0 at max level is shipped as measured); soul cost 75
  flat. Ledger rows: Poltergeist always-true immunity (D-02-2); mob clear drop
  semantics, DESPAWN vs KILL (D-02-3).
- **era:** FREEZE's powder-snow visual is 1.17+ — the legacy overlay must fall
  back to the potion-root presentation; `GHAST_SCREAM2`/`ENDERDRAGON_GROWL`/
  `LEVEL_UP` are 1.8 sound ids (resolver-mapped).

### Nutrition (`enchants/nutrition`)

- **codex:** `02-enchants-armor-m-z.md § Nutrition`
- **activation:** trigger `EAT` (leggings worn; only hunger **gains** amplify).
- **decomposition:** `1. FOOD_GAIN_SCALE(factor=1.1+0.3·L, mode=absolute)` (gap) —
  per-level factors ×1.4 / ×1.7 / ×2.0 applied to the resulting food level
  (measured semantics), server-clamped at 20.
- **gaps:** `FOOD_GAIN_SCALE — scale the hunger restored by an EAT event; params:
  factor, mode (absolute = scale the resulting food level, delta = scale the
  gain); consumers: hunger-amplifier leggings. Why: MODIFY_FOOD is a flat give and
  no fact exposes the eat event's before/after food values.`
  **SHIPPED as `MODIFY_FOOD mode=scale-gain`.**
- **interactions:** the jar also marked the wearer for an external golden-apple
  sickness system (consumer UNRESOLVED in the codex) — not ported.
- **strings:** none.
- **numbers:** max 3, weight 9 (highest in this set), thresholds 15/20/25, tier 2.
  Multipliers ×1.4/×1.7/×2.0 — the config description's "1.2–2x" low end is wrong
  in the jar; code values ship. Absolute-mode quirk (benefit grows with fullness)
  is measured behavior and ships as mode=absolute.
- **era:** none.

### Obsidianshield (`enchants/obsidianshield`)

- **codex:** `02-enchants-armor-m-z.md § Obsidianshield`
- **activation:** trigger `PASSIVE` (maintained while worn).
- **decomposition:** `1. POTION(effect=FIRE_RESISTANCE, level=1, duration=∞)` —
  the engine's passive-potion maintenance (permanent while worn, removed on
  unequip) replaces the jar's `Integer.MAX_VALUE` duration + tracker.
- **interactions:** on the jar's stackable whitelist but max 1 — stacking is
  effect-inert (duplicate pieces only guard against premature removal); the engine
  passive layer's dedupe covers this. Any stronger Fire Resistance source
  suppresses it (equal-or-higher amplifier rule) — engine potion-priority rule.
- **strings:** worn/removed feedback (jar tracker lines, verbatim):
  `§b§l[+] §bObsidianshield I:§7 applying FIRE_RESISTANCE I`,
  `§c§l[-] §cObsidianshield I:§7 removing FIRE_RESISTANCE I` — lang-layer
  equivalents.
- **numbers:** max 1, weight 2, threshold 30, tier 4. Fire Resistance I, permanent.
- **era:** none.

### Overload (`enchants/overload`)

- **codex:** `02-enchants-armor-m-z.md § Overload`
- **activation:** trigger `PASSIVE` (maintained while worn).
- **decomposition:** `1. POTION(effect=HEALTH_BOOST, level=L, duration=∞)` —
  amplifier L−1 = +4·L max health (+2/+4/+6 hearts).
- **interactions:** stackable in bookkeeping, **not in effect** — equal-or-higher
  amplifier is skipped, so pieces never sum and only the highest level expresses;
  POTION (non-stacking amplifier semantics) matches this exactly, plus the
  highest-only interaction rule. Progression interlock with its heroic upgrade
  (tier 7): the upgrade only applies over max-level Overload, and Overload refuses
  to apply over the upgrade — anvil/progression rule, not runtime. A mastery
  drain effect (mastery doc) reduces the expressed HEALTH_BOOST amplifier by its
  own level + 1, skipping the effect entirely at ≤ 0 — interaction rule.
- **strings:** tracker-equivalents, e.g.
  `§b§l[+] §bOverload III:§7 applying HEALTH_BOOST III` /
  `§c§l[-] §cOverload III:§7 removing HEALTH_BOOST III`.
- **numbers:** max 3, weight 2, thresholds 20/25/30, tier 5. Health Boost I/II/III
  (+4/+8/+12 HP), permanent while worn.
- **era:** none.

### Paradox (`enchants/paradox`)

- **codex:** `02-enchants-armor-m-z.md § Paradox`
- **activation:** trigger `DEFENSE`; condition
  `%damagecause% == "ENTITY_ATTACK" || %damagecause% == "PROJECTILE"` and
  `%nearbyallies% >= 1` (gap fact — the jar only charges souls when at least one
  ally is in range); chance `10·L` %; `soul-cost: 5`.
- **decomposition:**
  1. `MODIFY_HEALTH(amount="%damage% * 0.1·L", mode=give,
     who=@Aoe{r=8+4·L, filter=ALLIES})` — **intended** heal (measured is 0.0, see
     numbers)
  2. per healed ally `PARTICLE(HAPPY_VILLAGER, count=20)` +
     `SOUND(EAT, volume=1.0, pitch=2.0)`
  3. `SOUND(LEVEL_UP, volume=1.0, pitch=0.65, who=@Self)`
  4. `MESSAGE` lines (strings below)
- **gaps:** `ALLY_COUNT_FACT — a %nearbyallies% condition fact (radius-scoped
  count of allied players); params: radius; consumers: soul-charged ally auras
  that must not debit on an empty ally set. Why: %nearbyenemies% exists, no ally
  counterpart.`
  **SHIPPED as `%nearbyallies%`** (wave 1b).
- **interactions:** ally = faction ally/member both ways, neither in duel —
  engine relation layer; spectators excluded; soul discounts (Outposts/faction
  upgrades incl. the top-rank no-discount quirk) are environment gates, not
  ported.
- **strings:** to each healed ally: `§a§l** PARADOX [{owner}] (+{heal}HP) **`
  (jar renders `(+0.0HP)`); to the wearer: `§2§l** PARADOX [{total} -> HP]  **`
  (two spaces before the trailing `**`, verbatim); soul lines (jar prints them
  only when the balance is a multiple of 100 or < 10 — lang-layer choice):
  `§c§l- {cost} Soul Gems`, `§7You have §n{souls}§7 souls left.`; out of souls:
  `§c§l** OUT OF SOULS **`.
- **numbers:** max 5, weight 2, thresholds 15/18/21/24/27, tier 6. Chance
  10/20/30/40/50 %; radius 12/16/20/24/28 (jar box is horizontal only — the
  vertical reach was `radius+128`, an asymmetry AOE does not replicate; noted);
  soul cost 5 flat, no cooldown (sustained fire drains souls — measured).
  **Known bug (fatal):** heal `damage × (level/10)` uses integer division — always
  **0.0 HP** at every level while still charging souls and playing feedback.
  Intended `damage × level/10` (10 %·L of the triggering hit) ships
  (ledger D-02-4). The jar's "ally needs healing" gate degenerates to "any ally nearby"
  because of the same bug — the `%nearbyallies%` gate is the intended form.
- **era:** `HAPPY_VILLAGER` particle and `EAT`/`LEVEL_UP` sounds are 1.8 ids
  (resolver-mapped).

### Phoenix (`enchants/phoenix`)

- **codex:** `02-enchants-armor-m-z.md § Phoenix`, `00-MECHANICS.md` §3
- **activation:** ability 1 — trigger `DEFENSE` (all damage causes); condition
  `%damage% >= %actor.health%` (lethal-hit gate); `soul-cost: 500` base with
  escalation (gap); cooldown 3600/2400/1200 t. Ability 2 — trigger `PASSIVE`:
  suppression immunity.
- **decomposition:**
  1. `CANCEL` — the lethal hit is fully negated
  2. `MODIFY_HEALTH(amount="%actor.maxhealth%", mode=set, who=@Self)` — full heal
  3. `SOUND(ENDERDRAGON_GROWL, volume=1.0, pitch=1.25)` at the owner (audible copy
     for everyone nearby is the broadcast sound at the same location)
  4. `PARTICLE(FLAME, count=80)` + `PARTICLE(LAVA, count=20)`
  5. `MESSAGE` own lines; `MESSAGE(who=@AllPlayers{r=48})` broadcast
  6. (separate PASSIVE ability) `SUPPRESS_IMMUNE(chance=100)` — Phoenix is the
     one enchant exempt from defensive-proc suppression
- **gaps:** `ESCALATING_SOUL_COST — a soul-cost gate whose price multiplies per
  proc and decays on a clock; params: base (500), growth-factor (2), cap (8000),
  decay-period (12000 t, −1 step, floor 0); consumers: last-stand saves. Why:
  soul-cost is static, no soul-balance fact exists, and no primitive holds an
  arithmetic per-player counter.`
  **SHIPPED as `soul-cost-growth` / `-cap` / `-decay-period`.**
  `MULTI_ABILITY_ENCHANT — an enchant carrying several ability blocks each with
  its own trigger/condition/chance; consumers here: Phoenix (DEFENSE save +
  PASSIVE immunity), Rocket Escape (DEFENSE + FALL), Protection (three
  independent sub-rolls per pulse), Spirit Link (mutually-exclusive condition
  split). Why: the enchant schema binds one trigger and one ability per level. If
  the compiler already accepts an ability list, this gap is zero work.`
  **SHIPPED as the compiler's ability list** (wave 1a).
- **interactions:** a Death Knight mask on the attacker blocks the save 50 % of
  the time (masks doc 11) — and the jar still burns the full cooldown on a
  blocked save (measured, shipped as measured); suppression exemption is the
  SUPPRESS_IMMUNE ability; the jar's armor-value>25 self-disable quirk is an
  artifact of its lethal-gate math, not ported (the `%damage%` gate has no such
  hole); out-of-souls feedback throttled 15 s (lang).
- **strings:** blocked, to the owner:
  `§c§l* PHOENIX BLOCKED [§7{attacker}§c§l] *`; blocked, to the attacker:
  `§c§l* DEATH KNIGHT MASK [§7{owner}'s Phoenix Blocked§c§l] *`. On success, to
  the owner: blank line, `§6§l*** §nPHOENIX SOUL§6§l ***`,
  `§c§l- {cost} Soul Gems`, `§7You have §n{souls}§7 souls left.`, blank line.
  Broadcast (48 blocks): `§c§l*** PHOENIX SOUL (§7{owner}, -{cost} souls§c§l) ***`.
  Out of souls: `§c§l** OUT OF SOULS **`.
- **numbers:** max 3, weight 2, thresholds 15/18/21, tier 6. Cooldown
  `(4−L)` min = 3600/2400/1200 t; soul cost 500·2^procs capped at 8000
  (500/1000/2000/4000/8000, ≥ 5 procs stay 8000); decay −1 proc per 10 min.
- **era:** `ENDERDRAGON_GROWL` 1.8 sound id; FLAME/LAVA particles fine.

### Plague Carrier (`enchants/plague-carrier`)

- **codex:** `02-enchants-armor-m-z.md § Plague Carrier`
- **activation:** trigger `DEFENSE`; condition `%damage% >= %actor.health%`
  (lethal-hit gate); cooldown 200 t (10 s re-arm).
- **decomposition:**
  1. `MESSAGE(text=<warning>, who=@Aoe{r=round(1.5·L), filter=ENEMIES})` —
     hostile players only in the jar (FILTER_COMPOSE note)
  2. `POTION(effect=POISON, level=(L>=7 ? 2 : 1), duration=(2+L)·20,
     who=@Aoe{r=round(1.5·L), filter=ENEMIES})`
  3. `SPAWN_ENTITY(type=CREEPER, count=round(1.5·L)/2, powered=true)` +
     `SUMMON_PAYLOAD(name="§dPlague Carrier", phase=detonate, radius=5×4×5,
     terrain=none, effects=[POISON always; BLINDNESS L>=3; WEAKNESS L>=6;
     SLOWNESS L==8 — each duration L·2·20 t, amplifier (L==8 ? 2 : L>=4 ? 1 : 0)],
     filter=ALL)` (gap; the jar payload has **no** ally filter — measured,
     record the ally-exemption question as an interaction decision)
  4. `KILL(who=@Self)` — the wearer is deliberately finished off; the incoming
     event is NOT cancelled
  5. `SOUND(CREEPER_HISS, volume=2.0, pitch=0.75)` + `PARTICLE(CLOUD, count=75)`;
     per victim `SOUND(CREEPER_HISS, volume=1.6, pitch=0.75)`; per spawn
     `PARTICLE(CLOUD, count=20)`; per detonation `SOUND(EXPLODE)` +
     `PARTICLE(LARGE_EXPLODE, count=3)`
- **gaps:** `SUMMON_PAYLOAD — attach configured behavior to a summoned entity:
  display name, self-buff potion list, and an effect payload fired on a phase
  (detonate | death | periodic pulse with period/radius/filter/max-targets),
  detonation replacing the vanilla explosion (no terrain damage, summon removed);
  params as listed; consumers: Plague Carrier, Self Destruct, Spirits, Undead
  Ruse. Why: SPAWN_ENTITY/SPAWN_SWARM/GUARD spawn bare bodies; detonate=PLAYER_HIT
  only gates a vanilla creeper explosion and cannot substitute a payload.`
  **SHIPPED as the `SUMMON_PAYLOAD` trigger** (waves 1c/1d).
- **interactions:** death-burst poison respects relations (truce+ skipped) while
  the creeper payload hits everyone including allies — measured asymmetry,
  recorded for the interaction layer to keep or normalize; summons drop no loot
  and give no XP; all creepers spawn at one point (jar) — SPAWN_ENTITY placement
  matches.
- **strings:** warning, verbatim:
  `§d§l(!) §d{owner} has died, and was a Plague Carrier! RUN!`; creeper name
  `§dPlague Carrier` (always visible).
- **numbers:** max 8, weight 3, thresholds 25..95 step 10, tier 2. Radius
  2/3/5/6/8/9/11/12; creepers 1/1/2/3/4/4/5/6; death-burst poison 60..200 t
  (3–10 s), Poison I (II from L7); payload durations 40..320 t (2–16 s),
  amplifier 0/0/0/1/1/1/1/2 → Poison/Blindness/Weakness/Slowness per the phase
  table (Blindness from L3, Weakness from L6, Slowness only L8).
- **era:** charged creeper is 1.8-safe; `CREEPER_HISS`/`EXPLODE` 1.8 sound ids;
  `LARGE_EXPLODE`/`CLOUD` 1.8 particle ids.

### Poisoned (`enchants/poisoned`)

- **codex:** `02-enchants-armor-m-z.md § Poisoned`
- **activation:** trigger `DEFENSE`; condition
  `%damagecause% == "ENTITY_ATTACK"`; chance per level (see numbers).
- **decomposition:** `1. POTION(effect=POISON, level=(L>2 ? 2 : 1),
  duration=<per-level>, who=@Attacker)`.
- **interactions:** non-stacking; the jar's non-forced potion can be shadowed by
  a stronger existing Poison (engine potion-priority rule).
- **strings:** none.
- **numbers:** max 4, weight 2, thresholds 14/18/22/26, tier 3. Jar chance
  10/20/30/30 % (capped at 30 from L3 — measured, shipped); jar duration is a
  random integer second from `(int)(rand·L + 0.5·L)`: L1 0–1 s (half the procs are
  **zero-tick no-ops** — known bug), L2 1–2 s, L3 1–4 s, L4 2–5 s. Shipped
  (random durations are not expressible): L1 chance 5 % duration 20 t (folds the
  zero-tick half into the roll), L2 20 % / 30 t, L3 30 % / 50 t, L4 30 % / 70 t —
  distribution means (ledger D-02-5).
- **era:** none.

### Protection (`enchants/protection`)

- **codex:** `02-enchants-armor-m-z.md § Protection`
- **activation:** trigger `REPEATING`, paced by cooldown 200/80/40/40/40 t;
  four sub-abilities on the same pulse (MULTI_ABILITY_ENCHANT).
- **decomposition:**
  1. `MODIFY_HEALTH(amount=(L>4 ? 2 : 1), mode=give, who=@Aoe{r=2·L,
     filter=ALLIES})` + per-healed `PARTICLE(RED_DUST, count=20)`
  2. (15 % roll) `POTION(effect=REGENERATION, level=(L>2 ? 2 : 1),
     duration=L·20, who=@Aoe{r=2·L, filter=ALLIES})` + `PARTICLE(SPELL, count=10)`
  3. (10 % roll, L ≥ 2) `POTION(effect=ABSORPTION, level=L+2, duration=L·40,
     who=@Aoe{…})` + `PARTICLE(SPELL, count=20)`
  4. (8.5 % roll, L ≥ 3) `POTION(effect=HEALTH_BOOST, level=L+2, duration=L·40,
     who=@Aoe{…})` + `PARTICLE(SPELL, count=30)`
- **gaps:** none — consumes `MULTI_ABILITY_ENCHANT` (declared under Phoenix) for the
  three independent sub-rolls sharing one pulse, **SHIPPED as the compiler's ability
  list** (wave 1a).
- **interactions:** allies = faction ally/member (relation layer); the wearer is
  **never** buffed (AOE excludes the activator — matches the jar); spectator /
  duel / staff-freeze exemptions are engine-level; mixed-level pieces genuinely
  multiply pulse streams in the jar (each level's task processes the wearer) —
  shipped as highest-only per the standard rule, noted as a divergence candidate.
- **strings:** none.
- **numbers:** max 5, weight 2, thresholds 10/15/20/25/30, tier 5. Pulse period
  200/80/40/40/40 t — the jar's `40·(5/L)` integer division parks L3–5 at 40 t
  (measured, shipped); radius 2/4/6/8/10; direct heal 1/1/1/1/2 HP (the jar's
  strict `health + k < max` skip-at-the-brim quirk is replaced by the engine's
  clamp — benign); Regen I/I/II/II/II for 20..100 t; Absorption IV..VII for
  80..200 t; Health Boost V..VII for 120..200 t; sub-roll chances 15/10/8.5 %
  level-independent.
- **era:** `RED_DUST`/`SPELL` 1.8 particle ids.

### Ragdoll (`enchants/ragdoll`)

- **codex:** `02-enchants-armor-m-z.md § Ragdoll`
- **activation:** trigger `DEFENSE` (any cause with a living damager); chance
  `12.5·L` %.
- **decomposition:** `1. VELOCITY(mode=away, anchor=attacker, strength=1.5+0.5·L,
  who=@Self)` — anchor param is the gap: the wearer is launched away from the
  attacker.
- **gaps:** `VELOCITY_ANCHOR — an anchor param for VELOCITY mode=away (activator |
  attacker | victim) choosing the point the target is pushed away from; consumers:
  self-launch defensive procs. Why: away is hardwired to push away from the
  activator, which is the wearer itself here.`
  **SHIPPED as `VELOCITY.anchor`.**
- **interactions:** jar skips iron-golem attackers and The End / KOTH worlds
  (environment gates, not shipped); fires on any living damager including allies
  and pets (measured — record for the interaction layer); no fall-damage
  exemption on landing (measured, unlike Rocket Escape).
- **strings:** none.
- **numbers:** max 4, weight 3, thresholds 25/35/45/55, tier 4. Chance
  12.5/25/37.5/50 %; push speed 2.0/2.5/3.0/3.5.
- **era:** none.

### Repair Guard (`enchants/repair-guard`)

- **codex:** `02-enchants-armor-m-z.md § Repair Guard`
- **activation:** `EQUIP_CHANGE_TRIGGER` (gap) — fires on unequip of the carrying
  piece; condition `%item.durabilitypercent% <= 15/20/25` (per level, remaining
  durability — the **intended** gate, see numbers); cooldown 600 t.
- **decomposition:** `1. POTION(effect=ABSORPTION, level=3+L, duration=(2+L)·20,
  who=@Self)` — applied force (overrides weaker absorption).
- **gaps:** `EQUIP_CHANGE_TRIGGER — a trigger firing when a piece carrying the
  ability is equipped or unequipped, exposing piece facts
  (%item.durabilitypercent%); params: direction (equip|unequip); consumers:
  repair-window buffs. Why: PASSIVE is maintained state with no transition event,
  and ITEM_DAMAGE fires on durability loss, not removal.`
  **SHIPPED as the `EQUIP_CHANGE` trigger** (wave 1c).
- **interactions:** one proc per cooldown window no matter how many damaged
  pieces are removed; the jar's equip half was a dead no-op (nothing to port).
- **strings:** none.
- **numbers:** max 3, weight 2, thresholds 10/15/20, tier 3. Absorption IV/V/VI
  (16/20/24 HP = 8/10/12 hearts) for 60/80/100 t; cooldown 600 t (30 s) flat.
  **Known bug (fatal):** the jar's durability ratio uses short/short integer
  division — 0 for any damaged piece — so it procs on unequipping **any** piece
  with ≥ 1 damage at every level, and the ratio is also inverted vs the
  description. Intended (shipped): proc only at ≤ 15/20/25 % remaining
  durability (ledger D-02-6).
- **era:** durability API differs (legacy `getDurability` = damage taken) — the
  item layer's normalized durability fact absorbs it.

### Resilience (`enchants/resilience`)

- **codex:** `02-enchants-armor-m-z.md § Resilience`
- **activation:** trigger `DEFENSE`; condition
  `%damage% >= %actor.health% && %damage% >= 10−L`; chance `5·L` %.
- **decomposition:**
  1. `DAMAGE_MOD(side=defense, mode=add, amount=50)` — halve the hit
  2. `MESSAGE(text=<proc line>, who=@Self)`
- **interactions:** helmet-only, non-stacking; the combat-log NPC "+15 % HP per
  level" from the description lives in an external plugin (codex UNRESOLVED) —
  not ported; message is sent whether or not the halved hit still kills
  (measured).
- **strings:** `§2§l** RESILIENCE (§250% DMG TAKEN§l) **` (verbatim).
- **numbers:** max 4, weight 2, thresholds 17/21/25/29, tier 3. Minimum-damage
  floor 9/8/7/6 (inverted ladder — higher level lowers the bar; measured,
  shipped); chance 5/10/15/20 %; halving flat 50 % at all levels. **Known bug:**
  the jar gates on final damage but halves base damage (the message overstates
  the save) — the engine's single damage scalar halves the hit for real
  (ledger D-02-7).
- **era:** none.

### Rocket Escape (`enchants/rocket-escape`)

- **codex:** `02-enchants-armor-m-z.md § Rocket Escape`
- **activation:** ability 1 — trigger `DEFENSE`; condition
  `%damage% >= %actor.health%`; cooldown 600 t (30 s). Ability 2 — trigger
  `FALL`; condition `%rocketescaping%` (SET_VAR window). MULTI_ABILITY_ENCHANT.
- **decomposition:**
  1. `CANCEL` — the lethal hit is fully negated
  2. `SOUND(EXPLODE, volume=1.0, pitch=0.54, who=@Self)`
  3. `VELOCITY(mode=add, y=4+2·L, who=@Self)` — pure vertical launch
  4. `REMOVE_POTION(effect=SLOW)` + `REMOVE_POTION(effect=SLOW_DIGGING)`
  5. `POTION(effect=REGENERATION, level=L+1, duration=20·(L+2), who=@Self)`
  6. `SET_VAR(name=rocketescaping, value=1, ttl=20·(L+2)+5)` — escape window
  7. `PARTICLE(CLOUD, count=69)` at launch; repeated at the window's end
  8. `MESSAGE` lines (strings below)
  9. (ability 2, FALL while `%rocketescaping%`) `CANCEL` — no fall damage from
     the launch (replaces the jar's external never-cleared exemption flag)
- **gaps:** none — consumes `MULTI_ABILITY_ENCHANT` (declared under Phoenix),
  **SHIPPED as the compiler's ability list** (wave 1a).
- **interactions:** an attacker's Sabotage window (swords doc, 1000 ms) blocks
  the escape at 10 %·sabotage-level — interaction rule; the jar burns the 30 s
  cooldown even on a sabotaged escape (measured, shipped as measured — same
  pattern as Phoenix's blocked save); The End / KOTH / dungeon-parkour skips and
  the reduced duel-world launch (2+L) are environment gates, not shipped; the
  jar's global SLOW untrack spammed removal lines across enchants — engine
  REMOVE_POTION is scoped, hygiene not replicated.
- **strings:** sabotaged: `§c§l ** §7Rocket Escape:§c§l SABOTAGED **` (leading
  space verbatim). On launch: blank line,
  `§a§l(!) §aYour Rocket Escape boots have activated, recover while they last!`,
  blank line.
- **numbers:** max 3, weight 3, thresholds 25/30/35, tier 3. Launch Y velocity
  6/8/10; Regeneration II/III/IV for 60/80/100 t; escape window 65/85/105 t;
  cooldown 600 t flat.
- **era:** `EXPLODE` 1.8 sound id; raw Y velocities 6–10 exceed client-warning
  thresholds identically on 1.8 (jar-faithful).

### Self Destruct (`enchants/self-destruct`)

- **codex:** `02-enchants-armor-m-z.md § Self Destruct`
- **activation:** trigger `DEFENSE`; condition `%damage% >= %actor.health%`;
  cooldown 200 t (10 s).
- **decomposition:**
  1. `MESSAGE(text=<warning>, who=@Aoe{r=int(2.5·L), filter=ENEMIES})` — the jar
     warns non-allies only (truce nuance recorded for the relation layer)
  2. `SPAWN_ENTITY(type=PRIMED_TNT, count=int(2.5·L), ttl=120−20·L)` +
     `SUMMON_PAYLOAD(phase=detonate, terrain=none, scatter=±1..3 XZ air-scan,
     effects=[DAMAGE 16.0; IGNITE 40 t; push 1.7 away from the blast], radius=
     4×4×4, filter=ENEMIES-with-truce-skip, skip-spectators)` (gap)
  3. `SOUND(EXPLODE, volume=2.0, pitch=0.75)`; per detonation `SOUND(EXPLODE,
     volume=1.0, pitch=1.0)` + `PARTICLE(LARGE_EXPLODE, count=3)`
  — the wearer is neither healed nor saved: the incoming damage stands (measured;
  contrast Plague Carrier's deliberate finish and Smoke Bomb's cancel).
- **gaps:** none — consumes `SUMMON_PAYLOAD` (declared under Plague Carrier;
  `scatter` is a payload placement param), **SHIPPED as the `SUMMON_PAYLOAD`
  trigger** (waves 1c/1d).
- **interactions:** PvP-region and relation checks per victim (relation layer);
  the jar resolved the owner by name — a logged-off owner dropped the ally filter
  entirely (bug-adjacent; engine ownership is identity-based, not replicated);
  TNT tagged no-loot.
- **strings:** `§c§l(!) §c{owner}'s Self-Destruct was activated, RUN!` (verbatim).
- **numbers:** max 3, weight 3, thresholds 25/35/45, tier 2. TNT count 2/5/7;
  fuse 100/80/60 t (5/4/3 s); payload flat: 16.0 damage, 40 t fire, push 1.7,
  no terrain damage, 10 s re-arm. **Known bug (bytecode-confirmed):** the jar's
  knockback pushes the **TNT entity** (arguments inverted) — victims take zero
  knockback. Intended (shipped): victims pushed at 1.7 away from the blast
  (ledger D-02-8).
- **era:** legacy entity id `PRIMED_TNT` vs modern `TNT` — resolver handle;
  `EXPLODE` 1.8 sound id.

### Shockwave (`enchants/shockwave`)

- **codex:** `02-enchants-armor-m-z.md § Shockwave`
- **activation:** trigger `DEFENSE`; condition
  `%damagecause% == "ENTITY_ATTACK"`; chance `2·L` %.
- **decomposition:**
  1. `VELOCITY(mode=away, strength=1.7, who=@Aoe{r=L, filter=ENEMIES})` — all
     hostiles in radius launched radially from the wearer (jar pushes every
     non-ally living entity, mobs included)
  2. per target `PARTICLE(ENCHANTMENT_TABLE, count=100)`
  3. `SOUND(FIREWORK_LAUNCH, volume=1.0, pitch=0.1)` +
     `PARTICLE(ENCHANTMENT_TABLE, count=100, who=@EyeHeight)`
- **interactions:** chestplate-only, non-stacking; vanished players skipped
  (engine visibility rule); per-target PvP-region check (relation layer). The
  jar's KOTH-reduced 1.35 push is dead code inside a not-KOTH guard — Shockwave
  simply never fires in that world; environment gate, not shipped.
- **strings:** none.
- **numbers:** max 5, weight 2, thresholds 15/20/25/30/35, tier 3. Chance
  2/4/6/8/10 %; push radius = level (1–5 blocks — at L1 smaller than melee
  reach, measured); push speed 1.7 flat.
- **era:** `ENCHANTMENT_TABLE` 1.8 particle id; `FIREWORK_LAUNCH` 1.8 sound id.

### Smoke Bomb (`enchants/smoke-bomb`)

- **codex:** `02-enchants-armor-m-z.md § Smoke Bomb`
- **activation:** trigger `DEFENSE`; condition `%damage% >= %actor.health%`;
  cooldown 300 t (15 s).
- **decomposition:**
  1. `CANCEL` — the lethal hit is fully negated (a soul-free second Phoenix;
     measured, shipped)
  2. `POTION(effect=BLINDNESS, level=min(L,3)+1, duration=max(L,3)·20,
     who=@Aoe{r=round(1.5·L), filter=ENEMIES})` — hostile players only in the jar
     (FILTER_COMPOSE note)
  3. `POTION(effect=SLOW_DIGGING, level=min(L,3)+1, duration=max(L,3)·20,
     who=@Aoe{…})`
  4. `PARTICLE(CLOUD, count=75)` + `SOUND(FIREWORK_TWINKLE2, volume=2.0,
     pitch=0.75)`; per victim `SOUND(FIREWORK_TWINKLE2, volume=1.6, pitch=0.75)`
  5. `MESSAGE(text=<escape line>, who=@Aoe{…})`
- **gaps:** none — consumes `FILTER_COMPOSE` (declared under Nature Wrath),
  **SHIPPED as the filter `+` conjunction on AOE/NEAREST**.
- **interactions:** helmet-only, non-stacking; truce+ players exempt (relation
  layer).
- **strings:** `§c§l(!) §c{owner} has thrown a Smoke Bomb in an attempt to
  escape!` (verbatim).
- **numbers:** max 8, weight 3, thresholds 25..95 step 10, tier 3. Radius
  2/3/5/6/8/9/11/12; duration 60/60/60/80/100/120/140/160 t; amplifier saturates:
  Blindness/Mining Fatigue II/III/IV then IV flat from L3 (the jar's
  max/min-in-opposite-directions quirk, measured, shipped).
- **era:** `FIREWORK_TWINKLE2` 1.8 sound id; `SLOW_DIGGING` legacy potion id.

### Spirit Link (`enchants/spirit-link`)

- **codex:** `02-enchants-armor-m-z.md § Spirit Link`
- **activation:** trigger `DEFENSE`; condition
  `%damagecause% == "ENTITY_ATTACK"`; chance `10·L` %; two mutually-exclusive
  condition-split abilities express the heal cap (MULTI_ABILITY_ENCHANT).
- **decomposition:** with divisor `D = 4·(6−L)` (20/16/12/8/4) and cap threshold
  `T = 4·D` (80/64/48/32/16):
  1. (ability A, condition `… && %damage% < T`)
     `MODIFY_HEALTH(amount="%damage% / D", mode=give, who=@Aoe{r=2·L,
     filter=ALLIES})`
  2. (ability B, condition `… && %damage% >= T`)
     `MODIFY_HEALTH(amount=4.0, mode=give, who=@Aoe{r=2·L, filter=ALLIES})`
  3. per healed ally `PARTICLE(HEART, count=1)` at eye height; at the wearer
     `PARTICLE(HEART, count=3)` + `SOUND(ORB_PICKUP, volume=1.0, pitch=0.35)`
  — the split is roll-safe: the conditions are disjoint, so exactly one ability
  rolls per event.
- **gaps:** none — consumes `MULTI_ABILITY_ENCHANT` (declared under Phoenix),
  **SHIPPED as the compiler's ability list** (wave 1a).
- **interactions:** chestplate-only, non-stacking; ally = faction ally/member
  both ways, neither in duel; the wearer never heals themself (AOE excludes the
  activator — matches the jar); heal is per-ally, not divided (a faction ball
  multiplies total healing — measured).
- **strings:** none.
- **numbers:** max 5, weight 2, thresholds 15/20/25/30/35, tier 3. Chance
  10/20/30/40/50 %; heal `min(4.0, damage/20 … damage/4)` by level; cap 4.0 HP
  flat; radius 2/4/6/8/10.
- **era:** `ORB_PICKUP` 1.8 sound id.

### Spirits (`enchants/spirits`)

- **codex:** `02-enchants-armor-m-z.md § Spirits`
- **activation:** trigger `DEFENSE`; conditions
  `%damagecause% == "ENTITY_ATTACK" && %victim.type% == "PLAYER"` — on a DEFENSE
  pass the "victim" facts read the ATTACKER, so that IS the jar's player-damager
  requirement (`guardians.yml` has shipped it since batch 01); chance 2 % flat;
  cooldown 200 t (10 s).
- **decomposition:**
  1. `SPAWN_ENTITY(type=BLAZE, count=(L==10 ? 2 : 1), ttl=200,
     health=50+10·L, owner=activator, targeting=false)` +
     `SUMMON_PAYLOAD(name="§c§l{owner}'s Spirit", self-buffs=[FIRE_RESISTANCE
     always; REGENERATION L>=4; STRENGTH L>=6; SPEED L>=8; RESISTANCE L==10 — all
     level I, permanent], phase=periodic, period=(int)((20+(10−L)·4)·1.5),
     radius=8+L, filter=ALLIES, max-targets=(L>6 ? 2 : 1),
     effects=[MODIFY_HEALTH give (L<=5 ? 1 : 2); SOUND(ORB_PICKUP, volume=0.3,
     pitch=1.4); PARTICLE(HEART, count=15)])` (gap)
  2. `PARTICLE(SPELL, count=45)` + `PARTICLE(FLAME, count=35)` on proc; per spawn
     `SOUND(IRONGOLEM_DEATH, volume=1.0, pitch=0.55)`; per pulse
     `PARTICLE(HEART, count=20)` at the blaze
- **gaps:** none — consumes `SUMMON_PAYLOAD` (declared under Plague Carrier),
  **SHIPPED as the `SUMMON_PAYLOAD` trigger** (waves 1c/1d).
- **interactions:** spirits can never damage or target players (jar cancels
  both; `targeting=false` + a passive payload reproduces it — the L≥6
  Strength/Speed buffs are decorative, measured); jar ally test is **exact**
  ALLY — the summoner's own faction, including the summoner, is never healed
  (measured; contradicts its description; record for the relation layer);
  per-ally heal pacing equals the pulse period (payload `max-targets` + period);
  50-entities-per-chunk spawn guard (engine summon cap); blaze fireballs were
  converted to no-grief small fireballs (engine summon projectile rule);
  despawn at ttl end, no drops.
- **strings:** blaze name `§c§l{owner}'s Spirit` (always visible).
- **numbers:** max 10, weight 2, thresholds 20..65 step 5, tier 4. Chance 2 %
  flat — the jar clamps `0.01 + 0.05·L` down to 0.02 at every level (nullifying
  clamp; the clamp is the deliberate value, shipped as measured); blazes 1
  (2 at L10 — `level/10` integer division, measured); blaze HP 60..150; pulse
  period 84/78/72/66/60/54/48/42/36/30 t; heal 1 HP (L1–5) / 2 HP (L6–10) — the
  jar's `5/2` integer halving parks L9–10 at 2 HP, measured; max targets 1
  (2 from L7); radius 9..18; lifetime 200 t flat.
- **era:** `IRONGOLEM_DEATH` 1.8 sound id; blaze AI/fireball behavior differs
  slightly on 1.8 (small-fireball conversion must be era-checked).

### Springs (`enchants/springs`)

- **codex:** `02-enchants-armor-m-z.md § Springs`
- **activation:** trigger `PASSIVE` (boots, maintained while worn).
- **decomposition:** `1. POTION(effect=JUMP, level=L, duration=∞)` — Jump Boost
  I/II/III permanent while worn.
- **interactions:** on the jar's stackable whitelist but boots-only — inert;
  amplifier-128 freeze effects (Nature Wrath, Trap) strip the vanilla effect
  while worn — the engine's passive-potion reconciliation re-applies after the
  freeze expires (the jar's stuck-until-retrigger gap is hygiene, not
  replicated).
- **strings:** tracker-equivalents, e.g.
  `§b§l[+] §bSprings III:§7 applying JUMP III` /
  `§c§l[-] §cSprings III:§7 removing JUMP III`.
- **numbers:** max 3, weight 2, thresholds 7/12/17 (lowest base in this doc),
  tier 3.
- **era:** `JUMP` legacy potion id.

### Sticky (`enchants/sticky`)

- **codex:** `02-enchants-armor-m-z.md § Sticky`
- **activation:** PASSIVE (worn marker); consumed by the Disarmor roll (swords
  doc 03).
- **decomposition:** `1. PASSIVE marker` — interaction-layer chance-delta on
  Disarmor: `-0.25 pp × Sticky level` off Disarmor's `0.25 pp × its level` roll,
  **no floor** (negative = never procs).
- **interactions:** Sticky level N exactly cancels Disarmor level N and
  everything below it — absolute immunity at equal-or-higher level (measured and
  matching the jar's own description); worn across four slots but read
  last-writer-wins in the jar — highest-only rule ships; Disarmor is additionally
  gated on its target being ≤ 10 HP (recorded in doc 03).
- **strings:** none (Disarmor's success feedback lives in doc 03).
- **numbers:** max 8, weight 2, thresholds 17/21/25/29/33/37/41/45, tier 4.
  Reduction 0.25 pp per level (0.25–2.0 pp), i.e. 12.5 % of Disarmor's max per
  level.
- **era:** none.

### Stormcaller (`enchants/stormcaller`)

- **codex:** `02-enchants-armor-m-z.md § Stormcaller`
- **activation:** trigger `DEFENSE` (any cause with a non-null, non-self
  damager); chance `4.5·L` %.
- **decomposition:**
  1. `LIGHTNING(damage=10, who=@Attacker)` — the bolt's vanilla 5-heart hit
     (the engine bolt is safe: no block ignition, no mob conversion — jar-real
     lightning side effects are not replicated, noted)
  2. `DAMAGE(amount=10.0, who=@Attacker)` — **intended** target (measured: the
     wearer, see numbers)
  3. `VELOCITY(mode=away, strength=1.5, who=@Attacker)`
- **interactions:** no ally, region, or cause filter in the jar (fires off any
  entity-sourced damage — record for the interaction layer); non-stacking;
  self-attack guarded.
- **strings:** none.
- **numbers:** max 4, weight 2, thresholds 15/18/21/24, tier 3. Chance
  4.5/9/13.5/18 %; lightning + 10.0 flat damage; push 1.5 flat. **Known bug
  (bytecode-confirmed):** the jar's 10.0 damage lands on the **wearer** (5 hearts
  of self-damage per proc) while bolt and knockback correctly target the
  attacker. Intended (shipped): all three on the attacker (ledger D-02-9).
- **era:** real-lightning vs effect-lightning split exists on 1.8 identically;
  no hazard.

### Tank (`enchants/tank`)

- **codex:** `02-enchants-armor-m-z.md § Tank`, `00-MECHANICS.md` §3.4
- **activation:** trigger `DEFENSE`; condition
  `%victim.helditem% contains "_AXE" && %damage% * (1 − 0.01875·L) > 1.0`.
- **decomposition:** `1. DAMAGE_MOD(side=defense, mode=add, amount=1.875·L)` —
  per-level 1.875/3.75/5.625/7.5 (percent), contributed per worn piece.
- **interactions:** **stackable** (jar whitelist): every piece fires with its own
  level. Jar composition is multiplicative per piece (full L4 set
  `0.925⁴` = 26.80 % — single-pass; Tank null-guards the attacker so pass 2 is a
  no-op, D-001 moot for it); the engine folds additively (full L4 set = 30 %) —
  divergence recorded (ledger D-02-10). Player-attacker-with-axe-only:
  mob/projectile/environmental damage unreduced (the held-item condition encodes
  it).
- **strings:** none (block-break flourish below).
- **numbers:** max 4, weight 2, thresholds 15/20/25/30, tier 4. Reduction
  1.875 %/level (config description says 1.85 % — description wrong, code
  ships); small hits skipped rather than clamped when the reduced value would be
  ≤ 1.0 (the condition encodes it, measured); world flourish: iron-block
  break effect at the wearer's feet per proc per piece.
- **era:** `_AXE` substring is version-safe (pickaxes never match); 1.8 material
  names (`WOOD_AXE`, `GOLD_AXE`) vs modern — the contains-match absorbs both.

### Trickster (`enchants/trickster`)

- **codex:** `02-enchants-armor-m-z.md § Trickster`
- **activation:** trigger `DEFENSE`; condition `%victim.type% == "PLAYER"` (the
  counterpart entity of a defense activation is the attacker; the codex gate is
  attacker-is-a-player and nothing else — no damage-cause narrowing, and the
  dispatcher resolves a projectile's shooter as the attacker, so a player-shot
  arrow procs it too); chance `1.25·L` %.
- **decomposition:**
  1. `SOUND(PORTAL_TRIGGER, volume=0.8, pitch=1.4)` +
     `PARTICLE(CLOUD, count=20)` at the origin
  2. `BLINK(distance=4)` — teleport along the wearer's facing up to 4 blocks
  3. `PARTICLE(WITCH_MAGIC, count=20)` at the destination
- **interactions:** non-stacking; no cooldown (10 % of hits at L8 — measured);
  no region check in the jar (engine teleport rules apply).
- **strings:** none.
- **numbers:** max 8, weight 2, thresholds 20..55 step 5, tier 3. Chance
  1.25/2.5/3.75/5/6.25/7.5/8.75/10 %; range 4 flat. **Deviation (flattening):**
  the jar teleports to the looked-at block only when it has two stacked air
  blocks (landing the wearer mid-air) and copies the **attacker's** facing;
  BLINK is ground-safe and keeps the wearer's own facing — behavior ships as
  BLINK (ledger D-02-11). (The jar's "behind your opponent" description was
  never true — record strings/description honestly from behavior.)
- **era:** `WITCH_MAGIC` 1.8 particle id; `PORTAL_TRIGGER` 1.8 sound id.

### Undead Ruse (`enchants/undead-ruse`)

- **codex:** `02-enchants-armor-m-z.md § Undead Ruse`
- **activation:** trigger `DEFENSE`; conditions
  `%damagecause% == "ENTITY_ATTACK" && %victim.type% == "PLAYER"` — the jar's
  player-damager requirement, kept: on DEFENSE the `%victim.*%` facts read the
  attacker (see § Spirits); chance `min(1·L, 4)` %.
- **decomposition:**
  1. `SPAWN_SWARM(type=ZOMBIE, count=<per-level>, ttl=0)` +
     `SUMMON_PAYLOAD(name="§d§l{owner}'s Undead Minion", self-buffs=[SPEED
     amp (L>6 ? 2 : L>3 ? 1 : 0); STRENGTH amp 2 when L>4; FAST_DIGGING amp 2
     when L>7 — permanent])` (gap) — ring placement replaces the jar's
     stacked-in-one-point spawn at the wearer's crosshair (could bury them in
     walls; not replicated, noted)
  2. `VIEWER_HIDE(duration=20·count+20, viewer=attacker, who=@Self)` (gap) —
     intended duration; measured 20 t flat (see numbers)
  3. `PARTICLE(WITCH_MAGIC, count=20)` at the spawn; `PARTICLE(WITCH_MAGIC,
     count=35)` on vanish and on reappear
- **gaps:** none — consumes `SUMMON_PAYLOAD` (declared under Plague Carrier),
  **SHIPPED as the `SUMMON_PAYLOAD` trigger** (waves 1c/1d).
  `VIEWER_HIDE — hide a target player from specific viewer(s) for a duration
  (full model hide, not a potion); params: duration, viewer (attacker | all);
  consumers: decoy/vanish defensive procs. Why: POTION INVISIBILITY hides from
  everyone, leaves armor visible, and cannot scope to one viewer.`
  **SHIPPED as the `VIEWER_HIDE` effect** (wave 1d.2).
- **interactions:** minions never hurt (or get hurt by) the owner or the owner's
  allies and never target them or spectators — engine summon-ownership rules
  (the jar's name-string ownership orphaned minions on rename; identity-based
  ownership ships); no cooldown and permanent zombies in the jar, bounded only by
  the 50-per-chunk guard — engine summon cap applies; drops cleared on death.
- **strings:** zombie name `§d§l{owner}'s Undead Minion` (always visible).
- **numbers:** max 10, weight 2, thresholds 20..65 step 5, tier 3. Chance
  1/2/3/4 % then clamped 4 % from L5 up (measured, shipped); zombie count jar
  random 1..⌈L/2⌉ — flattened to fixed 1/1/2/2/3/3/4/4/5/5 (static summon
  count, ledger D-02-12); buffs per the self-buff table, permanent. **Known
  bug:** the vanish window is always 20 t — the jar reuses a loop counter already
  decremented to 0; intended `(zombies × 20) + 20` t ships (ledger D-02-13).
- **era:** `WITCH_MAGIC` 1.8 particle id; `INCREASE_DAMAGE`/`FAST_DIGGING`
  legacy potion ids; zombie ttl=0 (permanent) matches the jar on both eras.

### Valor (`enchants/valor`)

- **codex:** `02-enchants-armor-m-z.md § Valor`, `00-MECHANICS.md` §3.4
- **activation:** trigger `DEFENSE`; condition
  `%actor.health% <= 2·L+6 && (%actor.helditem% == "IRON_SWORD" ||
  %actor.helditem% == "DIAMOND_SWORD") && %damage% * (1 − (0.015·L+0.075)) > 1.0`.
- **decomposition:** `1. DAMAGE_MOD(side=defense, mode=add, amount=1.5·L+7.5)` —
  per-level 9/10.5/12/13.5/15 (percent), contributed per worn piece.
- **interactions:** **stackable** (jar whitelist), per-piece multiplicative in
  the jar; Valor is victim-gated (never reads the attacker) so it **double-fires**
  in the jar — full max-set net `0.85⁸` = 72.75 %. Single-pass values ship
  (D-001): `0.85⁴` = 47.80 % measured single-pass vs 60 % under the engine's
  additive fold — divergence recorded (ledger D-02-14).
- **strings:** none (gold-block break flourish at the wearer's feet per proc per
  piece).
- **numbers:** max 5, weight 2, thresholds 20/25/30/35/40, tier 4. Reduction
  9/10.5/12/13.5/15 % (config description's "up to 22.5 %" was never the code —
  code ships); health gate 8/10/12/14/16 HP; wooden/stone/gold swords do not
  qualify (measured, encoded in the condition); small-hit skip encoded in the
  condition.
- **era:** 1.8 material names for swords are identical (`IRON_SWORD`,
  `DIAMOND_SWORD`) — no hazard.

### Voodoo (`enchants/voodoo`)

- **codex:** `02-enchants-armor-m-z.md § Voodoo`
- **activation:** trigger `DEFENSE`; condition
  `%damagecause% == "ENTITY_ATTACK"`; chance `4·L` %.
- **decomposition:**
  1. `POTION(effect=WEAKNESS, level=(L<4 ? 1 : 2), duration=(int)(1.5·L)·20+25,
     who=@Attacker)`
  2. `PARTICLE(MOB_SPELL, count=15, who=@Attacker)` at eye height
- **interactions:** non-stacking; non-forced potion can be shadowed by a
  stronger existing Weakness (engine potion-priority rule).
- **strings:** none.
- **numbers:** max 6, weight 2, thresholds 17/23/29/35/41/47, tier 3. Chance
  4/8/12/16/20/24 %; duration 45/85/105/145/165/205 t (the jar's `+25`-tick tail
  and truncated `1.5·L` ladder ship as measured — quarter-second ends and uneven
  +1s/+2s steps are the real values); Weakness I (I/I/I then II from L4).
- **era:** `MOB_SPELL` 1.8 particle id.

### Wither (`enchants/wither`)

- **codex:** `02-enchants-armor-m-z.md § Wither`
- **activation:** trigger `DEFENSE` (any cause with a non-null attacker — no
  cause filter, so projectile hits qualify too); chance `1.5·L` %.
- **decomposition:** `1. POTION(effect=WITHER, level=L, duration=20·L,
  who=@Attacker)`.
- **interactions:** non-stacking; non-forced potion shadowed by stronger
  existing Wither (engine potion-priority rule).
- **strings:** none.
- **numbers:** max 5, weight 2, thresholds 15/20/25/30/35, tier 3. Chance
  1.5/3/4.5/6/7.5 %; duration 20/40/60/80/100 t; Wither I–V (amp 4 at L5 is far
  above vanilla and ships as measured).
- **era:** Wither V tick cadence differs by version (~every 2 ticks on 1.8-era
  servers) — same config, era-dependent DPS; noted for the legacy sweep.
