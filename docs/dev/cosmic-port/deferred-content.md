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
| `enchants/solitude` | 03 | SHIPPED as a roster marker (effect-less HELD, sword). Its whole payload is the `%solitude%` marker §Silence reads, and the matrix's "cleared on un-hold" half has no expression: `SET_VAR` has no lifecycle-teardown, so the recorded `ttl=0` marker written on take-hold never drops and one take-hold would boost every later Silence off any weapon, forever. Needs either a SET_VAR HELD/PASSIVE stop half or an actor-side co-held worn-enchant-level fact (which would drop the marker entirely). | wave 1f (consumer side) |

## Partial entries (file shipped, one clause pending)

These files ARE authored and compile — the pack is complete without them
changing. Each carries one recorded clause the frozen surface cannot express
yet, noted in the file itself and tracked here so a later wave knows where to
come back.

| Entry | Pending clause | Blocked on | Unblocks at |
| --- | --- | --- | --- |
| `enchants/deep-wounds` | the bleed-stack fold `DAMAGE_MOD amount = "%victim.var.bleedstacks% * 0.5"` (+0.5% a live stack, ceiling ×1.11/×1.12/×1.13 at 20 stacks) | the doc-04 Bleed publisher that writes the stack var — NO engine work: the VAR_SCALED_DAMAGE gap was absorbed into EXPR_PARAMS, so the clause is authorable the moment the publisher lands | batch 4 |
| `enchants/divine-immolation` | per-tick cosmetics (`ENTITY_ZOMBIFIED_PIGLIN_ANGRY` 0.6/0.8, FLAME×20, LAVA×15) and the shipped-but-inert `POTION(WITHER)` status row | PERIODIC_DAMAGE tick-cue params for the cosmetics; PERIODIC_DAMAGE `replace` semantics for the status (the window currently denies WITHER outright) | wave 1f |
| `enchants/trap` + `enchants/disarmor` | the Metaphysical / Sticky reduction clauses (chance rebate, blocked-proc line, 1% floor) | the victim-side worn-enchant-level fact — already carried consumer-side by the `enchants/metaphysical` and `enchants/sticky` rows above | wave 1f (consumer side) |
| `enchants/thundering-blow` | the recorded PER-VICTIM 50 t cooldown, shipped as the engine's per-player bucket | per-victim cooldown scope; the coarsening is recorded here rather than as a deviation (one attacker's proc now paces across targets) | follow-up candidate |

## Engine follow-up pool fed by these rows

- **Wave 2 critical path:** SUMMON_PAYLOAD (5 consumers above + sets/masks later),
  ESCALATING_SOUL_COST, PROC_REBOUND.
- **Wave 1f (small):** victim worn-enchant-level fact (Metaphysical/Sticky
  consumers, Hero Killer's heroic-piece counting — the orphaned WORN_GEAR_FACT
  from clustering) and its actor-side twin (Silence reading co-held Solitude);
  a `SET_VAR` lifecycle-teardown so a HELD/PASSIVE marker drops on unequip
  (Solitude — the same-item constraint depends on it);
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
  per-victim cooldown scope (Thundering Blow's recorded 50 t is per-target;
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
  `WITHER_HURT→ENTITY_WITHER_HURT` (Silence, batch 03).

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
