# Decomposition matrix — armor enchants A–L (codex doc 01)

36 entries, codex order. Behavioral authority: `01-enchants-armor-a-l.md` (local-only
codex). Numbers are the codex's measured values; where the codex marks a bug the
as-intended value is stated with a proposed `deviations.md` row id (`D-01-NN`,
pending ledger merge — `D-001` already covers the defensive single-pass ruling).

Shared rulings applied to every entry below:

- **Single pass (D-001):** the original fires the defensive pass twice per melee hit
  (`00-MECHANICS.md` §3.4); every entry here records SINGLE-PASS intended values.
- **Worn-state resolution (D-01-15):** the original's metadata-keyed enchants are
  last-equip-wins and unequip-any-piece-disables; the port resolves the highest worn
  level via WornState. Per-piece stacking survives only where the original's stacking
  whitelist granted it, authored as an interaction-layer stacking rule.
- **Suppression:** the original's `noDefenseProcs` flag (Silence, Mastery Tombstone)
  maps to `SUPPRESS(scope=TYPE, key=defense)` interaction rules; the 50% leak on the
  second pass disappears with D-001.
- **World gates:** the End tier>5 gate and CosmicOutposts world tiers are
  cosmic-server policy; expressible later as interaction-layer `%actor.world%`
  conditions, not folded into any entry.
- **External plugins:** Factions ally checks map to the engine's `ALLIES`/`ENEMIES`
  selector filters; Essentials/StaffPlus/WorldGuard/mcMMO hooks are out of scope and
  noted per entry where they gated behavior.

---

## Entries

### Aegis (`enchants/aegis`)

- **codex:** `01-enchants-armor-a-l.md § Aegis`
- **activation:** trigger `DEFENSE`; condition `%victim.type% == "PLAYER" && %recentattackers% > <8 - level>` (per-level literal 7/6/5/4/3/2; `%recentattackers%` = distinct attackers that hit the wearer inside the recent window, 100t)
- **decomposition:**
  1. `PARTICLE(particle=MAGIC_CRIT, count=16, who=@Self)` — at eye height
  2. `DAMAGE_MOD(side=defense, mode=add, amount=50)`
- **interactions:** not in the stacking whitelist — one proc at the highest worn level; attacker-gated, so single-pass already matched measured behavior.
- **strings:** none.
- **numbers:** window 100t (level-independent); reduction ×0.5 flat at every level; gate `uniqueAttackers > 8 - level` → L1 ≥8, L2 ≥7, L3 ≥6, L4 ≥5, L5 ≥4, L6 ≥3. Known bug: measured jar only reduces hits from attackers *not yet tracked* and never adds them, and the first window never seeds the set — as-intended: reduce any attacker's hit once the unique-attacker count exceeds `8 - level` (D-01-01).
- **era:** `MAGIC_CRIT` is the 1.8.9 particle name (modern `CRIT_MAGIC`) — resolver alias; no other hazards.

### Angelic (`enchants/angelic`)

- **codex:** `01-enchants-armor-a-l.md § Angelic`
- **activation:** trigger `DEFENSE`; chance `3 × level`%; condition `%angelic.active% != 1` (re-entry guard: a roll during a live regen is consumed, matching measured)
- **decomposition:**
  1. `SET_VAR(name=angelic.active, value=1, ttl=<duration>)`
  2. `MODIFY_HEALTH(amount=1, mode=give, who=@Self)` + `PARTICLE(particle=SPELL, count=15, who=@Self)`
  3. `MODIFY_HEALTH(amount=1, mode=give, who=@Self, wait=<period>)` + `PARTICLE(particle=SPELL, count=15, who=@Self, wait=<period>)`
  4. L5 only: `MODIFY_HEALTH(amount=1, mode=give, who=@Self, wait=120)` + matching `PARTICLE(wait=120)`
- **interactions:** IS in the stacking whitelist (per-piece chance rolls) — interaction-layer stacking rule; the `angelic.active` guard preserves the measured single-concurrent-regen behavior.
- **strings:** none.
- **numbers:** chance 3/6/9/12/15%; period 100/90/80/70/60t; measured totals 2.0 HP at L1–4, 2.0 or 3.0 HP at L5 (third pulse only when the random duration roll lands ≥120t, a 37.5% sub-chance); heal per pulse 1.0 HP flat. Shipped: deterministic third pulse at L5 (`ttl=125t`) — D-01-02. Single-pass values per D-001.
- **era:** `SPELL` particle exists on 1.8.9; no hazards.

### Anti Gravity (`enchants/anti-gravity`)

- **codex:** `01-enchants-armor-a-l.md § Anti Gravity`
- **activation:** trigger `PASSIVE` (boots only); no condition
- **decomposition:**
  1. `POTION(effect=JUMP, level=<level + 3>, duration=maintained-while-worn, who=@Self)` — §B lifecycle removes it on unequip
- **interactions:** whitelisted for stacking but boots-only; highest-amplifier-wins against other Jump Boost sources (engine potion reconciliation). The original's parkour/maze region suppression is cosmic-server policy — dropped.
- **strings:** shared potion-registry equip feedback, verbatim: `§b§l[+] §b{enchant} {level}:§7 applying {effect} {level}` on apply, `§c§l[-] §c{enchant} {level}:§7 removing {effect} {level}` on removal (roman-numeral levels).
- **numbers:** Jump Boost IV/V/VI at L1/2/3 (amp `level + 2`); duration permanent while worn (measured: an effectively unbounded tick count).
- **era:** JUMP effect exists on 1.8.9; no hazards.

### Aquatic (`enchants/aquatic`)

- **codex:** `01-enchants-armor-a-l.md § Aquatic`
- **activation:** trigger `PASSIVE` (helmets only); no condition
- **decomposition:**
  1. `POTION(effect=WATER_BREATHING, level=1, duration=maintained-while-worn, who=@Self)`
- **interactions:** whitelisted for stacking but helmet-only; potion-registry reconciliation.
- **strings:** shared potion-registry `[+]`/`[-]` lines (see Anti Gravity).
- **numbers:** single level; Water Breathing I permanent while worn. The measured `interval = 0.0` is enchant-table plumbing, no runtime effect.
- **era:** none.

### Armored (`enchants/armored`)

- **codex:** `01-enchants-armor-a-l.md § Armored`
- **activation:** trigger `DEFENSE`; condition `%victim.helditem% contains "SWORD" && %damage% > <floor>` (attacker-held check; floor = `1 / (1 - reduction)` per level: 1.0191/1.039/1.0596/1.0811 — the measured skip-below-1.0 guard)
- **decomposition:**
  1. `PARTICLE(particle=BLOCK_CRACK, block=DIAMOND_BLOCK, count=8, who=@Self)` + `SOUND(sound=BLOCK_STONE_BREAK, volume=1, pitch=1)` — the measured world-effect 2001 with block id 57
  2. `DAMAGE_MOD(side=defense, mode=add, amount=<1.875 × level>)`
- **interactions:** IS in the stacking whitelist — per-piece interaction-layer stacking rule; four L4 pieces compound multiplicatively `0.925^4` = 26.80% total (measured). Attacker-gated → single pass already matched. Heroic counterpart `Paladin Armored` (doc 06 range) upgrades over it.
- **strings:** none.
- **numbers:** reduction 1.875/3.75/5.625/7.5% per level; guard skips the reduction when it would land at or below 1.0 damage; sword-material gate only (axes/bows/mobs bypass).
- **era:** diamond-block break cue: 1.8.9 sound is the legacy `dig.stone` family — resolver-mapped; `BLOCK_CRACK` particle fine.

### Arrow Deflect (`enchants/arrow-deflect`)

- **codex:** `01-enchants-armor-a-l.md § Arrow Deflect`
- **activation:** trigger `DEFENSE`; two ability blocks split on the armed flag:
  - deflect: condition `%damagecause% == "PROJECTILE" && %arrowdeflect.armed% == 1`
  - arm: condition `%damagecause% == "PROJECTILE" && %arrowdeflect.armed% != 1`
- **decomposition:**
  1. (deflect) `CANCEL()` ; `SET_VAR(name=arrowdeflect.armed, value=, ttl=1)` — clear ; `PARTICLE(particle=SPELL, count=20, who=@Self)` ; `SOUND(sound=ENTITY_ITEM_BREAK, volume=0.7, pitch=0.2)`
  2. (arm) `SET_VAR(name=arrowdeflect.armed, value=1, ttl=<level × 10>)`
- **interactions:** Lethal Sniper bypass is an interaction-layer rule on the sniper's side (its tagged arrows suppress the deflect with `0.1 × sniperLevel` chance and, per measured, skip re-arming); not folded here. Not whitelisted for stacking.
- **strings:** none.
- **numbers:** window `level × 500` ms = 10/20/30/40t. The strict arm→deflect alternation (at best every second arrow, ≤50% steady-state) is the measured mechanism and is reproduced, not treated as a bug. Residual: the measured jar deletes the arrow entity; `CANCEL` negates the damage but leaves the arrow — cosmetic only. Single-pass per D-001.
- **era:** `ITEM_BREAK` → 1.8.9 `random.break`; `SPELL` fine.

### Avenging Angel (`enchants/avenging-angel`)

- **codex:** `01-enchants-armor-a-l.md § Avenging Angel`
- **activation:** gap trigger (below): fires on wearers when an allied player dies within radius; conditions `%actor.healthpercent% < 100`, ability cooldown
- **decomposition:** (given `PROXIMITY_EVENT_TRIGGER(event=player-death, relation=ALLY, range=<radius>)`)
  1. `MODIFY_HEALTH(amount=100, mode=give, who=@Self)` — full heal (clamped to max)
  2. `POTION(effect=ABSORPTION, level=6, duration=<20 × (6 + level)>, who=@Self)`
  3. `MESSAGE(text=…)` (see strings)
- **gaps:** `PROXIMITY_EVENT_TRIGGER — activation source that fires an ability on entities wearing the content when a specified event happens to ANOTHER entity within range; params: event (player-death | tagged-effect-application), range, relation filter (ALLIES/ENEMIES/ALL); consumers: death-rescue auras (this entry), ally-state leeches (Blood Lust). No existing trigger observes another entity's event: DEATH fires on the dying entity's own equipment, and no selector can require "wearing enchant X".`
- **interactions:** dead player must be in a normal faction (relation filter); potion-registry absorption tracking; the measured unscoped absorption removal (which also stripped third-party absorption) is superseded by scoped potion removal.
- **strings:** to the healed ally, verbatim (the unclosed `[` is verbatim): `§e§l* AVENGING ANGEL [§7+{hp} HP ({player})§e§l *` — `{hp}` formatted `#,###.##` (comma thousands, ≤2 decimals, trailing zeros dropped, no K/M/B).
- **numbers:** radius `min(50 + level × 12, 100)` = 62/74/86/98 (cap never reached); absorption 140/160/180/200t at amp 5 (Absorption VI); heal always to full. Known bug: measured cooldown is 20 s (the cooldown map's own write-expiry wins; the stored 10 s value is never read) — as-intended 10 s (D-01-03).
- **era:** ABSORPTION exists on 1.8.9; no hazards.

### Blood Link (`enchants/blood-link`)

- **codex:** `01-enchants-armor-a-l.md § Blood Link`
- **activation:** trigger `GUARDIAN_HURT`; chance `5 × level`%; condition `%actor.healthpercent% < 100`
- **decomposition:**
  1. `MODIFY_HEALTH(amount=<level == 5 ? 2 : 1>, mode=give, who=@Self)`
  2. `PARTICLE(particle=HEART, count=1, who=@Self)` — at eye height (the measured second heart at the golem is dropped with its block-corner snap quirk)
- **interactions:** meaningful only alongside Guardians (the engine `GUARD` summon feeds `GUARDIAN_HURT`) — cross-enchant dependency recorded; no cooldown, every hit on the guardian rolls.
- **strings:** none.
- **numbers:** chance 5/10/15/20/25%; heal 1.0 HP at L1–4, 2.0 HP at L5.
- **era:** `HEART` particle fine on 1.8.9.

### Blood Lust (`enchants/blood-lust`)

- **codex:** `01-enchants-armor-a-l.md § Blood Lust`
- **activation:** gap trigger: fires on wearers within 7 blocks when a bleed DoT stack is applied to an allied player; chance `20 + 5 × level`%
- **decomposition:** (given `PROXIMITY_EVENT_TRIGGER(event=tagged-effect-application, tag=bleed, relation=ALLIES, range=7)`)
  1. `MODIFY_HEALTH(amount=<clamp(0.025 × level × %bleed.stacks%, 2.0, unbounded)>, mode=give, who=@Self)` — the measured `max(2.0, bleedStack/2 × 0.05 × level)`, via the `VAR_SCALED_PARAM` gap
  2. `PARTICLE(particle=DRIP_LAVA, count=10, who=@Self)` ; `SOUND(sound=ENTITY_GENERIC_EAT, volume=0.4, pitch=0.6)`
- **gaps:**
  - `PROXIMITY_EVENT_TRIGGER` — shared with Avenging Angel (see that entry for the definition); this consumer parameterizes `event=tagged-effect-application`.
  - `VAR_SCALED_PARAM` — shared, defined in doc 07 (§ Death Pact): a numeric effect param authored as `clamp(a·%var% + b, lo, hi)` over the condition-var vocabulary, evaluated at activation. Here `var=%bleed.stacks%`, `a=0.025 × level`, `b=0`, `lo=2.0` (the floor), `hi` unbounded. Requires the doc 04 bleed decompositions to expose the stack count as a condition var as well as the `bleed` effect tag.
- **interactions:** hard dependency on the Bleed / Heroic Bleed axe decompositions (doc 04) publishing a `bleed` effect tag; ally relation via the trigger's relation filter; excludes the bleeding victim's attacker by construction (the trigger fires for third-party wearers).
- **strings:** none.
- **numbers:** chance 25/30/35/40/45/50%; heal `max(2.0, bleedStack/2 × 0.05 × level)` measured and shipped in full through `VAR_SCALED_PARAM`. The 2.0 floor dominates below stack 14 at L6 (earliest exceedance anywhere on the ladder: stack ≥14 at L6; lower levels need proportionally deeper stacks), so in ordinary play the felt value is 2.0 HP — the scaling term only matters at stack depths the bleed family can reach with the floor already saturated.
- **era:** `DRIP_LAVA` particle (modern `DRIPPING_LAVA`) and `EAT` sound (1.8.9 `random.eat`) — resolver aliases.

### Cactus (`enchants/cactus`)

- **codex:** `01-enchants-armor-a-l.md § Cactus`
- **activation:** trigger `DEFENSE`; condition `%victim.type% == "PLAYER"` (player attackers only)
- **decomposition:**
  1. `DAMAGE(amount=<0.5 × level>, who=@Attacker)` — thorns; like the measured behaviour this runs the target's full damage pipeline (armour, invulnerability ticks, further procs), not an event tweak
  2. `PARTICLE(particle=BLOCK_CRACK, block=CACTUS, count=8, who=@Self)` + `SOUND(sound=BLOCK_WOOL_BREAK, volume=1, pitch=1)` — world-effect 2001 with block id 81
- **interactions:** not whitelisted — one proc at highest level. The measured recursive chain (Cactus vs Cactus, reflected hits re-entering defensive procs) is bounded by the engine's re-hit guard; recorded, not reproduced unbounded.
- **strings:** none.
- **numbers:** reflected damage 0.5/1.0 at L1/2; no cooldown; the stored map level was dead (dispatcher level used) — no behavioral delta.
- **era:** cactus break cue on 1.8.9 is the legacy cloth/`dig.cloth` sound family — resolver-mapped.

### Clarity (`enchants/clarity`)

- **codex:** `01-enchants-armor-a-l.md § Clarity`
- **activation:** trigger `REPEATING` (`repeat: 3`); condition `%clarity.grace% != 1` (grace flag set by Blind's interaction rule)
- **decomposition:**
  1. `REMOVE_POTION(effect=BLINDNESS, who=@Self)` — cleanse; the amplifier threshold is enforced by interaction rules (below), since active-effect amplifiers are not condition facts
  2. on a successful cleanse: `SOUND(sound=BLOCK_FIRE_EXTINGUISH, volume=1.0, pitch=0.6)` ; `PARTICLE(particle=SPELL, count=20, who=@Self)`
- **interactions:** all amplifier-threshold logic is authored as interaction-layer YAML against the known blindness sources (their amplifiers are static per level, so the rules are per-level pairs, no runtime amp var needed):
  - Blind (swords, doc 03): blocked/stripped when Blind's amplifier ≤ threshold (L1: amp 0; L2: amp ≤1; L3: amp ≤3); Blind's `recentlyBlinded` grace = Blind applies `SUPPRESS(scope=ENCHANT, key=clarity, duration=<grace>)` on application.
  - Dimensional Traveler set (doc 10): its amp-0 blind blocked at any Clarity level.
  - Ender Walker self-blind: suppressed by Clarity presence (measured presence-only check — any level).
- **strings:** none.
- **numbers:** thresholds `level == 3 ? 3 : level - 1` = 0/1/3 (the L2→L3 two-tier jump is verbatim); scan period 3t, level-independent; cleanse removes the whole effect, not a downgrade.
- **era:** `FIZZ` → 1.8.9 `random.fizz`; `SPELL` fine.

### Commander (`enchants/commander`)

- **codex:** `01-enchants-armor-a-l.md § Commander`
- **activation:** trigger `REPEATING` (`repeat: 160`); no condition
- **decomposition:** (all `who=@AOE{r=<level × 2 + 3>, filter=ALLIES}`, wearer excluded to match measured support-only behavior)
  1. `POTION(effect=FAST_DIGGING, level=<level > 4 ? 2 : 1>, duration=<level > 3 ? 600 : 300>)`
  2. L2+: `REMOVE_POTION(effect=WEAKNESS)`
  3. L3+: `REMOVE_POTION(effect=CONFUSION)`
  4. L4+: `POTION(effect=SPEED, level=<level == 4 ? 1 : 2>, duration=300)`
  5. L5: `REMOVE_POTION(effect=POISON)`
  6. `PARTICLE(particle=BLOCK_CRACK, block=DIAMOND_BLOCK, count=8, who=@Self)` — when allies were buffed
- **interactions:** ally detection = `ALLIES` filter (Factions in the original; disabled in duels there — server policy, dropped); the engine's non-force potion application matches the measured "stronger existing effect blocks refresh".
- **strings:** none.
- **numbers:** radius 5/7/9/11/13 (cosmic used a cube of that half-extent; engine AOE is spherical — recorded approximation); Haste I ×15 s (L1–3), Haste I ×30 s (L4), Haste II ×30 s (L5); Speed I/II ×15 s at L4/L5; period 8 s level-independent; buffs refresh faster than they lapse (cadence 8 s vs 15–30 s durations).
- **era:** `FAST_DIGGING`/`CONFUSION` legacy effect names — resolver aliases; no hazards.

### Creeper Armor (`enchants/creeper-armor`)

- **codex:** `01-enchants-armor-a-l.md § Creeper Armor`
- **activation:** trigger `DEFENSE`; condition `%damagecause% == "BLOCK_EXPLOSION" || %damagecause% == "ENTITY_EXPLOSION"`
- **decomposition:**
  1. `CANCEL()` — total explosion immunity at every level
  2. L2+: `KNOCKBACK_CONTROL(multiplier=0, duration=2, who=@Self)`
  3. L3 (as-intended only): heal `min(6.0, 0.1 × event damage)` — needs the gap below
- **gaps:** `EVENT_DAMAGE_FRACTION — numeric effect amounts expressible as a fraction of the triggering event's (pre-mutation) damage, with an optional cap; params: fraction, cap; consumers: damage-fraction heals — this entry's as-intended L3 heal (fraction 0.1, cap 6.0) and Ender Walker's DoT-conversion heal (fraction 1.0, uncapped). Here it is needed ONLY for the as-intended L3 heal; the measured value is a dead-code 0.0 and is fully expressible by omission.`
- **interactions:** the original bypassed all dispatch gates (End tier, outposts, `noDefenseProcs`, rank cap); the port applies standard gates — recorded under the shared rulings.
- **strings:** none.
- **numbers:** cancellation 100%, level-independent; knockback nulled at L2+; L3 heal measured **0.0** (it reads the event damage after the cancellation has already zeroed it) — as-intended `min(6.0, 0.1 × raw damage)` (D-01-04). `interval = 0.0` means the enchant table only ever rolled L3 — table plumbing, no runtime effect.
- **era:** none.

### Curse (`enchants/curse`)

- **codex:** `01-enchants-armor-a-l.md § Curse`
- **activation:** trigger `DEFENSE`; chance `3 × level`%; condition `%damagecause% == "ENTITY_ATTACK"`
- **decomposition:** (all `who=@Self`)
  1. `POTION(effect=INCREASE_DAMAGE, level=2, duration=<30 × level>)`
  2. `POTION(effect=DAMAGE_RESISTANCE, level=2, duration=<30 × level>)`
  3. `POTION(effect=SLOW, level=2, duration=<20 × level>)` — the deliberate self-debuff
- **interactions:** Drunk's equip-time cleanse can strip the self-Slowness (near-no-op in the original, recorded on Drunk); non-force application blocked by equal/stronger existing effects (measured).
- **strings:** none.
- **numbers:** chance 3–15%; Strength II + Resistance II for `30 × level`t (1.5–7.5 s); Slowness II for `20 × level`t (1.0–5.0 s); amplifiers level-independent. Resistance II's 40% reduction on a tier-2 chestplate is measured design, kept.
- **era:** legacy effect names (`INCREASE_DAMAGE`, `DAMAGE_RESISTANCE`, `SLOW`) — resolver aliases.

### Death God (`enchants/death-god`)

- **codex:** `01-enchants-armor-a-l.md § Death God`
- **activation:** trigger `DEFENSE`; chance `1.5 × level`%; condition `%posthit.health% <= <4 + level>` (gap fact below; per-level literal 5/6/7)
- **decomposition:**
  1. `CANCEL()`
  2. `MODIFY_HEALTH(amount=<5 + level>, mode=give, who=@Self)`
  3. `MESSAGE(text=…)` (see strings)
- **gaps:** `POST_HIT_HEALTH_VAR — new DEFENSE-scope condition fact %posthit.health% = the actor's health minus the pending post-mitigation damage; params: none; consumers: death-save and low-health-band gates (Death God, Ender Shift, Lifebloom, Lucky here; Phoenix-class saves in doc 02). Conditions cannot express it today: the grammar has no arithmetic and no var-vs-var comparison, and no existing fact exposes predicted post-hit health.`
- **interactions:** none.
- **strings:** verbatim, to the wearer: `§6§l* DEATH GOD [§6+{hp}HP§l] *` — `{hp}` = `5 + level` (6/7/8); the mid-string bold-reset rendering quirk is a client artifact of the verbatim codes, preserved as-is.
- **numbers:** chance 1.5/3.0/4.5%; trigger band: post-hit health ≤ 5/6/7 HP (a low-health band, not a lethal-only save — verbatim); heal 6/7/8 HP; no cooldown (every qualifying hit rolls, all causes). Single-pass per D-001.
- **era:** none.

### Deathbringer (`enchants/deathbringer`)

- **codex:** `01-enchants-armor-a-l.md § Deathbringer`
- **activation:** triggers `ATTACK` and `BOW` (two ability blocks — measured covers direct hits and projectiles); chance `10 × level`%
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=100)` — ×2.0 outgoing
- **interactions:** heroic upgrade path to `Planetary Deathbringer` (doc 06) — apply-over rules live in the tier/upgrade layer. The measured rank-cap/gate bypass (raw listener) is superseded by standard gates.
- **strings:** none.
- **numbers:** chance 10/20/30%; multiplier ×2.0 level-independent; the measured missing-level fallback to L1 (10%) is a desync artifact with no port equivalent.
- **era:** none.

### Destruction (`enchants/destruction`)

- **codex:** `01-enchants-armor-a-l.md § Destruction`
- **activation:** trigger `REPEATING` (`repeat: <period>`, per level); four ability blocks share `who=@AOE{r=<level>, filter=ENEMIES}`
- **decomposition:**
  1. (every run) `DAMAGE(amount=<level == 5 ? 2 : 1>)` + `PARTICLE(particle=LAVA, count=20)` per struck enemy
  2. (chance 15%) `POTION(effect=SLOW, level=<level > 2 ? 2 : 1>, duration=<level × 20>)` + `PARTICLE(particle=WITCH_MAGIC, count=10)`
  3. (L2+, chance 10%) `POTION(effect=SLOW_DIGGING, level=<level + 2>, duration=<level × 40>)` + `PARTICLE(particle=WITCH_MAGIC, count=20)`
  4. (L3+, chance 8.5%) `POTION(effect=WITHER, level=2, duration=<level × 40>)` + `PARTICLE(particle=WITCH_MAGIC, count=30)`
- **interactions:** `ENEMIES` filter carries the neutral-or-worse faction gate; WorldGuard pvp-deny, Essentials god/vanish, StaffPlus freeze, `spectator`/`NPC` exemptions are external — the engine's own target-validity rules apply instead.
- **strings:** none.
- **numbers:** measured periods 300/120/60/60/60t (`60 × (5 / level)` integer division — L3–5 indistinguishable, L1 5× slower than L2) — as-intended 300/150/100/75/60t (D-01-05). Radius = level (cube half-extent measured; spherical here). Damage 1.0 (2.0 at L5) per tick. Sub-chances 15%/10%/8.5% level-independent; Wither amp fixed at II; Mining Fatigue reaches amp 6 (VII) at L5 — verbatim. Known bug: one vanished/spectator/frozen bystander aborted the wearer's whole pass — as-intended: skip only that target (D-01-06).
- **era:** WITHER effect exists on 1.8.9; `WITCH_MAGIC` → modern `SPELL_WITCH`/`WITCH` — resolver alias.

### Diminish (`enchants/diminish`)

- **codex:** `01-enchants-armor-a-l.md § Diminish`
- **activation:** trigger `DEFENSE`; chance `1.5 × level`%; condition `%damage% > 0`
- **decomposition:**
  1. `DAMAGE_CAP(factor=1.0, reflect=false, duration=<window>)` — arms on this hit; the engine primitive is Diminish by construction ("cap the next incoming hit at factor × the last damage taken")
  2. `MESSAGE(text=…)` (see strings)
- **interactions:** heroic upgrade `Vengeful Diminish` (doc 06); not whitelisted, chestplate-only.
- **strings:** verbatim, on arming: `§e§l* DIMINISH [§eDMG: {damage}§l] *` — `{damage}` formatted `#.##` (≤2 decimals, trailing zeros stripped).
- **numbers:** arm chance 1.5–9.0% (L1–6); cap value = the arming hit's final damage, level-independent. Known bugs: the measured charge (a) persists forever with no timeout and (b) sets base damage from a final-damage number, so the delivered cap is re-reduced by armor (over-delivery) — as-intended: cap the next larger hit at exactly the stored value within a bounded window (`DAMAGE_CAP` duration, 100t default) (D-01-07). The measured keep-charge-until-a-larger-hit subtlety collapses into the primitive's next-hit semantics under the same row. Single-pass per D-001.
- **era:** none.

### Dodge (`enchants/dodge`)

- **codex:** `01-enchants-armor-a-l.md § Dodge`
- **activation:** trigger `DEFENSE`; chance `2.5 × level`%; condition `(%damagecause% == "ENTITY_ATTACK" || %damagecause% == "PROJECTILE") && (%sneaking% : +15 %chance%)`
- **decomposition:**
  1. `CANCEL()`
  2. `MESSAGE(text=…)` ; `PARTICLE(particle=CLOUD, count=10, who=@Self)` ; `SOUND(sound=ENTITY_BAT_TAKEOFF, volume=1.0, pitch=0.75)` — world-audible, as measured
- **interactions:** not whitelisted — one roll per hit.
- **strings:** verbatim, to the wearer: `§e*DODGE*` (no bold, no spaces).
- **numbers:** base chance 2.5/5/7.5/10/12.5%; sneak bonus flat +15 percentage points at every level (crouching L1 = 17.5% out-dodges standing L5 = 12.5% — measured design, kept). Single-pass per D-001.
- **era:** `BAT_TAKEOFF` → 1.8.9 `mob.bat.takeoff` — resolver alias.

### Drunk (`enchants/drunk`)

- **codex:** `01-enchants-armor-a-l.md § Drunk`
- **activation:** two blocks: trigger `PASSIVE` (helmet worn); trigger `DEFENSE`, chance `level`%, no cause filter (verbatim — falls, fire, poison all roll)
- **decomposition:**
  1. (PASSIVE, all maintained-while-worn, `who=@Self`) `POTION(effect=INCREASE_DAMAGE, level=<level/2 + 1>)` ; `POTION(effect=SLOW, level=<max(level-2,0) + 1>)` ; `POTION(effect=SLOW_DIGGING, level=<level/2 + 1>)`
  2. (DEFENSE) `POTION(effect=CONFUSION, level=<max(0, level-3) + 1>, duration=<level × 40>, who=@Self)` ; `MESSAGE(text=…)`
- **interactions:** whitelisted but helmet-only; the measured equip-time cleanse of weaker Slowness/Mining Fatigue is a near-no-op (it re-applies its own immediately) and is not ported; nausea was untracked in the original and thus survived unequip — the port's §B lifecycle removes only the maintained trio, matching.
- **strings:** defence proc, verbatim: `§eYou're starting to feel very dizzy...` ; plus the shared potion-registry `[+]`/`[-]` lines (three per equip/unequip).
- **numbers:** per level 1–4 — Strength I/II/II/III, Slowness I/I/II/III, Mining Fatigue I/II/II/III (all permanent while worn); nausea chance 1/2/3/4%, duration 40/80/120/160t, Nausea I (II at L4). Net-negative at low levels is measured design, kept.
- **era:** legacy effect names — resolver aliases.

### Ender Shift (`enchants/ender-shift`)

- **codex:** `01-enchants-armor-a-l.md § Ender Shift`
- **activation:** trigger `DEFENSE`; condition `%posthit.health% <= 0` (gap fact, see Death God); ability cooldown 600t (30 s)
- **decomposition:**
  1. `CANCEL()`
  2. `POTION(effect=NIGHT_VISION, level=1, duration=<d>, who=@Self)` ; `POTION(effect=SPEED, level=<level + 3>, duration=<d>)` ; `POTION(effect=JUMP, level=<level>, duration=<d>)` ; `POTION(effect=ABSORPTION, level=<level + 3>, duration=<d>)` — `d = 20 × (level + 7)`
  3. `WEAKEN(percent=100, duration=<d>, who=@Self)` — the "escape, don't fight" clause: outgoing damage zeroed for the shift window (the measured bow-launch cancel collapses into this; the shot fires but deals nothing)
  4. `MESSAGE(text=…)` ; `SOUND(sound=ENTITY_ENDER_DRAGON_GROWL, volume=1.0, pitch=0.54)` ; `PARTICLE(particle=WITCH_MAGIC, count=100, spread=2, who=@Self)` (repeated at expiry, `wait=<d>`)
- **gaps:** `POST_HIT_HEALTH_VAR` — shared, defined at Death God.
- **interactions:** the measured 10-tick buffs-outlast-attack-lock gap disappears (one duration governs both); custom `armorModifier` NBT feeding the predictor has no port equivalent.
- **strings:** verbatim, to the wearer, three lines: empty line, `§d§l(!) §dYou were about to die, so you have entered the Ender dimension, escape to safety!`, empty line.
- **numbers:** duration 160/180/200t; Speed IV/V/VI; Jump Boost I/II/III; Absorption IV/V/VI; Night Vision I; cooldown 30 s — all per measured. Lethality predictor: measured uses a home-grown armor model (4%/point, ignores Resistance/Protection, silently never fires at armor ≥ 25) — as-intended: true post-mitigation lethality via `%posthit.health%` (D-01-14). Single-pass per D-001.
- **era:** `ENDERDRAGON_GROWL` → 1.8.9 `mob.enderdragon.growl`; helmets+boots item set is era-neutral.

### Ender Walker (`enchants/ender-walker`)

- **codex:** `01-enchants-armor-a-l.md § Ender Walker`
- **activation:** trigger `DEFENSE`; condition `%damagecause% == "WITHER" || %damagecause% == "POISON"`; two blocks (immunity always, heal chance-gated)
- **decomposition:**
  1. (always) `CANCEL()` ; `PARTICLE(particle=PORTAL, count=70, who=@Self)` ; `POTION(effect=BLINDNESS, level=1, duration=200, who=@Self, wait=2)` — the self-blind, suppressed by the Clarity interaction rule
  2. (chance `15 × level`%) `MODIFY_HEALTH(amount=<event damage × 1.0>, mode=give, who=@Self)` — the heal equals the cancelled DoT tick's damage, via the `EVENT_DAMAGE_FRACTION` gap ; `PARTICLE(particle=WITCH_MAGIC, count=40, who=@Self)`
- **gaps:** `EVENT_DAMAGE_FRACTION` — shared, defined at Creeper Armor; this consumer parameterizes `fraction=1.0` with no cap. Wither and poison ticks are a flat 1.0, so a fixed `amount=1` would feel the same in ordinary play; the gap buys fidelity to the codex and correctness under any amplified or third-party DoT, not a balance change.
- **interactions:** Clarity presence suppresses the self-blind (measured presence-only — any Clarity level; authored as the Clarity interaction rule); equip cue `SOUND(sound=ENTITY_WITHER_HURT, volume=1.0, pitch=0.6)` on the PASSIVE equip edge.
- **strings:** none.
- **numbers:** DoT immunity 100% at every level (level-independent, verbatim); heal chance 15–75%; a successful roll is a net HP gain (DoT converted to healing — measured design); self-blindness 200t (10 s) Blindness I per proc, level-independent. Single-pass per D-001.
- **era:** `WITHER_HURT` → 1.8.9 `mob.wither.hurt`; `PORTAL`/`WITCH_MAGIC` fine.

### Enlighted (`enchants/enlighted`)

- **codex:** `01-enchants-armor-a-l.md § Enlighted`
- **activation:** trigger `DEFENSE`; chance `7.5 × level`%; no cause filter (verbatim — heals on fall/fire/drowning too)
- **decomposition:**
  1. `MODIFY_HEALTH(amount=1, mode=give, who=@Self, wait=1)` — 1-tick deferral so the heal lands after the damage, as measured
- **interactions:** heroic upgrade `Divine Enlighted` (doc 06).
- **strings:** none.
- **numbers:** chance 7.5/15/22.5%; heal 1.0 HP flat; the measured defensive level-clamp (`level > 3 → 3`) is unreachable through the port's level model. Single-pass per D-001.
- **era:** none.

### Frozen (`enchants/frozen`)

- **codex:** `01-enchants-armor-a-l.md § Frozen`
- **activation:** trigger `DEFENSE`; chance `4 × level`%; condition `%damagecause% == "ENTITY_ATTACK"`
- **decomposition:**
  1. `POTION(effect=SLOW, level=<level > 2 ? 2 : 1>, duration=<per level>, who=@Attacker)`
  2. `PARTICLE(particle=BLOCK_CRACK, block=DIAMOND_BLOCK, count=8, who=@Self)` + `SOUND(sound=BLOCK_STONE_BREAK, volume=1, pitch=1)` — verbatim diamond-block cue (not ice; copy-paste in the original, kept)
- **interactions:** retired in the original (on the retired list: excluded from random rolls/mystery books but still functional on items that already carry it) — record in the rarity/roll layer, not here.
- **strings:** none.
- **numbers:** chance 4/8/12%; Slowness I at L1–2, Slowness II at L3; measured duration random `(randInt[0, level-1] + level) × 20`t → L1 exactly 20t, L2 40–60t, L3 60–100t. Shipped fixed top-of-range 20/60/100t — randomized effect durations are not expressible (D-01-08).
- **era:** as Armored (block-crack cue mapping).

### Gears (`enchants/gears`)

- **codex:** `01-enchants-armor-a-l.md § Gears`
- **activation:** trigger `PASSIVE` (boots only); no condition
- **decomposition:**
  1. `POTION(effect=SPEED, level=<level>, duration=maintained-while-worn, who=@Self)`
- **interactions:** whitelisted but boots-only; potion-registry reconciliation (an Ender Shift Speed IV–VI window outranks it, then it re-asserts). The original's dead "suppress the [+] message for Gears IV" branch is unreachable (max 3) — documented, not ported.
- **strings:** shared potion-registry `[+]`/`[-]` lines (see Anti Gravity).
- **numbers:** Speed I/II/III at L1/2/3, permanent while worn; unusually cheap table thresholds (7/13/19) are enchant-table plumbing.
- **era:** none.

### Ghost (`enchants/ghost`)

- **codex:** `01-enchants-armor-a-l.md § Ghost`
- **activation:** none portable — the class only publishes a metadata level; nothing in the decompiled corpus reads it (codex UNRESOLVED).
- **decomposition:** none (inert in corpus). If an external consumer is ever identified, the publisher maps to `PASSIVE` + `SET_VAR(name=ghost.level, value=<level>)`; until then the entry ships no behavior and the item exists for parity/economy only.
- **interactions:** UNRESOLVED external consumer — port decision required before shipping any effect.
- **strings:** none.
- **numbers:** metadata value = level (1–3); no other level behavior exists.
- **era:** n/a.

### Glowing (`enchants/glowing`)

- **codex:** `01-enchants-armor-a-l.md § Glowing`
- **activation:** trigger `PASSIVE` (helmets only); no condition
- **decomposition:**
  1. `POTION(effect=NIGHT_VISION, level=1, duration=maintained-while-worn, who=@Self)`
- **interactions:** potion-registry; Ender Shift's short Night Vision does not displace the permanent one.
- **strings:** shared potion-registry `[+]`/`[-]` lines.
- **numbers:** single level, Night Vision I permanent while worn.
- **era:** the name says "Glowing" but the effect is Night Vision — the 1.9+ GLOWING outline is NOT meant (the original targets 1.7); do not "modernize" it on any lane.

### Guardians (`enchants/guardians`)

- **codex:** `01-enchants-armor-a-l.md § Guardians`
- **activation:** trigger `DEFENSE`; condition `%damagecause% == "ENTITY_ATTACK" && %victim.type% == "PLAYER"` (direct player melee only — projectiles never summon, verbatim); chance (see numbers); ability cooldown 200t (10 s)
- **decomposition:**
  1. `GUARD(type=IRON_GOLEM, count=<level == 10 ? 2 : 1>, ttl=600, name="§b§l{player}'s Guardian")` — targets the attacker, ally-safe, auto-removed (engine GUARD semantics)
  2. `PARTICLE(particle=SPELL, count=45, spread=1, who=@Self)` ; `SOUND(sound=ENTITY_IRON_GOLEM_DEATH, volume=1.0, pitch=0.55)` — world-audible per spawn
- **gaps:** `GUARD_STAT_PARAMS — parameter extension to GUARD: per-spawn max health and a potion-effect loadout (SPAWN_ENTITY already has health but lacks guard retaliation/name; GUARD has name/retaliation but no health or effects); params: health, effects list; consumers: scaling guardian summons (this entry; ancestral Spirits in doc 02).`
- **interactions:** feeds `GUARDIAN_HURT` (Blood Link); Hijack (doc 05) steals guardians; Mastery Rot-and-Decay (doc 07) reads guardian ownership; mcMMO/no-loot exemptions are engine summon policy; the golem-vs-truce asymmetry in the original collapses into GUARD's single ally rule — recorded.
- **strings:** golem name, verbatim: `§b§l{player}'s Guardian`.
- **numbers:** golem HP `50 + level × 10` (60–150) and buff ladder — FIRE_RESISTANCE always; REGENERATION L4+; INCREASE_DAMAGE L6+; SPEED L8+; DAMAGE_RESISTANCE L10 (all amp 0, permanent for the golem's life) — via `GUARD_STAT_PARAMS`. Lifetime 600t; retarget cadence 20t (engine-internal); chunk cap 100 (engine summon policy). Known bug: measured summon chance is a flat 2% at every level (`0.01 + 0.05 × level` clamped by an evidently mistyped `0.02` cap; koth/duels halve it) — as-intended `min(1 + 5 × level, 20)`% with the 0.2 cap (D-01-09).
- **era:** iron golems on 1.8: NoAI/targeting quirks (datawatcher 15) — GUARD implementation must use the era shim; `IRONGOLEM_DEATH` → 1.8.9 `mob.irongolem.death`.

### Hardened (`enchants/hardened`)

- **codex:** `01-enchants-armor-a-l.md § Hardened`
- **activation:** trigger `DEFENSE`; chance `20 × level`%; no cause filter (verbatim)
- **decomposition:**
  1. `DURABILITY(amount=1, target=armor, mode=restore, who=@Self)` — the original repaired the single most-damaged piece by 1; the engine's armor-target restore is the matching primitive (implementation picks the repair policy; recorded)
  2. `PARTICLE(particle=SPELL, count=4, who=@Self)` — at feet
- **interactions:** none.
- **strings:** none.
- **numbers:** chance 20/40/60%; repair 1 durability point flat; no cooldown — near-immortal armor vs chip damage but cannot out-repair a real fight (measured design). Single-pass per D-001.
- **era:** none.

### Heavy (`enchants/heavy`)

- **codex:** `01-enchants-armor-a-l.md § Heavy`
- **activation:** trigger `DEFENSE`; condition `%victim.helditem% == "BOW" && %damage% > <floor>` (floor `1 / (1 - reduction)`: 1.0204/1.0417/1.0638/1.0870/1.1111)
- **decomposition:**
  1. `PARTICLE(particle=BLOCK_CRACK, block=EMERALD_BLOCK, count=8, who=@Self)` + `SOUND(sound=BLOCK_STONE_BREAK, volume=1, pitch=1)` — world-effect 2001, block id 133
  2. `DAMAGE_MOD(side=defense, mode=add, amount=<2 × level>)`
- **interactions:** IS in the stacking whitelist — per-piece interaction rule; four L5 pieces compound `0.90^4` = 34.39% total bow reduction (measured; exceeds Armored's despite the lower tier — verbatim). Attacker-held-bow check at damage time (a swap mid-flight evades it) — the port keys on the attacker's held item at resolution, matching.
- **strings:** none.
- **numbers:** reduction 2/4/6/8/10% per level; skip-below-1.0 guard as Armored.
- **era:** emerald block exists on 1.8.9 (1.3+); cue mapping as Armored.

### Immortal (`enchants/immortal`)

- **codex:** `01-enchants-armor-a-l.md § Immortal`
- **activation:** trigger `DEFENSE`; every damage event (no chance); two blocks — funded and out-of-souls
- **decomposition:**
  1. (funded) `REMOVE_SOULS(amount=<max(1, 5 - level)>)` — must gate the rest of the sequence (gap below)
  2. `DURABILITY(amount=2, target=armor, mode=restore, who=@Self)` — all worn pieces +2, matching measured
  3. `DURABILITY(amount=1, target=armor, mode=damage, who=@Attacker)` — when the attacker is a player (the measured slot-matched-to-defender's-piece choice collapses into the armor target; recorded)
  4. (funded, every 20th soul) `MESSAGE` block (see strings) + `SOUND(sound=ENTITY_PLAYER_LEVELUP, volume=1.0, pitch=0.65)`
  5. (out of souls, throttle 300t) `MESSAGE(text=…)` ; `PARTICLE(particle=LAVA, count=20, who=@Self)` ; `SOUND(sound=ENTITY_ITEM_BREAK, volume=0.7, pitch=0.4)`
- **gaps:** `SOUL_COST_GATE — ability-level soul cost that gates activation on the payer's available souls (charge-or-abort) and exposes a %souls% condition fact for threshold feedback; params: amount; consumers: soul-powered sustain (this entry) and the soul family in doc 07. REMOVE_SOULS alone is documented as a no-op without soul mode and cannot abort the remaining effect sequence or drive the out-of-souls branch.`
- **interactions:** Soul Trap (doc 04) blocks Immortal — interaction rule `SUPPRESS(scope=ENCHANT, key=immortal)` while soul-trapped; souls ride the engine SoulPool; CosmicOutposts/FactionUpgrades soul-cost discounts are external economy, out of scope (measured tables recorded in the codex).
- **strings:** every-20-souls block, verbatim (four lines): empty line, `§6§l** IMMORTAL **`, `§7You have §n{souls}§7 souls left.`, empty line. Out of souls, verbatim: `§c§l** OUT OF SOULS **`.
- **numbers:** soul cost `5 - level` = 4/3/2/1, floored at 1; repair +2 per worn slot per event; attacker armor damage −1; out-of-souls message throttle 15 s. The measured %-20 message check runs after deduction and can step over multiples (message often never shows) — the port checks the post-deduction total crossing a 20-boundary; behavior-equivalent feedback, no ledger row (feedback only). The original bypassed every dispatch gate (tier-6 in the End included) — standard gates apply per shared rulings.
- **era:** souls/gems are engine features on both lanes; `LEVEL_UP` → 1.8.9 `random.levelup`.

### Implants (`enchants/implants`)

- **codex:** `01-enchants-armor-a-l.md § Implants`
- **activation:** two `REPEATING` blocks (helmet worn): food every `repeat=<P>`; health every `repeat=<2P>`
- **decomposition:**
  1. (food) condition `%actor.food% < 20` → `MODIFY_FOOD(amount=1, mode=give, who=@Self)`
  2. (health) `MODIFY_HEALTH(amount=1, mode=give, who=@Self)`
- **interactions:** heroic counterpart `Alien Implants` (doc 06); helmet-only.
- **strings:** none.
- **numbers:** measured periods 120/40/40t (`40 × (3 / level)` integer division — L2 and L3 identical, L1 3× slower) — as-intended 120/60/40t (D-01-10). Health cadence = every other food tick (240/120/80t as-intended). Known bug: the measured heal is skipped whenever it would reach *or exceed* max health (a strict below-max guard), so the wearer stalls one point below max forever — as-intended: heal clamps to max (D-01-11).
- **era:** none.

### Leadership (`enchants/leadership`)

- **codex:** `01-enchants-armor-a-l.md § Leadership`
- **activation:** trigger `ATTACK`; no chance
- **decomposition:**
  1. `DAMAGE_SCALE(side=attack, mode=add, per=10, cap=75, who=@AOE{r=<level × 2 + 3>, filter=ALLIES})` — +10% outgoing per nearby ally, capped at +75%, resolved live per hit
- **interactions:** ally filter carries the Factions both-directions ally-or-member rule; the original's koth/`cosmic-station*` half-rate (`per=5`) is a server-world policy — expressible later via `%actor.world%` conditions, not shipped; the snow-block scan particle was tied to the original's 6 s cache task and has no live-resolution equivalent (dropped, cosmetic).
- **strings:** none.
- **numbers:** level sets only the ally radius 5/7/9/11/13 (cube half-extent measured, spherical here); multiplier by ally count `min(1.75, 1 + 0.1 × allies)` — cap reached at 8 allies; the wearer never counts themselves. Cadence: measured refreshed the count every 120t and cached it; the port resolves per hit (strictly fresher — no ledger row, the cached value was an implementation artifact). Known bug: the measured buff survives unequip indefinitely (metadata never cleared — a standing exploit) — as-intended: only while the enchant is worn (D-01-12).
- **era:** none.

### Lifebloom (`enchants/lifebloom`)

- **codex:** `01-enchants-armor-a-l.md § Lifebloom`
- **activation:** trigger `DEFENSE`; condition `%posthit.health% <= 0` (gap fact, see Death God); ability cooldown 200t (10 s)
- **decomposition:** (the wearer's death is NOT prevented — no `CANCEL`, verbatim; a pure death-rattle for allies)
  1. `MODIFY_HEALTH(amount=100, mode=give, who=@AOE{r=<radius>, filter=ALLIES})` — full heal, clamped to max
  2. L4+: `POTION(effect=ABSORPTION, level=<level - 3>, duration=120, who=@AOE{r=<radius>, filter=ALLIES})`
  3. `MESSAGE(text=…, who=@AOE{r=<radius>, filter=ALLIES})` (see strings)
  4. at the wearer: `SOUND(sound=ENTITY_EXPERIENCE_ORB_PICKUP, volume=2.0, pitch=0.75)` (world-audible) ; `PARTICLE(particle=RED_DUST, count=65, who=@Self)` — at each ally: `SOUND(…, volume=1.5, pitch=0.75)` ; `PARTICLE(particle=HAPPY_VILLAGER, count=16)`
- **gaps:** `POST_HIT_HEALTH_VAR` — shared, defined at Death God.
- **interactions:** ally filter (Factions); leggings-only, not whitelisted. The measured cooldown burns even when no ally is in range (stamped before the scan) — reproduced by the ability cooldown arming on activation.
- **strings:** to each healed ally, verbatim: `§a§l(!) §a{player}'s Lifebloom was activated, fight on!`.
- **numbers:** radius `(int)(level × 2.5)` = 2/5/7/10/12 (truncations verbatim); full heal level-independent; Absorption I/II at L4/L5 for 120t; cooldown 10 s. Lethality predictor: same armor-model bug family as Ender Shift — as-intended true post-mitigation lethality (D-01-14). Single-pass per D-001.
- **era:** `ORB_PICKUP` → 1.8.9 `random.orb`; `RED_DUST`/`HAPPY_VILLAGER` legacy particle names — resolver aliases.

### Lucky (`enchants/lucky`)

- **codex:** `01-enchants-armor-a-l.md § Lucky`
- **activation:** trigger `DEFENSE`; condition `%posthit.health% <= 0` (gap fact); chance (see numbers); no cooldown (consecutive-hit saves possible, verbatim)
- **decomposition:**
  1. `CANCEL()`
  2. `PARTICLE(particle=CLOUD, count=30, who=@Self)` — at feet
- **gaps:** `POST_HIT_HEALTH_VAR` — shared, defined at Death God.
- **interactions:** heroic counterpart `Infinite Luck` (doc 06); not whitelisted.
- **strings:** none.
- **numbers:** measured chance `(level + 1)/400` = 0.50–2.75% across L1–10 (the inclusive comparison against a 0-based 400-outcome roll adds one outcome) — as-intended `level/400` = 0.25–2.50% (D-01-13); save is total negation, level-independent; predictor bug family as Ender Shift (D-01-14). Single-pass per D-001.
- **era:** none.

### Nimble (`enchants/nimble`)

- **codex:** `01-enchants-armor-a-l.md § Nimble`
- **activation:** trigger `EXP_GAIN` (boots worn)
- **decomposition:**
  1. `EXP_MULTIPLY(factor=<1.0 + 0.1 × level>)`
- **interactions:** the original multiplied **mcMMO Acrobatics** XP only; mcMMO is outside the port, so the decomposition transplants the bonus onto vanilla XP gain — a scope substitution recorded here (not a ledger row: the measured referent does not exist in the port).
- **strings:** none.
- **numbers:** ×1.1/×1.2/×1.3/×1.4/×1.5 at L1–5; read live from the worn boots (no gates bypassed in the port).
- **era:** none.

---

## Gap index (this doc)

| Gap | Consumers here |
| --- | --- |
| `PROXIMITY_EVENT_TRIGGER` | Avenging Angel (player-death), Blood Lust (tagged-effect-application) |
| `POST_HIT_HEALTH_VAR` | Death God, Ender Shift, Lifebloom, Lucky |
| `GUARD_STAT_PARAMS` | Guardians |
| `EVENT_DAMAGE_FRACTION` | Creeper Armor (as-intended L3 heal only), Ender Walker (DoT-conversion heal, fraction 1.0) |
| `VAR_SCALED_PARAM` | Blood Lust (bleed-stack scaled heal; gap defined in doc 07) |
| `SOUL_COST_GATE` | Immortal |

## Proposed deviation rows (for `deviations.md`)

| Proposed id | Item | Measured → intended |
| --- | --- | --- |
| D-01-01 | Aegis | reduction only for never-tracked attackers, set never seeded first window → reduce any attacker once unique count > 8−level |
| D-01-02 | Angelic | L5 third pulse on 37.5% duration roll → deterministic third pulse |
| D-01-03 | Avenging Angel | 20 s cooldown (map expiry; stored 10 s never read) → 10 s |
| D-01-04 | Creeper Armor | L3 heal always 0.0 (reads zeroed damage) → min(6, 10% of raw damage) |
| D-01-05 | Destruction | periods 300/120/60/60/60t (int div) → 300/150/100/75/60t |
| D-01-06 | Destruction | vanished/spectator bystander aborts the whole pass → skip only that target |
| D-01-07 | Diminish | cap persists forever; base←final over-delivery → bounded DAMAGE_CAP window at stored value |
| D-01-08 | Frozen | random slow duration (level..2·level−1 s) → fixed 1/3/5 s |
| D-01-09 | Guardians | summon chance flat 2% (0.02 clamp) → min(1+5·level, 20)% |
| D-01-10 | Implants | periods 120/40/40t (int div, L2==L3) → 120/60/40t |
| D-01-11 | Implants | heal stalls at maxHealth−1 → heal clamps to max |
| D-01-12 | Leadership | ally buff survives unequip forever → worn-only |
| D-01-13 | Lucky | save chance (level+1)/400 → level/400 |
| D-01-14 | Ender Shift / Lifebloom / Lucky | home-grown lethality armor model (dead at armor ≥ 25) → true post-mitigation lethality |
| D-01-15 | metadata-keyed family (Avenging Angel, Blood Link, Blood Lust, Clarity, Deathbringer, Ghost, Leadership) | last-equip-wins / unequip-any-disables → WornState highest-worn-level |
