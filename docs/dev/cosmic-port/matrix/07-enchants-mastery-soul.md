# Matrix 07 — Mastery enchantments (16 items)

Source codex: `07-enchants-mastery-soul.md`. Behavioral authority is the local-only
codex; entries never quote decompiled code. Values are the measured jar numbers;
codex-marked bugs also state the as-intended value (ledger rows pending in
`deviations.md`).

## Family-wide rules (recorded once, referenced by entries)

- **Offence dispatch (codex §1.6a):** mastery ATTACK-side procs fire only on
  player-vs-player hits, off the wearer's four armor pieces. With the same mastery
  on several pieces the **lowest** level procs (measured: the minimum level
  across the worn pieces); the engine's WornState dedupe must be configured to
  match or get an owner ruling — recorded here, not a gap.
- **RIFT mask negation:** a victim wearing the RIFT mask negates any offensive
  mastery proc 50% of the time — interaction-layer rule (masks ↔ mastery group),
  authored as a YAML condition, never per-enchant.
- **Dragon Slayer reflect/negate:** `mastery_reflect_enchant = 10` gives, per
  offensive proc at level ≤ 10: reflect 10.01% (roles swapped), else negate
  25.99% (integer-division `i/3` shape, codex §1.6a). Authored on the armor-set
  side (doc 10) against the mastery GROUP.
- **Defensive double-fire:** masteries whose defence hook ignores the attacker
  (Death Pact defence, Permafrost) apply twice per melee hit in the jar. The
  matrix records SINGLE-PASS intended values (`00-MECHANICS.md` §3, `D-001`).
- **Silence:** the jar's `noDefenseProcs` flag (written by Tombstone and the
  Silence sword enchant) is absolute on the melee/projectile path but only 50%
  effective on the generic-damage path. Port: one uniform `SUPPRESS` window
  (single-pass reading, `D-001` family).
- **Geometry:** codex scan boxes are asymmetric `[−r, +r)` cubes; engine
  selectors/shapes are symmetric. Recorded once (ledger note), not per entry.
- **Presentation:** all 16 are tier-8; lore line renders `§4<Name> <Roman>`.
  Group "Default" (a mastery never conflicts with anything); not Heroic; immune
  to black-scroll extraction; applied to locked kit armor only via the Mastery
  Shard hand-off (economy/config layer, out of the effect matrix).

---

## Entries

### Auto Sell (`mastery/auto-sell`)

- **codex:** `07-enchants-mastery-soul.md § Auto Sell`
- **activation:** trigger `MINE` — verified against the engine: breaking a block
  dispatches `MINE` (`BREAK` is the held-item-breaks trigger), so the authored
  `MINE` is correct (tools family; the jar also reaches this from block-damage
  and silently ignores it — not ported); interaction-layer yield conditions
  below; only fires when the first drop has a configured sell price > 0,
  otherwise the block breaks normally.
- **decomposition:**
  1. `BREAK_BLOCK(drops=false)` `at=@Here` — block cleared, nothing drops (the
     jar sets the block to air and grants the vanilla break XP directly; the
     engine keeps the break's own XP).
  2. [gap] `SHOP_SELL(price-table=shop, first-drop-only=true, per-item=false)` —
     credit the sell price (measured: unit price of the first drop, ×1).
- **gaps:** `SHOP_SELL — credit the actor the configured sell price of the
  trigger block's drops from a material(+data)-keyed price table, optionally
  clearing the drops; params: price-table handle, first-drop-only BOOL, per-item
  BOOL (multiply by drop count), grant-xp BOOL; consumers: block-sell tools
  (Auto Smelt / Detonate / Atomic Detonate / Telepathy in doc 06 read the same
  table). No existing primitive: MODIFY_MONEY is a static amount; nothing reads
  a price table.`
- **interactions:** yields to a held item carrying Detonate or Atomic Detonate
  (those sell their own output); yields to Auto Smelt when the block is
  IRON_ORE/GOLD_ORE (Auto Smelt sells the smelted output) — both authored as
  interaction-layer conditions, not folded into the gap.
- **strings:** none — completely silent (no message, sound, or particle).
- **numbers:** level-independent (`max = 1`). Measured payout = first drop's
  unit price, **not** multiplied by stack size and ignoring further drops
  (codex-marked bug) → intended Σ(price × amount) over all drops — ledger.
  Two impossible negative-drop-count guards and the duplicated drop lookup are
  non-behavioral.
- **era:** none — all 1.8.9-era tools/materials (legacy GOLD_*/WOOD_* names via
  the resolver aliases).

---

### Chain Lifesteal (`mastery/chain-lifesteal`)

- **codex:** `07-enchants-mastery-soul.md § Chain Lifesteal`
- **activation:** trigger `ATTACK`; condition `%victim.type% == PLAYER`
  (family PvP rule); chance `5·L %`.
- **decomposition:**
  1. `MODIFY_HEALTH(amount=d1, mode=transfer)`
     `who=@AOE{r=d0, filter=ENEMIES, limit=i}` — AOE is centered on the victim
     and includes them (measured adds the victim to its own chain); `transfer`
     drains the target raw (no damage event, armour bypassed — matches the jar's
     direct health write) and heals the activator. `d1` is re-rolled per target →
     RANDOM_AMOUNT gap.
  2. `PARTICLE(particle=BLOCK_CRACK, block=REDSTONE_BLOCK, count=10)` +
     `SOUND(sound=BLOCK_STONE_BREAK, volume=1, pitch=1)` per drained target
     (world-effect 2001 with REDSTONE_BLOCK equivalent).
- **gaps:**
  - `RANDOM_AMOUNT — numeric effect params authored as a uniform [min,max)
    range rolled per target application; params: min, max; consumers: per-target
    random drains/heals across the corpus.`
  - `LETHAL_CANCEL — while applying a multi-target effect list, stop the
    remaining targets and CANCEL the triggering damage event when a target dies
    to the effect; params: cancel-event BOOL; consumers: chain drains (measured:
    a chain kill cancels the source hit). Candidate to drop at the
    proposed-primitives bar.`
- **interactions:** NECROMANCER mask grants immunity to the primary victim and
  to every chain target (interaction layer); global effect gates (survival,
  vanish, god, PvP region) are engine-native; measured truce check compares each
  chain target to the **victim**, so an attacker's ally near the victim is
  drained — engine `filter=ENEMIES` is actor-relative (intended reading);
  family offence dispatch rules apply.
- **strings:** none (client hurt-flash + block-crack visuals only).
- **numbers:** per level 1–5 — chance 5/10/15/20/25%; chain radius
  `d0 = 1+⌊L/2⌋` = 1/2/2/3/3; chain cap measured `i` = 1/2/2/3/3
  (`ceil` over integer division is a no-op, codex-marked) → intended
  `1+ceil(L/2)` = 2/2/3/3/4 — ledger; drain/heal per target
  `d1 ∈ [0.5, 0.5+L)` HP. Measured heal goes to the **hit victim**, not the
  wearer (bytecode-verified bug) → intended: heal the wearer (`transfer` does
  exactly this) — ledger. Under the intended heal the victim's self-slot becomes
  a real drain instead of the measured net-zero.
- **era:** REDSTONE_BLOCK and the block-crack effect exist in 1.8.9; sound
  handle needs the legacy name mapping (`dig.stone`).

---

### Death Pact (`mastery/death-pact`)

- **codex:** `07-enchants-mastery-soul.md § Death Pact`
- **activation:** two abilities. Offence: trigger `ATTACK`, condition
  `%victim.type% == PLAYER`, no chance gate (fires every qualifying hit).
  Defence: trigger `DEFENSE`, no chance gate.
- **decomposition:**
  1. (offence) `DAMAGE_MOD(side=attack, mode=add, amount=+d3_off)` where
     `d3_off = clamp((2+2L)·10·(1−%actor.healthpercent%/100), 0, 25)` →
     VAR_SCALED_PARAM gap (intended sign: bonus; measured jar reduces own
     damage — ledger).
  2. (defence) `DAMAGE_MOD(side=defense, mode=add, amount=d3_def)` where
     `d3_def = clamp(2L·10·missing, 0, 50)` → VAR_SCALED_PARAM gap.
- **gaps:** `VAR_SCALED_PARAM — author a numeric effect/flow param as
  clamp(a·%var% + b, lo, hi) over the existing condition-var vocabulary,
  evaluated at activation; params: var, a, b, lo, hi; consumers: missing-health
  scaled damage mods (here) and health-scaled proc chances (Feign Death).`
- **interactions:** offence sits behind the family dispatch (RIFT mask,
  reflect/negate); defence is silenced by the shared defensive `SUPPRESS` window
  (Tombstone / Silence); when both parties wear it the offence modifier applies
  before the victim's defence modifier (jar priority order — engine fold order
  note); defensive double-fire → single pass (`D-001`).
- **strings:** none whatsoever (silent at both ends).
- **numbers:** offence `d1` per level 1–3 = 4/6/8 (0.4/0.6/0.8 pp per 1%
  missing), cap 25% — measured multiplier `×(1−0.01·d3)` on the wearer's own
  outgoing hit (codex-marked sign inversion) → intended `×(1+0.01·d3)` — ledger.
  Defence `d1` = 2/4/6 (0.2/0.4/0.6 pp per 1% missing), cap 50% — cap
  unreachable at L1–2 (max 20%/40% at 100% missing). Measured defence fires on
  **every** damage cause (fall, fire, void…) — codex-marked → intended: combat
  damage only (`DEFENSE`) — ledger. Full health = ×1.0 both halves.
- **era:** none.

---

### Demonic Gateway (`mastery/demonic-gateway`)

- **codex:** `07-enchants-mastery-soul.md § Demonic Gateway`
- **activation:** trigger `DEFENSE`; condition `%victim.type% == PLAYER`
  (attacker must be a player); chance `1.5 + 0.5·L %`; ability cooldown 200t
  (the jar's 10 s per-caster lockout; measured arms only when ≥ 1 crystal
  actually spawned — gate the cooldown on placement success, CAGE-style).
- **decomposition:**
  1. [gap] `TURRET_RING(type=ENDER_CRYSTAL, count=i, ring-radius=d3, ttl=20·d1,
     acquire-range=d7, initial-delay=30t, period=8..13t,
     projectile=WITHER_SKULL, projectile-speed=0.05+0.0025·L,
     spawn-sound=ENTITY_GHAST_SHOOT{3.0,0.9},
     spawn-visuals=MOBSPAWNER_FLAMES+lightning-effect,
     despawn-particle=SPELL_WITCH{speed 0.75, count 16})` — invulnerable,
     explosion-cancelling crystals on open PvP-enabled ground; skull hits fire
     the actor's `IMPACT` abilities (FALLING_BLOCK's contract).
  2. `MESSAGE(text=broadcast, channel=chat)` `who=@AOE{r=24, filter=ENEMIES}`.
  3. (IMPACT ability, fired per skull strike) `IGNORE_ARMOR()` +
     `DAMAGE(percent-of-max=5)` — 5% of the struck player's max health (jar:
     raw health write, armour bypassed);
     then chance `6+3·L %`: `FREEZE(duration=20, dot=0, slow=100)` +
     `MESSAGE(trap string)` `who=@Victim`; then condition `%distance% < 4`:
     `VELOCITY(mode=away, strength=1.1)` +
     `SOUND(sound=ENTITY_WITHER_SHOOT, volume=1.0, pitch=1.1)`.
- **gaps:** `TURRET_RING — summon N invulnerable stationary entities placed on
  open ground in a ring around the actor for T ticks (placement PvP-region
  gated, skipped sites logged); each acquires the nearest eligible visible enemy
  within acquire-range and fires a configured slow projectile on a jittered
  period, the first volley only after an initial arming delay; a projectile
  striking a player fires the actor's IMPACT abilities on them; turret entities
  are damage- and explosion-immune and never grief blocks; despawn visuals on
  expiry; params: entity type, count, ring radius, ttl, acquire range, initial
  delay, period range, projectile type/speed, spawn/despawn sound+particle;
  consumers: stationary summon artillery (this doc; doc 10 armor-set turrets).`
- **interactions:** truce/ally exclusion on broadcast and targeting
  (`filter=ENEMIES`); trap skipped when the target is already fully slowed
  (FREEZE refresh semantics cover); crystals immune to all damage (gap
  contract); family proc-veto event applies at spawn and per skull hit.
- **strings:** broadcast
  `§2§l** DEMONIC GATEWAY {level-roman} (§a{attacker}§2§l) **`; trap
  `§c§l* DEMONIC GATEWAY TRAP [by: §7{attacker} ({seconds}s)§c§l] *`.
- **numbers:** per level 1–6 — chance 2.0/2.5/3.0/3.5/4.0/4.5%; crystals
  `i = min(2+⌊L/2⌋, 5)` = 2/3/3/4/4/5; ring radius `d3` = 7/7/7/8/8/8 (the
  `max/2` term is a constant 3 — measured, kept); lifetime
  `d1 = min(2.5·L, 20)` s = 2.5/5/7.5/10/12.5/15 (50–300t); acquire range
  `d7 = 5+L` = 6–11; skull speed mult 0.0525–0.0650; refire 8–13t; skull hit =
  5% max health (armour-bypassing); trap chance `0.06+0.03·L` = 9–24%; trap
  duration measured **1 s at every level** (`(int)` truncation, codex-marked
  bug) → intended `min(5, 1+0.125·L)` s — ledger; knockback 1.1 within 4 blocks
  of the firing crystal.
- **era:** ENDER_CRYSTAL + WITHER_SKULL exist in 1.8.9; sound handles need
  legacy mapping (GHAST_FIREBALL / WITHER_SHOOT); MOBSPAWNER_FLAMES effect and
  lightning-effect are era-safe; crystal invulnerability is NMS-flag work on
  1.8.9 (legacy overlay hazard).

---

### Discombobulate (`mastery/discombobulate`)

- **codex:** `07-enchants-mastery-soul.md § Discombobulate`
- **activation:** trigger `PASSIVE` (maintained while worn).
- **decomposition:**
  1. `SET_VAR(name=mastery.discombobulate, value=1, ttl=0)` `who=@Self` —
     armed on equip, cleared on unequip (maintained-passive semantics).
- **interactions:** none in-tree — the codex records the consumer as UNRESOLVED
  (external plugin); the flag is ported for parity only and gates nothing.
- **strings:** none.
- **numbers:** level-independent (`max = 1`; the jar stores the level, never
  reads it).
- **era:** none.

---

### Explosives Expert (`mastery/explosives-expert`)

- **codex:** `07-enchants-mastery-soul.md § Explosives Expert`
- **activation:** trigger `PASSIVE` (maintained while worn).
- **decomposition:**
  1. `SET_VAR(name=mastery.explosives-expert, value=1, ttl=0)` `who=@Self`.
- **interactions:** consumed by **Atomic Detonate** (doc 06): wearing the flag
  removes the tool-class gating of its explosion mining (pickaxe-class and
  spade-class blocks break regardless of held tool) — authored as an
  interaction-layer condition on the atomic-detonate item reading
  `%mastery.explosives-expert%`. Plain Detonate ignores the flag (measured;
  kept).
- **strings:** none.
- **numbers:** level-independent (`max = 1`, weight 1 — the lowest weight of any
  mastery, tied with Feign Death, Horrify, and Poltergeist).
- **era:** none.

---

### Feign Death (`mastery/feign-death`)

- **codex:** `07-enchants-mastery-soul.md § Feign Death`
- **activation:** trigger `DEFENSE`; conditions
  `%victim.type% == PLAYER && (%damagecause% == ENTITY_ATTACK || %damagecause% == PROJECTILE) && %damage% < %actor.health%`
  (hit must be survivable); chance `(L + 5·missingFraction) %` →
  VAR_SCALED_PARAM gap; ability cooldown 200t (10 s lockout).
- **decomposition:**
  1. [gap] `VANISH(duration=30·L t, decoy=true, decoy-ttl=30, break-hits=L,
     var=mastery.feign.vanished)` — packet-hide from all players; unequipped
     decoy corpse plays the death animation, destroyed after 30t.
  2. `MESSAGE(channel=title, text="§c§lFeign Death", subtitle="§c{seconds}")`
     `who=@Self`.
  3. `SOUND(sound=ENTITY_WITHER_SHOOT, volume=3.0, pitch=0.9)`.
  4. `MESSAGE(text=vanish chat line)` `who=@Self`; unvanish line on expiry (gap
     hook or `wait=`-delayed MESSAGE guarded on the var).
  5. (companion ability, trigger `ATTACK`, condition
     `%mastery.feign.vanished% == 1`) `MESSAGE(ghost-hit line)` `who=@Victim`.
- **gaps:**
  - `VANISH — fully packet-hide the actor from every player for a duration,
    optionally spawning an unequipped decoy that plays the death animation and
    despawns after decoy-ttl; the vanish breaks early after break-hits outgoing
    landed hits; maintains a named var while active and re-syncs for players who
    join mid-vanish; params: duration, decoy BOOL, decoy-ttl, break-hits,
    var name; consumers: stealth / fake-death procs.`
  - `VAR_SCALED_PARAM` (shared — see Death Pact).
- **interactions:** taking damage while vanished never consumes a hit
  (measured; kept in the gap contract); a re-proc replaces the previous window
  (the jar's stale-timer early-termination quirk is not replicated — the gap
  refreshes); ghost-hit message replaces the jar's faction-relation color prefix
  with plain `§f` (engine has no faction relations; ally-color mapping per the
  spec's faction ruling).
- **strings:** title `§c§lFeign Death`, subtitle `§c{seconds}`; chat
  `§4§l* Feign Death - VANISHED [{seconds}s] *`;
  `§4§l* Feign Death - UNVANISHED *`; to the victim of a ghost hit
  `§c-{damage} HP (§f{attacker}'s Ghost§c)`.
- **numbers:** per level 1–4 — base chance 1/2/3/4% plus up to +5 pp at 0%
  health (linear in missing-health fraction); vanish 1.5·L s = 1.5/3.0/4.5/6.0
  (30/60/90/120t); decoy destroyed at 30t; free hits measured `L−1` — at level
  1 the vanish ends on the **first** outgoing hit (codex-marked major) →
  intended `L` free hits — ledger; lockout 10 s.
- **era:** decoy + hide are packet work — the 1.8.9 overlay uses era spawn/
  destroy/status packets (legacy hazard); title packets pre-1.8 protocol
  differences; sound legacy name.

---

### Horrify (`mastery/horrify`)

- **codex:** `07-enchants-mastery-soul.md § Horrify`
- **activation:** trigger `DEFENSE`; condition `%victim.type% == PLAYER`;
  chance `2·L %`.
- **decomposition:** (targets `@AOE{r=32, filter=ENEMIES}` — measured cube →
  symmetric sphere per family geometry note)
  1. `FREEZE(duration=20·i, dot=0, slow=100)` — jar walk-speed 0.001 with
     proper restore (the jar's hard-coded 0.2 restore discards custom speed;
     engine FREEZE restores correctly).
  2. `POTION(effect=JUMP, level=129, duration=20·i)` — the amp-128 no-jump.
  3. [gap] `FACING_SET(mode=away, of=@Self)` — spin each target to face away
     from the wearer.
  4. `SET_VAR(name=horrify.nofall, ttl=200)` per target + [gap]
     `FALL_SHIELD(window=200)` — one-shot cancel of the target's next fall
     damage inside 10 s.
  5. `MESSAGE(horrified line)` + `SOUND(sound=ENTITY_GHAST_SCREAM, volume=1.0,
     pitch=1.4)` per target.
  6. `MESSAGE(summary)` `who=@Self` — only when ≥ 1 target was horrified.
- **gaps:**
  - `FACING_SET — set a target's yaw/pitch to face toward/away from a reference
    entity (in-place teleport); params: mode toward|away, reference; consumers:
    fear/disorient procs.`
  - `FALL_SHIELD — grant any player (not necessarily an enchant wearer) a
    one-shot cancel of their next fall damage within a window; params: window
    ticks; consumers: effects that freeze or displace enemies mid-air.`
- **interactions:** `immune_freeze` (Dragon Slayer set; also honoured by Trap /
  Ice Aspect) blocks a target with the
  `§8§l* DRAGON SLAYER [§7Horrify blocked!§8§l] *` message — measured: one
  immune target aborts the **entire** proc (codex-marked major) → intended:
  skip that target only — ledger. The jar's ally-protection cancel/un-cancel
  listener pair nets to a no-op and is not ported (its only residue — cancels
  sticking on zero-damage or PvP-off hits — is engine-native gating).
- **strings:** to each target
  `§c§l* HORRIFIED §c[§c§l{seconds}s§c] §c§l*`; to the wearer
  `§a§l* HORRIFIED {count} player(s) [§7{seconds}s§a§l] *`; immunity
  `§8§l* DRAGON SLAYER [§7Horrify blocked!§8§l] *`.
- **numbers:** per level 1–4 — chance 2/4/6/8%; duration `i = 2+⌊L/2⌋` =
  2/3/3/4 s (levels 2 and 3 identical — measured, kept); JUMP 40/60/60/80t
  amp 128; no-fall window 10 s, one-shot; radius fixed 32.
- **era:** JUMP amp-128 no-jump behaves differently across eras (it is the
  classic 1.8 trick; engine FREEZE carries the cross-version handling);
  GHAST_SCREAM legacy sound name; FREEZE's powder-snow visual is 1.17+ — the
  legacy sweep needs the fallback visual.

---

### Lava Strider (`mastery/lava-strider`)

- **codex:** `07-enchants-mastery-soul.md § Lava Strider`
- **activation:** trigger `PASSIVE` (boots slot only — the only mastery whose
  natural items are restricted to boots).
- **decomposition:**
  1. `SET_VAR(name=mastery.lava-strider, value=1, ttl=0)` `who=@Self`.
- **interactions:** none in-tree — consumer UNRESOLVED in the codex (external
  plugin, presumably lava traversal). If the port chooses to realize it,
  `WALKER(material=…, replace=…)` over lava is the natural authoring — pending
  an owner ruling; the matrix records the measured flag only.
- **strings:** none.
- **numbers:** level-independent (`max = 1`).
- **era:** none for the flag; a realized lava-walker inherits WALKER's era
  handling.

---

### Mark of the Beast (`mastery/mark-of-the-beast`)

- **codex:** `07-enchants-mastery-soul.md § Mark of the Beast`
- **activation:** trigger `DEFENSE`; condition `%victim.type% == PLAYER`;
  chance `1.5·L %`; per-target cooldown bucket = the mark duration (intended
  re-mark guard; the measured guard tests only for the key's presence, so it can
  wedge permanently on a lost removal task — ledger).
- **decomposition:**
  1. [gap] `VULNERABILITY(percent=100, duration=20·d0,
     hit-message="§c* MARK OF THE BEAST [-§c{damage}§c HP] *",
     expiry-message="§c§l* MARK OFF §c§l*")` `who=@Attacker` — the marked
     player takes double damage from **every** source (fall, fire, void
     included — measured, kept: it is the point of the mark).
  2. `PARTICLE(particle=SPELL_WITCH, count=20)` `who=@Attacker` — intended
     placement (measured plays at the defender's eyes — ledger).
  3. `MESSAGE(broadcast)` `who=@AOE{r=20+2·L, filter=PLAYERS}` — measured has
     no ally/vanish filter (everyone in range is told; kept).
  4. `MESSAGE(marked line)` `who=@Attacker`.
- **gaps:** `VULNERABILITY — target takes +percent damage from every damage
  source for duration ticks (non-stacking), with optional per-amplified-hit and
  expiry feedback messages; params: percent, duration, hit-message,
  expiry-message; consumers: universal damage-amplification marks. MARK is
  insufficient: it only boosts the actor's own hits.`
- **interactions:** ally gate is ally-only (ALLY/MEMBER in both directions) —
  TRUCE-level relations are still markable; the engine ally model folds truce
  into allies, flagged for the spec's faction-mapping ruling. The per-hit
  message reports the pre-doubling damage in the jar (half the applied value) —
  intended: report the applied damage (display-value note, no ledger row;
  string itself unchanged).
- **strings:** broadcast
  `§e§l* MARK OF THE BEAST [§7{attacker}: {seconds}s§e§l] *`; to the marked
  player `§c§l* MARK OF THE BEAST [§7{seconds}s§c§l] *`; expiry
  `§c§l* MARK OFF §c§l*`; per amplified hit
  `§c* MARK OF THE BEAST [-§c{damage}§c HP] *`.
- **numbers:** per level 1–6 — chance 1.5/3.0/4.5/6.0/7.5/9.0%; mark duration
  `d0 = ⌊L/3⌋+2` = 2/2/3/3/3/4 s; broadcast half-extent `20+2·L` =
  22/24/26/28/30/32; multiplier ×2.0 fixed at all levels.
- **era:** SPELL_WITCH particle = 1.8.9 `witchMagic` (resolver handles the wire
  name).

---

### Mortal Coil (`mastery/mortal-coil`)

- **codex:** `07-enchants-mastery-soul.md § Mortal Coil`
- **activation:** trigger `ATTACK`; condition `%victim.type% == PLAYER`;
  chance `2.5 + 1.5·L %`; per-target cooldown bucket = the effect duration
  (measured re-proc block while affected).
- **decomposition:**
  1. [gap] `POTION_AMP_REDUCE(effect=HEALTH_BOOST, amount=L+1,
     duration=20·(2+0.4·L))` `who=@Victim` — reduce the amplifier of the
     victim's (enchant-sourced) Health Boost by `L+1` for the window; health is
     clamped down to the new max immediately. `POTION_LOCK(effect=HEALTH_BOOST,
     ticks=…)` is the exact decomposition whenever the source amplifier is
     ≤ L+1 (full strip) — the gap only covers the partial-reduction case.
  2. `MESSAGE(coil line)` `who=@Victim`.
- **gaps:** `POTION_AMP_REDUCE — temporarily reduce the effective amplifier of
  a named potion effect on the target by N (re-applications during the window
  are capped at source−N; ≤ 0 means denied entirely), restoring the buff at
  expiry; params: effect, amount, duration; consumers: buff-sapping marks.
  POTION_LOCK covers only the full-strip case.`
- **interactions:** LOVER mask grants total (silent) immunity — interaction
  layer; the affected-state is also read by the Santa mask (doc 11 cross-ref);
  no expiry message, sound, or particle (measured; kept).
- **strings:**
  `§c§l* MORTAL COIL {level-roman} (§7{attacker} [{seconds}s]§c§l) *`.
- **numbers:** per level 1–5 — chance 4.0/5.5/7.0/8.5/10.0%; duration
  `2+0.4·L` = 2.4/2.8/3.2/3.6/4.0 s; amplifier reduction −(L+1) = −2…−6
  (up to −24 max HP at L5); health clamped downward only, never restored up on
  expiry (measured — the gap restores the buff, vanilla then restores max
  health; current health stays down, matching the jar). Measured suppression
  persists until the next potion refresh (duration is a minimum — codex quirk)
  → intended: exact window — ledger.
- **era:** HEALTH_BOOST exists on 1.8.9; no hazards.

---

### Permafrost (`mastery/permafrost`)

- **codex:** `07-enchants-mastery-soul.md § Permafrost`
- **activation:** trigger `DEFENSE` (measured: any damage with no gate but the
  roll, and double-fires on melee — single-pass combat reading per `D-001`);
  chance `2 + L/3 %` (true double division).
- **decomposition:**
  1. `TEMP_BLOCK(shape=FOOTPRINT, material=SNOW_BLOCK, radius=i, dy=-1,
     airOnly=false, ticks=20·j)` `who=@Self`, plus a second stamp at `dy=-2` for
     the lower of the two floor layers — a non-airOnly FOOTPRINT replaces only
     the solid ground under the feet (solid, air above), which is exactly the
     codex's freeze filter, so the airOnly objection does not arise;
     TempBlockLedger natively restores, layers overlapping placements (the
     "not already frozen" guard), blocks melt/break, and survives shutdown.
     `i` = 5 at L3+ exceeds FOOTPRINT's radius cap of 4 → TEMP_BLOCK_EXTENT gap
     (extent overrun only).
  2. `SOUND(sound=BLOCK_GLASS_BREAK, volume=3.0, pitch=1.1)` +
     `MESSAGE(permafrost line)` `who=@Self` — only when ≥ 1 block froze; 40% of
     frozen blocks also emit the snow break-effect (TEMP_BLOCK placement visual
     note).
  3. (second ability, trigger `DEFENSE`, conditions
     `%actor.groundblock% == SNOW_BLOCK && %mastery.ownedground% == 1`
     [gap var] and `%damagecause% == ENTITY_ATTACK || %damagecause% ==
     PROJECTILE`) `DAMAGE_MOD(side=defense, mode=add, amount=14+L)` +
     `POTION(effect=SLOW_DIGGING, level=2, duration=50)` `who=@Attacker`.
- **gaps:**
  - `OWNED_GROUND — boolean condition fact: the block the actor stands on is a
    temp block placed by this actor (TempBlockLedger owner lookup); consumers:
    standing-on-own-field bonuses.`
  - `TEMP_BLOCK_EXTENT — raise TEMP_BLOCK's FOOTPRINT radius cap from 4 to 5
    (an 11×11 footprint); extent only, no new semantics; params: existing;
    consumers: floor-replacement fields (Permafrost at L3+).`
- **interactions:** measured freezes only inside Factions **warzone** claims
  (silently no-ops elsewhere) — the engine has no zone-claim concept; flagged
  for the spec's faction/zone-mapping ruling. Damage reduction is keyed to the
  block's owner = the victim (attackers/teammates on the frost get nothing —
  OWNED_GROUND preserves this). No proc-veto event in the jar (family note).
  Double-fire → single pass (`D-001`).
- **strings:** `§c§l* PERMAFROST [§7{seconds}s§c§l] *`.
- **numbers:** per level 1–6 — chance 2.33333/2.66667/3.0/3.33333/3.66667/4.0%;
  radius `i = min(2+L, 5)` = 3/4/5/5/5/5; duration `j = ceil(4+L/3)` =
  5/5/5/6/6/6 s (100/100/100/120/120/120t restore); reduction `14+L` =
  15–20% (×0.85…×0.80); attacker debuff Mining Fatigue II 50t re-applied per
  hit; 40% per-block break effect; blocks restored physics-off.
- **era:** SNOW_BLOCK is era-safe; `GLASS` sound → BLOCK_GLASS_BREAK legacy
  mapping; SLOW_DIGGING → MINING_FATIGUE modern registry name (resolver).

---

### Poltergeist (`mastery/poltergeist`)

- **codex:** `07-enchants-mastery-soul.md § Poltergeist`
- **activation:** two abilities — trigger `FALL` (no gate, no roll) and trigger
  `PASSIVE` (flag).
- **decomposition:**
  1. (FALL) `CANCEL()` — total fall-damage immunity at every level.
  2. (PASSIVE) `SET_VAR(name=mastery.poltergeist, value={level}, ttl=0)`
     `who=@Self`.
- **interactions:** three consumers roll immunity off the flag (rules authored
  on the consumer entries as interaction-layer conditions reading
  `%mastery.poltergeist%`): Nature's Wrath freeze (doc 01 — measured treats any
  non-zero chance as 100% immunity), Dimensional Traveler (doc 10 — measured
  12/25/37%), Mother of Yijki (doc 10 — measured 11.5/24/36.5%). Codex-marked
  inconsistency → intended: one uniform `12.5·L %` roll everywhere — ledger.
  The jar's fall-cancel is 50%-suppressible by the silence flag on the
  generic-damage path only — port: uniform SUPPRESS interaction (`D-001`
  family note).
- **strings:** none from this item; consumers print
  `§4§l* POLTERGEIST [§7Immune: {source}§4§l] *` (recorded on their entries).
- **numbers:** fall cancel level-independent; nominal proc chance
  `12.5·L %` = 12.5/25/37.5% (levels 1–3).
- **era:** none.

---

### Rot and Decay (`mastery/rot-and-decay`)

- **codex:** `07-enchants-mastery-soul.md § Rot and Decay`
- **activation:** trigger `DEFENSE`; conditions `%victim.type% == PLAYER`
  (melee/projectile PvP); chance `5 + L %`; PvP-region gate engine-native.
- **decomposition:**
  1. [gap] `SUMMON_PURGE(radius=15, filter=not-own-or-ally-or-offline,
     particles=LARGE_SMOKE{0.3,10}+SPELL_WITCH{0.7,12})` — remove (not convert)
     foreign summons near the wearer. `CONVERT_SUMMON` is the near-miss: it
     converts instead of removing.
  2. `SPAWN_ENTITY(type=ZOMBIE, count=k, ttl=0, owner=activator,
     speed=speed-mult)` — plus custom name → SUMMON_STYLE gap (name lives on
     GUARD, speed on SPAWN_ENTITY; neither has both).
  3. [gap] `SUMMON_STRIKE_PAYLOAD(consume=true, cancel=true,
     particles=LARGE_SMOKE{0.5,20})` — an owned zombie's first melee hit on a
     player is cancelled, the zombie is consumed, and the actor's `IMPACT`
     abilities fire on the struck player.
  4. (IMPACT ability A, chance 50) `SET_VAR(name=rot.branch, value=1, ttl=1)` +
     `IGNORE_ARMOR()` + `DAMAGE(percent-of-max=10)` +
     `MESSAGE(rotted line)` `who=@Victim`.
  5. (IMPACT ability B, condition `%rot.branch% != 1`) [gap]
     `DURABILITY_PERCENT(percent=2.5, slots=first-worn, mode=damage)`
     `who=@Victim` + `SOUND(sound=ENTITY_ZOMBIE_ATTACK_IRON_DOOR, volume=3.0,
     pitch=1.5)` + `MESSAGE(decayed line)` `who=@Victim`.
  6. [gap] `PHANTOM_BLOCKS(radius=3+⌊L/3⌋, material-ally=GLOWSTONE,
     material-enemy=END_STONE, duration=20·L)` — per-viewer client-only
     overlay on qualifying surface blocks (solid, non-transparent, passable
     above, PvP-enabled); self + truce-or-better see glowstone, everyone else
     end stone; auto-revert.
  7. [gap] `STACKING_DOT(step=2, period=10, cap=min(6,L), stack-ttl=60,
     lead-in=20, message=decaying line)` — enemies standing in/on the field
     take `2·stacks` real (pipeline) damage every 10t, stacks +1 per tick up to
     the cap, 3 s stack window.
- **gaps:**
  - `SUMMON_PURGE — remove summoned entities within radius whose owner is not
    the actor (or is offline / an ally, per filter), with per-summon despawn
    particles; params: radius, filter, visuals; consumers: necromantic field
    procs. CONVERT_SUMMON converts rather than removes.`
  - `SUMMON_STYLE — unify summon styling params across GUARD/SPAWN_ENTITY
    (custom name + speed multiplier on one primitive); params: name, speed;
    consumers: named styled summons.`
  - `SUMMON_STRIKE_PAYLOAD — when an owned summon lands a melee hit on a
    player: cancel the vanilla damage, consume the summon (despawn +
    particles), and fire the actor's IMPACT abilities on the struck player;
    params: consume BOOL, cancel BOOL, visuals; consumers: one-hit courier
    summons.`
  - `PHANTOM_BLOCKS — per-viewer client-only block overlay over qualifying
    surface blocks within radius for duration: the actor and allies see
    material A, enemies material B; reverts automatically and on relog; params:
    radius, ally material, enemy material, duration; consumers: illusion
    terrain.`
  - `STACKING_DOT — per-victim ramping damage while they remain inside the
    actor's active field: damage = step × stacks each period, stacks +1 per
    tick capped at cap, stack window ttl, damage through the real pipeline,
    per-tick victim message; params: step, period, cap, stack-ttl, lead-in;
    consumers: decay/plague fields.`
  - `DURABILITY_PERCENT` (shared — see Soul Siphon).
- **interactions:** Mother of Yijki set + Yijki sword grants immunity to both
  the field DoT and the zombie payload (interaction layer); the wearer's own
  zombies carry the shared summon tag, so a later proc by another player can
  purge them (measured; kept via SUMMON_PURGE filter); measured stack counter
  is shared across all attackers per victim (quirk — kept measured, noted);
  zombies drop nothing on death; chunk-cap 50 spawn skip is an engine internal.
- **strings:** zombie name `§2§l{attacker}'s Rotting Corpse`; field tick
  `§c§l* DECAYING [§7-{damage}HP ({stacks} stacks)§c§l] *`; zombie-hit health
  branch `§c§l* ROTTED [§7-{damage}HP ({attacker})§c§l] *`; durability branch
  `§c§l* DECAYED [§7- {durability} Durability§c§l] *`. The wearer gets no
  message at all (measured; kept).
- **numbers:** per level 1–10 — chance 6/7/8/9/10/11/12/13/14/15%; field
  radius `3+⌊L/3⌋` = 3/3/4/4/4/5/5/5/6/6; zombies `k = 1+⌊L/3⌋` =
  1/1/2/2/2/3/3/3/4/4; zombie speed Speed I/I/I/II/II/II/III/III/III/III
  (SPAWN_ENTITY speed multipliers 1.2/1.4/1.6); field duration `20·L` t =
  1–10 s; DoT lead-in 20t then every 10t; stack cap `min(6, L)`; max tick
  damage `2·cap` = 2…12 HP; zombie hit: 50% → 10% of max health
  (armour-bypassing), 50% → `ceil(2.5% max durability)` off the first worn
  piece (boots-first, measured order); durability decay ignores level.
- **era:** END_STONE is `ENDER_STONE` on 1.8.9 (resolver alias); GLOWSTONE
  era-safe; ZOMBIE_METAL sound legacy mapping; per-viewer block overlay is
  packet work with 1.8-protocol block-change format (legacy overlay hazard);
  SPELL_WITCH particle wire name.

---

### Soul Siphon (`mastery/soul-siphon`)

- **codex:** `07-enchants-mastery-soul.md § Soul Siphon`
- **activation:** trigger `ATTACK`; condition `%victim.type% == PLAYER`;
  chance `4 + L %`.
- **decomposition:**
  1. `MODIFY_HEALTH(amount=2+2·L, mode=give)` `who=@Self` — unconditional heal
     once the proc fires (measured: even when both branches do nothing; kept).
  2. (condition `%victim.souls% > 0` [gap var]) [gap]
     `SOUL_TRANSFER(cap=ceil(25·L), ratio=0.5, overflow=mint-shard)`
     `who=@Victim` + `MESSAGE(soul lines)` to both parties.
  3. (condition `%victim.souls% == 0`) [gap]
     `DURABILITY_PERCENT(percent=2.5, flat-per-level=0.02·L,
     slots=random-worn, mode=damage, transfer-ratio=1/3, transfer-min=1)`
     `who=@Victim` — drain a random worn slot on the victim, repair the
     attacker's matching slot by a third (min 1) + `MESSAGE(durability lines)`
     to both parties.
- **gaps:**
  - `SOUL_COUNT_VAR — condition facts %actor.souls% / %victim.souls% (total
    souls across carried gems); consumers: soul-gated procs (also the soul-mode
    enchants, docs 03–05).`
  - `SOUL_TRANSFER — move min(victim souls, cap) souls from the victim's gems
    to the actor's first carried gem, minting a shard when they carry none;
    the actor receives floor(ratio × stolen) (the rest is destroyed); params:
    cap, ratio, overflow-mode; consumers: soul-stealing procs. REMOVE_SOULS is
    the near-miss: debit-only, soul-mode-gated, no count fact, no transfer.`
  - `DURABILITY_PERCENT — damage or restore percent-of-max durability (+ an
    optional flat per-level term, ceil) on chosen worn slots
    (random | all | first-worn | matching), clamped to remaining durability,
    with an optional transfer ratio crediting the actor's matching slot
    (min 1); params: percent, flat, slots, mode, transfer-ratio, transfer-min;
    consumers: armor-decay/repair procs (Rot and Decay, Tombstone, here).
    DURABILITY is the near-miss: flat int amounts only.`
- **interactions:** the soul economy is the same pool Soul Mode drains
  (engine SoulPool); durability branch is reachable only at exactly 0 souls
  (measured arithmetic; preserved by the two conditions); armour pushed past
  breaking destroys the piece with the item-break sound (engine durability
  semantics).
- **strings:** (verbatim — these four uniquely omit the trailing `*`)
  victim soul branch `§c§l* SOUL SIPHON [§7-{souls} Souls (§7{attacker})§c§l]`;
  attacker soul branch
  `§a§l* SOUL SIPHON [§7{victim} (+{souls} Souls)§a§l]`; victim durability
  branch `§c§l* SOUL SIPHON [§7-{durability} Durability§c§l]`; attacker
  durability branch `§a§l* SOUL SIPHON [§7+{durability} Durability§a§l]`.
- **numbers:** per level 1–4 — chance 5/6/7/8% (strict `<` roll — same
  distribution); heal `2+2·L` = 4/6/8/10 HP (raw, clamped at max); soul steal
  `j = min(max(1, victimSouls), ceil(25·L))` — cap 25/50/75/100; attacker
  gains `⌊j/2⌋` = 12/25/37/50 at cap (half destroyed — measured design, kept);
  durability drain `ceil(2.5%·max + 0.02·L)` (≈ 10/14/13/11 on diamond
  helmet/chest/legs/boots — level term swamped by the ceil at every vanilla
  max, measured); repair `max(1, ⌊drain/3⌋)`. Measured: when the attacker
  lacks the matching slot the fallback repairs the confirmed-empty slot —
  i.e. nothing (codex-marked major) → intended: repair the attacker's first
  worn slot — ledger.
- **era:** none notable; item-break sound legacy name via resolver.

---

### Tombstone (`mastery/tombstone`)

- **codex:** `07-enchants-mastery-soul.md § Tombstone`
- **activation:** trigger `DEFENSE`; condition `%victim.type% == PLAYER`;
  chance `0.02·(L/3)` (true double division).
- **decomposition:** (targets `@AOE{r=6+L, filter=ENEMIES, limit=i}`)
  1. `SOUND(sound=BLOCK_ANVIL_LAND, volume=4.0, pitch=1.9)` `who=@Self` (proc
     cue).
  2. Per target: `FREEZE(duration=60, dot=0, slow=100)` +
     `POTION(effect=JUMP, level=129, duration=60)` +
     `POTION(effect=SLOW, level=129, duration=60)` +
     `SOUND(sound=ENTITY_WITHER_HURT, volume=1.0, pitch=0.25)`.
  3. `SUPPRESS(scope=TYPE, key=defense, duration=80, mode=timed)` per target —
     silences the target's defensive procs for 4 s; blocked by the target's
     `SUPPRESS_IMMUNE` (Dragon Slayer authors `SUPPRESS_IMMUNE(chance=75)`,
     matching the jar's `immune_silence` 75% roll); on block the target still
     receives the freeze, potions, and anvil (measured; expressible —
     SUPPRESS_IMMUNE only blocks the suppression).
  4. `PARTICLE(particle=ENCHANTMENT_TABLE, count=20)` +
     `PARTICLE(particle=PORTAL, count=20)` per silenced target; expiry pair via
     `wait=80` (`PORTAL` count 30 at expiry — measured).
  5. `FALLING_BLOCK(material=ANVIL, radius=0, height=5, ttl=40)` per target
     (`ttl=40` is the engine default — port choice; the jar's anvil dies on
     landing and the codex records no ttl) +
     `SOUND(sound=BLOCK_ANVIL_LAND, volume=3.0, pitch=1.1)` — the landing anvil
     fires the actor's `IMPACT` abilities on everything it hits (engine
     contract; matches the jar hitting any eligible player in the box,
     including non-targets and overlaps), never places a block, drops nothing.
  6. (IMPACT ability) [gap] `DURABILITY_PERCENT(percent=10, slots=all,
     mode=damage)` `who=@Victim` +
     `SOUND(sound=ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, volume=5.0, pitch=0.8)` +
     `SOUND(sound=ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, volume=5.0, pitch=1.1)` +
     `MESSAGE(anvil line)` `who=@Victim`.
  7. `MESSAGE(tombstone line)` per target;
     `MESSAGE(summary)` `who=@Self` — sent unconditionally, `[0 players]`
     included (measured; kept).
- **gaps:** `DURABILITY_PERCENT` (shared — see Soul Siphon).
- **interactions:** writes the same suppression window the Silence sword
  enchant uses — one shared `SUPPRESS scope=TYPE key=defense` key coordinated
  across enchants (interaction layer); Dragon Slayer `SUPPRESS_IMMUNE(75)`;
  suppression uniform across damage paths per `D-001` (jar: absolute on melee,
  50% on generic); jar freeze-restore removes SLOW but leaves JUMP running its
  full 60t — engine FREEZE + explicit potions reproduce the observable state.
- **strings:** silence blocked
  `§c§l* SILENCE BLOCKED [§7{attacker}§c§l] *`; to each target
  `§5§l* TOMBSTONE (§7{attacker} §7[{seconds}s]§5§l) *` — the `{seconds}`
  operand is the hard-coded 80t silence, so it always reads `4` (measured;
  kept); anvil hit
  `§c§l* TOMBSTONE [§7{attacker} (-10% Armor Durability)§c§l] *`; wearer
  summary `§c§l* TOMBSTONE [§7{count} players§c§l] *`.
- **numbers:** per level 1–10 — chance 0.66667/1.33333/2.0/2.66667/3.33333/
  4.0/4.66667/5.33333/6.0/6.66667%; targets `i = 4+⌊L/2⌋` =
  4/5/5/6/6/7/7/8/8/9; radius `6+L` = 7–16; freeze 60t (3 s) level-independent;
  silence 80t (4 s) level-independent; anvil: 10% of max durability off **each**
  of the four armor slots per anvil, overlapping anvils stack (measured; kept).
  Measured `(int)` truncation zeroes the hit on items with max durability < 10
  (codex-marked) → intended minimum 1 point — ledger.
- **era:** falling ANVIL entity works on 1.8.9 (anvil-with-data falling block —
  legacy overlay hazard for block-state data); sounds ANVIL_LAND / WITHER_HURT /
  ZOMBIE_WOODBREAK / ZOMBIE_WOOD are legacy names via resolver; landing
  world-effect 2001; JUMP/SLOW amp-128 era behavior as in Horrify.

---

## Cross-cutting: Soul Mode (not a matrix item)

The codex's Soul Mode system (global toggle, per-player god-mode flag, 5-tick
drain task, per-proc soul costs) is not one of this doc's 16 items. Its
consumers are the tier-6 soul enchants (Soul Trap, Hero Killer, Sabotage,
Divine Immolation, Teleblock, Nature's Wrath, Paradox — docs 03/04/05/01),
whose matrix entries carry the per-proc costs and guards. The engine already
has the soul economy (SoulPool, `REMOVE_SOULS`); the `SOUL_COUNT_VAR` gap
declared here serves those entries too. Known Soul Mode bugs for the ledger
when those docs are written: the "per second" drain actually charges 4×/s
(5-tick task); the cost `ceil` operates on an already-truncated int (no-op);
`HEROIC_SOUL_MASTERY` level 3+ maps to ×1.0 (worse than level 2); the
Cosmonaut-outpost halving can drive the cost to a permanent 0.
