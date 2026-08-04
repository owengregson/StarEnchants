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
| ~~`enchants/plague-carrier`~~ | 02 | ~~SUMMON_PAYLOAD (detonate burst, terrain=none); legacy note: CREEPER_HISS alias unverified~~ **AUTHORED in batch 5** — the creeper burst is a second ability on the `SUMMON_PAYLOAD` trigger, configured by the charged creeper's `payload-*` box (`ALL`, the jar's measured no-ally-filter asymmetry); the CREEPER_HISS alias has since LANDED in `Aliases.SOUND` (`CREEPER_HISS→ENTITY_CREEPER_PRIMED`, verified against both era constant lists), so the legacy lane plays the burst; one clause pending below | closed |
| `enchants/nutrition` | 02 | MODIFY_FOOD `mode=absolute` (matrix ships measured absolute semantics, not delta) + EAT is a held-only trigger (worn leggings never walk it) — both need rulings | wave 1f/2 |
| `enchants/repair-guard` | 02 | `%item.durabilitypercent%` fact (the EQUIP_CHANGE gap's unshipped half); authoring without it reproduces the jar's fatal proc-on-every-unequip | wave 1f |
| ~~`enchants/metaphysical`~~ | 02 | ~~SHIPPED as a roster marker (effect-less PASSIVE, boots); its consumers (Trap/Snare/Pummel) need a victim worn-enchant-level fact for the reduction clauses~~ **CLOSED by the wave-1f enchlevel facts** — `trap.yml` and `snare.yml` author the rebate as a chance expression over `%victim.enchlevel.metaphysical%` (the key is the file STEM; the `enchants/metaphysical` stable-key spelling reads 0 in silence). The marker file stays effect-less for good. `pummel.yml` (batch 06) and `titan-trap.yml` (batch 06, `max(1, <base> - 1.25 × …)`) author theirs the same way, so all four consumers now read the marker; only the blocked-proc line they share is still owed, and Pummel's per-target SUBJECT — both on their partial rows below | closed |
| ~~`enchants/sticky`~~ | 02 | ~~shipped as a roster marker (same victim worn-level fact for its consumers)~~ **CLOSED by the wave-1f enchlevel facts** — `disarmor.yml` authors `-0.25 pp × %victim.enchlevel.sticky%` on every rung, with no floor: the expression chance's [0,100] clamp IS the recorded absolute immunity at equal-or-higher Sticky | closed |
| `enchants/kill-aura` | 03 | two independent blockers: the activation gate needs the masks family's monster-summon state on the victim (doc-11 interaction fact, nothing on the frozen surface expresses it), and the `killaura` var it writes is matrix-UNRESOLVED — no consumer exists anywhere in the jar corpus. Authored without the gate it would burst EXPLOSION_LARGE on ordinary hits to write a var nobody reads. | batch 11 + owner ruling on the var |
| `enchants/hero-killer` | 04 | the victim worn-gear fact, and the wave-1f enchlevel facts do NOT supply it. The gate is "victim wearing ≥ 1 Heroic Armor piece" — a COUNT of worn pieces carrying an item-model classification (matrix 06: a worn LEATHER heroic-armor piece; `HeroicStat`, not an enchant), whereas `%victim.enchlevel.<key>%` returns the LEVEL of ONE custom enchant keyed by its file stem. There is no enchant key naming heroic armour (cosmic-pack ships no heroic items at all yet), and no fact family counts worn pieces, so no spelling of the new fact expresses the gate and a level must not be substituted for a count. Everything else IS expressible today, so this stays a one-clause drop-in: `soul-cost: 4` carries the whole soul half (a soul-cost ability never fires outside soul mode, and the charge is the availability gate — the as-intended reading of D-04-11's over-draw), `%heldticks% > 5` is the held-swap gate, `%soultrap% != 1` the actor-side Soul Trap read (authorable whether or not Soul Trap itself ships). Authored WITHOUT the heroic gate it would hand a flat +10/20/30 % against every player, which is a different enchant. | WORN_GEAR_FACT — a worn-armour classification/piece-count fact |
| ~~`enchants/solitude`~~ | 03 | ~~SHIPPED as a roster marker (effect-less HELD, sword). Its whole payload is the `%solitude%` marker §Silence reads, and the matrix's "cleared on un-hold" half has no expression: `SET_VAR` has no lifecycle-teardown, so the recorded `ttl=0` marker written on take-hold never drops and one take-hold would boost every later Silence off any weapon, forever. Needs either a SET_VAR HELD/PASSIVE stop half or an actor-side co-held worn-enchant-level fact (which would drop the marker entirely).~~ **CLOSED by the wave-1f enchlevel facts** — the second route landed and the marker is dropped entirely: `silence.yml`'s S = 0..3 ladder now reads `%actor.enchlevel.solitude%`, whose map is built from the wielder's resolved equipment (armour **plus** the held hands — a non-armour held item stays in it), so a held Solitude sword reads while held and stops reading when it leaves the hand. No SET_VAR, so no teardown to miss. One recorded delta on `silence.yml`: the read is per-WIELDER, not per-item, so an off-hand Solitude sword also boosts (and can boost a Silence BOW), where the jar demanded the same item | closed |
| `enchants/target-tracking` | 05 | a victim-name token on `RUN_COMMAND`. The entry's ENTIRE payload is one command, `f focus <the struck player>`, and `RUN_COMMAND` substitutes ACTOR tokens only (`{PLAYER}`/`{UUID}`/`{WORLD}`); `{VICTIM}` exists on `MESSAGE` but not here, and the effect takes no `who` target, so nothing can name the target of the hit. Authored as-is it would run the literal string `f focus {VICTIM}` on every hit. Second, softer question for the same file: SE has no factions of its own and the matrix itself says the pack omits or retargets this enchant on a stack without a factions plugin — the command string is server-configurable, so the pack-side ruling (ship the bridge, or ship nothing) is owed alongside the token | wave 1f (token) + owner ruling (factions bridge) |
| `enchants/soul-trap` | 04 | `SOUL_MODE_DISABLE` — an effect forcing a target player out of soul/god mode; the wave-2 soul family. Nothing ELSE in the entry blocks: `HELD_SWAP_GATE` ships as `%heldticks%`, `SOUL_STATE_FACTS` as `%actor.souls%`/`%victim.souls%`, `VICTIM_VAR_FACT` as `%victim.var.<name>%`, and the drain / trap-window / re-trap-immunity rows are REMOVE_SOULS + SET_VAR today. Shipping without it would bill the attacker 5 souls, drain the victim's gem and advertise `** SOUL TRAP [Ns] **` while leaving the victim in the very mode the trap exists to break — and Hero Killer's `soultrap` self-gate would read a var that marks nothing. Carries its own era debt when it lands: `ENDERMAN_SCREAM→ENTITY_ENDERMAN_SCREAM` plus the `WITCH_MAGIC`/`SPELL` particle aliases. | wave 2 |
| `enchants/heroic-enchant-reflect` | 06 | `PROC_REFLECT` — the same incoming-direction primitive its base `enchants/enchant-reflect` (row above, batch 02) is stopped on: on an incoming hit, re-execute the ATTACKING item's triggered abilities with the roles swapped, gated by attacking-enchant tier ≤ `tier-max` AND reflect level ≥ that enchant's level, and then NOT applying the reflected enchant normally for that hit. Nothing on the surface does it — `REFLECT` returns a damage percentage, `ECHO_STRIKE` re-runs the actor's OWN attack, and neither re-runs the attacker's procs against him. There is no second half to ship: the entry's whole decomposition is that one row, and everything else recorded is its parameters (max 10, ladder `0.02 + 0.01×(level/3)` = 2/3/4/5 % over four integer-division steps, victim-must-be-a-player, 20-armor set, base 25.0 interval 10.0, table weight 10). The heroic grade differs from the base ONLY in `tier-max` (7 vs 5), so it reflects other heroics and soul enchants; authored without the primitive it would be an empty PASSIVE marker with no consumer anywhere. Lands WITH its base, which the contract's REPLACE pairs it to, and brings the reflect-priority chain with it (mastery `tier==8` else heroic `≤7` else normal `≤5`, exclusive — the highest-priority branch runs even when a lower one sits at a higher level) | wave 2 (with `enchants/enchant-reflect`) |
| `enchants/skilling` | 06 | `EXTERNAL_SKILL_XP_MULTIPLIER` — multiply the XP a registered third-party skill system awards, filtered to skill categories (`factor`, `categories`). The entry's ENTIRE decomposition is that one row (`1.0 + 0.04 × L`, +4 %…+40 % on the five gathering skills), so there is no second half to ship. Nothing on the surface can observe another plugin's XP events: `EXP_GAIN` / `EXP_MULTIPLY` cover VANILLA player XP only, and authoring it there would multiply the wrong economy entirely. The matrix additionally hands the scope call to the spec owner (drop the entry, or take the soft-depend), and the jar's hook is a RAW listener that bypasses every dispatch gate — it works in The End and through outpost tier suppression — so routing it through normal dispatch (END-SUPPRESS then applies) is a second, felt ruling owed at the same time. Acquisition is fully recorded for whenever it lands: max 10, table weight 2, the 21-tool item set, base 15.0, interval 6.0, and tier 2 → `uncommon` from the registration appendix | owner ruling (soft-depend vs drop) + the primitive |
| `enchants/atomic-detonate` | 06 | `BLOCK_MATERIAL_FILTER`, plus the asymmetric half of `FACE_ORIENTED_BOX_SELECTOR`. The volume gap is HALF closed — `@Bore{half-width, half-height, depth}` IS the matrix's face-oriented box and spells L2 (5×5×5) and L4 (7×7×7) exactly — but BORE's cross-section is symmetric and the jar's L1 (4×4, extents 1,2,1,2) and L3 (6×6, extents 2,3,2,3) are not, so two of the four rungs have no spelling. The filter gap is not closed at all, and it is the real stop: `BREAK_BLOCK` carries NO material guard anywhere (`DispatchSinkBase.breakBlock` breaks whatever the selector hands it), and BORE's `materials` is an ALLOW list whose complement — the jar's 21-entry deny list (OBSIDIAN, BEDROCK, fluids, doors, hopper, anvil, comparators…) — is unbounded and unwritable. Shipped as-is, one swing would delete bedrock, obsidian and spawners inside a 343-block cube, with neither the pickaxe/spade tool-class sublists nor the 7-material void-drops list (`BREAK_BLOCK.drops` is all-or-nothing) expressible either. Both gaps are shared with the base `enchants/detonate`, whose 3×3×depth slab BORE DOES express — the base is blocked on the deny list alone. Nothing else on the entry blocks: D-06-10's selling component is out by ruling R9, and D-06-11 / D-06-2 (origin double-drop, cached-face NPE) are fixed structurally by the engine's own break path. Its heroic metadata is a drop-in the moment the filter lands — `requires: [enchants/detonate]` + `removes-required: true`, tier 7, the 21-tool `applies-to`, max 4, weight 6, always 100 %, `PARTICLE(EXPLOSION_LARGE)` per block and one `DURABILITY(damage 1)` per explosion | `BLOCK_MATERIAL_FILTER` (deny list + tool-class sublists + void-drops) on the block-volume family, and asymmetric extents on `@Bore` |
| `enchants/hijack` | 05 | target-scoped summon conversion. The upgrade half landed: `SUMMON_REBIND` carries the fresh full-health respawn, the Guardians ladder (`health` 70/90/110/130, FIRE_RESISTANCE plus REGENERATION/INCREASE_DAMAGE/SPEED), the restarted 600 t self-destruct, the rename and `rise: 2`. But it only ever replaces a summon the ACTIVATOR ALREADY OWNS, and the one ownership-transfer effect — `CONVERT_SUMMON` — is the Grand Bell (ADR-0071): it takes a PLAYER target and rings its radius around THAT player, so it cannot flip the single remote golem an arrow just struck. The matrix's `CONVERT_SUMMON(radius=1, who=@Victim)` is inexpressible (a non-player target resolves to nothing, and `who=@Self` would ring the shooter). Authored anyway, the file would roll 8/16/24/32 %, broadcast `§5§l*** HIJACK (…) ***` to everyone inside 24 blocks and leave the golem serving its original owner — the theft IS the enchant. Nothing else blocks: the gate is `%victim.type% == "IRON_GOLEM"` (not `%victim.mobtype%`, the MythicMobs hook — the Boss Slayer ruling), and the whole guard loadout is SUMMON_REBIND params. Carries era debt when it lands: `IRONGOLEM_DEATH→ENTITY_IRON_GOLEM_DEATH` (shared with Guardians, batch 01). Considered and rejected: faking the outcome with `DESPAWN(@Victim)` + `GUARD` — GUARD targets `T.ATTACKER`, which a BOW activation does not have, it has no `rise`, and with no ownership precondition it would delete ANY iron golem, minting a free tier-8 guardian off a farmed wild one. | wave 1f — a `who`-targeted convert, or an ownership-stealing flag on SUMMON_REBIND |
| `enchants/ghostly-ghost` | 06 | the SAME matrix-UNRESOLVED external metadata consumer that stopped `enchants/ghost` in batch 01, one rung up: matrix 06 records that no reader of `heroicGhostEnchantment` (nor of the non-heroic `ghostEnchantment`) exists anywhere in the decompiled tree, so the whole payload is a marker nobody consults. Its own entry says "ships as an inert marker", and that reading is NOT taken, for two reasons. First, one family, one call: the base is deferred on this exact blocker and shipping the heroic while the base waits would split the pair. Second, the heroic APPLY-GATE makes the file unreachable anyway — `requires: ["enchants/ghost"]` cannot be satisfied on a pack that ships no Ghost, so the book would roll (weight 0: heroic-book only) and never apply to anything. It is NOT a roster marker in the metaphysical.yml / sticky.yml sense either: those ship because consumers inside this pack read their worn level, and this one has no reader at all. Everything else is a drop-in the moment the owner rules — PASSIVE, 20 armour, max 3, `SET_VAR(name=ghostly-ghost, value=L, ttl=0)` with the engine's WornState teardown replacing the jar's unconditional unequip clear (the engine keeps the highest remaining level, which is the better half of that quirk) | the `enchants/ghost` owner ruling (batch 01) — one decision covers both grades |
| `enchants/detonate` | 06 | `BLOCK_MATERIAL_FILTER` — the per-block DENY list. The other half of the matrix's gap pair is CLOSED: `@Bore{half-width=1, half-height=1, depth=D}` is exactly the recorded 3x3 slab marched D layers into the mined face, and its `depth` counting the activation block's own layer reproduces the jar's 9xD volume block-for-block (the recorded 10/19/28 "blocks touched" totals are 9xD plus the D-06-3 origin re-process, which the engine never does). What is left has no expression: `@Bore`'s `materials` is an ALLOW list ("keep only these"), and the entry's filter is a 22-material DENY list plus tool-class-conditional sublists plus a comparator/diode-above guard. Inverting it would mean enumerating every mineable block on every supported version, and the omission is not cosmetic — authored without the filter the volume breaks BEDROCK, obsidian, fluids, doors, hoppers and anvils on a 100 %-at-L3 proc, which is a server-grief tool, not a mining enchant. Everything else is a drop-in when the filter lands: the depth roll is per-level chance on two ordered abilities (hi-depth rule then fallback), `PARTICLE(EXPLOSION_LARGE)` and `DURABILITY(amount=1, mode=damage, target=item)` ride the same list, and D-06-2's "orient from the mined face" is what `@Bore` already does. Its era debt travels with it (`SMOOTH_BRICK`/`MYCEL`/`*_SPADE` aliases, `LARGE_EXPLODE`→`EXPLOSION_LARGE`). Sibling `heroic/atomic-detonate` needs the same filter AND the asymmetric extents `@Bore` does not carry. | `BLOCK_MATERIAL_FILTER` (deny + tool-class sublists + void-drops) |
| `enchants/experience` | 06 | block-provenance on `EXP_GAIN`. The entry is one `EXP_MULTIPLY(1.0+0.25L)` behind the gate `%isblock% == true`, and that gate cannot be satisfied: `%isblock%`/`%block.type%` are populated ONLY from an activation's block, and the only `EXP_GAIN` fire path (`TriggerListeners.onExpChange` → `fireExp`) builds a blockless self-context, so the fact is false on every XP gain. Authored WITH the condition the enchant is silently dead forever; authored WITHOUT it, a held tool multiplies XP from mob kills, bottles, furnaces, trading and fishing by up to 2.25x — an economy-wide change, not the recorded block-XP boost. `EXP_MULTIPLY` on the MINE trigger is not a way around it either: `fireMine` never reads the sink's XP multiplier (only `fireExp` does), so it would compile and do nothing. Everything else about the entry is trivial (5 flat levels, no chance, no cooldown). | either the XP source block on the `EXP_GAIN` context (a `BlockExpEvent` hook feeding `%isblock%`), or an XP read-back on the MINE path |
| `enchants/obsidian-destroyer` | 06 | block facts on `INTERACT_LEFT`. The entry is `BREAK_BLOCK(drops=true)` at the clicked block under `%block.type% == "OBSIDIAN"` at `20xL` %, and the trigger carries no block: `TriggerListeners.onInteract` builds `ActivationContext(player, null, null, player.getLocation())`, so `%block.type%` is empty on every left click even though `PlayerInteractEvent#getClickedBlock` has it. Neither half survives that. The gate authored as recorded never matches (dead enchant); dropped, the ability instant-breaks WHATEVER `@Block{distance=5}` ray-traces onto — at L5 that is a 100 %-per-left-click block eraser for the whole world, the worst possible failure. The volume selectors are no substitute: they carry a `materials` allow list but centre on the activation block, which on this trigger is the player's own feet. MINE is not the trigger either — the enchant exists to skip the 9-second obsidian break, so a hook that fires only once the break finished is not the enchant. | the clicked block on the `INTERACT_LEFT`/`INTERACT_RIGHT` activation context (feeds `%block.type%`/`%isblock%` and gives `@Here` a real block) |

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
| `enchants/trap` + `enchants/disarmor` + `enchants/snare` + `enchants/titan-trap` | ~~the Metaphysical / Sticky chance rebates and Trap's 1 % floor~~ **AUTHORED off the wave-1f enchlevel facts** (`max(1, <base> - 2.5 × %victim.enchlevel.metaphysical%)` on Trap, `<base> - 0.25 × %victim.enchlevel.sticky%` on Disarmor, `<base> - 7 × %victim.enchlevel.metaphysical%` on Snare — no authored floor where the matrix records none, since the expression chance clamps to [0,100] and that 0 bound IS the absolute immunity). Each rung has since gained the heroic grade's own additive term at the same coefficient — see the `enchants/polymorphic-metaphysical` row. What remains on all three is ONLY the victim-shown blocked-proc line (`§8§l** METAPHYSICAL (§8Trap blocked!§l) **` / `(§8Snare blocked!§l)`, and Sticky's Disarmor equivalent, which the matrix never quotes) | not a fact these files can read: the matrix fires each line "on a would-be proc", and its own decomposition is a PRE-roll chance subtraction it calls distribution-identical — so the engine rolls once, at the already-reduced chance, and nothing reports a roll that failed only because of the rebate. Re-gating on the FULL-immunity regime was considered and rejected: it would print only when the victim is already immune, and Trap's D-03-11 floor deletes that regime, so the consumers would diverge — and Titan Trap's own 0.01 floor means it is NEVER fully blocked, so a full-immunity gating would print its line exactly never. Closes with a post-gate "rebate blocked this proc" hook (or the matrix's interaction-rule layer, which the surface has no analogue for) | follow-up candidate |
| `enchants/pummel` | ~~the Metaphysical rebate~~ **AUTHORED as a batch-06 drop-in** (`<base> - 4 × %victim.enchlevel.metaphysical%` on every rung, no floor — the expression chance's own [0,100] clamp is the bound the matrix records; the heroic grade's additive term at the same coefficient landed with the `enchants/polymorphic-metaphysical` ruling). What remains is the SUBJECT and the blocked line `§8§l** METAPHYSICAL (§8Pummel blocked!§l) **`. D-04-13 wants a PER-TARGET veto; a chance is rolled ONCE per activation and no fact names a selector target, so the only readable Metaphysical level is the STRUCK victim's — who is `exclude=victim` and never in the splash. What ships is therefore "the player you hit rebates the splash on everyone around them": the jar's crowd-shielding shape at the recorded magnitude and direction, keyed to the wrong body | the per-target half needs a fact addressing the AoE member under test (nothing on the surface addresses a selector target from a condition or a chance); the blocked line stays out for the reason the row above gives | follow-up candidate (subject + line) |
| ~~`enchants/corrupt`~~ | ~~the Inversion carve-out: a victim HOLDING an Inversion item takes the flag and the portal burst but NONE of the DoT rows (jar order kept)~~ **AUTHORED as a batch-06 drop-in** off `%victim.enchlevel.inversion%` (the worn map spans armour plus the held hands, so a held Inversion sword reports its level; `%victim.helditem%` was only ever a material, which is why the row existed). Shipped as TWO MUTUALLY EXCLUSIVE branches per level (`… && %victim.enchlevel.inversion% == 0` for the rot, `… > 0` for flag+burst only) rather than one ability with a gated tail: the surface has no per-effect condition, and two independently-rolled abilities would let the flag land without the rot — disjoint conditions keep it to one roll at the recorded chance. The enchant's root `condition:` is gone with them, since an ability condition REPLACES it rather than anding. The other direction was already CLOSED in batch 4 (swords/Inversion reads the flag actor-side as a bare `%corrupt%`) | nothing — the fact landed in wave 1f and the clause is authored | closed |
| `enchants/bleed` | the PLAYER-side derived slow — `MOVEMENT_SPEED(speed = "0.2 - 0.005 * i")` held for as long as stacks live — and the death half of D-04-6 (stacks clear on death). The counter itself, its gate, the mob SLOWNESS branch and the crack burst all ship | two engine halves: (a) a walk-speed grant with no expiry (`ticks` is a finite span, `0` reverts on the spot), i.e. the derived-modifier hook the matrix's STACK_COUNTER gap describes, or a `SET_VAR` state-tied modifier; (b) a player-side var death clear — `EntityVarCleanupListener` deliberately sweeps MOBS only, so a bled player's stacks currently survive their own death | wave 1f |
| `enchants/cleave` | the recorded PER-SPLASH-VICTIM 20 t (1000 ms) stamp, shipped as the ability's own 30 t per-player bucket | per-victim cooldown scope — the identical stop the `enchants/thundering-blow` row carries; one attacker's splash now paces across targets | follow-up candidate |
| `enchants/thundering-blow` | the recorded PER-VICTIM 50 t cooldown, shipped as the engine's per-player bucket | per-victim cooldown scope; the coarsening is recorded here rather than as a deviation (one attacker's proc now paces across targets) | follow-up candidate |
| `enchants/mighty-cleave` (batch 06) | the recorded PER-SPLASH-VICTIM 20 t stamp, shipped as the ability's own 30 t per-player bucket — the counterpart's row above, one grade up. The SHARED half IS authored here (`SUPPRESS(scope=ENCHANT, key=enchants/cleave, duration=20, who=@Self)`, the mutual lockout `cleave.yml` assigns to the heroic side), coarsened from the victim to the holder | per-victim cooldown scope, exactly as `enchants/cleave` and `enchants/thundering-blow` carry it | follow-up candidate |
| `enchants/polymorphic-metaphysical` (batch 06) | ~~TWO clauses, and together they leave the file INERT in play.~~ ONE clause now. ~~(a) The downstream rebate feed: `%victim.enchlevel.<key>%` keys on the FILE STEM (`WornResolver.stemOf`), so this grade publishes `polymorphic-metaphysical` while `trap.yml` (−2.5 pp), `titan-trap.yml` (−1.25 pp) and `snare.yml` (−7 pp) all read `metaphysical` alone and see 0 for a wearer carrying only the heroic — the three files' "the heroic writes the SAME worn state" comments do not hold on the shipped fact, and Pummel's −4 pp will inherit the same trap.~~ **(a) CLOSED by owner ruling — the feed is ADDITIVE, not highest-of-the-two.** All four consumers now carry a SECOND term at their own coefficient, `<base> − <coef> × %victim.enchlevel.metaphysical% − <coef> × %victim.enchlevel.polymorphic-metaphysical%`: `trap.yml` −2.5 pp and `titan-trap.yml` −1.25 pp keep their `max(1, …)` floors (D-03-11 and the entry's own 0.01), `snare.yml` −7 pp and `pummel.yml` −4 pp keep none, the expression chance's [0,100] clamp being the bound. The heroic's coefficient repeats the base's because matrix 06 § Polymorphic Metaphysical records the heroic's stored level feeding the SAME reductions. `max()` was the shape this row previously proposed and is not what shipped: additive and max() are indistinguishable in play (both grades are BOOTS-only and the contract's REPLACE keeps them off one item, so the two terms are never non-zero together), and additive is the safe reading if a pack ever separates the grades. The four files' stale "the heroic writes the SAME worn state" comments are rewritten to the stem-keyed truth. (b) The Ice Aspect veto (`0.2 × level` = 20/40/60/80 % per proc) and its wearer-shown line `§d§l** POLYMORPHIC METAPHYSICAL (§7Ice Aspect blocked!§d§l) **`. The roster marker itself ships (applies, renders, occupies its rung) | (a) ~~NOT an engine gap — a one-term edit per consumer, `max(%victim.enchlevel.metaphysical%, %victim.enchlevel.polymorphic-metaphysical%)`, which spells the highest-only rule out; four files across three batches, so it is recorded rather than reached from the heroic's own file.~~ **AUTHORED** — never an engine gap, and the four consumer edits landed in one pass on the owner's additive shape. (b) the interaction-rule layer the Metaphysical blocked-proc lines already wait on: a chance-per-INCOMING-proc veto. SUPPRESS cannot stand in — the attacker's ATTACK walk runs BEFORE the defender's DEFENSE walk in one event, so a defensively armed window parks Ice Aspect a hit late, for a fixed span, against every other victim too; `mode: next-hit` is consumed on the target's own incoming hits, not their next swing | (a) ~~drop-in~~ closed + (b) follow-up candidate |
| `enchants/spirits` | the ally heal pulse never reaches its effects. The payload ability is authored and configured (period/radius/ALLIES/max-targets all per level), but every block of an enchant shares ONE cooldown bucket, and the 200 t re-arm the DEFENSE spawn takes covers the blaze's whole 200 t life — gate 6 denies every pulse it could ever fire. The blaze, its buffs, its name and the proc cues all ship | per-ability cooldown-scope opt-out — the identical stop `enchants/rocket-escape`'s FALL companion carries in the wave-1f pool below, total here rather than partial (cooldown == summon lifetime, so no pulse survives it) | wave 1f |
| `enchants/guardians` + `enchants/spirits` + `enchants/undead-ruse` | the summon NAME's owner token. All three author the matrix's verbatim `{owner}` string as `{player}` (`§b§l{player}'s Guardian`, `§c§l{player}'s Spirit`, `§d§l{player}'s Undead Minion`), but nothing substitutes it: `GUARD`/`SPAWN_ENTITY`/`SPAWN_SWARM` hand `name` to `DispatchSinkBase.applyGuardName`, which only runs `Colors.translate` — so the nameplate reads the literal `{player}`. `{ATTACKER}`/`{VICTIM}` are `MESSAGE`-only, `{PLAYER}` is `RUN_COMMAND`-only, and no `{player}` substitution exists anywhere in the tree. Everything else on all three files ships | an actor-name token on the summon-name param — the one substitution the three spawners share. Not a stop: the nameplate is cosmetic and the idiom has shipped since batch 01, so the pack is internally consistent and flips in one pass when the token lands (`SUMMON_REBIND`'s rename takes the same param, so Hijack inherits the fix) | wave 1f |
| `enchants/undead-ruse` | the minion OWNERSHIP half: the jar's minions never target or hurt their summoner, and these will — vanilla zombie AI takes the wearer standing inside their own ring. Count, buff amplifiers, names, permanence, the vanish window and all three particle bursts ship | an `owner` param on SPAWN_SWARM. `owner: activator` on SPAWN_ENTITY/GUARD binds `GuardianCasts`, which `SummonTargetGuardListener` reads to cancel a summon acquiring its owner; the swarm spawner binds only `SwarmSpawns` (disable-teardown), so there is no ownership to read | wave 1f |
| `enchants/self-destruct` | the per-level FUSE ladder (100/80/60 t = 5/4/3 s): all three levels ship on vanilla's own 80 t fuse | the summon surface has no fuse param, and `ttl` is a DESPAWN rather than a detonation — any ttl at or under 80 t removes the charge BEFORE it can explode, so an authored 60 t would leave L3 spawning duds (ttl is omitted instead; a primed TNT always leaves by exploding). Closes with a `fuse` param on SPAWN_ENTITY, the shape `powered` already takes for creepers | wave 1f |
| `enchants/plague-carrier` + `enchants/self-destruct` | the payload only lands while the OWNER still wears the piece. Both entries are death-triggered — Plague Carrier finishes the wearer itself, Self Destruct lets the lethal hit stand — and `SummonPayloadService` runs the owner's abilities out of their LIVE `WornState`. Death drops the armour, the armour-change feeder (modern) / gear poll (1.8) refreshes off that drop and respawn refreshes again, so the blast most likely resolves for an owner who no longer carries the enchant — and it leaves a DUD, because the detonate phase has already cancelled the vanilla explosion. Everything else ships. Both files also express their recorded 200 t re-arm without `cooldown:`, for the shared-bucket reason the `enchants/spirits` row above carries — a cooldown on the proc would deny the detonations that follow it: Plague Carrier drops the re-arm (its own `KILL` makes one unreachable) and Self Destruct rides a 200 t `SET_VAR` marker in its condition | the payload's abilities snapshotted onto the SUMMON at spawn (or a worn-state read pinned to the spawning activation), so a summon can outlive its owner's gear the way the jar's did | wave 1f |
| `enchants/hellfire` + `enchants/infernal` | the BOW_FIRE flaming-arrow dressing — permanent (`2147483647` t) fire on Hellfire's shot, `<level>*60` t on Infernal's | `PROJECTILE_DRESSING` rides an ENTITY on the loosed arrow (`type`/`ttl`/`invulnerable`/`no-pickup`) and carries no fire, and nothing else addresses a shot in flight — `IGNITE` takes its targets from a selector and no selector names the projectile. Needs a fire-ticks param on PROJECTILE_DRESSING (or a projectile selector). Both files' direct-hit burns and landing AoEs ship whole, and neither ever depended on the dressing | wave 1f |
| `enchants/dimension-rift` | the REVERT hook: as each rifted block restores, players within 2 blocks lose Jump Boost and are popped upward (`REMOVE_POTION(JUMP_BOOST)` + `VELOCITY(mode=add, y=0.5)`). The soul-sand floor, the scattered web layer (`fill-chance` closed the matrix's `TEMP_BLOCK_FILL_CHANCE` gap), both particle aggregates, the level*15+40 timers and the player gate all ship | `TEMP_BLOCK_REVERT_HOOK` — an effect list executed at TempBlockLedger restore time, re-selecting by proximity to the restored blocks. `wait:` fires on a fixed offset from the activation, not on the ledger's own revert, and it cannot re-target off blocks it did not place | wave 1f/2 |
| `enchants/explosive` + `enchants/cowification` | two rider clauses. (a) The jar spawned Explosive's wither skull with `yield=0`/`incendiary=false` — pure scenery that never detonates — and `PROJECTILE_DRESSING` carries neither knob, while its `invulnerable`/`no-pickup` guards only apply to a LivingEntity rider (a WITHER_SKULL is not one), so the rider keeps vanilla skull behaviour. (b) The single-rider PRIORITY: the jar suppresses Cowification's cow when the bow also carries Explosive, and Explosive makes no reciprocal check; on the frozen surface the last `PROJECTILE_DRESSING` of the shot simply wins. Cowification's `ENTITY_COW_HURT` 1.0/0.7 cue to whoever strikes the cow is the same engine-owned lifecycle and is likewise unauthored | (a) yield/incendiary params on PROJECTILE_DRESSING (the same effect the `enchants/hellfire` + `enchants/infernal` row wants fire ticks on); (b) ~~no longer blocked~~ **AUTHORED as a batch-06 drop-in** — `cowification.yml`'s BOW_FIRE ability carries `condition: "%actor.enchlevel.explosive% == 0"` (the shooter's worn map spans the held hands, so their own bow reports its Explosive level) and `explosive.yml` stays check-free, exactly as measured. `venom.yml` stood its rider down for the same jar reason and could take the same gate, but its file records the collision as engine-owned instead — the two readings want reconciling in one pass. Clause (a) and the `ENTITY_COW_HURT` striker cue remain, both cosmetic (a dressing rider is scenery and is removed the moment the arrow lands), so this is recorded, not a stop | (a) wave 1f/2 |
| `enchants/teleblock` | the LAUNCH-time soul charge. The jar bills at the shot and stamps the arrow "this shot was funded", so a missed shot still costs souls; the port bills at IMPACT (`soul-cost` on the BOW ability) — same cost per LANDED shot, same soul-mode + all-or-nothing gate, same silent refusal, but a miss is now free. Everything else ships: the funded launch cues, the D-05-6 as-intended teleblock window, the pearl strip and the verbatim message | a per-projectile payload stamp — a BOW ability resolves off the bow, not off the arrow a BOW_FIRE ability paid for, so no cross-ability "this shot was funded" link exists. Billing at launch instead would leave the impact ability ungated (a free teleblock for a shooter with an empty gem), which is why the charge MOVED rather than being split; a var-armed window leaks the same way on a miss | follow-up candidate |
| `enchants/auto-smelt` | two clauses. (a) The Detonate stand-down: the jar disables Auto Smelt outright on a tool that also carries Detonate or Atomic Detonate, and inside those volumes smelting instead requires Auto Smelt AND Fuse. Nothing runtime-scoped expresses "inert while this item also carries X" (`blacklist:` is an APPLY-time refusal, and using it would forbid the exact Auto Smelt + Fuse + Detonate tool the in-volume rule is built on). Moot until an excavation entry ships, so it is recorded rather than approximated. (b) The ore ids: the gate is the jar's `IRON_ORE`/`GOLD_ORE` verbatim, while the engine's own smelt table also knows `DEEPSLATE_*`, `NETHER_GOLD_ORE` and the copper ores that postdate the jar — on modern versions most iron mined below y=0 is deepslate and this enchant will not touch it | (a) a runtime co-enchant suppression scope, or the Detonate entries landing and carrying the rule themselves; (b) an owner era ruling on widening jar-era block ids to their post-flattening variants (the same question every future block-gated entry will ask) | (a) with detonate; (b) owner ruling |
| `enchants/fuse` | nothing on the file — it is authored exactly as the matrix decomposes it (an effect-less marker, 5 pickaxes, max 1). Its ONLY meaning is the in-volume smelt rule on `enchants/detonate` / `heroic/atomic-detonate`, and both are stopped above, so the marker currently does nothing on any item. Recorded here so the pair is re-read together when the filter gap closes | the Detonate stop | with detonate |
| `enchants/haste` | nothing pending on the numbers — the entry ships whole (Haste I/II/III, flat 40 t, no roll, no cooldown). Recorded only so the two accepted widenings are traceable: `INTERACT_LEFT` also fires on a swing at open air (the jar's block-damage hook did not), and the jar's refresh path additionally demanded an iron or diamond pickaxe in hand, which matrix-06 explicitly declines to replicate across the 21-tool set | nothing — accepted per the matrix | closed |
| `enchants/guided-rocket-escape` | three clauses, all on the SABOTAGE and world edges. (a) The veto ships as a pre-roll chance subtraction (`chance: "100 - 10 * %sabotage%"`, reading sabotage.yml's own victim-side mark actor-side) — distribution-identical, the shape Trap/Snare use for Metaphysical — so the two halves that shape cannot carry are lost: the jar BURNS the 15 s cooldown on a vetoed escape (a failed roll arms nothing here), and the victim line `§c§l ** §7Guided Rocket Escape:§c§l SABOTAGED **` (leading space verbatim) fires "on a would-be proc" that one already-reduced roll cannot report. (b) The cooldown bucket the matrix records as SHARED with the non-heroic at asymmetric thresholds (15 s read here, 30 s there) is per-enchant in the engine, so each grade paces itself — only observable while both are worn on different pieces. (c) The duels-lowered launch (`y = 2+L` in `world_duels`/`world_duels2`) and the dungeon-parkour region veto are not ported: the first needs the whole ladder duplicated behind another deployment's world names, the second a region-flag surface SE has no analogue for. The `world_koth` refusal DOES ship, as `disabled-worlds:` — inert anywhere that world does not exist. Everything else is authored, including the FLY window on the closed `FLY_SPEED_PARAM` gap | (a) the post-gate "a rebate blocked this proc" hook the three Metaphysical consumers already want, plus a cooldown that arms on a vetoed activation; (b) a cooldown scope shared ACROSS two enchant keys (the engine interns one scope per enchant, and `none` is the only opt-out); (c) deployment topology, an operator edit rather than an engine gap | follow-up candidate |
| `enchants/infinite-luck` | the CONSUMER, which is the whole point of the entry. This file is authored exactly as the matrix decomposes it — a PASSIVE roster marker with zero local logic, effect-less rather than the recorded `SET_VAR(..., ttl=0)` for the Solitude reason (a ttl-0 marker written on a PASSIVE walk never drops), publishing its level as worn state for `%actor.enchlevel.infinite-luck%`. What has no home yet is matrix 10's threshold check: satisfied when the stored level ≥ N, over an accumulator each worn LEATHER heroic-armor piece raises by +12.5 (four pieces = 50.0; iron/diamond heroic pieces contribute 0), which the codex leaves UNRESOLVED past that point. Consequence worth stating plainly: `removes-required` strips Lucky as this lands, so until the consumer ships an upgraded item trades a working `level/400` death save for an inert marker — the jar's own arrangement, recorded rather than softened | two things, neither this file's: the matrix-10 semantics (owner/matrix ruling on the UNRESOLVED accumulator), and WORN_GEAR_FACT for the piece half — a worn-armour CLASSIFICATION and piece COUNT, the identical fact `enchants/hero-killer` is stopped on, which the wave-1f enchlevel facts do not supply | batch 10 + WORN_GEAR_FACT |
| `enchants/arrow-deflect` + `enchants/lethal-sniper` | the Lethal Sniper bypass: a Lethal Sniper arrow beats the victim's Arrow Deflect at `0.1 × its level` (10–50 %). The matrix authors it on ARROW DEFLECT, whose file predates this enchant and ships without it; `lethal-sniper.yml` carries the recorded rule in prose only. Everything else on both files ships | no longer blocked — a chance rebate on arrow-deflect.yml's deflect block reading `%victim.enchlevel.lethal-sniper%` (on its DEFENSE pass the "victim" IS the shooter, and the worn map includes the held bow). Left as a drop-in rather than reached into from batch 06, since the deflect block currently carries no `chance:` at all and the edit belongs with whoever next opens that file | drop-in |
| every heroic file (batch 06) + its non-heroic base | two halves of the shared heroic APPLICATION CONTRACT (matrix 06 § Heroic application rules). Each heroic ships `requires: ["enchants/<base>"]` + `removes-required: true`, which carries the APPLY-GATE, the REPLACE (the base's line is deleted, so the two never both run) and the SLOT-EXEMPT rule (`freedBy` makes the upgrade a net zero slots). What is NOT carried: (a) the gate's "at the partner's MAX level" gradient — `ItemEnchanter.checkRelationships` tests the prereq at `>= the level being applied`, so Deep Bleed II wants Bleed II rather than Bleed VI, and a partly-levelled base can be upgraded early; (b) REVERSE-BLOCK ("a non-heroic can never be applied over its heroic"), which `blacklist:` cannot carry from EITHER side. `ItemEnchanter.checkRelationships` tests it BIDIRECTIONALLY (`def.blacklist().contains(other) \|\| otherDef.blacklist().contains(def.key())`), so a `blacklist` on the base file fires just as surely on the heroic's own apply — where the base is the line `requires` demands be present — and refuses it. The two clauses are mutually exclusive on one pair of keys, and `requires` wins because it carries three contract rules to REVERSE-BLOCK's one. Consequence, and the reason not to "fix" it casually: re-applying the base over its heroic succeeds and both lines run — but that is also the ONLY way to climb a heroic's own ladder, since `removes-required` eats the prerequisite on every apply, so a hard REVERSE-BLOCK would freeze every heroic at the level it was first applied at. THIRD contract rule, settled by owner ruling rather than pending: **no heroic may carry `suppress-immune`.** END-SUPPRESS ("all tier > 5 enchants are inert while the holder is in The End", a SUPPRESS-scope world rule) has to be able to reach every tier-7 file, and `suppress-immune` exempts an ability from ALL suppression outright (`SuppressionStore.suppressesAny` returns false before it reads a single scope) — so the flag and the contract cannot both hold. It outranks the maintained-passive convention batches 1–2 apply to worn windows: `alien-implants.yml` (hunger lock) and `godly-overload.yml` (permanent HEALTH_BOOST) both shipped the flag and both had it removed, accepting that a silence window now lapses the lock and drops the buff where their non-heroic bases keep theirs. Any future heroic authored off a base that carries `suppress-immune` drops it at the grade seam | (a) a `requires-max` (or `requires-level: max`) flag on the prereq, since the reader has no notion of "the prereq's own ceiling"; (b) an apply-time rule that exempts the pair it names from the bidirectional blacklist test (or an upgrade-aware `requires` that accepts "the prereq OR this enchant already present") — an engine change, not a base-file sweep | (a) + (b) wave 1f/2 |
| `enchants/vengeful-diminish` + `enchants/diminish` | the cap BASIS is one hit stale. `DAMAGE_CAP` fixes its value at ARM time from `DamageCapStore.lastTaken`, and `CombatDispatch` records the arming hit only AFTER the defence walk folds and commits it — so the stored cap prices off the PREVIOUS committed hit, not the one that armed it. Both grades inherit it identically (the heroic at `factor 0.5` behind its own `-50 %` fold, the base at `1.0`), so the matrix's "half the (already-halved) arming hit" is the right number off the wrong hit. Everything else on both files ships, and the recorded ARMED-STATE SHARING turns out to need nothing: `DamageCapStore` is keyed per PLAYER with one armed cap and re-arm replaces it, which reproduces the jar's shared key exactly (matrix line owed — it predicted per-enchant state and called that a structural change) | an arm-time read of the IN-FLIGHT hit's folded damage (or recording last-taken before the defence walk, which would change what "last" means for every other consumer) | follow-up candidate |
| `enchants/titan-trap` + the Dragon Slayer set | the heroic's MEASURED exemptions are copied, not fixed: unlike its counterpart it does not fire the proc-veto event and ignores the set's `immune_freeze`. So the set-side rule the armour batch owes (`dragon-slayer-blocks-freeze`, with `§8§l* DRAGON SLAYER [§7Trap blocked!§8§l] *`) must be keyed to `enchants/trap` ALONE — keying it to the freeze family would silently align the pair and lose the recorded posture. Recorded here because the enforcement lives in a batch that has not been authored yet | nothing engine-side — a cross-batch keying constraint the sets batch has to honour (or an owner ruling to align the two grades with one interaction rule, which the matrix explicitly offers) | with the armour-set batch |

## Engine follow-up pool fed by these rows

- **Wave 2 critical path:** SUMMON_PAYLOAD (5 consumers above + sets/masks later),
  ESCALATING_SOUL_COST, PROC_REBOUND.
- **Wave 1f (small):** ~~victim worn-enchant-level fact and its actor-side twin~~
  **SHIPPED** as `%victim.enchlevel.<stem>%` / `%actor.enchlevel.<stem>%`, keyed
  on the file stem and reading a level of 0 for an enchant nobody carries. It
  closed the Metaphysical/Sticky consumers and Silence's co-held Solitude (which
  also retired the `SET_VAR` teardown that row wanted — an unheld sword just
  leaves the worn state), and left Corrupt's held-Inversion carve-out and
  Explosive's priority check as drop-ins. It did NOT close Hero Killer:
  WORN_GEAR_FACT is a worn-armour CLASSIFICATION and piece COUNT, not one
  enchant's level, and no fact family counts pieces — still open;
  a state-tied (unbounded) `MOVEMENT_SPEED` grant that lives until the counter
  feeding it clears, plus a player-side var death clear — the two halves of
  Bleed's player slow, and what the matrix's STACK_COUNTER "derived-modifier
  hook" actually asked for beyond the counter itself (`SET_VAR op=increment`
  shipped that half in wave 1);
  `%item.durabilitypercent%`; MODIFY_FOOD `mode=absolute`;
  EAT worn-scan ruling; ~~per-block cooldown-scope opt-out~~ **SHIPPED** as the
  per-ability `cooldown-scope: none`, which drops that block's enchant scope so
  it neither blocks on nor arms the shared bucket — batch 06's
  `guided-rocket-escape.yml` authors its FALL companion with it, and every row
  written before it is now a drop-in whenever that file is next opened (Rocket
  Escape's FALL companion is cooldown-starved by its own launch, and every
  SUMMON_PAYLOAD consumer pays it twice over: the proc that spawns the summon
  arms the bucket the payload must then pass — Spirits loses its whole pulse to
  it, and Plague Carrier / Self Destruct had to express their re-arm without
  `cooldown:`);
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
- **Block-context facts (batch 06, the mining family):** `%block.type%` /
  `%isblock%` populate ONLY from `ActivationContext.block`, which today only the
  MINE listener sets. Two triggers that obviously have a block do not pass one:
  `INTERACT_LEFT`/`INTERACT_RIGHT` (`PlayerInteractEvent#getClickedBlock` is
  right there, and passing it would also give `@Here` a real block on a click)
  and `EXP_GAIN` (no source provenance at all, so "block XP only" is
  unexpressible). Those two gaps stop `enchants/obsidian-destroyer` and
  `enchants/experience` outright. Third in the family, unrelated to facts:
  `BLOCK_MATERIAL_FILTER`, a per-block DENY/void-drops list on the volume
  selectors — `@Bore`'s `materials` allow list cannot express a 22-material deny
  list, and it is all that still blocks both excavation entries.
- **Legacy-sweep alias rows.** THIRTEEN ARE DONE — ~~`DIG_STONE→BLOCK_STONE_BREAK`,
  `FIREWORK_LAUNCH→ENTITY_FIREWORK_ROCKET_LAUNCH`,
  `FIREWORK_TWINKLE2→ENTITY_FIREWORK_ROCKET_TWINKLE_FAR`,
  `FIREWORK_BLAST→ENTITY_FIREWORK_ROCKET_BLAST`,
  `CREEPER_HISS→ENTITY_CREEPER_PRIMED`, `PISTON_EXTEND→BLOCK_PISTON_EXTEND`,
  `DRINK→ENTITY_GENERIC_DRINK`, `ANVIL_BREAK→BLOCK_ANVIL_BREAK`,
  `ZOMBIE_PIG_ANGRY→ENTITY_ZOMBIFIED_PIGLIN_ANGRY`,
  `CHICKEN_HURT→ENTITY_CHICKEN_HURT`,
  `MAGMACUBE_WALK2→ENTITY_MAGMA_CUBE_SQUISH`,
  `GHAST_SCREAM→ENTITY_GHAST_SCREAM`, `WITHER_HURT→ENTITY_WITHER_HURT`~~ all
  landed in `Aliases.SOUND` (the "cosmic-port legacy sweep" block), each verified
  against both the 1.8 enum and the modern constant list before landing. Those
  cover Inversion, Vampire, Disarmor, Disintegrate, Divine Immolation, Epicness
  and Silence (batch 03), Plague Carrier and Eagle Eye (batch 05) — and Plague
  Carrier's `CREEPER_HISS` pairing is now verified, retiring the "unverified"
  caveat its entry row above carries.
  STILL OWED:
  `SPLASH→ENTITY_GENERIC_SPLASH` (Blessed, batch 04 — the 1.8-era sound name;
  note `Aliases.PARTICLE` already carries an unrelated `WATER_SPLASH→SPLASH`),
  `MYCEL→MYCELIUM` (Devour, batch 04 — a `Aliases.MATERIAL` row, not a sound:
  the block-crack burst carries the material through the same resolver and
  `MYCEL` is the 1.8.9 spelling),
  `ARROW_HIT→ENTITY_ARROW_HIT` (Piercing and Longbow, batch 05) and
  `HURT_FLESH→ENTITY_PLAYER_HURT` (Sniper, batch 05; Lethal Sniper rides the
  same row, batch 06) — matrix 05 marks both entries era-clean, but
  `Aliases.SOUND` carries neither pair today;
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
  `BAT_TAKEOFF→ENTITY_BAT_TAKEOFF` (Dodge, batch 01, and its heroic Ethereal
  Dodge, batch 06 — `dodge.yml`'s era line already calls this "a resolver alias",
  but `Aliases.SOUND` carries no such row, so BOTH files silently drop the cue on
  the 1.8 lane; the pair is the ordinary `ENTITY_`-prefix drop);
  `WATER→BLOCK_WATER_AMBIENT` (Titan Trap, batch 06 — the matrix flags the
  rename on the entry; `Aliases.SOUND` carries no row, and `BLOCK_WATER_AMBIENT`
  IS in both modern fixtures) and, on `Aliases.MATERIAL` rather than the sound
  table, `STATIONARY_WATER→WATER` (the same entry's head-height water cue: 1.8
  splits still from flowing water and the file authors the modern `WATER`, so the
  legacy lane places the flowing block or nothing until the sweep rules);
  `ZOMBIE_METAL→ENTITY_ZOMBIE_ATTACK_IRON_DOOR` and
  `WITHER_SHOOT→ENTITY_WITHER_SHOOT` (Bidirectional Teleportation, batch 06 —
  both flagged on the matrix entry, neither in `Aliases.SOUND`; the file authors
  the modern spellings, so the grapple's whole two-cue burst is silent on the
  1.8 lane until the rows land. The same entry also rides the existing
  `ORB_PICKUP` row for its ally blink; `reflective-block.yml`, batch 06, rides
  the `ZOMBIE_METAL` row too — it is that entry's ONLY cue, so the reflect lands
  silently on the legacy lane until the row does);
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
  and rewrites the alias row to match. The row itself has LANDED on the settled
  spelling, so the legacy lane resolves today; only the character question is
  still open.

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
- **`matrix/06` § tool enchantments — the acquisition rows carry no tier.** The
  nine tool entries end on `max N; table weight W; item set …; base X, interval
  Y` and never state a rung, unlike docs 01–04 (and unlike the doc's own heroic
  entries, which all say tier 7). The rungs exist and the doc even prints them
  in its own preamble table, one line above the entries: `detonate` 4, `fuse` 4,
  `obsidiandestroyer` 2, `telepathy` 2, `skilling` 2, `haste` 1, `oxygenate` 1,
  `autosmelt` 1, `experience` 1. Batch 06 authored off that table
  (`auto-smelt.yml` and `haste.yml` common, `fuse.yml` epic), noting it in-file.
  The backfill should copy those nine onto the entries themselves.
- **`matrix/06` § Detonate — the `FACE_ORIENTED_BOX_SELECTOR` gap block is
  stale for the symmetric half.** It reads "TRENCH is a single perpendicular
  layer and TUNNEL a 1×1 line — no combination yields a 3×3×depth slab", which
  was true when the block was written and is not now: `@Bore{half-width,
  half-height, depth, materials}` shipped, is oriented on the mined face, and
  `@Bore{half-width=1, half-height=1, depth=D}` IS the entry's 3×3×D slab. Only
  the ASYMMETRIC extents (Atomic Detonate's `(1,2,1,2)`/`(2,3,2,3)`) and the
  per-block DENY filter are still open — the batch-1 staleness pattern above,
  now confirmed on doc 06. Both entries stay deferred on
  `BLOCK_MATERIAL_FILTER`, not on the selector.
- **`matrix/04` § Reforged — the weapons-and-tools item set is missing the
  spades.** The entry spells the set as "5 swords + 5 axes + bow + 5 pickaxes +
  5 hoes", and `reforged.yml` faithfully ships `[SWORD, AXE, BOW, PICKAXE,
  HOE]`. The codex's own set table (doc 15) records `weapons_and_tools` as
  5 swords, 5 axes, bow, 5 pickaxes, 5 hoes **and 5 spades** (then the 5 axes
  a second time — 31 entries, 26 distinct), so the shipped file is one whole
  tool family short and Reforged never lands on a shovel. If the table is
  right, the matrix line gains the spades and `reforged.yml` gains `SHOVEL`.
  Same table is what batch 06 read `tools` (21) off: 5 pickaxes + 5 hoes +
  5 spades + 5 axes + FISHING_ROD, which is why `haste.yml` is
  `[TOOL, FISHING_ROD]` and not `[TOOL]`.
  **ITEM-SET RULING (owner, refining the batch-3 spell-it-out convention
  `obliterate.yml` records).** Take a COMPOSITE whenever it is an EXACT match for
  the jar's set — `ItemGroups` defines `TOOL` as pickaxe + axe + shovel + hoe and
  nothing else, so `[TOOL, FISHING_ROD]` is the canonical spelling of the 21-tool
  set (`haste.yml`, `oxygenate.yml`, `telepathy.yml`, and `atomic-detonate.yml`
  when it lands). ENUMERATE only where no composite fits — the weapons cases,
  where `WEAPON` would drag in the crossbow, trident and mace the jar never
  listed (`obliterate.yml` `[SWORD, AXE, BOW]`, `reforged.yml` above).
- **`matrix/06` § Master Inquisitive — the payout ability has no victim to read.**
  The decomposition gates the `EXP_GAIN` ability on
  `%victim.inquisitive-mark% >= 1`, but an XP-gain activation carries no victim
  (the marked mob is already dead) and no such fact family exists — the victim
  var spelling is `%victim.var.<name>%`, and it is unreadable there. The shipped
  file takes the base's three-rule chain instead: ATTACK writes the mark on the
  mob, KILL reads it back through `%victim.var.inquisitive.master%` and opens a
  40 t pickup window on the holder, EXP_GAIN spends that window. Same delta
  `inquisitive.yml` already carries (the jar paid out on ANY killer's blow; the
  window is the holder's own kill), so the two grades stay uniform. The matrix
  line should decompose the payout as a KILL → window → EXP_GAIN chain.
- **`matrix/06` gap index — three more gaps already closed.**
  `ANY_DAMAGE_TRIGGER` (defined at Divine Enlighted, consumed by Guided Rocket
  Escape) ships as the `HURT` trigger — DEFENSE-direction, no target, every
  damage cause — which `enlighted.yml` has taken for the same jar behaviour
  since batch 01; `FLY_SPEED_PARAM` (Guided Rocket Escape) ships as `FLY`'s own
  `speed` param, restoring the PRIOR fly speed rather than the jar's hard-coded
  0.1; and `IMPACT_HEIGHT_VAR` (Lethal Sniper) ships as `%impactheight%`,
  already load-bearing on `sniper.yml`. `TARGET_SCOPED_VAR` was closed in
  wave 1 (`SET_VAR who=@Victim` + `%victim.var.<name>%`). The batch-1 staleness
  pattern, confirmed a second time on doc 06.
- **`matrix/06` gap index — three MORE closed, on the tools/heroic batch.**
  `AIR_TICKS_RESTORE` (Oxygenate) ships as `FILL_OXYGEN`'s own `amount` param —
  "air ticks to add, clamped to the target's maximum air" is the gap block
  verbatim; its optional `skip-if-overflow` is not wanted, since D-06-6 rules
  the clamped top-up the as-intended reading. `FOOD_DRAIN_CANCEL` (Alien
  Implants) ships as `MODIFY_FOOD mode=cancel-drain`, which cancels hunger LOSS
  for a window and leaves gains alone — the jar's "only cancels decreases, so
  eating still works" exactly, re-armed on REPEATING per the effect's own
  authoring note. `PULL_IMPULSE` (Bidirectional Teleportation) ships as
  `VELOCITY mode=toward, anchor=activator` with an EXPRESSION `strength`: the
  recorded `clamp(distance²/50, 1.0, cap)` is authored literally, so only the
  jar's separate Y softening (`y × -mag/1.75`) is approximated, not the
  magnitude curve. `RELATION_VAR`, defined on the same entry, was already closed
  in wave 1b as `%victim.relation%`. Four more instances of the batch-1
  staleness pattern.
- **`matrix/06` § Bidirectional Teleportation — the ally branch cancels BEFORE
  it range-checks.** The decomposition orders the ally arm as "1. condition
  `%distance% <= 30` … else too-far string; 2. `CANCEL()`", which would let a
  too-far allied arrow deal its damage. The source cancels the event and removes
  the arrow first and only then compares `distanceSquared`, so a too-far allied
  hit still deals ZERO — it just prints instead of blinking. That is the same
  shape `teleportation.yml` already ships for the non-heroic (both arms open
  with CANCEL), and `bidirectional-teleportation.yml` follows it. The matrix
  line should move the cancel above the range test.
- **`matrix/06` § Vengeful Diminish — the armed-cap state is NOT per-enchant.**
  The interactions line records the jar's shared armed-cap key as a structural
  change ("engine state is per-enchant"). It is not: `DamageCapStore` holds ONE
  armed cap keyed by player UUID, and a re-arm replaces it wholesale (factor and
  reflect together), so the jar's shared-key behaviour is reproduced exactly and
  the co-occurrence caveat the entry raises is the jar's own. The matrix line
  should drop the structural-change note and record the last-arm-wins wrinkle
  instead; `vengeful-diminish.yml` carries the corrected reading.
