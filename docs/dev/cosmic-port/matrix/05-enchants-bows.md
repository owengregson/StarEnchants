# Matrix 05 — bow enchantments (22 items)

Source codex: `05-enchants-bows.md`. Entries in codex order, format per `../README.md`.

Corpus-wide notes (apply to every entry unless the entry says otherwise):

- **Draw gate.** The jar's `shootForce >= 0.75F` launch gate reads metadata nothing in
  the tree ever writes — measured behavior is *no* draw gate. Not ported; SE `BOW` /
  `BOW_FIRE` fire at any draw.
- **Melee mirror.** The jar's shared impact pipeline accepts `ENTITY_ATTACK` as well as
  `PROJECTILE`, so most bow enchants also proc when the holder melees with the bow in
  hand. Where an entry says "melee mirror: yes", author a sibling `ATTACK`-trigger
  ability with identical params. Enchants gated on a projectile damager say "no".
- **Mid-flight bow swap.** Impact-time enchants re-read the *current* held item in the
  jar (swap bows mid-flight → new bow procs). An engine-pipeline artifact; SE binds the
  ability set per its own dispatch rules. Not replicated, not ledgered.
- **Defensive single-pass.** Farcast is this doc's only defensive hook; matrix records
  single-pass intended values per `deviations.md` D-001 (`00-MECHANICS.md` §3).
- **External-plugin gates.** WorldGuard PvP regions, Factions claims/relations,
  Essentials god/vanish, StaffPlus freeze, `spectator`/`do_not_clear` metadata, rank
  caps, and CosmicOutposts tier suppression are stack-level gates, not enchant behavior.
  They map to the engine's global protection/ally/vanish gates and the interaction
  layer; entries record only the enchant-specific rules.
- **The End tier gate.** Tier-6+ enchants (here: Teleblock) are inert in The End —
  interaction-layer world rule, recorded on the entry.
- **Handles.** Decompositions use modern authoring handles (`ENTITY_COW_AMBIENT`,
  `SPELL_WITCH`, …); the boot-time resolver owns legacy/modern aliasing. The **era**
  line flags only genuine 1.8.9 hazards.
- **Deviation rows.** Ledgered bugs below use proposed ids `D-05-1` … `D-05-7` pending
  assignment in `deviations.md`.

---

## Entries

### Arrow Lifesteal (`enchants/arrow-lifesteal`)

- **codex:** `05-enchants-bows.md § Arrow Lifesteal`
- **activation:** trigger `BOW`; melee mirror: yes. No further guards in the jar (no
  player check, no damage floor, no cooldown).
- **decomposition:**
  1. `MODIFY_HEALTH(amount=<level>, mode=give, who=@Self)` — per-level ability
     `chance` gates it (see numbers).
- **interactions:** none (touches no shared state).
- **strings:** none.
- **numbers:** chance per level `10 / 20 / 30.000000000000004 / 30 / 30` %
  (`level > 3 ? 0.3 : 0.1 * level`; level 3 is one FP-epsilon above levels 4–5 —
  copied exactly). Heal `+level` (1–5), clamped at max health.
  **Known bug:** the jar sets health to `min(floor(health) + level, floor(maxHealth))`
  — the attacker's fractional health silently shrinks the heal and a fractional max
  truncates the cap. As-intended: flat `+level` heal clamped to true max
  (`MODIFY_HEALTH` above). Deviation row: `D-05-1`.
- **era:** none (pure health write).

---

### Cowification (`enchants/cowification`)

- **codex:** `05-enchants-bows.md § Cowification`
- **activation:** three abilities — `BOW_FIRE` (cow rider), `BOW` direct hit (melee
  mirror: yes for the direct-hit pair), projectile-landing AoE (gap trigger).
- **decomposition:**
  - ability A (trigger `BOW_FIRE`, chance 100): cow rider on the arrow — **gap**
    `PROJECTILE_DRESSING` (rider `COW`, ttl 200t, invulnerable window 200t, no item
    pickup; rider despawns on landing).
  - ability B (trigger `BOW`, chance `10*level`):
    1. `POTION(effect=CONFUSION, level=2, duration=20*<level>, who=@Victim)` (Nausea II)
    2. `SOUND(sound=ENTITY_COW_AMBIENT, volume=1.0, pitch=0.75, who=@Victim)` (victim-only)
  - ability C (trigger `BOW`, chance 100 — the jar's particle sits outside the roll):
    1. `PARTICLE(particle=EXPLOSION_LARGE, count=1, spread=0, who=@Victim)` (eye height, speed 0.25)
  - ability D (landing AoE — **gap** `PROJECTILE_LAND`, chance 100):
    1. `SOUND(sound=ENTITY_COW_HURT, volume=1.0, pitch=0.85)` (world, at landing)
    2. `PARTICLE(particle=EXPLOSION_LARGE, count=8)` (landing point, random spread, speed 0.5)
    3. via `@Aoe{r=<level>, filter=ENEMIES}` centered on the landing:
       `POTION(effect=CONFUSION, level=2, duration=25*<level>)` — jar target set is
       living entities (mobs included) excluding owner + allies; no spectator /
       Ender Dragon filter (unlike Explosive). Jar volume is a cube of half-extent
       `level`; SE `AOE` is spherical `r=<level>` — shape delta noted, radius exact.
- **gaps:**
  - `PROJECTILE_DRESSING` — decorate the projectile of the current `BOW_FIRE`
    activation: optional rider entity (type, ttl, invulnerable window, no-pickup) and/or
    fire ticks; one rider slot per projectile. No primitive can attach an entity to, or
    ignite, the fired projectile (selectors resolve living entities only). Consumers:
    Cowification (cow), Explosive (wither skull), Venom (XP orb), Hellfire (fire),
    Infernal (fire).
  - `PROJECTILE_LAND` — trigger fired where the actor's projectile lands (block lodge
    or entity strike), activation site = landing location, shot-time level binding.
    `BOW` needs a target and cannot fire on a miss; `IMPACT` is the FALLING_BLOCK
    landing hook. Consumers: the five arrow-AoE enchants above.
- **interactions:** single rider slot — Explosive's wither skull wins over the cow
  (jar: cow suppressed when the held item also carries Explosive); authored as an
  interaction-layer priority rule on `PROJECTILE_DRESSING` riders. Ally exclusion in
  the AoE = engine ally gate. Cowification is retired from tier rolls but functional
  if present (catalog flag, not behavior). Landing AoE gated by the engine protection
  gate (jar: WorldGuard PvP check at the landing point only — after rider removal).
- **strings:** none.
- **numbers:** direct chance `10 / 20 / 30.000000000000004` %; direct Nausea II
  `20/40/60` t; AoE half-extent `1/2/3`; AoE Nausea II `25/50/75` t; cow lifetime
  200 t; cow invuln 200 t. Damage to the cow itself is cancelled + zeroed
  (rider semantics), with `ENTITY_COW_HURT` 1.0/0.7 to the striker.
- **era:** arrow passenger stacking on 1.8.9 uses the single-passenger mount API
  (`setPassenger`) — rider gap must fall back accordingly. Sounds/particles all exist
  in 1.8 (`COW_IDLE`/`COW_HURT`/`largeexplode`); `EXPLOSION_LARGE` renames at 1.20.5
  (resolver).

---

### Dimension Rift (`enchants/dimension-rift`)

- **codex:** `05-enchants-bows.md § Dimension Rift`
- **activation:** trigger `BOW`, chance `5*level` %; condition
  `%victim.type% == "PLAYER"`; melee mirror: **no** (jar requires a projectile
  damager). Launch cosmetic on `BOW_FIRE`.
- **decomposition:**
  - ability A (trigger `BOW_FIRE`, chance 100):
    1. `PARTICLE(particle=SPELL_WITCH, count=16)` (launch location, speed 0.7)
  - ability B (trigger `BOW`, chance `5*<level>`, condition `%victim.type% == "PLAYER"`):
    1. `TEMP_BLOCK(shape=BOX, material=SOUL_SAND, width=<W>, depth=<D>, height=1,
       dy=-1, ticks=<level>*15+40, airOnly=false, who=@Victim)` — footprint per level
       below; jar anchors even footprints off-center (offsets 0…1), SE centers the box
       (≤1-block placement delta). Engine TempBlockLedger supplies revert + the
       protected-material skip (jar skips chests, `SOUL_SAND`, `WEB`, `BEDROCK`, both
       portal blocks).
    2. `PARTICLE(particle=PORTAL, count=10)` per placed column (+1y, speed 0.7)
    3. web layer: `TEMP_BLOCK(shape=BOX, material=COBWEB, width=<W>, depth=<D>,
       height=1, dy=0, ticks=<level>*15+40, airOnly=true, who=@Victim)` with a
       per-column fill chance `10*<level>` % — **gap** `TEMP_BLOCK_FILL_CHANCE`
    4. `PARTICLE(particle=SPELL_WITCH, count=10)` per placed web (+0.5y, speed 0.7)
    5. on revert: strip Jump Boost and pop players upward — **gap**
       `TEMP_BLOCK_REVERT_HOOK` carrying `REMOVE_POTION(effect=JUMP_BOOST)` +
       `VELOCITY(mode=add, y=0.5)` for players within 2 blocks of a restored block
       (block-break world effect per restored block is ledger-native).
- **gaps:**
  - `TEMP_BLOCK_FILL_CHANCE` — per-block probability `p` applied as a TEMP_BLOCK/CAGE
    shape lays each block (scatter fill). Ability-level `chance` is all-or-nothing per
    activation; no primitive rolls per block. Consumers: partial web/scatter overlays.
  - `TEMP_BLOCK_REVERT_HOOK` — effect list executed when a temp-block placement
    reverts, targeting players within `r` of the restored blocks. No primitive can
    schedule effects at ledger-revert time. Consumers: rift-style terrain traps.
- **interactions:** the jar's placement gates (`world_koth` victim-world, `dungeon`
  shooter-world, "not a normal faction claim" **and** "inside a named WorldGuard
  region" — contradictory in open wilderness, so the enchant rarely fires; codex
  quirk-major) are server-stack wiring, not enchant behavior → engine protection gate
  plus interaction-layer world rules. Static revert map restored on plugin disable =
  TempBlockLedger shutdown semantics.
- **strings:** none.
- **numbers:** proc chance `5 / 10 / 15.000000000000002 / 20` %; footprint (W×D)
  `2×2 / 2×2 / 2×3 / 3×3` (x-range −1…1 from level 4, z-range −1…1 from level 3);
  web chance per column `10 / 20 / 30.000000000000004 / 40` %; revert at
  `55 / 70 / 85 / 100` t (map cleanup +1 t is jar bookkeeping, dropped); revert boost:
  velocity `(0, 0.5, 0)` + Jump Boost strip, radius 2 blocks (chunk-delta ≤ 1
  prefilter is an implementation detail). No damage modification.
- **era:** `COBWEB` is the modern handle — 1.8.9 material is `WEB` (resolver alias);
  `SOUL_SAND` fine. `SPELL_WITCH`/`PORTAL` exist in 1.8. Revert "pop" writes absolute
  velocity — 1.8 client velocity packets fine.

---

### Eagle Eye (`enchants/eagle-eye`)

- **codex:** `05-enchants-bows.md § Eagle Eye`
- **activation:** trigger `BOW`, chance `0.05*level + 0.2`; condition
  `%victim.type% == "PLAYER"`; melee mirror: yes (no cause gate in the jar).
- **decomposition:** (intended distance curve — see numbers; four mutually exclusive
  condition branches over the existing `%distance%` var)
  1. condition `%distance% < 10`: `DURABILITY(amount=1, target=armor, mode=damage, who=@Victim)`
  2. condition `%distance% >= 10 && %distance% < 20`: `DURABILITY(amount=2, target=armor, mode=damage, who=@Victim)`
  3. condition `%distance% >= 20 && %distance% < 32`: `DURABILITY(amount=3, target=armor, mode=damage, who=@Victim)`
  4. condition `%distance% >= 32`: `DURABILITY(amount=4, target=armor, mode=damage, who=@Victim)`
  5. `PARTICLE(particle=CRIT_MAGIC, count=30, who=@Victim)` (random spread, speed 0.45)
  6. `SOUND(sound=BLOCK_ANVIL_DESTROY, volume=0.3, pitch=0.8, who=@Self)` — jar plays
     it to the attacker at the victim's location (SOUND has no location param; noted).
  `DURABILITY(target=armor)` applies per worn piece, matching the jar's four
  per-piece calls; a piece driven past max durability breaks (engine break semantics —
  jar deletes the piece with `ITEM_BREAK` 3.0/0.8).
- **interactions:** the jar's cooldown reads the victim's `lastArrowDamageEvent`
  window (`level*200` ms = `level*4` t), which only the armor enchant Arrow Deflect
  writes — no Arrow Deflect on the victim, no cooldown at all. Authored as an
  interaction-layer rule shared with Piercing (same read) and Arrow Deflect (writer);
  the enchant itself ships with no own cooldown (measured default). No ally/PvP
  filter in the jar beyond the global gates.
- **strings:** none.
- **numbers:** chance `25 / 30.000000000000004 / 35.000000000000003 / 40 / 45` %.
  **Known bug:** the distance→durability curve (1 + 1 each at distance² ≥ 100/400/1024,
  i.e. 10/20/32 blocks, cap 4) is computed and never read — measured behavior is a
  flat **1** per piece at all distances and levels. As-intended: the curve above
  (matches the config description "deal 1-4 durability damage… the further away, the
  more"). Deviation row: `D-05-2`. Victim-keyed cooldown `200/400/600/800/1000` ms
  (see interactions; higher level = rarer proc under sustained fire — copied as-is).
- **era:** `BLOCK_ANVIL_DESTROY` maps to 1.8 `ANVIL_BREAK`; `CRIT_MAGIC` maps to 1.8
  `magicCrit` (resolver). Armor-slot indexing differences are engine-internal.

---

### Explosive (`enchants/explosive`)

- **codex:** `05-enchants-bows.md § Explosive`
- **activation:** `BOW_FIRE` (skull rider), `BOW` direct hit (melee mirror: yes —
  the jar's impact hook has no cause gate, victim must be a player), landing AoE (gap).
- **decomposition:**
  - ability A (trigger `BOW_FIRE`, chance 100): **gap** `PROJECTILE_DRESSING`
    (rider `WITHER_SKULL`, incendiary=false, yield=0 — pure visual, removed on
    landing; never detonates).
  - ability B (trigger `BOW`, chance 100, condition `%victim.type% == "PLAYER"`):
    1. `POTION(effect=WITHER, level=2, duration=20*<level>, who=@Victim)` (Wither II)
    2. `PARTICLE(particle=EXPLOSION_LARGE, count=1, spread=0, who=@Victim)` (eye, speed 0.25)
  - ability C (landing — **gap** `PROJECTILE_LAND`, chance 100):
    1. `PARTICLE(particle=EXPLOSION_LARGE, count=8)` (landing, random spread, speed 0.5)
    2. via `@Aoe{r=<level>, filter=ENEMIES}`:
       `POTION(effect=WITHER, level=2, duration=20*<level>)` — jar set: living
       entities (mobs included), excluding owner, allies, `spectator`, Ender Dragon;
       cube half-extent `level` vs SE spherical `r` (shape delta noted).
- **gaps:** `PROJECTILE_DRESSING`, `PROJECTILE_LAND` (defined under Cowification).
- **interactions:** wins the single rider slot — Cowification and Venom check for
  Explosive and stand down; Explosive makes no reciprocal check (rider-priority
  interaction rule). **Virus multiplies every Wither tick this applies** (see Virus).
  The Wither DoT is the damage vector; the AoE region gate checks only the landing
  point in the jar (a target in a safe zone beside the impact is still withered) —
  SE uses the per-target protection gate; delta noted for the parity harness.
- **strings:** none.
- **numbers:** direct + AoE Wither II `20/40/60/80/100` t (levels 1–5); AoE
  half-extent `1/2/3/4/5`; both applications 100 % (no rolls); skull yield `0.0`,
  incendiary `false`. No sounds anywhere. No damage modification (Wither ticks only).
- **era:** `WITHER_SKULL` rider exists on 1.8.9 (single-passenger API, same hazard as
  Cowification). Wither effect fine on 1.8.

---

### Farcast (`enchants/farcast`)

- **codex:** `05-enchants-bows.md § Farcast` (+ `00-MECHANICS.md` §3 for the
  defensive pass)
- **activation:** trigger `DEFENSE` (holder is hit while wearing/holding the bow —
  jar reads armor + held item, so holding is enough); requires a live attacker
  (`DEFENSE` needs-target ✓); single-pass per D-001 (Farcast null-guards the
  attacker, so the jar's pass 2 was a no-op anyway).
- **decomposition:** (intended behavior — see numbers)
  1. chance = `0.1 + 0.025*<level> / max(0.25, healthRatio)` — **gap**
     `VAR_SCALED_CHANCE`
  2. `PARTICLE(particle=EXPLOSION_LARGE, count=3, who=@Attacker)` (+1y, speed 1.0)
  3. `VELOCITY(mode=away, strength=2.3, who=@Attacker)` — flat impulse directly away
     from the defender (jar: normalized direction × 2.3, no added vertical)
  Interim primitive-only fallback while the gap lands: three condition branches at the
  codex's tabulated anchors — `%actor.healthpercent% > 50` → chance `10+2.5*level`;
  `> 25 && <= 50` → `10+5*level`; `<= 25` → `10+10*level` (exact at the anchors,
  piecewise between).
- **gaps:** `VAR_SCALED_CHANCE` — proc chance computed from an arithmetic expression
  over condition vars (here `base + slope / max(floor, %actor.healthpercent%/100)`).
  Flow-mod `±N %chance%` clauses add constant points on one boolean test only; no
  primitive evaluates a var expression into the roll. Consumers: health-scaled
  defensive procs.
- **interactions:** the jar's `pushAwayEntityEvent` anticheat stamp and
  `inDungeonParkour` abort are external-stack concerns (engine knockback path covers
  them). D-001 single-pass ruling applies.
- **strings:** none.
- **numbers:** **Known bug (fatal):** the jar's Ender-Dragon-exclusion guard is
  inverted, demanding the defender *be* an Ender Dragon — Farcast never procs for
  players; the enchant is inert in PvP. As-intended (config text: "Chance to knockback
  melee attackers … The lower your health, the higher the chance"): chance
  `f = 0.1 + 0.025*level / max(0.25, health/maxHealth)` → full-health
  `12.5 / 15 / 17.500001 / 20 / 22.5` %, at 50 % health `15 / 20 / 25 / 30 / 35` %,
  at ≤ 25 % health (clamped) `20 / 30 / 40 / 50 / 60` %; push speed 2.3. Deviation
  row: `D-05-3`. Divide-by-zero max-health edge collapses to 10 % in the jar —
  unreachable in SE (engine guards max health > 0).
- **era:** none (velocity + particle only).

---

### Healing (`enchants/healing`)

- **codex:** `05-enchants-bows.md § Healing`
- **activation:** trigger `BOW` on an **ally** victim (gap fact below); level bound at
  shot time in the jar (raw-event enchant); melee mirror: **no** (projectile damager
  required). Launch cosmetic on `BOW_FIRE`.
- **decomposition:** sibling abilities on `BOW`, all gated by
  condition `%victim.relation% == "ALLY"` (**gap** `TARGET_RELATION_FACT`):
  - ability A (chance 100):
    1. `CANCEL()` — allied arrow deals nothing (event cancelled, damage zeroed)
    2. `SOUND(sound=ENTITY_EXPERIENCE_ORB_PICKUP, volume=0.75, pitch=0.341, who=@Self)`
    3. `PARTICLE(particle=BLOCK_CRACK, block=EMERALD_BLOCK, who=@Victim)` (+0.5y —
       jar uses world-effect 2001 = block-break particles + break sound)
  - ability B (chance `15*(<level>-2)` for level ≥ 3, else absent):
    1. `SET_VAR(name=healing_absorb, value=1, ttl=2)` (branch flag)
    2. `POTION(effect=ABSORPTION, level=<level>-2, duration=20*(1+<level>), who=@Victim)`
  - ability C (chance 100, condition `%victim.healthpercent% < 100`):
    1. `MODIFY_HEALTH(amount=<roll>, mode=give, who=@Victim)` where `<roll>` is a
       uniform integer in `[level, 3*level - 1]` — **gap** `RANDOM_RANGE_PARAM`
  - ability D (chance 100, condition
    `%victim.healthpercent% >= 100 && !%healing_absorb%`):
    1. `POTION(effect=HEALTH_BOOST, level=<level>, duration=20*(4+<level>), who=@Victim)`
    (codex: "HEALTH_BOOST only ever lands on an already-full-health ally"; the jar's
    exact test is `health + roll > maxHealth` — full-health gate per the codex quirk,
    exact overflow test would need var arithmetic, accepted)
  - ability E (chance `25*<level>`):
    1. `DURABILITY(amount=1, target=armor, mode=restore, who=@Self)` restricted to the
       **most damaged** worn piece — **gap** `DURABILITY_PIECE_SELECT`. Jar repairs
       the *shooter's* armor (bytecode-verified); codex flags it as a probable
       copy/paste slip but gives no ruling — measured behavior kept (repair @Self),
       flagged for owner review, **not** ledgered.
  - ability F (trigger `BOW_FIRE`, chance 100):
    1. `PARTICLE(particle=BLOCK_CRACK, block=EMERALD_BLOCK, who=@Self)` (launch cue)
- **gaps:**
  - `TARGET_RELATION_FACT` — `%victim.relation%` (`ALLY`/`ENEMY`/`NEUTRAL`) condition
    var backed by the engine's existing ally model; ally-awareness exists only as an
    `AOE`/`NEAREST` selector *filter* today, not as a condition fact. Consumers:
    ally-gated support procs (Healing, Teleportation), ally-exclusion conditions.
  - `RANDOM_RANGE_PARAM` — uniform random roll `[min, max]` for a numeric effect
    param, rolled per activation. Params are compile-time constants today; N
    chance-chained abilities cannot express an exclusive uniform partition sanely.
    Consumers: variable heal/damage rolls.
  - `DURABILITY_PIECE_SELECT` — piece-selection mode (`most-damaged` | `random` |
    `all`) on `DURABILITY(target=armor)`; today the effect addresses the slot set as
    a whole. Consumers: single-point armor-repair drips.
- **interactions:** the jar cancels the event at LOWEST, so on an allied hit **no
  other bow enchant procs** — authored as an interaction-layer precedence rule
  (Healing's `CANCEL` suppresses same-hit abilities). Healing bypasses the jar's rank
  cap and The End tier gate (raw-event enchant) — noted; SE applies its normal gates.
  Self-shot: jar cancels + `CAT_HISS` 0.85/0.2; SE never dispatches `BOW` with
  victim == actor (cue dropped, noted).
- **strings:** none.
- **numbers:** heal roll `nextInt(2*level) + level` = integer `[level, 3*level-1]` →
  ranges `1–2 / 2–5 / 3–8 / 4–11`; Absorption flag chance
  `— / — / 15 / 30.000000000000004` % (levels 1–2 never); HEALTH_BOOST (overheal,
  no-flag) `100/120/140/160` t at potion level `1/2/3/4` (Health Boost I–IV);
  ABSORPTION (flag) `80/100` t at potion level `1/2` (levels 3–4); armor repair chance
  `25 / 50 / 75 / 100` %, 1 point, most-damaged worn piece. Ally check in the jar is
  one-directional + same-faction; SE ally model is symmetric (delta noted).
- **era:** `BLOCK_CRACK` with block data exists on 1.8.9 (`blockcrack_133_0`);
  Absorption + Health Boost exist since 1.6. `ENTITY_EXPERIENCE_ORB_PICKUP` maps to
  1.8 `ORB_PICKUP`.

---

### Hellfire (`enchants/hellfire`)

- **codex:** `05-enchants-bows.md § Hellfire`
- **activation:** `BOW_FIRE` (flaming arrow), `BOW` direct hit (melee mirror: yes),
  landing AoE (gap). Direct hit on a *player* victim is gated by can-see + survival
  gamemode in the jar (vanish/gamemode gates → engine global gates); mob victims burn
  unconditionally.
- **decomposition:**
  - ability A (trigger `BOW_FIRE`, chance 100): **gap** `PROJECTILE_DRESSING`
    (fire ticks `2147483647` — permanent flaming arrow; vanilla flaming-arrow contact
    ignition ~100 t on the struck entity rides along).
  - ability B (trigger `BOW`, chance 100):
    1. `IGNITE(duration=<level>*40, who=@Victim)` — jar is an absolute
       `setFireTicks` (can shorten an existing longer burn; engine IGNITE semantics
       noted for the parity harness)
    2. `PARTICLE(particle=FLAME, count=30, who=@Victim)` (eye, random spread, speed 0.15)
    3. `PARTICLE(particle=LAVA, count=20, who=@Victim)` (eye, random spread, speed 0.5)
  - ability C (landing — **gap** `PROJECTILE_LAND`, chance 100):
    1. `SOUND(sound=BLOCK_FIRE_AMBIENT, volume=1.0, pitch=0.85)` (world, landing)
    2. `PARTICLE(particle=EXPLOSION_LARGE, count=8)` + `PARTICLE(particle=FLAME, count=45)` (landing, speed 0.5 / 0.25)
    3. via `@Aoe{r=2*<level>, filter=ENEMIES}` (jar: **players only** — a trailing
       player-type test excludes mobs; measured behavior kept):
       - additive burn: `IGNITE(duration=<level>*20)` — jar *adds*
         `getFireTicks() + i*20` (multi-arrow volleys stack; engine IGNITE absolute —
         delta noted)
       - `DAMAGE(amount=1+<level>)` — jar deals it 1 t later as a fresh no-damager
         hit (armor-reduced, no kill credit, no pipeline re-entry)
       - `PARTICLE(particle=LAVA, count=16)` per burned player (+1y, speed 0.5)
- **gaps:** `PROJECTILE_DRESSING`, `PROJECTILE_LAND` (defined under Cowification).
- **interactions:** jar's `world_duels2` halving (radius `i*2/2`, damage `(1+i)/1.5`)
  is server-world wiring — interaction-layer world rule if ever wanted, not ported by
  default. Essentials god/vanish + StaffPlus freeze AoE filters → engine gates.
  Hellfire removes its arrow on landing (only enchant that does) — engine cleanup.
  Fire damage is NOT Virus-amplified (Virus keys on WITHER/POISON causes only).
- **strings:** none.
- **numbers:** direct fire `40/80/120/160/200` t; arrow fire `2147483647` t
  (level-independent); AoE half-extent `2/4/6/8/10`; AoE burn `+20/40/60/80/100` t;
  delayed damage `2.0/3.0/4.0/5.0/6.0` (fresh hit, 1 t later, armor-reduced). 100 %
  everywhere — no rolls.
- **era:** permanent-fire arrows render as burning entities on 1.8.9 (fine);
  `BLOCK_FIRE_AMBIENT` maps to 1.8 `FIRE`; `LAVA`/`FLAME` particles exist in 1.8.

---

### Hijack (`enchants/hijack`)

- **codex:** `05-enchants-bows.md § Hijack`
- **activation:** trigger `BOW`, chance `8*level` %; condition
  `%victim.mobtype% == "IRON_GOLEM"`; melee mirror: **no** (`PROJECTILE` cause gate).
  Jar additionally requires the golem to be a Guardians summon
  (`guardianSummoner` metadata) — see decomposition note.
- **decomposition:**
  1. `CONVERT_SUMMON(radius=1, who=@Victim)` — rebinds the struck golem to the
     shooter (enemy summon turns on its former owner). Jar-only restriction: summons
     only; `CONVERT_SUMMON` also converts wild mobs — the `%victim.mobtype%` gate
     narrows to iron golems, residual delta (natural golems convertible) noted.
  2. fresh-respawn upgrade — **gap** `SUMMON_REBIND_UPGRADE`: jar deletes the golem
     (no death event, no drops) and respawns a **fresh, full-health** guardian 2
     blocks up at guardian tier `2*<level>`, name `§b§l{OWNER}'s Guardian`, 30 s
     (600 t) self-destruct restarted; stats belong to the Guardians ladder (matrix
     doc 01 § Guardians).
  3. `MESSAGE(text="§5§l*** HIJACK (§7§m{OLD_OWNER}§5§l -> §f{NEW_OWNER}§5§l) ***", who=@AllPlayers{r=24})`
  4. `SOUND(sound=ENTITY_IRON_GOLEM_DEATH, volume=0.8, pitch=1.2, who=@AllPlayers{r=24})`
  5. `PARTICLE(particle=SPELL_WITCH, count=60, who=@Victim)` (+1y, speed 1.2)
- **gaps:** `SUMMON_REBIND_UPGRADE` — parameterize summon conversion with a fresh
  respawn: full health at the new owner's summon tier (`tier` param), ttl reset,
  rename. `CONVERT_SUMMON` rebinds in place (current health, no tier change, no
  rename). Consumers: projectile summon-theft.
- **interactions:** consumes the Guardians (armor enchant) summon system — converted
  guard adopts the hijacker's `GUARDIAN_HURT` wiring and the Guardians stat ladder
  (health `50 + gtier*10` → `70/90/110/130`; permanent Fire Resistance always, plus
  Regeneration at gtier ≥ 4, Strength at ≥ 6, Speed at ≥ 8; Resistance needs
  gtier 10 — unreachable via Hijack, max 8). Jar rolls RNG before the type check
  (outcome-identical; SE gate order condition-then-chance, noted). Broadcast has no
  ally filter (world-visible within 24 blocks).
- **strings:**
  - broadcast: `§5§l*** HIJACK (§7§m{OLD_OWNER}§5§l -> §f{NEW_OWNER}§5§l) ***`
  - guard name (Guardians-owned string): `§b§l{OWNER}'s Guardian`
- **numbers:** chance `8 / 16 / 24 / 32` %; guardian tier passed `2/4/6/8`;
  fresh health `70/90/110/130`; respawn offset +2y; self-destruct 600 t (restarted —
  a hijack extends the golem's life); broadcast/sound box 24 blocks; spawner-side
  `ENTITY_IRON_GOLEM_DEATH` 1.0/0.55 world sound rides with the Guardians spawner.
- **era:** guard retargeting on 1.8.9 rides the legacy AI overlay (NoAI datawatcher
  era traps — see pets/guard notes); custom-name-visible works on 1.8.

---

### Infernal (`enchants/infernal`)

- **codex:** `05-enchants-bows.md § Infernal`
- **activation:** `BOW_FIRE` (burning arrow), `BOW` direct hit (melee mirror: yes —
  codex explicit, zero guards), landing AoE (gap). 100 % everywhere — no rolls.
- **decomposition:**
  - ability A (trigger `BOW_FIRE`, chance 100): **gap** `PROJECTILE_DRESSING`
    (fire ticks `<level>*60` on the arrow; vanilla flaming-arrow contact ignition
    rides along).
  - ability B (trigger `BOW`, chance 100):
    1. `IGNITE(duration=<level>*20, who=@Victim)` (absolute set, like the jar)
    2. `PARTICLE(particle=FLAME, count=10, who=@Victim)` (eye, random spread, speed 0.25)
  - ability C (landing — **gap** `PROJECTILE_LAND`, chance 100):
    1. `PARTICLE(particle=FLAME, count=20)` (landing, random spread, speed 0.5)
    2. via `@Aoe{r=<level>, filter=ENEMIES}`: `IGNITE(duration=<level>*20)` — jar
       set: living entities (mobs included) minus owner/allies/`spectator`/
       `do_not_clear`; no Ender Dragon filter; absolute set (can shorten a longer burn).
- **gaps:** `PROJECTILE_DRESSING`, `PROJECTILE_LAND` (defined under Cowification).
- **interactions:** retired from tier rolls (listed twice in the jar's retired list —
  catalog trivia), fully functional when present. Fire damage not Virus-amplified.
- **strings:** none.
- **numbers:** direct fire `20/40/60` t; arrow fire `60/120/180` t; AoE half-extent
  `1/2/3`; AoE fire `20/40/60` t (absolute, not additive — unlike Hellfire). No
  sounds.
- **era:** none beyond the shared flaming-arrow note.

---

### Lightning (`enchants/lightning`)

- **codex:** `05-enchants-bows.md § Lightning`
- **activation:** trigger `BOW`, chance `10*level` %; condition
  `%victim.type% == "PLAYER"` (jar checks after the roll — outcome-identical);
  melee mirror: yes.
- **decomposition:**
  1. `LIGHTNING(damage=5.0, who=@Victim)` — cosmetic strike (jar uses
     `strikeLightningEffect`: no fire, no block ignition) + 5.0 bonus damage.
- **interactions:** none in-tree beyond the re-entry below.
- **strings:** none.
- **numbers:** chance `10 / 20 / 30.000000000000004` %; damage flat `5.0` at every
  level. Table-roll quirk: `interval = 0.0` means the enchant table only ever yields
  level 3 (levels 1–2 via books/scrolls only) — catalog concern, copied to the tier
  config, not an engine matter. **Known bug:** the jar deals the 5.0 as a fresh
  `ENTITY_ATTACK` with the shooter as damager, which **re-enters the whole enchant
  pipeline** (bounded only by immunity ticks + the roll). As-intended: a single
  non-re-entrant 5.0 bonus (`LIGHTNING` above; SE effects never re-dispatch
  abilities). Deviation row: `D-05-4`.
- **era:** `strikeLightningEffect` equivalent exists on 1.8.9; thunder sound is
  vanilla-side.

---

### Longbow (`enchants/longbow`)

- **codex:** `05-enchants-bows.md § Longbow`
- **activation:** trigger `BOW`, chance 100; condition
  `%victim.helditem% contains "BOW"` (the only gate — no distance term despite the
  name); melee mirror: yes (codex explicit: meleeing a bow-holder doubles melee
  damage at level 4).
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=25*<level>)` — +25 %/level fold
     contribution
  2. `SOUND(sound=ENTITY_ARROW_HIT, volume=1.0, pitch=0.4, who=@Victim)` (victim-only)
- **interactions:** jar composes **multiplicatively** with Sniper (both MONITOR
  `setDamage` multiplies) and multiplies the already-halved value under Unfocus
  (HIGHEST runs first): `base / 2 * sniperMult * longbowMult`. SE's damage fold is
  additive by engine invariant — composition delta is an engine-wide ruling
  (ADR-0050 family), recorded here, not ledgered per-enchant.
- **strings:** none.
- **numbers:** multiplier `1.25 / 1.5 / 1.75 / 2.0` (+25/50/75/100 %); 100 % proc
  when the victim holds a bow; no cooldown. Jar NPEs on null mob equipment — engine
  var read is null-safe (non-behavioral).
- **era:** none.

---

### Pacify (`enchants/pacify`)

- **codex:** `05-enchants-bows.md § Pacify`
- **activation:** trigger `BOW`, chance `0.5 + level*0.125`; condition
  `%victim.type% == "PLAYER"`; melee mirror: yes (codex explicit).
- **decomposition:**
  1. `SUPPRESS(scope=ENCHANT, key=rage, duration=15*<level>, mode=timed, who=@Victim)`
     — blocks the victim's Rage stack accumulation for the window; re-application
     overwrites (timer refreshed, not extended — matches the jar's unconditional
     rewrite of the expiry stamp).
- **interactions:** the sword enchant Rage is the sole consumer (jar key
  `noRageUntil`); authored as the SUPPRESS above against the interaction layer. The
  distinct `effectedByRage` key (Rage → Sniper immunity) is **not** written by Pacify
  — do not conflate.
- **strings:** none (no sounds, no particles either).
- **numbers:** chance `62.5 / 75 / 87.5 / 100` % (level 4: `Math.random() < 1.0`
  always passes — a guaranteed proc, copied as 100); lockout `750/1500/2250/3000` ms
  = `15/30/45/60` t.
- **era:** none.

---

### Piercing (`enchants/piercing`)

- **codex:** `05-enchants-bows.md § Piercing`
- **activation:** trigger `BOW`, chance 100; condition `%victim.type% == "PLAYER"`;
  melee mirror: yes.
- **decomposition:** (intended value — see numbers)
  1. `DAMAGE_MOD(side=attack, mode=add, amount=2.5*<level>)` — +2.5 %/level of the
     triggering hit, folded in-event (never swallowed)
  2. `SOUND(sound=ENTITY_ARROW_HIT, volume=1.2, pitch=0.6, who=@Self)` (jar plays it
     to the attacker at the attacker's own location)
  3. `PARTICLE(particle=CRIT, count=20, who=@Victim)` (random spread, speed 0.5)
- **interactions:** shares the victim-keyed `lastArrowDamageEvent` window
  (`level*200` ms) with Eagle Eye (reader) and Arrow Deflect (only writer) — without
  Arrow Deflect on the victim there is no cooldown at all; same interaction-layer rule
  as Eagle Eye, enchant ships with no own cooldown (measured default). Higher level =
  longer window = rarer proc under sustained fire (copied to the rule).
- **strings:** none.
- **numbers:** bonus `2.5 / 5 / 7.5 / 10 / 12.5` % of event damage. **Known bug
  (nullifying clamp):** the jar deals the bonus as a same-tick separate
  `damage()` with no damager, inside the victim's immunity window from the arrow —
  since it is almost always smaller than the arrow damage it is **swallowed entirely**
  (net ≈ 0); the no-damager hit also re-fires the victim's defensive pass with
  attacker = null. As-intended: the in-event fold above. Deviation row: `D-05-5`.
- **era:** none.

---

### Snare (`enchants/snare`)

- **codex:** `05-enchants-bows.md § Snare`
- **activation:** trigger `BOW`, chance `9*level` %; no player gate (mobs snareable —
  copied); melee mirror: yes.
- **decomposition:**
  1. `POTION(effect=SLOW, level=<level>+1, duration=20*<level>+20, who=@Victim)`
     (Slowness II–V)
  2. level ≥ 3 only: `POTION(effect=SLOW_DIGGING, level=<level>-2, duration=20*<level>+20, who=@Victim)`
     (Mining Fatigue I–II; the jar's `level >= 3` guard exists because the amplifier
     `level-3` would go negative below that)
  3. `PARTICLE(particle=BLOCK_CRACK, block=VINE, who=@Victim)` (+0.5y — jar
     world-effect 2001 with raw id 106 = VINE; includes the vanilla break sound)
- **interactions:** Metaphysical (armor, and its heroic variant Polymorphic
  Metaphysical) reduces the proc: effective chance
  `max(0, level*0.09 − metaLevel*0.07)`; Metaphysical ≥ 4 fully
  immunises against Snare I–III. Authored as an interaction-layer condition
  (`−7*metaLevel %chance%` flow-mod keyed on the victim's Metaphysical level), with
  the blocked message below emitted by that rule. Same reduction pattern family as
  Pummel/Trap/Titan Trap. Snare is retired from tier rolls (catalog flag).
- **strings:** (victim-shown, only when Metaphysical blocks the proc; authored on the
  interaction rule)
  - `§8§l** METAPHYSICAL (§8Snare blocked!§l) **`
- **numbers:** chance `9 / 18 / 27 / 36` %; duration `40/60/80/100` t; Slowness
  amplifier = level (Slowness II/III/IV/V); Mining Fatigue amplifier `0/1` at levels
  3/4; Metaphysical reduction per level `7 / 14 / 21.000000000000002 / 28 /
  35.000000000000003` percentage points. No damage modification; no cooldown.
- **era:** `VINE` exists on 1.8.9; `BLOCK_CRACK` era note as per Healing.

---

### Sniper (`enchants/sniper`)

- **codex:** `05-enchants-bows.md § Sniper`
- **activation:** trigger `BOW`; melee mirror: **no** (`PROJECTILE` cause gate);
  headshot gate `arrowY − victimFeetY > 1.9` (**gap** below); chance
  `0.35 + level*0.075`; rage immunity (interactions).
- **decomposition:**
  1. condition `%hit.offsety% > 1.9` — **gap** `PROJECTILE_HIT_HEIGHT`
  2. `DAMAGE_MOD(side=attack, mode=add, amount=42*<level>)` — ×(1 + 0.42·level)
  3. `MESSAGE(text=<per-level literal below>, who=@Victim)` (victim only — the
     shooter gets no feedback; copied)
  4. `SOUND(sound=ENTITY_PLAYER_HURT, volume=2.0, pitch=0.3, who=@Victim)`
  5. `PARTICLE(particle=BLOCK_CRACK, block=REDSTONE_BLOCK, who=@Victim)` (eye
     height), repeated `max(1, level/2)` times — integer division: `1/1/1/2/2`
     repeats (visually identical to a single call; copied as-is, no intended change)
- **gaps:** `PROJECTILE_HIT_HEIGHT` — `%hit.offsety%` condition var: the projectile's
  impact Y minus the victim's feet Y, bound for the current `BOW` activation. No
  existing var exposes impact geometry (SE has no headshot detection — confirmed by
  the EE-import compromise that substituted flat damage). Consumers: headshot-gated
  procs (Sniper here; Lethal Sniper in doc 06).
- **interactions:** victims stamped by Rage within the last 200 ms are immune
  (`effectedByRage`, written by the sword enchant Rage) — interaction-layer condition
  against Rage's recent-application window. Heroic upgrade path: an item with Lethal
  Sniper rejects Sniper; Lethal Sniper requires Sniper V (catalog rule; Lethal
  Sniper's own numbers live in doc 06). Composition with Longbow/Unfocus: see
  Longbow's interaction note (jar multiplicative at MONITOR, SE additive fold).
- **strings:** verbatim per level, raw `Double.toString` of the multiplier preserved:
  - L1: `§c§l*** HEADSHOT [+1.42x DMG] ***`
  - L2: `§c§l*** HEADSHOT [+1.8399999999999999x DMG] ***`
  - L3: `§c§l*** HEADSHOT [+2.26x DMG] ***`
  - L4: `§c§l*** HEADSHOT [+2.6799999999999997x DMG] ***`
  - L5: `§c§l*** HEADSHOT [+3.1x DMG] ***`
  (the `+<n>x` reads like an addition but is the total multiplier — copied verbatim)
- **numbers:** chance `42.5 / 50 / 57.5 / 64.99999999999999 / 72.5` % (level 4 is one
  ULP below 0.65 — copied exactly; sub-observable); multiplier `1.42 /
  1.8399999999999999 / 2.26 / 2.6799999999999997 / 3.1` → fold amount `+42/84/126/
  168/210` %. Config text promises "up to 3.5x"; implementation caps at 3.1x — text
  drift only, behavior copied. The headshot test uses the arrow's Y, not the hitbox
  intersection (steep arrows can headshot a torso hit) — `%hit.offsety%` spec must
  match (projectile position at impact).
- **era:** none (`REDSTONE_BLOCK` fine on 1.8.9).

---

### Target Tracking (`enchants/target-tracking`)

- **codex:** `05-enchants-bows.md § Target Tracking`
- **activation:** trigger `BOW`, chance 100; condition
  `%victim.type% == "PLAYER"`; melee mirror: yes (codex explicit: every melee hit
  re-focuses). Max level 1; `level` never read.
- **decomposition:**
  1. `RUN_COMMAND(command="f focus {VICTIM}", as=player)` — the jar invokes the
     Factions `/f focus` handler as the attacker; the command string is
     server-configurable (SE has no factions of its own — on stacks without a
     factions plugin the pack omits/retargets this enchant).
- **interactions:** entirely a Factions bridge (attacker's faction must be a normal
  player faction in the jar — that guard lives in the target plugin's command
  handler surface, noted for pack config). No rate limit — the jar re-issues the
  focus on every hit (chat spam is the external command's output); copied (no
  cooldown).
- **strings:** none in the enchant (any output comes from the focused command).
- **numbers:** level-independent; 100 % on every qualifying hit; no cooldown; the
  jar's unresolved `false` flag on the focus call stays unresolved (external API).
- **era:** none (command bridge).

---

### Teleblock (`enchants/teleblock`)

- **codex:** `05-enchants-bows.md § Teleblock`
- **activation:** trigger `BOW_FIRE` (soul spend, cast cues) + trigger `BOW`
  (application; condition `%victim.type% == "PLAYER"`); melee mirror: **no** (no
  impact hook in the jar). Tier 6 soul enchant.
- **decomposition:**
  - ability A (trigger `BOW_FIRE`, chance 100):
    1. `REMOVE_SOULS(amount=6*<level>, who=@Self)` — soul-mode + balance gating is
       the souls layer's job; insufficient souls → whole shot unstamped (no
       application ability); souls spent even if the arrow misses, **no refund** on
       immune targets (copied)
    2. `PARTICLE(particle=SPELL_WITCH, count=65, who=@Self)` (+1y, speed 0.5)
    3. `SOUND(sound=ENTITY_GENERIC_EAT, volume=0.4, pitch=0.2, who=@Self)`
  - ability B (trigger `BOW`, chance 100, condition `%victim.type% == "PLAYER"`):
    1. `TELEBLOCK(duration=<intended ticks below>, who=@Victim)`
    2. `REMOVE_ITEM(material=ENDER_PEARL, count=3*<level>, who=@Victim)` — jar scans
       main storage + hotbar only (not armor slots) and skips any stack with a
       display name (renamed pearls immune); SE `REMOVE_ITEM` has no name filter —
       named-stack exemption noted as an engine-semantics delta, not gapped
    3. `MESSAGE(text=<teleblock notice below>, who=@Victim)`
    4. `SOUND(sound=ENTITY_ENDERMAN_HURT, volume=0.75, pitch=0.6, who=@Victim)`
- **interactions:** (all interaction-layer conditions; bypasses 1–3 run **before**
  metadata + pearl removal, so an immune target loses nothing — shooter souls are
  gone regardless)
  1. full RANGER armor set → total immunity (`armor-sets/ranger`, doc 10)
  2. GLITCH mask equipped → total immunity (`masks/glitch`, doc 11)
  3. Ranger bonus crystals → `20*crystals` % immunity roll per hit
     (20/40/60/80/100 %), emits the RANGER CRYSTAL string below
  4. Anti Teleblock Pet → a *cure*, not prevention: clears an active teleblock
     (`pets/anti-teleblock`, doc 12; cooldown `max(30, 120 − 6*(petLevel−1))` s)
  5. inert in The End (tier-6 world rule)
  6. soul milestone: every 100th soul emits the SOULS string (souls-layer feedback
     rule, not this enchant's ability)
  Jar listener ignores cancelled hits' cancellation (`ignoreCancelled=false` at
  NORMAL — teleblocks even already-cancelled hits); SE gate order applies, delta
  noted for the parity harness. Jar stamps ANY projectile launched while holding the
  bow (no arrow-type guard) — SE `BOW_FIRE` is bow-shot-only (artifact, not
  ported).
- **strings:** verbatim.
  - teleblock notice, to the victim (concat bug preserved — see numbers):
    `§5** TELEBLOCK [53s] [-0ep] **` / `[56s]` / `[59s]` / `[512s]` / `[515s]` per
    level 1–5. The `[-0ep]` slot is the jar's *remaining-shortfall* counter — `0`
    whenever the victim carried enough pearls, else the count it failed to take;
    rendered as the literal `[-{SHORTFALL}ep]` with `{SHORTFALL}` normally `0`.
    Strings never deviate; the intended `[8s]…[20s]` rendering needs an owner ruling
    before any change.
  - Ranger-crystal immunity, to the victim (crystal interaction rule):
    `§a§l* RANGER CRYSTAL [§7Immune to Teleblock§a§l] *`
  - soul milestone, to the shooter (souls layer):
    `§e§l** SOULS: §n{SOULS}§e§l **`
- **numbers:** soul cost `6/12/18/24/30`; pearls destroyed `3/6/9/12/15`.
  **Known bug (precedence):** duration `System.currentTimeMillis() + (5 + level*3000)`
  ms → measured `3005/6005/9005/12005/15005` ms (3.005–15.005 s); the author meant
  `5000 + level*3000` → as-intended `8/11/14/17/20` s = `160/220/280/340/400` t
  (`TELEBLOCK` duration above). Deviation row: `D-05-6`. **Known bug (string
  concat):** `"[" + 5 + i*3 + "s]"` string-appends instead of adding → `[53s]…[515s]`
  (strings kept verbatim, see above). Table-roll quirk: `interval = 0.0` → table only
  ever rolls level 5.
- **era:** `ENDER_PEARL` fine; `ENTITY_ENDERMAN_HURT`/`ENTITY_GENERIC_EAT` map to 1.8
  `ENDERMAN_HIT`/`EAT`. Teleblock enforcement itself is the SE effect (jar delegated
  to an external combat plugin — `UNRESOLVED` in codex; SE semantics: pearl + chorus
  blocked, chorus N/A on 1.8.9).

---

### Teleportation (`enchants/teleportation`)

- **codex:** `05-enchants-bows.md § Teleportation`
- **activation:** trigger `BOW` on an **ally** victim (gap fact, shared with
  Healing); level bound at shot time in the jar; melee mirror: **no**. Launch
  cosmetic on `BOW_FIRE`. No cooldown of any kind (an unlimited blink at level 5 —
  copied).
- **decomposition:** all `BOW` abilities gated by
  condition `%victim.relation% == "ALLY"` (**gap** `TARGET_RELATION_FACT`):
  - ability A (in range; condition `… && %distance% <= 6*<level>`):
    1. `CANCEL()` (allied hit deals nothing)
    2. `TELEPORT(to=VICTIM, who=@Self)` — jar lands on the ally's exact position but
       keeps the **shooter's own** pitch/yaw (camera preserved; SE TELEPORT
       orientation semantics noted for parity)
    3. `SOUND(sound=ENTITY_EXPERIENCE_ORB_PICKUP, volume=0.75, pitch=0.341, who=@Self)`
    4. `PARTICLE(particle=SPELL_WITCH, count=35, who=@Victim)` (+0.5y, random spread, speed 0.5)
  - ability B (too far; condition `… && %distance% > 6*<level>`):
    1. `CANCEL()` — the jar cancels + eats the arrow *before* the range check, so a
       too-far allied hit still deals zero (copied)
    2. `MESSAGE(text=<too-far string>, who=@Self)`
  - ability C (trigger `BOW_FIRE`, chance 100):
    1. `PARTICLE(particle=SPELL_WITCH, count=35, who=@Self)` (launch, speed 0.5)
- **gaps:** `TARGET_RELATION_FACT` (defined under Healing).
- **interactions:** like Healing, the LOWEST-priority cancel suppresses every other
  bow enchant on an allied hit — same interaction-layer precedence rule. The
  End/KOTH PvP-transition rule (may not teleport from PvP-enabled into PvP-disabled
  there; emits the second string) and the jar's `cosmic-station-1` full exemption are
  interaction-layer world/region rules. The transient `ignore_combat_tag` stamp
  around the teleport is combat-tag interop (engine teleport path owns it). Unlike
  Healing, the jar's rank cap + tier gates apply here (it resolves its enchant set
  through the jar's normal validity filter).
- **strings:** verbatim, both to the shooter (misspelling **"Teleporation"** is in
  the source and is preserved):
  - `§c§l(!) §cYour ally is too far away to teleport to with this level of Teleporation.`
  - `§c§l(!) §cYou cannot teleport from PvP-enabled to PvP-disabled with Teleportation in The End or KOTH.`
- **numbers:** range `6/12/18/24/30` blocks (compared squared: `36/144/324/576/900`);
  everything else level-independent; 100 % on qualifying hits; no cooldown. Self-shot:
  jar cancels + `CAT_HISS` 0.85/0.2 (SE: self-victim never dispatched, cue dropped —
  same note as Healing).
- **era:** none (teleport + cosmetics; `SPELL_WITCH` exists on 1.8 as `witchMagic`).

---

### Unfocus (`enchants/unfocus`)

- **codex:** `05-enchants-bows.md § Unfocus`
- **activation:** trigger `BOW`, chance `0.5*level` (see numbers); condition
  `%victim.type% == "PLAYER"`; melee mirror: yes (no cause gate on application).
  Re-application while active is blocked (cannot refresh/extend until it lapses) —
  matches `WEAKEN`'s non-stacking semantics; a level-1 proc locks out a level-5 proc
  for its 2 s (copied).
- **decomposition:**
  1. `WEAKEN(percent=50, duration=40*<level>, who=@Victim)` — halves the victim's
     outgoing damage for the window; **gap** `OUTGOING_DEBUFF_CAUSE_FILTER` restricts
     it to projectile damage (jar halves *all* projectile damage the victim deals —
     any projectile, not just bow — and leaves melee untouched)
  2. `MESSAGE(text="§2** UNFOCUS [{SECONDS}s] **", who=@Victim)` — `{SECONDS}` =
     `2*level`
  3. `SOUND(sound=BLOCK_PORTAL_TRIGGER, volume=1.2, pitch=0.6, who=@Victim)`
  4. `SOUND(sound=ENTITY_ARROW_HIT, volume=1.2, pitch=2.5, who=@Self)`
  5. per-consumption spam — **gap** `MARK_CONSUME_FEEDBACK`: the jar sends
     `§2** UNFOCUSED [50% BOW DMG] **` to the unfocused player on **every** projectile
     hit they land while the debuff is active (up to 10 s of chat spam at level 5 —
     strings never deviate, so the parameterized feedback is required)
- **gaps:**
  - `OUTGOING_DEBUFF_CAUSE_FILTER` — optional damage-cause/trigger filter
    (`causes: [PROJECTILE]`, …) on outgoing-damage debuff marks (WEAKEN family). The
    mark is applied once but consumed at fold time; no condition hook exists at
    consumption. Consumers: projectile-only damage debuffs.
  - `MARK_CONSUME_FEEDBACK` — per-consumption message param on stored damage marks
    (text + channel, sent to the mark's bearer when the mark modifies a hit).
    Consumers: Unfocus-style debuffs with per-hit feedback.
- **interactions:** priority ordering vs Sniper/Longbow: the jar halves at HIGHEST
  *before* their MONITOR multiplies (`base / 2 * sniperMult * longbowMult`); SE fold
  ordering per engine damage-stacking rules (see Longbow note). The jar halves even
  already-cancelled hits (`ignoreCancelled=false`) — SE gate order applies, noted.
- **strings:** verbatim.
  - application, to the victim: `§2** UNFOCUS [{SECONDS}s] **` (`{SECONDS}` =
    `2/4/6/8/10`)
  - per consumed hit, to the unfocused player: `§2** UNFOCUSED [50% BOW DMG] **`
- **numbers:** chance `50 / 100 / 100 / 100 / 100` % (`0.5*level` vs `[0,1)` —
  levels 2–5 always pass; codex marks the guarantee, gives no different intent →
  copied as measured, no ledger row); duration `2000/4000/6000/8000/10000` ms =
  `40/80/120/160/200` t; penalty a level-independent flat 50 % (the jar's
  `unfocusEnchantmentLevel` stamp is written and never read — dead state, dropped).
  Table-roll quirk: `interval = 0.0` → table only rolls level 5.
- **era:** `BLOCK_PORTAL_TRIGGER` maps to 1.8 `PORTAL_TRIGGER`.

---

### Venom (`enchants/venom`)

- **codex:** `05-enchants-bows.md § Venom`
- **activation:** `BOW_FIRE` (orb rider), `BOW` direct hit (melee mirror: yes — zero
  guards in the jar), landing AoE (gap; **dead in the jar** — see numbers).
- **decomposition:**
  - ability A (trigger `BOW_FIRE`, chance 100): **gap** `PROJECTILE_DRESSING`
    (rider `EXPERIENCE_ORB` with 0 XP — a pure visual marker, removed on landing).
  - ability B (trigger `BOW`, chance 100):
    1. `POTION(effect=POISON, level=2, duration=25*<level>, who=@Victim)` (Poison II)
  - ability C (landing — **gap** `PROJECTILE_LAND`, chance 100; intended behavior):
    1. via `@Aoe{r=<level>, filter=ENEMIES}` restricted to players (the jar's fixed
       check is a player-type test):
       `POTION(effect=POISON, level=2, duration=25*<level>)`
- **gaps:** `PROJECTILE_DRESSING`, `PROJECTILE_LAND` (defined under Cowification).
- **interactions:** stands down its orb rider when Explosive is on the same item
  (single rider slot — same interaction rule as Cowification). **Virus multiplies
  every Poison tick this applies** (see Virus); Poison cannot kill in vanilla
  (floors at 1 health) — vanilla rule, noted for the parity harness.
- **strings:** none (Venom has no sound and no particle anywhere — copied: the only
  silent bow enchant).
- **numbers:** direct Poison II `25/50/75` t (1.25/2.5/3.75 s), 100 %. **Known bug
  (dead code):** the AoE loop's condition tests the **arrow** for player-ness instead
  of the candidate — the AoE body is unreachable and Venom's area poison never
  applies. As-intended (compare Explosive's landing AoE, which tests the candidate):
  Poison II for `25*level` t on enemy players within `level` blocks of the
  landing. Deviation row: `D-05-7`.
- **era:** XP-orb arrow rider on 1.8.9: orb entities have client-side magnet/render
  quirks when mounted — legacy sweep should verify the rider renders (visual-only).

---

### Virus (`enchants/virus`)

- **codex:** `05-enchants-bows.md § Virus`
- **activation:** trigger `BOW`; melee mirror: yes (no cause gate). Two abilities:
  the infection mark is **100 % on every hit** (the jar writes the metadata before
  and outside the roll — codex quirk, copied); only the regen-strip is chance-gated.
- **decomposition:**
  - ability A (chance 100): DoT amplifier mark — **gap** `DOT_AMPLIFY_MARK`
    (`causes=[WITHER, POISON]`, `factor=<level>+1`, `duration=30*<level>`,
    `who=@Victim`); re-application refreshes unconditionally (unlike Unfocus — no
    already-active gate).
  - ability B (chance `15*<level>`):
    1. `REMOVE_POTION(effect=REGENERATION, who=@Victim)` — strips **all**
       regeneration, including golden-apple regen
    2. `PARTICLE(particle=SPELL_WITCH, count=25, who=@Victim)` (+1y, speed 0.4)
    3. `SOUND(sound=ENTITY_ENDERMAN_TELEPORT, volume=0.6, pitch=2.0, who=@Victim)`
- **gaps:** `DOT_AMPLIFY_MARK` — a timed mark on the target that multiplies their
  **incoming** damage from listed causes (`WITHER`/`POISON`) by `factor`. `MARK`
  scales only the *actor's own later hits*; `DAMAGE_MOD`/`DAMAGE_SCALE` act on the
  triggering fold; nothing intercepts the victim's later DoT ticks. Consumers:
  DoT force-multiplier enchants.
- **interactions:** the intended combo: Explosive's Wither II ticks and Venom's
  Poison II ticks (both vanilla 1.0/tick) become `2.0–5.0` per tick under Virus; any
  other wither/poison source is equally amplified. A Virus-only bow adds **no**
  damage of its own (pure force multiplier — copied). Mob victims are marked but the
  jar only consumes the mark for players — `DOT_AMPLIFY_MARK` spec: player bearers
  only (matches measured). Jar amplifies even cancelled DoT events
  (`ignoreCancelled=false`) — SE gate order applies, noted.
- **strings:** none.
- **numbers:** infection duration `1500/3000/4500/6000` ms = `30/60/90/120` t;
  multiplier `×2/×3/×4/×5` (`level+1`); regen-strip chance
  `15 / 30 / 44.999999999999996 / 60` %. Table-roll quirk: `interval = 0.0` → table
  only rolls level 4.
- **era:** none (`SPELL_WITCH`/`ENTITY_ENDERMAN_TELEPORT` map cleanly to 1.8).

---

## Gap index (this doc)

| Gap | Consumers here |
| --- | --- |
| `PROJECTILE_DRESSING` | Cowification, Explosive, Venom, Hellfire, Infernal |
| `PROJECTILE_LAND` | Cowification, Explosive, Hellfire, Infernal, Venom |
| `TEMP_BLOCK_FILL_CHANCE` | Dimension Rift |
| `TEMP_BLOCK_REVERT_HOOK` | Dimension Rift |
| `VAR_SCALED_CHANCE` | Farcast |
| `TARGET_RELATION_FACT` | Healing, Teleportation |
| `RANDOM_RANGE_PARAM` | Healing |
| `DURABILITY_PIECE_SELECT` | Healing |
| `SUMMON_REBIND_UPGRADE` | Hijack |
| `PROJECTILE_HIT_HEIGHT` | Sniper |
| `OUTGOING_DEBUFF_CAUSE_FILTER` | Unfocus |
| `MARK_CONSUME_FEEDBACK` | Unfocus |
| `DOT_AMPLIFY_MARK` | Virus |

## Deviation rows queued (proposed ids, pending `deviations.md` assignment)

- `D-05-1` Arrow Lifesteal — int-truncated heal → flat `+level` heal
- `D-05-2` Eagle Eye — dead distance curve → 1–4 durability by 10/20/32-block thresholds
- `D-05-3` Farcast — inverted guard (inert) → health-scaled knockback chance
- `D-05-4` Lightning — pipeline-re-entrant bonus hit → single non-re-entrant 5.0
- `D-05-5` Piercing — immunity-window-swallowed bonus → in-event +2.5 %/level fold
- `D-05-6` Teleblock — precedence-bug duration (3.005–15.005 s) → 8/11/14/17/20 s
- `D-05-7` Venom — unreachable AoE → Poison II AoE at the landing
