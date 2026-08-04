# Deferred content — the authoritative stop list

Every matrix entry NOT yet authored in `cosmic-pack`, with its blocking
dependency. An entry leaves this table only when its file lands. Batch verifiers
treat a missing file as unauthorized unless it has a row here.

| Entry | Batch | Blocked on | Unblocks at |
| --- | --- | --- | --- |
| `enchants/blood-lust` | 01 | doc-04 bleed-stack publisher + tagged-effect PROXIMITY_EVENT | after batch 4 + wave 2 |
| `enchants/ghost` | 01 | matrix-UNRESOLVED external metadata consumer — owner decision pending | owner ruling |
| `enchants/enchant-reflect` | 02 | PROC_REBOUND (incoming-direction family; #276 STOP analysis is the design input) | wave 2 |
| `enchants/phoenix` | 02 | ESCALATING_SOUL_COST (everything else expressible today — drop-in) | wave 2 |
| `enchants/self-destruct` | 02 | SUMMON_PAYLOAD (phase=detonate + scatter + payload effects) | wave 2 |
| `enchants/spirits` | 02 | SUMMON_PAYLOAD (periodic phase: pulsing ally heal on the summon) | wave 2 |
| `enchants/undead-ruse` | 02 | SUMMON_PAYLOAD (named ring minions with per-level buff amplifiers); VIEWER_HIDE half already shipped | wave 2 |
| `enchants/plague-carrier` | 02 | SUMMON_PAYLOAD (detonate burst, terrain=none); legacy note: CREEPER_HISS alias unverified | wave 2 |
| `enchants/nutrition` | 02 | MODIFY_FOOD `mode=absolute` (matrix ships measured absolute semantics, not delta) + EAT is a held-only trigger (worn leggings never walk it) — both need rulings | wave 1f/2 |
| `enchants/repair-guard` | 02 | `%item.durabilitypercent%` fact (the EQUIP_CHANGE gap's unshipped half); authoring without it reproduces the jar's fatal proc-on-every-unequip | wave 1f |
| `enchants/metaphysical` | 02 | SHIPPED as a roster marker (effect-less PASSIVE, boots); its consumers (Trap/Snare/Pummel) need a victim worn-enchant-level fact for the reduction clauses | wave 1f (consumer side) |
| `enchants/sticky` | 02 | shipped as a roster marker (same victim worn-level fact for its consumers) | wave 1f (consumer side) |
| `enchants/kill-aura` | 03 | two independent blockers: the activation gate needs the masks family's monster-summon state on the victim (doc-11 interaction fact, nothing on the frozen surface expresses it), and the `killaura` var it writes is matrix-UNRESOLVED — no consumer exists anywhere in the jar corpus. Authored without the gate it would burst EXPLOSION_LARGE on ordinary hits to write a var nobody reads. | batch 11 + owner ruling on the var |
| `enchants/hero-killer` | 04 | the victim worn-gear fact — the matrix gate is "victim wearing ≥ 1 Heroic Armor piece" (WORN_GEAR_FACT), and nothing on the frozen surface classifies worn armour on either side (`IGNORE_HEROIC` acts on heroic gear but cannot gate on it). Everything else IS expressible today, so this is a one-clause drop-in: `soul-cost: 4` carries the whole soul half (a soul-cost ability never fires outside soul mode, and the charge is the availability gate — the as-intended reading of D-04-11's over-draw), `%heldticks% > 5` is the held-swap gate, `%soultrap% != 1` the actor-side Soul Trap read (authorable whether or not Soul Trap itself ships). Authored WITHOUT the heroic gate it would hand a flat +10/20/30 % against every player, which is a different enchant. | wave 1f |
| `enchants/solitude` | 03 | SHIPPED as a roster marker (effect-less HELD, sword). Its whole payload is the `%solitude%` marker §Silence reads, and the matrix's "cleared on un-hold" half has no expression: `SET_VAR` has no lifecycle-teardown, so the recorded `ttl=0` marker written on take-hold never drops and one take-hold would boost every later Silence off any weapon, forever. Needs either a SET_VAR HELD/PASSIVE stop half or an actor-side co-held worn-enchant-level fact (which would drop the marker entirely). | wave 1f (consumer side) |
| `enchants/soul-trap` | 04 | `SOUL_MODE_DISABLE` — an effect forcing a target player out of soul/god mode; the wave-2 soul family. Nothing ELSE in the entry blocks: `HELD_SWAP_GATE` ships as `%heldticks%`, `SOUL_STATE_FACTS` as `%actor.souls%`/`%victim.souls%`, `VICTIM_VAR_FACT` as `%victim.var.<name>%`, and the drain / trap-window / re-trap-immunity rows are REMOVE_SOULS + SET_VAR today. Shipping without it would bill the attacker 5 souls, drain the victim's gem and advertise `** SOUL TRAP [Ns] **` while leaving the victim in the very mode the trap exists to break — and Hero Killer's `soultrap` self-gate would read a var that marks nothing. Carries its own era debt when it lands: `ENDERMAN_SCREAM→ENTITY_ENDERMAN_SCREAM` plus the `WITCH_MAGIC`/`SPELL` particle aliases. | wave 2 |

## Partial entries (file shipped, one clause pending)

These files ARE authored and compile — the pack is complete without them
changing. Each carries one recorded clause the frozen surface cannot express
yet, noted in the file itself and tracked here so a later wave knows where to
come back.

| Entry | Pending clause | Blocked on | Unblocks at |
| --- | --- | --- | --- |
| ~~`enchants/deep-wounds`~~ | ~~the bleed-stack fold `DAMAGE_MOD amount = "%victim.var.bleedstacks% * 0.5"` (+0.5% a live stack, ceiling ×1.11/×1.12/×1.13 at 20 stacks)~~ | **CLOSED in batch 4** — `enchants/bleed` shipped the publisher and the fold row is authored on all three levels; the file is whole | closed |
| `enchants/devour` + `enchants/rage` | the Devour→Rage exclusion is COARSER than the matrix's `devour-suppresses-rage-multiplier`: the rule parks Rage's damage fold and its `raged` victim mark but keeps the combo increment, and devour.yml ships `SUPPRESS(scope=ENCHANT, key=enchants/rage, duration=4, mode=timed, who=@Self)`, which parks the whole enchant — so the increment skips those 4 t too and a suppressed hit never banks | a suppression key finer than the enchant: Rage's fold, mark and increment are three abilities under ONE enchant key, and `scope=KIND` on DAMAGE_MOD would park every damage fold the holder owns. Closes with per-ability (or per-effect-line) suppression scope. Currently unreachable in play — Rage's `%actor.helditem% contains "_SWORD"` gate cannot pass on the axe Devour rides — so it is recorded, not a stop | follow-up candidate |
| `enchants/divine-immolation` | per-tick cosmetics (`ENTITY_ZOMBIFIED_PIGLIN_ANGRY` 0.6/0.8, FLAME×20, LAVA×15) and the shipped-but-inert `POTION(WITHER)` status row | PERIODIC_DAMAGE tick-cue params for the cosmetics; PERIODIC_DAMAGE `replace` semantics for the status (the window currently denies WITHER outright) | wave 1f |
| `enchants/trap` + `enchants/disarmor` | the Metaphysical / Sticky reduction clauses (chance rebate, blocked-proc line, 1% floor) | the victim-side worn-enchant-level fact — already carried consumer-side by the `enchants/metaphysical` and `enchants/sticky` rows above | wave 1f (consumer side) |
| `enchants/pummel` | the Metaphysical rebate: `-4 %chance%` per Metaphysical level on the bystander being tested, plus the blocked line `§8§l** METAPHYSICAL (§8Pummel blocked!§l) **`. Authored PER-TARGET when it lands — D-04-13 rules the jar's shared-threshold mutation a bug | the same victim-side worn-enchant-level fact the `enchants/metaphysical` row above already carries consumer-side (the `enchants/trap` + `enchants/disarmor` row is the same stop) | wave 1f (consumer side) |
| `enchants/corrupt` | the Inversion carve-out: a victim HOLDING an Inversion item takes the flag and the portal burst but NONE of the DoT rows (jar order kept) | the victim-side held/worn enchant-level fact — the same wave-1f fact the `enchants/metaphysical` and `enchants/sticky` consumer rows wait on (`%victim.helditem%` is a material, not an enchant level). The other direction is CLOSED in batch 4: swords/Inversion authors the corrupted branch off this file's flag, reading it actor-side as a bare `%corrupt%` (its HURT pass runs on the corrupted player), not `%victim.var.corrupt%` | wave 1f (consumer side) |
| `enchants/bleed` | the PLAYER-side derived slow — `MOVEMENT_SPEED(speed = "0.2 - 0.005 * i")` held for as long as stacks live — and the death half of D-04-6 (stacks clear on death). The counter itself, its gate, the mob SLOWNESS branch and the crack burst all ship | two engine halves: (a) a walk-speed grant with no expiry (`ticks` is a finite span, `0` reverts on the spot), i.e. the derived-modifier hook the matrix's STACK_COUNTER gap describes, or a `SET_VAR` state-tied modifier; (b) a player-side var death clear — `EntityVarCleanupListener` deliberately sweeps MOBS only, so a bled player's stacks currently survive their own death | wave 1f |
| `enchants/cleave` | the recorded PER-SPLASH-VICTIM 20 t (1000 ms) stamp, shipped as the ability's own 30 t per-player bucket | per-victim cooldown scope — the identical stop the `enchants/thundering-blow` row carries; one attacker's splash now paces across targets | follow-up candidate |
| `enchants/thundering-blow` | the recorded PER-VICTIM 50 t cooldown, shipped as the engine's per-player bucket | per-victim cooldown scope; the coarsening is recorded here rather than as a deviation (one attacker's proc now paces across targets) | follow-up candidate |

## Engine follow-up pool fed by these rows

- **Wave 2 critical path:** SUMMON_PAYLOAD (5 consumers above + sets/masks later),
  ESCALATING_SOUL_COST, PROC_REBOUND.
- **Wave 1f (small):** victim worn-enchant-level fact (Metaphysical/Sticky
  consumers, Hero Killer's heroic-piece counting — the orphaned WORN_GEAR_FACT
  from clustering, and Corrupt's held-Inversion carve-out, which needs the
  HELD half of the same fact) and its actor-side twin (Silence reading co-held
  Solitude);
  a `SET_VAR` lifecycle-teardown so a HELD/PASSIVE marker drops on unequip
  (Solitude — the same-item constraint depends on it);
  a state-tied (unbounded) `MOVEMENT_SPEED` grant that lives until the counter
  feeding it clears, plus a player-side var death clear — the two halves of
  Bleed's player slow, and what the matrix's STACK_COUNTER "derived-modifier
  hook" actually asked for beyond the counter itself (`SET_VAR op=increment`
  shipped that half in wave 1);
  `%item.durabilitypercent%`; MODIFY_FOOD `mode=absolute`;
  EAT worn-scan ruling; per-block cooldown-scope opt-out (Rocket Escape's FALL
  companion is cooldown-starved by its own launch); GUARD/SPAWN_ENTITY
  per-level potion amplifiers;
  PERIODIC_DAMAGE tick-cue params (per-pulse sound/particle beside the existing
  `feedback` line — Divine Immolation's dropped cosmetics, and every later
  converted-DoT enchant);
  PERIODIC_DAMAGE `replace` semantics fix — cancel the ticks, keep the status
  VISIBLE. The 1d.2 implementation reused the POTION_LOCK deny loop, which
  strips the named effect and denies re-application for the window; the
  original contract cancels only the ticking damage, so the converted DoT's
  own icon/particles survive as they do in the jar. Divine Immolation's
  `POTION(WITHER)` row is shipped-but-inert until this lands;
  per-victim cooldown scope (Thundering Blow's recorded 50 t and Cleave's 20 t
  splash stamp are both per-target;
  the engine's bucket is per-player — a hot-path store-shape question, since a
  per-victim bucket multiplies the key space by the live entity count).
- **Legacy-sweep alias rows:** `DIG_STONE→BLOCK_STONE_BREAK`,
  `FIREWORK_LAUNCH→ENTITY_FIREWORK_ROCKET_LAUNCH`,
  `FIREWORK_TWINKLE2→ENTITY_FIREWORK_ROCKET_TWINKLE_FAR`, verify `CREEPER_HISS`,
  `PISTON_EXTEND→BLOCK_PISTON_EXTEND` (Inversion, batch 03),
  `DRINK→ENTITY_GENERIC_DRINK` (Vampire, batch 03),
  `ANVIL_BREAK→BLOCK_ANVIL_BREAK` (Disarmor + Disintegrate, batch 03),
  `ZOMBIE_PIG_ANGRY→ENTITY_ZOMBIFIED_PIGLIN_ANGRY` and
  `FIREWORK_BLAST→ENTITY_FIREWORK_ROCKET_BLAST` (Divine Immolation, batch 03),
  `CHICKEN_HURT→ENTITY_CHICKEN_HURT`, `MAGMACUBE_WALK2→ENTITY_MAGMA_CUBE_SQUISH`
  and `GHAST_SCREAM→ENTITY_GHAST_SCREAM` (Epicness, batch 03),
  `WITHER_HURT→ENTITY_WITHER_HURT` (Silence, batch 03),
  `SPLASH→ENTITY_GENERIC_SPLASH` (Blessed, batch 04 — the 1.8-era sound name;
  note `Aliases.PARTICLE` already carries an unrelated `WATER_SPLASH→SPLASH`),
  `MYCEL→MYCELIUM` (Devour, batch 04 — a `Aliases.MATERIAL` row, not a sound:
  the block-crack burst carries the material through the same resolver and
  `MYCEL` is the 1.8.9 spelling).

## Matrix maintenance queue

Corrections owed to the matrix docs themselves, which live on the
`docs/cosmic-decomposition-matrix` branch and cannot be edited from a content
branch. Recorded here so authoring never silently diverges from a doc nobody
went back and fixed.

- **`matrix/03` § Trap — two false engine claims.** The decomposition says the
  engine's timed `MOVEMENT_SPEED` modifier "restores the PRIOR speed, fixing
  the jar's hard-coded 0.2F restore clobber": it does not. Engine policy hands
  back the vanilla 0.2 default precisely so a re-fire can never ratchet speed
  upward, which reproduces the jar's clobber rather than fixing it. The numbers
  line then says overlapping traps refresh — "the timed modifier refreshes
  instead": there is no refresh path. Every grant registers its own timed
  revert, so the first revert ends a freeze a later grant re-armed. Both claims
  need striking on the matrix branch; `trap.yml` already carries the corrected
  reading.
- **`matrix/03` § Trap — the attacker-side entry guard.** Recorded as
  `!%trap.frozen%` read on the attacker ("the attacker reads their own copy").
  Superseded by owner ruling for the reason `deviations.md D-06-17` already
  gives on Heroic Titan Trap: it is the same wrong-subject idiom, and the
  intent is anti-re-trap. Authored victim-side; the matrix line should point at
  D-06-17.
- **`matrix/04` § Boss Slayer — the shipped boss list cannot be
  `%victim.mobtype%`.** D-04-7 rules the jar's inert boss flag out and hands the
  designation to "a pack-configured boss mob-type list expressed as
  `%victim.mobtype%` conditions". That fact is the MythicMobs soft hook
  (ADR-0027): with no integration installed it resolves to the empty string for
  every entity, so a `%victim.mobtype%` list would ship the enchant exactly as
  inert as the jar it is fixing. `boss-slayer.yml` expresses the list as
  `%victim.type%` over the vanilla bosses (ENDER_DRAGON, WITHER — both resolve
  on 1.8.9) and documents the `%victim.mobtype%` widening for MythicMobs
  servers. The matrix line should name the fact it actually needs.
- **`matrix/04` § Blessed — the `deepwounds` writer's subject contradicts itself.**
  The activation line says Deep Wounds "writes on its attacker … read here from
  the actor's own store", but `matrix/03` § Deep Wounds decomposes the write as
  `SET_VAR(name=deepwounds, …) @Victim`. Both halves cannot hold: a write on the
  Deep Wounds attacker would never reach the Blessed wielder's store. The shipped
  pair is internally consistent on the victim-side reading —
  `deep-wounds.yml` writes `who: "@Victim"`, `blessed.yml` reads the bare
  actor-side `%deepwounds%`, and the wounded player IS the later Blessed actor.
  The matrix line should say "on its victim".
- **Batch-1 staleness pattern — closed gaps still marked open.** Gap blocks in
  `matrix/02` and `matrix/03` still read as open against a surface that has
  since shipped them: `VELOCITY_ANCHOR` (02 § Ragdoll) is the `anchor` param
  `VELOCITY` now carries; in 03, `VAR_SCALED_DAMAGE` was absorbed into
  EXPR_PARAMS, `TARGET_VAR_FACT` ships as `%victim.var.<name>%`, `COUNTER_VAR`
  as `SET_VAR op=increment` plus `%counter.<name>%`, and `PERIODIC_DAMAGE`,
  `HURT_TRIGGER` and `SPAWN_ORIGIN_FACT` (`%victim.fromspawner%`) all landed in
  wave 1. Authors must check each gap block against
  `docs/reference/authoring-surface.txt` before deferring on it — a gap block
  is a batch-1 snapshot, not a live statement. The matrix sweep should re-run
  that check across every doc, not just the two named here.
