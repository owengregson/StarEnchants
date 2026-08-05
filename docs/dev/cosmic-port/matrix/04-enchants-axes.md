# Decomposition matrix — axe enchants (codex 04)

Behavioral authority: `04-enchants-axes.md` (local-only codex). 21 entries, codex order.
Primitives cited are exactly those in `docs/reference/authoring-surface.txt` at HEAD;
envelope knobs (`chance`, `cooldown`, `soul-cost`, `repeat`, one `condition:` +
`effects:` list per level, cumulative per-effect `wait:`) are the enchant schema's.
Numeric params accept `%fact%` expressions (`+ - * /` only — no functions).

Family-wide notes:

- **Single pass (D-001).** Of the 21, only Arrow Break has a damage-affecting
  defensive hook (it is in the `00-MECHANICS.md` §3.4 double-fire column); Anti Gank's
  defense pass only records attacker UUIDs. All other entries are offence-only, so
  their measured values are already single-pass.
- **Composition.** The jar composes same-swing multipliers multiplicatively at
  MONITOR, in lore order; the engine damage fold is additive. Divergence needs two
  multipliers on one swing (rare for axes) and is accepted (ledger D-04-1).

---

## Entries

### Anti Gank (`axes/anti-gank`)

- **codex:** `04-enchants-axes.md § Anti Gank`
- **activation:** trigger `ATTACK`; per-level condition
  `%recentattackers% >= (6 - <level>)`; no chance roll (100% once gated). The jar's
  defence-pass bookkeeping (unique attacker UUIDs) maps to the engine-owned
  recent-attackers store feeding `%recentattackers%` — no authored defensive row.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount="%recentattackers% * 10")` — +10% per
     distinct recent attacker (expression-valued amount, precedent
     `"%ragestacks% * 1.5"` in the shipped pack).
  2. `PARTICLE(particle=BLOCK_CRACK, block=PUMPKIN, count=8, who=@Victim)` — jar
     plays world effect 2001 (block-break) with block id 86 (PUMPKIN) at victim +1y;
     the world effect carries no count, so `count=8` is (port-chosen).
- **gaps:** `EXPR_FUNCS — min/max/clamp/floor functions in the numeric expression
  grammar (NumExpr has only + - * / and negation); params: min(a,b), max(a,b),
  clamp(x,lo,hi), floor(x); consumers: this entry's ×1.5 cap
  (min(50, %recentattackers% * 10)), Bleed's stack-step curve. Without it the +50%
  ceiling (binds only at 6+ attackers) is inexpressible.`
- **interactions:** recent-attacker tracking is shared engine state with the gank
  armor family (Aegis, `%attackerindex%`). No suppression rules.
- **strings:** none.
- **numbers:** max 4; base 20.0, interval 5.0 (XP L1=20 L2=25 L3=30 L4=35); weight 7;
  tier 5. Threshold `6 - level` unique attackers (L1=5, L2=4, L3=3, L4=2); multiplier
  `min(1.5, 1.0 + 0.1 * size)` — a function of set size, not level (2→×1.2, 3→×1.3,
  4→×1.4, 5+→×1.5). Jar window 120 t with two codex-marked lapse bugs (offence expiry
  branch `return`s so the first post-lapse hit gets no bonus; defence expiry wipes
  the set without refreshing the stamp, starving it until an offensive hit re-arms).
  As intended: a clean rolling unique-attacker window; engine store is rolling with
  WINDOW_TICKS=200 (ledger D-04-2).
- **era:** BLOCK_CRACK + PUMPKIN safe on 1.8.9.

### Arrow Break (`axes/arrow-break`)

- **codex:** `04-enchants-axes.md § Arrow Break`
- **activation:** trigger `DEFENSE`; condition `%damagecause% == "PROJECTILE"`;
  envelope `chance` per level (single-pass intended values below).
- **decomposition:**
  1. `CANCEL()` — full negation of the projectile hit (jar: `setCancelled(true)` +
     `setDamage(0)` + projectile entity `remove()`; the entity removal is implicit
     once the hit is cancelled — arrow disposition noted as cosmetic).
  2. `PARTICLE(particle=CRIT, count=16, who=@Victim)` — CRIT ×16 on the defender
     (jar: at the projectile impact point — victim-anchored is the faithful
     rendering).
  3. `SOUND(sound=ENTITY_ITEM_BREAK, volume=0.7, pitch=0.2)` — defender-local.
- **gaps:** none.
- **interactions:** member of the defensive double-fire family (D-001) — the jar
  rolls this once per pass, twice per arrow.
- **strings:** none.
- **numbers:** max 6; base 15.0, interval 5.0 (L1=15 … L6=40); weight 2; tier 4.
  Measured per-pass chance `(0.1 + 0.04*level)/2` = 7/9/11/13/15/17%; with the
  double-fire the effective per-arrow chance is `1-(1-p)^2` ≈ 13.5/17.2/20.8/24.3/
  27.8/31.1%. Codex marks the ÷2 as a quirk (the tuned expression `0.1+0.04*level`
  is never used alone) — the halving reads as hand-compensation for the double-fire.
  Single-pass intended (shipped): `0.1 + 0.04*level` = **14/18/22/26/30/34%**
  (ledger D-04-3, extends D-001).
- **era:** `ITEM_BREAK` is the 1.8-era sound name (modern `ENTITY_ITEM_BREAK`) —
  sound resolver alias needed. CRIT particle safe.

### Barbarian (`axes/barbarian`)

- **codex:** `04-enchants-axes.md § Barbarian`
- **activation:** trigger `ATTACK`; condition
  `%victim.helditem% matchesregex ".*_AXE$"` (intended; measured gate is
  `contains "_AXE"`, which also matches every pickaxe); no chance roll.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=<5 * level>)` — ×(1 + 0.05·L).
- **gaps:** none.
- **interactions:** structural mirror of Insanity (victim holding a sword). Fires
  against mobs holding axes too (no player gate) — `%victim.helditem%` covers both.
- **strings:** none.
- **numbers:** max 4; base 20.0, interval 5.0 (L1=20 … L4=35); weight 7; tier 5.
  Bonus +5/10/15/20%. Codex-marked gate bug: `contains("_AXE")` counts pickaxe
  holders as axe holders → intended: axe materials only (ledger D-04-4).
- **era:** held-item material names differ per era (1.8.9 `WOOD_AXE`/`GOLD_AXE` vs
  modern `WOODEN_AXE`/`GOLDEN_AXE`); the `_AXE`-suffix regex holds on both, but any
  exact-name list would not.

### Berserk (`axes/berserk`)

- **codex:** `04-enchants-axes.md § Berserk`
- **activation:** trigger `ATTACK`; condition `%victim.type% == "PLAYER"`; envelope
  `chance: <3 * level>`.
- **decomposition:**
  1. `POTION(effect=STRENGTH, level=2, duration=<level * 40>, who=@Self)` —
     Strength II (jar amplifier 1) on the attacker for level·2 seconds.
- **gaps:** none.
- **interactions:** none. (The jar's mob-attacker ClassCastException path has no
  engine analogue — the activator is always the enchant holder.)
- **strings:** none.
- **numbers:** max 5; base 20.0, interval 5.0 (L1=20 … L5=40); weight 7; tier 2.
  Chance 3/6/9/12/15%; duration 40/80/120/160/200 t; amplifier flat II at every
  level (codex quirk, kept as measured — only duration scales).
- **era:** potion name `INCREASE_DAMAGE` (1.8) vs modern `STRENGTH` — resolver alias.

### Blacksmith (`axes/blacksmith`)

- **codex:** `04-enchants-axes.md § Blacksmith`
- **activation:** trigger `ATTACK`; envelope `chance: <7.5 * level>`; no other gate.
- **decomposition:**
  1. `DURABILITY(amount=<2 if level==5 else 1>, target=armor, mode=restore, who=@Self)`
     — jar heals the **most damaged** armor piece (see gap).
  2. levels ≥ 3 only: `DURABILITY(amount=1, target=armor, mode=restore, who=@Self)` —
     second re-scan, may hit the same or the next-most-damaged piece.
  3. `DAMAGE_MOD(side=attack, mode=add, amount=-50)` — the unconditional ×0.5
     outgoing-damage penalty on every proc (self-nerf; kept — codex reads it as an
     intentional trade-off).
- **gaps:** `DURABILITY_PIECE_SELECT — piece-selection mode for DURABILITY
  target=armor (most-damaged | least-damaged | all); params: select enum; consumers:
  single-piece armor-repair procs. DURABILITY today addresses the armor set as a
  whole; the jar repairs exactly one piece per call, chosen by highest damage.`
- **interactions:** heroic upgrade chain → "Master Blacksmith" (progression
  metadata, tiers file; not a runtime rule).
- **strings:** none.
- **numbers:** max 5; base 17.0, interval 6.0 (L1=17 L2=23 L3=29 L4=35 L5=41);
  weight 2; tier 5. Chance 7.5/15/22.5/30/37.5%; durability restored 1/1/2/2/3;
  penalty ×0.5 on every proc at every level.
- **era:** none.

### Bleed (`axes/bleed`)

- **codex:** `04-enchants-axes.md § Bleed` + `§ Bleed system deep-dive`
- **activation:** trigger `ATTACK`; condition
  `%damage% > 0 && %victim.bleedstacks% < 20`; envelope
  `chance: <8 * level + 40>`.
- **decomposition:** (the counter itself is the gap; magnitudes map to existing kinds)
  1. `STACK_COUNTER`-write: increment victim counter `bleedstacks` (step 1, cap 20,
     no decay, cleared on death and by explicit reset).
  2. Derived slow, with `i = floor(stacks / 2)` (intended `max(1, floor(stacks/2))`
     — see numbers): player victims
     `MOVEMENT_SPEED(speed=<0.2 - 0.005 * i>, ticks=<held while stacks > 0>)`;
     mob victims `POTION(effect=SLOWNESS, level=<max(1, i/3) + 1>, duration=<i * 20>,
     who=@Victim)`. The player/mob branch and the until-cleared hold ride the
     STACK_COUNTER capability's derived-modifier hook (one-condition envelope cannot
     branch per target type inside a single ability).
  3. `PARTICLE(particle=BLOCK_CRACK, block=REDSTONE_BLOCK, count=<15 per repeat,
     max(1, i/2) repeats>, who=@Victim)` — jar world-effect 2001 with block id 152 at
     victim +1y; the world effect carries no count, so the 15-per-repeat count is
     (port-chosen).
- **gaps:**
  - `STACK_COUNTER — named per-entity bounded counter effect (params: name, step,
    cap, ttl/decay=none, clear-on-death, reset op) readable as a fact; consumers:
    Bleed and heroic Deep Bleed writes, Blessed reset, Devour and swords/Deep Wounds
    reads. SET_VAR cannot increment (value is a string set, not an accumulator) and
    carries no cap.`
  - `VICTIM_VAR_FACT — read a named var/counter of the combat victim from the
    attacker's scope as %victim.<name>% (VarStore resolves %name% against the
    activator only); consumers: this gate (%victim.bleedstacks% < 20), Devour,
    Corrupt, Hex, Soul Trap re-proc gates.`
  - `EXPR_FUNCS` (see Anti Gank) — the `floor(stacks/2)` step curve.
- **interactions:** heroic "Deep Bleed" writes the **same** counter with a different
  walk-speed coefficient (0.0075 vs 0.005) — whichever adds the stack decides the
  written curve (shared-counter rule, one counter name). Devour and swords/Deep
  Wounds read the counter. Blessed resets it (attacker-side self-cleanse — its only
  remover). armor/Blood Lust: on each stack added to a player, allied Blood Lust
  wearers within 7 blocks (attacker excluded) roll `0.2 + 0.05*j` to heal
  `max(2.0, i * 0.05 * j)` HP with DRIP_LAVA ×10 + eat sound 0.4/0.6 — owned by the
  armor matrix doc; recorded here as the counter's increment hook.
- **strings:** none (Deep Bleed's chat line belongs to the heroic doc).
- **numbers:** max 6; base 10.0, interval 8.0 (L1=10 L2=18 L3=26 L4=34 L5=42 L6=50);
  weight 2; tier 4. Proc chance `0.08*level + 0.4` = 48/56/64/72/80/88%. Cap 20 at
  every level (the level param of the jar's cap getter is dead — measured = intended,
  kept). Stack effect is level-independent: walk speed `0.2 - 0.005*i` (floor 0.15 at
  cap = −25%), mob SLOW duration `i*20` t, amplifier `max(1, i/3)` (Slowness II–IV).
  Codex-marked bugs: (a) `i = stacks/2` unfloored → the first stack is a no-op
  (walkSpeed 0.2, SLOW 0 t); intended `max(1, …)` — the one-line divergence from
  Deep Bleed (ledger D-04-5). (b) no decay and no death/quit cleanup — a bled
  player keeps the slow until Blessed procs or restart; shipped: stacks clear on
  death, still no in-life decay (ledger D-04-6).
- **era:** REDSTONE_BLOCK safe on 1.8.9; walk-speed writes safe. DRIP_LAVA / `EAT`
  sound (Blood Lust hook) are 1.8-era names — armor doc's sweep.

### Bleed (legacy — UNREGISTERED) (`axes/bleed-legacy`)

- **codex:** `04-enchants-axes.md § Bleed (legacy — UNREGISTERED)`
- **activation:** **not ported** — dead code. The unregistered legacy Bleed variant
  is never constructed; its listener never registers; the `"ce_bleeding"` subsystem
  is unreachable at runtime. Measured jar behavior is *nothing*, so nothing ships.
- **decomposition:** recorded for completeness only — it would be fully expressible:
  trigger `ATTACK`; condition `%victim.type% == "PLAYER"`; chance `7.5 * level`;
  `POTION(effect=WEAKNESS, level=<level>, duration=<2 * level * 20>, who=@Victim)` on
  the nested sub-roll (`0.025*level` of the **same** draw — a strict subset, not an
  independent roll); DoT as a cumulative `wait:` chain — first
  `DAMAGE(amount=<per-tick-dot>, who=@Victim, wait=20)` then `2*level - 1` further
  `DAMAGE(amount=<per-tick-dot>, who=@Victim, wait=35)` rows, each with
  `SOUND(sound=ENTITY_PLAYER_HURT, volume=1.0, pitch=0.75, who=@Victim)`.
- **gaps:** none (not ported; no gap minted from dead code).
- **interactions:** none — it never touches `bleedstacks`, so Devour/Deep Wounds see
  nothing from it.
- **strings:** none.
- **numbers:** max 5; base 15.0, interval 3.0 (L1=15 … L5=27); weight 2. Bleed
  chance 7.5/15/22.5/30/37.5%; Weakness chance 2.5/5/7.5/10/12.5% (subset);
  Weakness duration 40/80/120/160/200 t, amplifier `level-1`. Per-tick DoT damage
  `max(0.5, level/2)` — **integer division**: 0.5/1.0/1.0/2.0/2.0
  (the L1 and L3 scaling silently collapses; codex-marked); intervals `2*level`;
  total 1/4/6/16/20. Timer delay 20 t, period 35 t.
- **era:** `HURT_FLESH` sound is 1.8-only (modern `ENTITY_PLAYER_HURT`) — moot
  unless ever revived.

### Blessed (`axes/blessed`)

- **codex:** `04-enchants-axes.md § Blessed`
- **activation:** trigger `ATTACK`; condition
  `%victim.type% == "PLAYER" && !(%deepwounds%)` — `deepwounds` is a SET_VAR the
  swords/Deep Wounds entry writes on its VICTIM (`SET_VAR … @Victim`, `matrix/03`
  § Deep Wounds) with ttl `level * 30` t (1500 ms/level), read here from the actor's
  own store because the wounded player IS the later Blessed actor; envelope
  `chance: <3 * level>`.
- **decomposition:** (everything downstream of the roll is level-independent)
  1. `STACK_COUNTER`-reset: zero the actor's own `bleedstacks` counter and restore
     walk speed (the jar's bleed-clear routine runs first and unconditionally, even
     when there is no debuff to cleanse — order kept).
  2. `SOUND(sound=ENTITY_GENERIC_SPLASH, volume=1.2, pitch=2.0)`.
  3. `CURE(category=HARMFUL, who=@Self)` **limited to one effect type** — see gap.
     Jar debuff list (9 types): BLINDNESS, CONFUSION, HARM, HUNGER, POISON, SLOW,
     SLOW_DIGGING, WEAKNESS, WITHER ≈ the HARMFUL category; which one is removed is
     iteration-order arbitrary in the jar.
  4. `MESSAGE(text="§e§l** BLESSED **", channel=chat, who=@Self)`.
- **gaps:** `CURE_LIMIT — bound the number of effect types CURE removes per
  activation; params: limit INT (default unlimited); consumers: single-debuff
  cleanses. Sequenced REMOVE_POTION rows cannot express "exactly one of whichever
  are active" (they would strip all nine).` Plus `STACK_COUNTER` (defined at
  Bleed) — the reset op.
- **interactions:** (a) Deep Wounds veto — the blocked-proc feedback line (strings
  below) fires when the veto condition trips; authored as the deep-wounds
  interaction rule's suppression feedback, not a primitive. (b) KOTH armor set:
  wearers get the BLESSED chat line throttled to one per 160 t (8 s), cleanse
  unthrottled — interaction-layer condition against set membership. (c) resets the
  Bleed counter (sole remover). (d) the jar's custom BLESS player event (an external
  hook) is not ported.
- **strings:** `§4** DEEP WOUNDS §7(NO BLESS)§4 **` (veto line);
  `§e§l** BLESSED **` (identical in throttled and unthrottled branches).
- **numbers:** max 4; base 30.0, interval 10.0 (L1=30 L2=40 L3=50 L4=60); weight 7;
  tier 4. Chance 3/6/9/12%; exactly 1 debuff removed per proc; deep-wounds veto
  window `level * 1500` ms of the Deep Wounds writer; KOTH throttle 160 t.
- **era:** `SPLASH` is the 1.8-era sound name (modern `ENTITY_GENERIC_SPLASH`); all
  nine potion types exist on 1.8.9 (CONFUSION/SLOW/SLOW_DIGGING legacy names).

### Boss Slayer (`axes/boss-slayer`)

- **codex:** `04-enchants-axes.md § Boss Slayer`
- **activation:** trigger `ATTACK`; condition
  `%victim.type% != "PLAYER" && <boss designation> &&
  %actor.helditem% matchesregex ".*_AXE$" && <held-swap gate>`; no chance roll.
  Boss designation: the jar reads an externally-written `"boss"` flag that **nothing
  in the corpus ever writes** (measured: inert); shipped: a `%victim.type%` list over
  the vanilla bosses — `%victim.type% == "ENDER_DRAGON" || %victim.type% ==
  "WITHER"` (both resolve on 1.8.9). NOT `%victim.mobtype%`, which is the MythicMobs
  soft hook (ADR-0027): with no integration installed it resolves to the empty string
  for every entity, so a `%victim.mobtype%` list would ship the enchant exactly as
  inert as the flag it replaces. Servers running MythicMobs widen the list with
  `%victim.mobtype%` entries — `boss-slayer.yml` documents that. `matrix/11` § Boss
  Mask carries the same designation, deliberately identical so the pack has ONE.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=<7.5 * level>)`.
- **gaps:** `HELD_SWAP_GATE — a fact for ticks since the actor's held hotbar slot
  last changed (%heldticks%), for anti-hot-swap conditions (jar: > 5 ticks since
  PlayerItemHeldEvent); params: none (a fact); consumers: Boss Slayer, Hero Killer,
  Soul Trap (+ swords Sabotage / Divine Immolation in doc 03). A HELD-trigger
  SET_VAR writer cannot be authored alongside — the enchant schema binds one ability
  per level, and a trigger list shares one effects list.`
- **interactions:** shares the held-swap gate with Hero Killer / Soul Trap (one fact,
  one threshold: `> 5` ticks).
- **strings:** none.
- **numbers:** max 5; base 10.0, interval 10.0 (L1=10 … L5=50); weight 2; tier 5.
  Bonus +7.5/15/22.5/30/37.5%. Measured-vs-shipped: inert in the jar (no boss-flag
  producer) → active against configured bosses (ledger D-04-7). Held-item gate
  uses `endsWith("_AXE")` (pickaxes correctly excluded — inconsistent with
  Barbarian's `contains`, consistent with the intended-Barbarian fix).
- **era:** axe-material era naming as Barbarian.

### Cleave (`axes/cleave`)

- **codex:** `04-enchants-axes.md § Cleave`
- **activation:** trigger `ATTACK`; envelope `cooldown: 30` (jar 1500 ms attacker
  cooldown); per-target cooldown 20 t on splash victims (engine per-target cooldown
  buckets; the jar stamps a per-victim splash timestamp, 1000 ms); condition
  `%damage% > 0`; no chance roll.
- **decomposition:**
  1. `DAMAGE(amount=<level-banded splash 1|2|3>, who=@Aoe{r=<0.45 * level>, filter=ENEMIES, exclude=victim})`
     — splash to bystanders around the victim; the primary victim is never splashed
     (jar: victim is the search origin; `exclude=victim` reproduces it exactly).
- **gaps:** none.
- **interactions:** (a) heroic/Heroic Cleave shares a mutual 20 t (1000 ms)
  exclusion window (the jar's shared cleave-proc marker) — authored from the heroic
  side as
  `SUPPRESS(scope=ENCHANT, key=cleave, duration=20, who=@Self)`. (b) mcMMO Skull
  Splitter integration: not ported (external plugin; with it gone, the jar's
  cooldown-consumed-on-suppressed-hit quirk has no analogue). (c) ally protection:
  jar spares mutual faction allies/members and anyone in a duel; engine `ENEMIES`
  filter carries the port's single ally model. (d) `spectator`-flagged entities:
  engine targeting exempts spectators natively.
- **strings:** none player-visible (two jar console log lines not ported).
- **numbers:** max 7; base 15.0, interval 5.0 (L1=15 … L7=45); weight 2; tier 4.
  Radius `0.45 * level` = 0.45/0.90/1.35/1.80/2.25/2.70/3.15 (jar box half-extent;
  AOE is a radius — shape delta noted, values kept); splash damage
  `level<=3 ? 1.0 : level<=6 ? 2.0 : 3.0` = 1/1/1/2/2/2/3. Jar splash is
  attacker-less generic damage (armour-reduced, **no kill credit**, defensive-only
  re-entry per `00-MECHANICS.md` §3.5 mode B); engine DAMAGE is
  activator-attributed (ledger D-04-8).
- **era:** none (no sounds/particles).

### Confusion (`axes/confusion`)

- **codex:** `04-enchants-axes.md § Confusion`
- **activation:** trigger `ATTACK`; condition `%victim.type% == "PLAYER"`; envelope
  `chance: <7.5 * level>`.
- **decomposition:**
  1. `POTION(effect=NAUSEA, level=<level + 1>, duration=<(2 * level + 4) * 20>,
     who=@Victim)` — jar amplifier is `level` (not `level-1`), so even L1 inflicts
     Nausea II; kept as measured.
- **gaps:** none.
- **interactions:** CONFUSION is in Blessed's debuff list — a Blessed proc can strip
  it (HARMFUL category covers it). Retired in the jar's tier registry yet still
  functional — port keeps it live; retirement is progression metadata.
- **strings:** none.
- **numbers:** max 3; base 15.0, interval 5.0 (L1=15 L2=20 L3=25); weight 2; tier 1.
  Chance 7.5/15/22.5%; duration 120/160/200 t; amplifier level (Nausea II/III/IV).
- **era:** potion name `CONFUSION` (1.8) vs modern `NAUSEA` — resolver alias.

### Corrupt (`axes/corrupt`)

- **codex:** `04-enchants-axes.md § Corrupt`
- **activation:** trigger `ATTACK`; condition
  `%victim.type% == "PLAYER" && !(%victim.corrupt%)` (re-proc gate on the victim's
  flag — VICTIM_VAR_FACT); envelope `chance: <5 + 2 * level>` (intended — see
  numbers).
- **decomposition:**
  1. `SET_VAR(name=corrupt, value=<level>, ttl=<level * 40>, who=@Victim)` — the
     corrupted flag + level (`level*2` s), read by swords/Inversion.
  2. `PARTICLE(particle=PORTAL, count=20, who=@Victim)` — at eye height, jar speed
     0.6F.
  3. DoT as a cumulative `wait:` chain per level, each application
     `DAMAGE(amount=<i>, who=@Victim)` +
     `PARTICLE(particle=BLOCK_CRACK, block=REDSTONE_WIRE, who=@Victim)`:
     first application at t=0, then every `level * 20` t while the task lives
     `level^2 * 20` t — L1: t 0,20 (2 × 1.0); L2: t 0,40,80 (3 × 1.0);
     L3: t 0,60,120,180 (4 × 2.0); L4: t 0,80,160,240,320 (5 × 2.0).
- **gaps:** `VICTIM_VAR_FACT` (see Bleed) — the attacker-side re-proc gate reads the
  victim's flag.
- **interactions:** (a) a victim **holding** an Inversion item suppresses the DoT
  rows but still receives the flag + particle (jar order kept) — interaction-layer
  rule keyed on the victim's held-item enchant state. (b) swords/Inversion (doc 03)
  reads the flag + level to convert its own heal into self-damage
  (`chance level*0.2` per its entry); its `§5* CORRUPTED [§c{damage}§5 DMG] *` line
  belongs to doc 03.
- **strings:** none from Corrupt itself.
- **numbers:** max 4; base 15.0, interval 5.0 (L1=15 … L4=30); weight 2; tier 4.
  **Codex-marked bug (major):** proc roll compares `Math.random()` to
  `5.0 + 0.02*level` — always true, **100% at every level**; as intended
  `0.05 + 0.02*level` = 7/9/11/13%, matching the activation envelope's
  `chance: <5 + 2 * level>` and the package's pattern (ledger D-04-9).
  Flag duration `level*2000` ms; DoT damage 1.0 (L1–2) / 2.0 (L3–4) per application;
  totals 2/3/8/10. Jar flag-vs-task lifetime mismatch (L1 flag 2 s > task 1 s; L4
  flag 8 s < task 16 s, so L2–4 can re-flag mid-DoT with no new DoT) is reproduced
  structurally by the ttl (flag) and wait-chain (task) above; the jar's async
  final-tick cancel race has no engine analogue (wait chains are deterministic).
- **era:** PORTAL particle and REDSTONE_WIRE block safe on 1.8.9.

### Decapitation (`axes/decapitation`)

- **codex:** `04-enchants-axes.md § Decapitation`
- **activation:** trigger `ATTACK`; condition `%victim.type% == "PLAYER"`; envelope
  `chance: <3 * level>`. Death-side: the head drop fires on the flagged victim's
  death — no engine trigger runs death-side for state planted by *another* player's
  enchant (DEATH scans the dying entity's own equipment), hence the gap.
- **decomposition:**
  1. `SET_VAR(name=decapflag, value=1, ttl=0, who=@Victim)` — permanent until
     consumed (jar: flag persists until that player's next death from any cause).
  2. `PLAYER_HEAD_DROP` (gap) on the flagged victim's death: add a player-head item
     (skull owner = victim) with the templated name/lore below to the death drops;
     killer-less deaths drop the owner-only head with no lore; consume the flag.
- **gaps:** `PLAYER_HEAD_DROP — on a flagged player's death, add a player-head
  ItemStack (skull-owner = a named player) with templated display name and lore
  (killer, date, coordinates, weapon placeholders) to the drops and consume the
  flag; params: flag var name, name template, lore templates, killer-less fallback;
  consumers: trophy-head death drops. DROP_ITEM/GIVE_ITEM carry no skull owner or
  lore, and no trigger runs on the victim side for attacker-planted state.`
- **interactions:** CosmicContests console command + once-per-victim contest marker:
  external plugin, not ported. Jar quirk kept as measured: the flag is set on a
  *proc*, not a kill, so any later death (any cause, any killer) drops the head,
  lore-attributed to whoever the killer happens to be.
- **strings:** (verbatim; placeholders as brace tokens)
  <!-- markdownlint-disable MD038 -- the trailing spaces inside these code spans are load-bearing verbatim lore -->
  - display: `§fSkull of {victim}`
  - lore 1: `§7Defeated by §f{killer}§7 on ` (trailing space)
  - lore 2: `§f{month} {day}, {year}§7 at ` (trailing space; `{month}` is the full
    month name)
  - lore 3: `§f{x}, {y}, {z}§7 with a(n) ` (trailing space; the **killer's** block
    coordinates — codex verified, plausibly by design; kept)
  - lore 4: `§f{weapon}!` — the killer's held item's display name, else its
    capitalized material name, else `Fists`
  - killer-less fallback head: display only, no lore.
- **numbers:** max 3; base 30.0, interval 10.0 (L1=30 L2=40 L3=50); weight 7;
  tier 1. Chance 3/6/9%; death handler level-independent. **Codex-marked bug:** the
  lore date comes from a static Calendar captured at class load — every head names
  the server's start-up date; as intended: the date of the death (ledger D-04-10).
- **era:** the head item is `SKULL_ITEM` durability 3 on 1.8.9 vs `PLAYER_HEAD` on
  modern — the biggest era hazard in this doc; skull-owner-by-name is 1.8-safe,
  modern profiles differ. `{month}` formatting locale-dependent.

### Devour (`axes/devour`)

- **codex:** `04-enchants-axes.md § Devour`
- **activation:** trigger `ATTACK`; condition
  `%damage% > 0 && %victim.bleedstacks% > 0`; no chance roll (100% when gated).
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add,
     amount="10 * <level> + 1.5 * %victim.bleedstacks%")` — ×(1 + 0.1·L +
     0.015·stacks); the stack term is capped naturally by the counter's cap of 20.
  2. `PARTICLE(particle=BLOCK_CRACK, block=MYCELIUM, who=@Victim)` — jar
     world-effect 2001 with block id 110 at the victim's feet (no +1y, unlike Bleed).
- **gaps:** `STACK_COUNTER` + `VICTIM_VAR_FACT` (both defined at Bleed) — the read
  side of the shared counter.
- **interactions:** mutual 4 t (200 ms) exclusion with swords/Rage, authored
  symmetrically: Devour's gate also requires the victim not be rage-flagged
  (suppressed Devour consumes nothing — no particle, jar order kept), and on apply
  each fires `SUPPRESS(scope=ENCHANT, key=<other>, duration=4, mode=timed,
  who=@Self)`. swords/Deep Wounds reads the same counter with different
  coefficients and is deliberately **not** in this exclusion — it can stack with
  Devour on one hit.
- **strings:** none.
- **numbers:** max 4; base 10.0, interval 8.0 (L1=10 L2=18 L3=26 L4=34); weight 2;
  tier 5. Multiplier `1 + 0.1*level + 0.015*stacks`; at the 20-stack cap
  ×1.400/1.500/1.600/1.700 (+40/50/60/70%). Per-stack contribution flat 1.5%
  regardless of level.
- **era:** block name `MYCEL` (1.8) vs modern `MYCELIUM` — material resolver alias.

### Hero Killer (`axes/hero-killer`)

- **codex:** `04-enchants-axes.md § Hero Killer`
- **activation:** trigger `ATTACK`; envelope `soul-cost: 4`; conditions: soul system
  enabled (pack config toggle — the jar's soul-system flag, default **off**),
  `%victim.type% == "PLAYER"`, held-swap gate (`HELD_SWAP_GATE`, > 5 t),
  `!(%soultrap%)` (the actor's own trap var, written by Soul Trap — same-scope
  SET_VAR read, no gap), actor in soul mode with souls > 0 (`SOUL_STATE_FACTS`),
  victim wearing ≥ 1 Heroic Armor piece (`WORN_GEAR_FACT`); no chance roll.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=<10 * level>)`.
- **gaps:**
  - `SOUL_STATE_FACTS — expose soul-system state as condition facts on both combat
    scopes (%souls%, %soulmode%, %victim.souls%, %victim.soulmode%); consumers:
    soul-gated procs, zero-soul fallback branches. REMOVE_SOULS/soul-cost act but
    nothing can be conditioned on soul state today.`
  - `WORN_GEAR_FACT — expose worn-armor classification of actor and victim as facts
    (per-slot material/family membership, e.g. heroic-set piece count); params:
    side, slot|any; consumers: gear-gated damage bonuses. Only held-item facts
    exist; IGNORE_HEROIC acts on heroic armor but cannot gate on it.`
  - `HELD_SWAP_GATE` (defined at Boss Slayer).
- **interactions:** disabled while the attacker is soul-trapped (Soul Trap's var);
  the jar's 1000 ms soul-removal throttle is shared with Soul Trap and two
  sword enchants — replaced by the envelope's per-activation `soul-cost`
  (ledger D-04-11). Heroic-armor detection is the item model's classification, not an NBT
  probe.
- **strings:** none.
- **numbers:** max 3; base 10.0, interval 10.0 (L1=10 L2=20 L3=30); weight 2;
  tier 6. Bonus +10/20/30%; cost 4 souls. **Codex-marked bugs:** the gate requires
  only souls > 0 then removes 4 (over-draw), and the shared 1000 ms limiter makes
  same-second procs free; as intended: 4 souls per activation, clamped/gated on
  availability (ledger D-04-11).
- **era:** none (no sounds/particles; leather-material heroic check is the item
  model's concern).

### Hex (`axes/hex`)

- **codex:** `04-enchants-axes.md § Hex`
- **activation:** trigger `ATTACK`; condition
  `%victim.type% == "PLAYER" && !(%victim.hexed%)` (VICTIM_VAR_FACT); envelope
  `chance: <2 * level>`.
- **decomposition:** (durations use the jar's integer division `2 + level/2`)
  1. `SET_VAR(name=hexed, value=1, ttl=<(2 + level/2) * 20>, who=@Victim)` — the
     re-proc gate (jar expiry is implicit-by-timestamp; ttl reproduces it).
  2. `REFLECT(percent=100, duration=<(2 + level/2) * 20>, who=@Victim)` — the hexed
     player takes their own outgoing damage back, **capped flat** at
     `5 + max(0, level - 2)` per hit (cap → gap). Jar self-damage is generic
     armour-reduced damage; engine REFLECT semantics own the application.
  3. `PARTICLE(particle=SPELL_WITCH, count=20, who=@Victim)` — at eye height, jar
     speed 0.6F.
  4. `MESSAGE(text="§5§l* HEX DEBUFF [§d§l{seconds}s§5§l] *", channel=chat,
     who=@Victim)`.
  5. `MESSAGE(text="§d§l* HEX OFF *", channel=chat, who=@Victim,
     wait=<(2 + level/2) * 20>)` — fire-and-forget expiry notice, as in the jar.
- **gaps:** `REFLECT_CAP — flat per-hit ceiling on the amount REFLECT returns, with
  an optional per-hit feedback template; params: cap DOUBLE, feedback STRING;
  consumers: capped self-reflect debuffs. REFLECT today is percent-only.` Plus
  `VICTIM_VAR_FACT` (defined at Bleed).
- **interactions:** the jar's reflect listener omits `ignoreCancelled`, so
  self-damage fires even on hits other plugins cancelled (`getFinalDamage() > 0`);
  the engine applies REFLECT to landed hits only — noted, accepted. Per-hit line
  `§5* HEX [§c{damage}§5 DMG] *` (DecimalFormat `#.##`) rides the REFLECT_CAP
  feedback param.
- **strings:** `§5§l* HEX DEBUFF [§d§l{seconds}s§5§l] *` ({seconds} = 2/3/3/4/4);
  `§d§l* HEX OFF *`; `§5* HEX [§c{damage}§5 DMG] *`.
- **numbers:** max 5; base 15.0, interval 5.0 (L1=15 … L5=35); weight 2; tier 5.
  Chance 2/4/6/8/10%; duration `2 + level/2` **integer-divided** seconds =
  2/3/3/4/4 s (40/60/60/80/80 t) — kept as measured (the chat line prints the same
  integer, so the truncation reads as designed); self-damage cap
  `5 + max(0, level-2)` = 5/5/6/7/8.
- **era:** particle `WITCH_MAGIC` (1.8) vs modern `SPELL_WITCH`/`WITCH` — resolver
  alias.

### Insanity (`axes/insanity`)

- **codex:** `04-enchants-axes.md § Insanity`
- **activation:** trigger `ATTACK`; condition
  `%victim.helditem% matchesregex ".*_SWORD$"`; no chance roll.
- **decomposition:**
  1. `DAMAGE_MOD(side=attack, mode=add, amount=<2 * level>)`.
- **gaps:** none.
- **interactions:** exact structural mirror of Barbarian (`_SWORD`, 0.02, max 8 vs
  `_AXE`, 0.05, max 4). Unlike Barbarian, `contains("_SWORD")` has no false-positive
  material — measured equals intended, no ledger row.
- **strings:** none.
- **numbers:** max 8; base 20.0, interval 5.0 (L1=20 … L8=55); weight 7; tier 5.
  Bonus +2/4/6/8/10/12/14/16% — top level weaker than top Barbarian (+20%), as in
  the jar.
- **era:** sword-material era naming (1.8.9 `GOLD_SWORD` vs modern `GOLDEN_SWORD`);
  the `_SWORD`-suffix regex holds on both.

### Pummel (`axes/pummel`)

- **codex:** `04-enchants-axes.md § Pummel`
- **activation:** trigger `ATTACK`; envelope `chance: <6 * level>`; no victim-type
  gate.
- **decomposition:**
  1. `POTION(effect=SLOWNESS, level=<level>, duration=<trunc(1.5 * level) * 20>,
     who=@Aoe{r=<level>, filter=ENEMIES, exclude=victim})` — bystanders only: the
     jar searches around the victim and the victim is never in their own result;
     `exclude=victim` reproduces it exactly. Amplifier `level - 1` (Slowness
     I/II/III) = engine level 1/2/3.
  2. `PARTICLE(particle=BLOCK_CRACK, block=STONE, who=@Victim, dy=-0.4)` — the jar
     anchors every burst at the VICTIM's feet + 0.5 (`playerHit.getLocation().add(0,
     0.5, 0)`), not at the bystanders it slows, and replays it once per affected
     bystander inside the same loop. `dy: -0.4` is that point off the engine's
     body-centre anchor. The jar also uses the victim's standing-block id (fallback
     STONE when air); a dynamic block param is not expressible — STONE
     approximation, cosmetic only.
- **gaps:** the per-bystander REPEAT has no spelling — `PARTICLE`'s single `who` both
  selects the targets and supplies the anchor, so a bystander-counted repeat at a
  victim anchor cannot be written; one burst ships (D-04-15). Nothing else new (the
  Metaphysical veto is interaction-layer; the `±N %chance%` condition clause carries
  the arithmetic).
- **interactions:** armor/Metaphysical (and heroic variant) reduces the proc chance
  against its wearer by 4 percentage points per Metaphysical level and emits the
  blocked line — authored as an interaction-layer per-target veto. Jar ally test is
  Factions `TRUCE`-or-better (looser than Bleed/Cleave's mutual-ally test); the
  engine's single ally model under `filter=ENEMIES` absorbs both. Mobs get no ally
  or Metaphysical protection (jar behavior; ENEMIES includes hostile mobs).
- **strings:** `§8§l** METAPHYSICAL (§8Pummel blocked!§l) **` (emitted by the
  interaction rule on a veto).
- **numbers:** max 3; base 20.0, interval 5.0 (L1=20 L2=25 L3=30); weight 7;
  tier 3. Chance 6/12/18%; radius `level` = 1/2/3 (jar box half-extent, AOE radius —
  shape delta noted); SLOW amplifier level−1. **Codex-marked bugs:** (a) duration
  `(int)(level * 1.5)` truncates → 20/60/80 t (1/3/4 s); as intended 1.5·level s =
  30/60/90 t (ledger D-04-12). (b) the Metaphysical veto mutates the shared chance
  threshold inside the target loop, so one wearer shields later-iterated bystanders
  with no Metaphysical; as intended: per-target veto only (ledger D-04-13).
- **era:** none beyond legacy `SLOW` potion name (modern `SLOWNESS`).

### Ravenous (`axes/ravenous`)

- **codex:** `04-enchants-axes.md § Ravenous`
- **activation:** trigger `ATTACK`; condition `%actor.food% < 20`; envelope
  `chance: <5 * level>`. Jar has no damage or cancellation check — kept (any hit on
  any entity qualifies).
- **decomposition:**
  1. `MODIFY_FOOD(amount=1, mode=give, who=@Self)` — +½ shank, no saturation change,
     capped at 20 by the gate.
- **gaps:** none.
- **interactions:** none. Retired in the jar's tier registry yet functional — port
  keeps it live (progression metadata).
- **strings:** none.
- **numbers:** max 4; base 20.0, interval 5.0 (L1=20 … L4=35); weight 7; tier 2.
  Chance 5/10/15/20%; restore flat +1 at every level (level buys frequency only).
- **era:** none.

### Reforged (`axes/reforged`)

- **codex:** `04-enchants-axes.md § Reforged`
- **activation:** triggers `[ATTACK, MINE]` (jar: melee hits and block breaks — the
  jar's BlockDamageEvent path is discarded dead work, not ported); applies-to the
  weapons-and-tools group (5 swords + 5 axes + bow + 5 pickaxes + 5 hoes + **5
  spades** — **not** axes only, despite the source folder). The spades come from the
  codex's own set table (doc 15), which records `weapons_and_tools` with them (then
  the 5 axes a second time — 31 entries, 26 distinct); without them Reforged never
  lands on a shovel, so `reforged.yml` gains `SHOVEL`. Envelope
  `chance: <8 * level>`; condition:
  held item damaged and damageable (engine DURABILITY restore is a natural no-op on
  pristine/non-damageable items — jar's `getDurability() != 0 &&
  getMaxStackSize() == 1` guards).
- **decomposition:**
  1. `DURABILITY(amount=1, target=item, mode=restore, who=@Self)`.
- **gaps:** none.
- **interactions:** **ITEM-SET RULING (owner)**, refining the batch-3 spell-it-out
  convention `obliterate.yml` records: take a COMPOSITE whenever it is an EXACT match
  for the jar's set — `ItemGroups` defines `TOOL` as pickaxe + axe + shovel + hoe and
  nothing else, so `[TOOL, FISHING_ROD]` is the canonical spelling of the 21-tool set
  (`haste.yml`, `oxygenate.yml`, `telepathy.yml`, and `atomic-detonate.yml` when it
  lands). ENUMERATE only where no composite fits — the weapons cases, where `WEAPON`
  would drag in the crossbow, trident and mace the jar never listed
  (`obliterate.yml` `[SWORD, AXE, BOW]`; this entry
  `[SWORD, AXE, BOW, PICKAXE, HOE, SHOVEL]`).
- **strings:** none.
- **numbers:** max 10; base 20.0, interval 5.0 (L1=20 … L10=65); weight 7; tier 3.
  Chance 8/16/24/32/40/48/56/64/72/80% — highest max and proc rate in the package;
  restore flat 1. Codex flags UNRESOLVED whether the jar's restore even lands (the
  patched-CraftBukkit `modifyDurability` return value is discarded); shipped as a
  working 1-point restore — no ledger row is possible without a measured baseline.
- **era:** durability model (legacy `short` damage vs modern `Damageable`) is
  absorbed by DURABILITY.

### Soul Trap (`axes/soul-trap`)

- **codex:** `04-enchants-axes.md § Soul Trap`
- **activation:** trigger `ATTACK`; envelope `soul-cost: 5`, `chance: <7 * level>`;
  conditions: soul system enabled (pack toggle), `%victim.type% == "PLAYER"`,
  held-swap gate (`HELD_SWAP_GATE`, > 5 t), `!(%soultrap%)` (actor's own trap var),
  actor in soul mode with souls > 0 (`SOUL_STATE_FACTS`),
  `!(%victim.soultrapimmune%)` (re-trap immunity, VICTIM_VAR_FACT).
- **decomposition:**
  1. `REMOVE_SOULS(amount=<10 * level>, who=@Victim)` — drains the victim's gem;
     the soul service clamps to held souls (jar `min(10*level, victim's souls)`).
  2. victim souls == 0 branch (SOUL_STATE_FACTS): `DAMAGE(amount=<1 + level>,
     who=@Victim)` instead — the fallback hit is generic armour-reduced damage in
     the jar.
  3. `SOUL_MODE_DISABLE` (gap) — force the victim out of soul/god mode (the jar's
     god-mode toggle runs when the mode is active).
  4. `SET_VAR(name=soultrap, value=1, ttl=<level * 80>, who=@Victim)` — the trap
     window (`level*4` s); read back same-scope by Hero Killer when the trapped
     player attacks.
  5. `SET_VAR(name=soultrapimmune, value=1, ttl=<level * 80 + 100>, who=@Victim)` —
     re-trap immunity = trap expiry + 5000 ms (jar enforces `expiry + 5000` at proc
     time; effective per-victim cooldown 9/13/17 s).
  6. `MESSAGE(text="§9§l** SOUL TRAP §7[{seconds}s]§9§l**", channel=chat,
     who=@Victim)` — applied even on the zero-souls fallback path, as in the jar.
  7. `SOUND(sound=ENTITY_ENDERMAN_SCREAM, volume=1.0, pitch=0.3, who=@Victim)`;
     `PARTICLE(particle=SPELL_WITCH, count=60, who=@Victim)` (+1y, jar speed 0.7F);
     `PARTICLE(particle=SPELL, count=25, who=@Victim)` (+1y, jar speed 0.4F).
- **gaps:** `SOUL_MODE_DISABLE — effect forcing a target player out of soul/god
  mode; params: none; consumers: trap-style soul counters. No existing effect
  touches soul-mode state.` Plus `SOUL_STATE_FACTS`, `HELD_SWAP_GATE`,
  `VICTIM_VAR_FACT` (defined earlier).
- **interactions:** writes the `soultrap` var Hero Killer's self-gate reads; the
  jar's shared 1000 ms soul-cost limiter is replaced by per-activation `soul-cost`
  (ledger D-04-14, shared with D-04-11). The jar's console steal log (with its `form`
  typo) is not ported.
- **strings:** `§9§l** SOUL TRAP §7[{seconds}s]§9§l**` — {seconds} = 4/8/12; note
  **no space** between `]` and the trailing `**`, verbatim. (The advertised seconds
  are the trap window; the real re-trap immunity is 5 s longer — jar behavior,
  kept.)
- **numbers:** max 3; base 10.0, interval 10.0 (L1=10 L2=20 L3=30); weight 2;
  tier 6. Chance 7/14/21%; trap 4/8/12 s; steal up to 10/20/30 souls; fallback
  damage 2/3/4; attacker cost 5 souls. A dead jar-side per-level value (`1.0*level`,
  computed and never read) is not ported. **Codex-marked bug:** gate requires only souls > 0 then removes 5
  (over-draw, behind the shared limiter); as intended: 5 per activation clamped to
  held souls (ledger D-04-14).
- **era:** sound `ENDERMAN_SCREAM` (1.8) vs modern `ENTITY_ENDERMAN_SCREAM`;
  particles `WITCH_MAGIC`/`SPELL` (1.8) vs modern `SPELL_WITCH`(`WITCH`)/`EFFECT` —
  resolver aliases.

---

## Gap index (this doc)

| Gap | Kind | Consumers here |
| --- | --- | --- |
| `EXPR_FUNCS` | expression grammar | Anti Gank (cap), Bleed (step curve) |
| `STACK_COUNTER` | effect + store | Bleed (write/curve), Blessed (reset), Devour (read) |
| `VICTIM_VAR_FACT` | fact scope | Bleed, Corrupt, Devour, Hex, Soul Trap |
| `CURE_LIMIT` | param extension (CURE) | Blessed |
| `DURABILITY_PIECE_SELECT` | param extension (DURABILITY) | Blacksmith |
| `PLAYER_HEAD_DROP` | effect + death hook | Decapitation |
| `REFLECT_CAP` | param extension (REFLECT) | Hex |
| `HELD_SWAP_GATE` | fact | Boss Slayer, Hero Killer, Soul Trap |
| `SOUL_STATE_FACTS` | fact family | Hero Killer, Soul Trap |
| `SOUL_MODE_DISABLE` | effect | Soul Trap |
| `WORN_GEAR_FACT` | fact family | Hero Killer |
