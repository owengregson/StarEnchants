# Deferred content — the authoritative stop list

Every matrix entry NOT yet authored in `cosmic-pack`, with its blocking
dependency. An entry leaves this table only when its file lands. Batch verifiers
treat a missing file as unauthorized unless it has a row here.

| Entry | Batch | Blocked on | Unblocks at |
| --- | --- | --- | --- |
| `enchants/blood-lust` | 01 | doc-04 bleed-stack publisher + tagged-effect PROXIMITY_EVENT | after batch 4 + wave 2 |
| `enchants/ghost` | 01 | matrix-UNRESOLVED external metadata consumer — owner decision pending | owner ruling |
| `enchants/enchant-reflect` | 02 | PROC_REBOUND (incoming-direction family; #276 STOP analysis is the design input) | wave 2 |
| ~~`enchants/phoenix`~~ | 02 | ~~ESCALATING_SOUL_COST (everything else expressible today — drop-in)~~ **AUTHORED in batch 5** — the ladder is the shipped envelope (`soul-cost: 500` + `soul-cost-growth: 2` + `soul-cost-cap: 8000` + `soul-cost-decay-period: 12000`), charged against carried gems (`soul-cost-carried: true`: matrix 07's Soul Mode section does not list Phoenix among the mode's consumers, and a last-stand save that only fired in soul mode would never fire). The recorded "one enchant exempt from defensive-proc suppression" ships as the enchant-scoped `suppress-immune: true`, NOT the second PASSIVE ability the matrix decomposes it as — the `SUPPRESS_IMMUNE` effect immunises the WEARER, so it would unsilence every enchant they own | closed |
| ~~`enchants/self-destruct`~~ | 02 | ~~SUMMON_PAYLOAD (phase=detonate + scatter + payload effects)~~ **AUTHORED in batch 5** — the blast is a second ability on the `SUMMON_PAYLOAD` trigger (DAMAGE 16 / IGNITE 40 t / `VELOCITY mode=away strength=1.7 anchor=activator`, which on a payload activation anchors at the charge), the charges ride `scatter: 3`; two clauses pending below | closed |
| ~~`enchants/spirits`~~ | 02 | ~~SUMMON_PAYLOAD (periodic phase: pulsing ally heal on the summon)~~ **AUTHORED in batch 5** — the pulse is a second ability on the SUMMON_PAYLOAD trigger, configured by the blaze's `payload-*` box; one clause pending below | closed |
| ~~`enchants/undead-ruse`~~ | 02 | ~~SUMMON_PAYLOAD (named ring minions with per-level buff amplifiers); VIEWER_HIDE half already shipped~~ **AUTHORED in batch 5** — SPAWN_SWARM's `name`/`effects` (with the `NAME*LEVEL` amplifier suffix) carried the whole minion half; one clause pending below | closed |
| ~~`enchants/plague-carrier`~~ | 02 | ~~SUMMON_PAYLOAD (detonate burst, terrain=none); legacy note: CREEPER_HISS alias unverified~~ **AUTHORED in batch 5** — the creeper burst is a second ability on the `SUMMON_PAYLOAD` trigger, configured by the charged creeper's `payload-*` box (`ALL`, the jar's measured no-ally-filter asymmetry); the CREEPER_HISS alias is still unverified and stays in the legacy-sweep list below; one clause pending below | closed |
| `enchants/nutrition` | 02 | MODIFY_FOOD `mode=absolute` (matrix ships measured absolute semantics, not delta) + EAT is a held-only trigger (worn leggings never walk it) — both need rulings | wave 1f/2 |
| `enchants/repair-guard` | 02 | `%item.durabilitypercent%` fact (the EQUIP_CHANGE gap's unshipped half); authoring without it reproduces the jar's fatal proc-on-every-unequip | wave 1f |
| `enchants/metaphysical` | 02 | SHIPPED as a roster marker (effect-less PASSIVE, boots); its consumers (Trap/Snare/Pummel) need a victim worn-enchant-level fact for the reduction clauses | wave 1f (consumer side) |
| `enchants/sticky` | 02 | shipped as a roster marker (same victim worn-level fact for its consumers) | wave 1f (consumer side) |
| `enchants/kill-aura` | 03 | two independent blockers: the activation gate needs the masks family's monster-summon state on the victim (doc-11 interaction fact, nothing on the frozen surface expresses it), and the `killaura` var it writes is matrix-UNRESOLVED — no consumer exists anywhere in the jar corpus. Authored without the gate it would burst EXPLOSION_LARGE on ordinary hits to write a var nobody reads. | batch 11 + owner ruling on the var |
| `enchants/hero-killer` | 04 | the victim worn-gear fact — the matrix gate is "victim wearing ≥ 1 Heroic Armor piece" (WORN_GEAR_FACT), and nothing on the frozen surface classifies worn armour on either side (`IGNORE_HEROIC` acts on heroic gear but cannot gate on it). Everything else IS expressible today, so this is a one-clause drop-in: `soul-cost: 4` carries the whole soul half (a soul-cost ability never fires outside soul mode, and the charge is the availability gate — the as-intended reading of D-04-11's over-draw), `%heldticks% > 5` is the held-swap gate, `%soultrap% != 1` the actor-side Soul Trap read (authorable whether or not Soul Trap itself ships). Authored WITHOUT the heroic gate it would hand a flat +10/20/30 % against every player, which is a different enchant. | wave 1f |
| `enchants/solitude` | 03 | SHIPPED as a roster marker (effect-less HELD, sword). Its whole payload is the `%solitude%` marker §Silence reads, and the matrix's "cleared on un-hold" half has no expression: `SET_VAR` has no lifecycle-teardown, so the recorded `ttl=0` marker written on take-hold never drops and one take-hold would boost every later Silence off any weapon, forever. Needs either a SET_VAR HELD/PASSIVE stop half or an actor-side co-held worn-enchant-level fact (which would drop the marker entirely). | wave 1f (consumer side) |
| `enchants/target-tracking` | 05 | a victim-name token on `RUN_COMMAND`. The entry's ENTIRE payload is one command, `f focus <the struck player>`, and `RUN_COMMAND` substitutes ACTOR tokens only (`{PLAYER}`/`{UUID}`/`{WORLD}`); `{VICTIM}` exists on `MESSAGE` but not here, and the effect takes no `who` target, so nothing can name the target of the hit. Authored as-is it would run the literal string `f focus {VICTIM}` on every hit. Second, softer question for the same file: SE has no factions of its own and the matrix itself says the pack omits or retargets this enchant on a stack without a factions plugin — the command string is server-configurable, so the pack-side ruling (ship the bridge, or ship nothing) is owed alongside the token | wave 1f (token) + owner ruling (factions bridge) |
| `enchants/soul-trap` | 04 | `SOUL_MODE_DISABLE` — an effect forcing a target player out of soul/god mode; the wave-2 soul family. Nothing ELSE in the entry blocks: `HELD_SWAP_GATE` ships as `%heldticks%`, `SOUL_STATE_FACTS` as `%actor.souls%`/`%victim.souls%`, `VICTIM_VAR_FACT` as `%victim.var.<name>%`, and the drain / trap-window / re-trap-immunity rows are REMOVE_SOULS + SET_VAR today. Shipping without it would bill the attacker 5 souls, drain the victim's gem and advertise `** SOUL TRAP [Ns] **` while leaving the victim in the very mode the trap exists to break — and Hero Killer's `soultrap` self-gate would read a var that marks nothing. Carries its own era debt when it lands: `ENDERMAN_SCREAM→ENTITY_ENDERMAN_SCREAM` plus the `WITCH_MAGIC`/`SPELL` particle aliases. | wave 2 |
| `enchants/hijack` | 05 | target-scoped summon conversion. The upgrade half landed: `SUMMON_REBIND` carries the fresh full-health respawn, the Guardians ladder (`health` 70/90/110/130, FIRE_RESISTANCE plus REGENERATION/INCREASE_DAMAGE/SPEED), the restarted 600 t self-destruct, the rename and `rise: 2`. But it only ever replaces a summon the ACTIVATOR ALREADY OWNS, and the one ownership-transfer effect — `CONVERT_SUMMON` — is the Grand Bell (ADR-0071): it takes a PLAYER target and rings its radius around THAT player, so it cannot flip the single remote golem an arrow just struck. The matrix's `CONVERT_SUMMON(radius=1, who=@Victim)` is inexpressible (a non-player target resolves to nothing, and `who=@Self` would ring the shooter). Authored anyway, the file would roll 8/16/24/32 %, broadcast `§5§l*** HIJACK (…) ***` to everyone inside 24 blocks and leave the golem serving its original owner — the theft IS the enchant. Nothing else blocks: the gate is `%victim.type% == "IRON_GOLEM"` (not `%victim.mobtype%`, the MythicMobs hook — the Boss Slayer ruling), and the whole guard loadout is SUMMON_REBIND params. Carries era debt when it lands: `IRONGOLEM_DEATH→ENTITY_IRON_GOLEM_DEATH` (shared with Guardians, batch 01). Considered and rejected: faking the outcome with `DESPAWN(@Victim)` + `GUARD` — GUARD targets `T.ATTACKER`, which a BOW activation does not have, it has no `rise`, and with no ownership precondition it would delete ANY iron golem, minting a free tier-8 guardian off a farmed wild one. | wave 1f — a `who`-targeted convert, or an ownership-stealing flag on SUMMON_REBIND |

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
| `enchants/snare` | the Metaphysical reduction: `-7 %chance%` per Metaphysical level on the victim (effective chance `max(0, 9×L − 7×Meta)`, so Metaphysical ≥ 4 fully immunises against Snare I–III), plus the victim-shown blocked line `§8§l** METAPHYSICAL (§8Snare blocked!§l) **`. Everything else ships — chance ladder, both potions, the vine burst | the same victim-side worn-enchant-level fact the `enchants/metaphysical` row above carries consumer-side (identical stop to the `enchants/trap` + `enchants/disarmor` and `enchants/pummel` rows) | wave 1f (consumer side) |
| `enchants/pummel` | the Metaphysical rebate: `-4 %chance%` per Metaphysical level on the bystander being tested, plus the blocked line `§8§l** METAPHYSICAL (§8Pummel blocked!§l) **`. Authored PER-TARGET when it lands — D-04-13 rules the jar's shared-threshold mutation a bug | the same victim-side worn-enchant-level fact the `enchants/metaphysical` row above already carries consumer-side (the `enchants/trap` + `enchants/disarmor` row is the same stop) | wave 1f (consumer side) |
| `enchants/corrupt` | the Inversion carve-out: a victim HOLDING an Inversion item takes the flag and the portal burst but NONE of the DoT rows (jar order kept) | the victim-side held/worn enchant-level fact — the same wave-1f fact the `enchants/metaphysical` and `enchants/sticky` consumer rows wait on (`%victim.helditem%` is a material, not an enchant level). The other direction is CLOSED in batch 4: swords/Inversion authors the corrupted branch off this file's flag, reading it actor-side as a bare `%corrupt%` (its HURT pass runs on the corrupted player), not `%victim.var.corrupt%` | wave 1f (consumer side) |
| `enchants/bleed` | the PLAYER-side derived slow — `MOVEMENT_SPEED(speed = "0.2 - 0.005 * i")` held for as long as stacks live — and the death half of D-04-6 (stacks clear on death). The counter itself, its gate, the mob SLOWNESS branch and the crack burst all ship | two engine halves: (a) a walk-speed grant with no expiry (`ticks` is a finite span, `0` reverts on the spot), i.e. the derived-modifier hook the matrix's STACK_COUNTER gap describes, or a `SET_VAR` state-tied modifier; (b) a player-side var death clear — `EntityVarCleanupListener` deliberately sweeps MOBS only, so a bled player's stacks currently survive their own death | wave 1f |
| `enchants/cleave` | the recorded PER-SPLASH-VICTIM 20 t (1000 ms) stamp, shipped as the ability's own 30 t per-player bucket | per-victim cooldown scope — the identical stop the `enchants/thundering-blow` row carries; one attacker's splash now paces across targets | follow-up candidate |
| `enchants/thundering-blow` | the recorded PER-VICTIM 50 t cooldown, shipped as the engine's per-player bucket | per-victim cooldown scope; the coarsening is recorded here rather than as a deviation (one attacker's proc now paces across targets) | follow-up candidate |
| `enchants/spirits` | the ally heal pulse never reaches its effects. The payload ability is authored and configured (period/radius/ALLIES/max-targets all per level), but every block of an enchant shares ONE cooldown bucket, and the 200 t re-arm the DEFENSE spawn takes covers the blaze's whole 200 t life — gate 6 denies every pulse it could ever fire. The blaze, its buffs, its name and the proc cues all ship | per-ability cooldown-scope opt-out — the identical stop `enchants/rocket-escape`'s FALL companion carries in the wave-1f pool below, total here rather than partial (cooldown == summon lifetime, so no pulse survives it) | wave 1f |
| `enchants/guardians` + `enchants/spirits` + `enchants/undead-ruse` | the summon NAME's owner token. All three author the matrix's verbatim `{owner}` string as `{player}` (`§b§l{player}'s Guardian`, `§c§l{player}'s Spirit`, `§d§l{player}'s Undead Minion`), but nothing substitutes it: `GUARD`/`SPAWN_ENTITY`/`SPAWN_SWARM` hand `name` to `DispatchSinkBase.applyGuardName`, which only runs `Colors.translate` — so the nameplate reads the literal `{player}`. `{ATTACKER}`/`{VICTIM}` are `MESSAGE`-only, `{PLAYER}` is `RUN_COMMAND`-only, and no `{player}` substitution exists anywhere in the tree. Everything else on all three files ships | an actor-name token on the summon-name param — the one substitution the three spawners share. Not a stop: the nameplate is cosmetic and the idiom has shipped since batch 01, so the pack is internally consistent and flips in one pass when the token lands (`SUMMON_REBIND`'s rename takes the same param, so Hijack inherits the fix) | wave 1f |
| `enchants/undead-ruse` | the minion OWNERSHIP half: the jar's minions never target or hurt their summoner, and these will — vanilla zombie AI takes the wearer standing inside their own ring. Count, buff amplifiers, names, permanence, the vanish window and all three particle bursts ship | an `owner` param on SPAWN_SWARM. `owner: activator` on SPAWN_ENTITY/GUARD binds `GuardianCasts`, which `SummonTargetGuardListener` reads to cancel a summon acquiring its owner; the swarm spawner binds only `SwarmSpawns` (disable-teardown), so there is no ownership to read | wave 1f |
| `enchants/self-destruct` | the per-level FUSE ladder (100/80/60 t = 5/4/3 s): all three levels ship on vanilla's own 80 t fuse | the summon surface has no fuse param, and `ttl` is a DESPAWN rather than a detonation — any ttl at or under 80 t removes the charge BEFORE it can explode, so an authored 60 t would leave L3 spawning duds (ttl is omitted instead; a primed TNT always leaves by exploding). Closes with a `fuse` param on SPAWN_ENTITY, the shape `powered` already takes for creepers | wave 1f |
| `enchants/plague-carrier` + `enchants/self-destruct` | the payload only lands while the OWNER still wears the piece. Both entries are death-triggered — Plague Carrier finishes the wearer itself, Self Destruct lets the lethal hit stand — and `SummonPayloadService` runs the owner's abilities out of their LIVE `WornState`. Death drops the armour, the armour-change feeder (modern) / gear poll (1.8) refreshes off that drop and respawn refreshes again, so the blast most likely resolves for an owner who no longer carries the enchant — and it leaves a DUD, because the detonate phase has already cancelled the vanilla explosion. Everything else ships. Both files also express their recorded 200 t re-arm without `cooldown:`, for the shared-bucket reason the `enchants/spirits` row above carries — a cooldown on the proc would deny the detonations that follow it: Plague Carrier drops the re-arm (its own `KILL` makes one unreachable) and Self Destruct rides a 200 t `SET_VAR` marker in its condition | the payload's abilities snapshotted onto the SUMMON at spawn (or a worn-state read pinned to the spawning activation), so a summon can outlive its owner's gear the way the jar's did | wave 1f |
| `enchants/hellfire` + `enchants/infernal` | the BOW_FIRE flaming-arrow dressing — permanent (`2147483647` t) fire on Hellfire's shot, `<level>*60` t on Infernal's | `PROJECTILE_DRESSING` rides an ENTITY on the loosed arrow (`type`/`ttl`/`invulnerable`/`no-pickup`) and carries no fire, and nothing else addresses a shot in flight — `IGNITE` takes its targets from a selector and no selector names the projectile. Needs a fire-ticks param on PROJECTILE_DRESSING (or a projectile selector). Both files' direct-hit burns and landing AoEs ship whole, and neither ever depended on the dressing | wave 1f |
| `enchants/dimension-rift` | the REVERT hook: as each rifted block restores, players within 2 blocks lose Jump Boost and are popped upward (`REMOVE_POTION(JUMP_BOOST)` + `VELOCITY(mode=add, y=0.5)`). The soul-sand floor, the scattered web layer (`fill-chance` closed the matrix's `TEMP_BLOCK_FILL_CHANCE` gap), both particle aggregates, the level*15+40 timers and the player gate all ship | `TEMP_BLOCK_REVERT_HOOK` — an effect list executed at TempBlockLedger restore time, re-selecting by proximity to the restored blocks. `wait:` fires on a fixed offset from the activation, not on the ledger's own revert, and it cannot re-target off blocks it did not place | wave 1f/2 |
| `enchants/explosive` + `enchants/cowification` | two rider clauses. (a) The jar spawned Explosive's wither skull with `yield=0`/`incendiary=false` — pure scenery that never detonates — and `PROJECTILE_DRESSING` carries neither knob, while its `invulnerable`/`no-pickup` guards only apply to a LivingEntity rider (a WITHER_SKULL is not one), so the rider keeps vanilla skull behaviour. (b) The single-rider PRIORITY: the jar suppresses Cowification's cow when the bow also carries Explosive, and Explosive makes no reciprocal check; on the frozen surface the last `PROJECTILE_DRESSING` of the shot simply wins. Cowification's `ENTITY_COW_HURT` 1.0/0.7 cue to whoever strikes the cow is the same engine-owned lifecycle and is likewise unauthored | (a) yield/incendiary params on PROJECTILE_DRESSING (the same effect the `enchants/hellfire` + `enchants/infernal` row wants fire ticks on); (b) the actor-side held-enchant-level fact of wave 1f — the twin of the victim-side fact the Metaphysical consumers wait on. Both clauses are cosmetic today (a dressing rider is scenery and is removed the moment the arrow lands), so this is recorded, not a stop | wave 1f |
| `enchants/teleblock` | the LAUNCH-time soul charge. The jar bills at the shot and stamps the arrow "this shot was funded", so a missed shot still costs souls; the port bills at IMPACT (`soul-cost` on the BOW ability) — same cost per LANDED shot, same soul-mode + all-or-nothing gate, same silent refusal, but a miss is now free. Everything else ships: the funded launch cues, the D-05-6 as-intended teleblock window, the pearl strip and the verbatim message | a per-projectile payload stamp — a BOW ability resolves off the bow, not off the arrow a BOW_FIRE ability paid for, so no cross-ability "this shot was funded" link exists. Billing at launch instead would leave the impact ability ungated (a free teleblock for a shooter with an empty gem), which is why the charge MOVED rather than being split; a var-armed window leaks the same way on a miss | follow-up candidate |

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
  companion is cooldown-starved by its own launch, and every SUMMON_PAYLOAD
  consumer pays it twice over: the proc that spawns the summon arms the bucket
  the payload must then pass — Spirits loses its whole pulse to it, and Plague
  Carrier / Self Destruct had to express their re-arm without `cooldown:`);
  a payload whose abilities are pinned to the SPAWNING activation rather than
  re-read from the owner's live WornState, so a summon can outlive its owner's
  gear (Plague Carrier and Self Destruct both kill their wearer by design);
  GUARD/SPAWN_ENTITY per-level potion amplifiers; a TNT `fuse` param on
  SPAWN_ENTITY (Self Destruct's 5/4/3 s ladder — `ttl` despawns the charge
  instead of firing it);
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
  `FIREWORK_TWINKLE2→ENTITY_FIREWORK_ROCKET_TWINKLE_FAR`,
  `CREEPER_HISS→ENTITY_CREEPER_PRIMED` (Plague Carrier, batch 05 — authored as
  the modern spelling, but NO alias row exists either way, so the 1.8 lane
  silently drops the cue until the sweep verifies the pairing),
  `PISTON_EXTEND→BLOCK_PISTON_EXTEND` (Inversion, batch 03),
  `DRINK→ENTITY_GENERIC_DRINK` (Vampire, batch 03),
  `ANVIL_BREAK→BLOCK_ANVIL_BREAK` (Disarmor + Disintegrate, batch 03, and Eagle
  Eye, batch 05 — see the collision note at the end of this list),
  `ZOMBIE_PIG_ANGRY→ENTITY_ZOMBIFIED_PIGLIN_ANGRY` and
  `FIREWORK_BLAST→ENTITY_FIREWORK_ROCKET_BLAST` (Divine Immolation, batch 03),
  `CHICKEN_HURT→ENTITY_CHICKEN_HURT`, `MAGMACUBE_WALK2→ENTITY_MAGMA_CUBE_SQUISH`
  and `GHAST_SCREAM→ENTITY_GHAST_SCREAM` (Epicness, batch 03),
  `WITHER_HURT→ENTITY_WITHER_HURT` (Silence, batch 03),
  `SPLASH→ENTITY_GENERIC_SPLASH` (Blessed, batch 04 — the 1.8-era sound name;
  note `Aliases.PARTICLE` already carries an unrelated `WATER_SPLASH→SPLASH`),
  `MYCEL→MYCELIUM` (Devour, batch 04 — a `Aliases.MATERIAL` row, not a sound:
  the block-crack burst carries the material through the same resolver and
  `MYCEL` is the 1.8.9 spelling),
  `ARROW_HIT→ENTITY_ARROW_HIT` (Piercing and Longbow, batch 05) and
  `HURT_FLESH→ENTITY_PLAYER_HURT` (Sniper, batch 05) — matrix 05 marks both
  entries era-clean, but `Aliases.SOUND` carries neither pair today;
  `ORB_PICKUP→ENTITY_EXPERIENCE_ORB_PICKUP` (Healing, batch 05) and
  `FIRE→BLOCK_FIRE_AMBIENT` (Hellfire, batch 05) — both flagged on their matrix
  entries, neither in `Aliases.SOUND`;
  `ENDERMAN_HIT→ENTITY_ENDERMAN_HURT` and `EAT→ENTITY_GENERIC_EAT` (Teleblock,
  batch 05 — the EAT row is also owed by Paradox, batch 02, which flagged it and
  never got one),
  `PORTAL_TRIGGER→BLOCK_PORTAL_TRIGGER` (Unfocus, batch 05, matrix-flagged) and
  `ENDERMAN_TELEPORT→ENTITY_ENDERMAN_TELEPORT` (Virus, batch 05 — matrix 05 calls
  the entry era-clean, but the 1.8 constant drops the `ENTITY_` prefix like every
  other pair here). Unfocus also rides the `ARROW_HIT` row above and Teleportation
  the `ORB_PICKUP` one.
  `COW_IDLE→ENTITY_COW_AMBIENT` and `COW_HURT→ENTITY_COW_HURT` (Cowification,
  batch 05 — matrix 05 says the cow sounds "all exist in 1.8", which is true of
  the SOUND but not of the modern spelling the file authors);
  **`ANVIL_BREAK` collision — SETTLED on `BLOCK_ANVIL_BREAK`.** The batch-05
  Eagle Eye row used to map the same 1.8 `ANVIL_BREAK` to
  `BLOCK_ANVIL_DESTROY`, on the premise that only one of the two modern names
  exists. That premise is FALSE: `test-fixtures/handles/sounds-1.21.11.txt` and
  `sounds-26.1.2.txt` (javap'd from the reference-cache paper-api jars) both
  carry `BLOCK_ANVIL_BREAK` **and** `BLOCK_ANVIL_DESTROY`, so
  `ModernHandleEraTest` passes either spelling and cannot arbitrate. Settled by
  convention on the batch-03 spelling — one spelling pack-wide —
  and `eagle-eye.yml` was flipped to `BLOCK_ANVIL_BREAK` (its 20 SOUND lines and
  its era comment). Left for the sweep to confirm against a real jar: 1.8's
  `ANVIL_BREAK` is `random.anvil_break`, whose modern id is `block.anvil.destroy`
  (= `BLOCK_ANVIL_DESTROY`), while `BLOCK_ANVIL_BREAK` is `block.anvil.break`
  — if the cue's character matters, the sweep flips all three files together
  and writes the alias row to match. Nothing resolves on the legacy lane either
  way until that row lands.

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
- **`matrix/05` — the whole doc is missing its acquisition rows.** Docs 01–04
  end every `numbers` block with `Acquisition: max N, base X, interval Y,
  weight W, tier T`, and shipped files take their `tier:` (and their max level)
  from it. Doc 05 carries none — the backfill commit covered 01 only — so the
  bow batch has no recorded rung for any of its 22 entries. Authored files take
  max level from the per-level ladders (unambiguous) and carry forward the tier
  the pack already ships for that enchant name, noting it in-file
  (`pacify.yml` rare, `piercing.yml` epic, `snare.yml` uncommon,
  `sniper.yml` legendary, `hellfire.yml` uncommon — so `pacify`, `snare` and
  `hellfire` ship BELOW their appendix rung, tier 4 / 3 / 4). Backfill 05 and
  re-check those five against it.
  Where the pack ships no prior rung, the codex's own registration appendix
  (`# | display | class | pkg | tier | max | base | interval`) IS the missing
  data and is what the batch-05 drop-ins used, noted in-file: `virus.yml`
  uncommon (tier 2, max 4), `teleportation.yml` and `venom.yml` rare (tier 3,
  max 5 / max 3), `unfocus.yml` epic (tier 4, max 5), `teleblock.yml` soul
  (tier 6, max 5 — the one entry whose tier the matrix DOES state, and the
  appendix agrees). The backfill should copy that appendix wholesale rather
  than re-deriving 22 rungs by hand. The bow-family batch took the same
  appendix, in-file on each: `explosive.yml` and `cowification.yml` uncommon
  (tier 2, max 5 / max 3), `farcast.yml` rare (tier 3, max 5),
  `arrow-lifesteal.yml`, `eagle-eye.yml` and `dimension-rift.yml` epic (tier 4,
  max 5 / 5 / 4), `healing.yml` and `lightning.yml` common (tier 1, max 4 /
  max 3), `infernal.yml` rare (tier 3, max 3) and `longbow.yml` epic (tier 4,
  max 4). Every one of those maxes is corroborated by the entry's own
  numbers ladder, so only the rungs are appendix-only.
- **`matrix/05` § Sniper — the Rage-immunity key has no writer under that
  name.** The entry gates on `effectedByRage` "written by the sword enchant
  Rage", but `matrix/03` § Rage decomposes exactly one victim-side stamp,
  `raged` (4 t / 200 ms), and that is what `rage.yml` publishes and
  `execute.yml` already reads. Same window, same subject, so `sniper.yml` reads
  `%victim.var.raged%`; the matrix line should either name `raged` or say which
  writer is owed for a second key.
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
- **`matrix/05` § Hijack — two decomposition rows the surface cannot honour.**
  The gate is written `%victim.mobtype% == "IRON_GOLEM"`, which is the
  MythicMobs soft hook (ADR-0027) and resolves to the empty string with no
  integration installed — the same fact the Boss Slayer row above corrects;
  `%victim.type%` is what an iron-golem gate needs. And step 1,
  `CONVERT_SUMMON(radius=1, who=@Victim)`, cannot express "flip THIS summon":
  the effect is the Grand Bell (ADR-0071), player-targeted and ringed around
  that player, so a golem target resolves to nothing. That second one is why
  the entry is deferred rather than shipped (row above), not a wording fix.
- **`matrix/05` gap index — four gaps already closed.** `TARGET_RELATION_FACT`
  ships as `%victim.relation%` (ALLY/ENEMY/NEUTRAL), `RANDOM_RANGE_PARAM` as
  the `rand(lo, hi)` expression function (`floor(rand(L, 3L))` is Healing's
  integer roll), `DURABILITY_PIECE_SELECT` as `DURABILITY select:
  most-damaged`, and `SUMMON_REBIND_UPGRADE` as the `SUMMON_REBIND` effect —
  the batch-1 staleness pattern above, confirmed on doc 05.
- **`matrix/02` §§ Spirits and Undead Ruse — "no attacker-type fact exists" is
  wrong, and the widening it justifies is unnecessary.** Both entries drop the
  jar's player-damager requirement and note the widening to mob melee. On a
  DEFENSE pass the "victim" IS the attacker, so `%victim.type% == "PLAYER"` is
  exactly that fact — `guardians.yml` (batch 01, the same jar condition) has
  shipped it since the first content batch. Both files are authored with the
  gate; the matrix lines should drop the widening note.
