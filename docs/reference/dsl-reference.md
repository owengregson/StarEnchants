# StarEnchants DSL reference

_Generated from the engine's effect / selector / trigger / condition / variable vocabularies. Do not edit by hand — run_ `./gradlew :engine:test --tests "*ReferenceDocDriftTest" -Dse.doc.regen=true` _to regenerate; the build fails if this file drifts from the code._

## Effects

The actions an ability runs. Each is a block map `{ HEAD: { param: value, who:, wait: } }` in an enchant/set/crystal's `effects:` list.

### ARMOR_BATTERY

Repair all of the actor's armor, then damage the opponent armor slot matching the actor's first worn source enchant.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ ARMOR_BATTERY: { source-enchant: <string>, repair: <int[0..]=2>, retaliation: <int[0..]=1> } }`
- _param_ `source-enchant` `string`
- _param_ `repair` `int[0..]`
- _param_ `retaliation` `int[0..]`
- _example_: `{ ARMOR_BATTERY: { source-enchant: enchants/immortal, repair: 2, retaliation: 1 } }`

### BATTERY

Arm a damage battery on the wearer: the next `hits` landed hits they take each bank `bank-percent`% of the final damage; their next landed hit on an enemy unloads the entire bank as bonus damage on that hit, then the core resets — a hit with nothing banked still spends the core. No time limit; the ability cooldown paces re-arms. Cleared on death.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ BATTERY: { bank-percent: <double[0..100]=20>, hits: <int[1..10]=3> } }`
- _param_ `bank-percent` `double[0..100]`
- _param_ `hits` `int[1..10]`
- _target_ `who`: selector `SELF`
- _example_: `{ BATTERY: { bank-percent: 20, hits: 3 } }`

### BLEED_STACK

Increment the shared 20-stack Bleed state and apply stack movement, block cues, and nearby allied Blood Lust healing.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ BLEED_STACK: { speed-step: <double[0..]>, half-floor: <int[0..]=0>, blood-lust-floor: <double[0..]>, blood-lust-scale: <double[0..]>, primary-block: <material>, secondary-block: <material>, slow: <potion_effect>, blood-lust-particle: <particle>, blood-lust-sound: <sound> } }`
- _param_ `speed-step` `double[0..]`
- _param_ `half-floor` `int[0..]`
- _param_ `blood-lust-floor` `double[0..]`
- _param_ `blood-lust-scale` `double[0..]`
- _param_ `primary-block` `material`
- _param_ `secondary-block` `material`
- _param_ `slow` `potion_effect`
- _param_ `blood-lust-particle` `particle`
- _param_ `blood-lust-sound` `sound`
- _target_ `who`: selector `VICTIM`
- _example_: `{ BLEED_STACK: { speed-step: 0.005, blood-lust-floor: 2, blood-lust-scale: 0.05, primary-block: REDSTONE_BLOCK, slow: SLOW, blood-lust-sound: EAT } }`

### BLESS

If blocked-by is active, show blocked-message and stop. Otherwise clear Cosmic Bleed, play the private sound, remove the first active authored potion, and show the success message. Optionally throttle only that message while one armor set is active.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ BLESS: { blocked-by: <string=>, blocked-message: <string=>, success-message: <string=>, sound: <sound>, volume: <double[0..]=1>, pitch: <double[0..]=1>, message-throttle-set: <string=>, message-throttle: <ticks[0..]=0>, effect-1: <potion_effect>, effect-2: <potion_effect>, effect-3: <potion_effect>, effect-4: <potion_effect>, effect-5: <potion_effect>, effect-6: <potion_effect>, effect-7: <potion_effect>, effect-8: <potion_effect>, effect-9: <potion_effect>, effect-10: <potion_effect>, effect-11: <potion_effect>, effect-12: <potion_effect>, effect-13: <potion_effect>, effect-14: <potion_effect>, effect-15: <potion_effect>, effect-16: <potion_effect> } }`
- _param_ `blocked-by` `string`
- _param_ `blocked-message` `string`
- _param_ `success-message` `string`
- _param_ `sound` `sound`
- _param_ `volume` `double[0..]`
- _param_ `pitch` `double[0..]`
- _param_ `message-throttle-set` `string`
- _param_ `message-throttle` `ticks[0..]`
- _param_ `effect-1` `potion_effect`
- _param_ `effect-2` `potion_effect`
- _param_ `effect-3` `potion_effect`
- _param_ `effect-4` `potion_effect`
- _param_ `effect-5` `potion_effect`
- _param_ `effect-6` `potion_effect`
- _param_ `effect-7` `potion_effect`
- _param_ `effect-8` `potion_effect`
- _param_ `effect-9` `potion_effect`
- _param_ `effect-10` `potion_effect`
- _param_ `effect-11` `potion_effect`
- _param_ `effect-12` `potion_effect`
- _param_ `effect-13` `potion_effect`
- _param_ `effect-14` `potion_effect`
- _param_ `effect-15` `potion_effect`
- _param_ `effect-16` `potion_effect`
- _target_ `who`: selector `SELF`
- _example_: `{ BLESS: { blocked-by: deep-wounds, sound: SPLASH, effect-1: POISON } }`

### BLINK

Blink (reforges): instantly teleport up to distance blocks along your facing if the path is clear — stops at the last open block, never phases into or through terrain. Walls stop it; the use is spent either way.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ BLINK: { distance: <double[1..16]=4>, particle: <particle=REDSTONE>, r: <int[0..255]=170>, g: <int[0..255]=60>, b: <int[0..255]=220>, size: <double[0..]=1>, count: <int[0..]=10>, arrival-sound: <sound=ENTITY_ENDERMAN_TELEPORT>, arrival-volume: <double[0..]=0.6>, arrival-pitch: <double[0..]=1.8>, arrival-accent: <sound=BLOCK_AMETHYST_BLOCK_CHIME>, accent-volume: <double[0..]=0.4>, accent-pitch: <double[0..]=1.6> } }`
- _param_ `distance` `double[1..16]` — max blink distance in blocks
- _param_ `particle` `particle`
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _param_ `size` `double[0..]`
- _param_ `count` `int[0..]` — departure/arrival puff motes
- _param_ `arrival-sound` `sound` — played ON the player after the hop lands — a sound at the origin is never heard by someone who just teleported away from it
- _param_ `arrival-volume` `double[0..]`
- _param_ `arrival-pitch` `double[0..]`
- _param_ `arrival-accent` `sound` — the shimmer layer over the arrival body
- _param_ `accent-volume` `double[0..]`
- _param_ `accent-pitch` `double[0..]`
- _example_: `{ BLINK: { distance: 4 } }`

### BLOCK_BREAK_EFFECT

Play the combined block-break particles and sound used by Bukkit STEP_SOUND/effect 2001. once-at-actor emits one cue at the captured actor origin only when 'who' matched.

- _affinity_: `REGION`
- _usage_: `{ BLOCK_BREAK_EFFECT: { block: <material>, anchor: <enum{body|feet|eye}=feet>, y-offset: <double=0.0>, once-at-actor: <bool=false> } }`
- _param_ `block` `material`
- _param_ `anchor` `enum{body|feet|eye}`
- _param_ `y-offset` `double`
- _param_ `once-at-actor` `bool`
- _target_ `who`: selector `HERE`
- _example_: `{ BLOCK_BREAK_EFFECT: { block: DIAMOND_BLOCK, who: "@Self" } }`

### BREAK_BLOCK

Break the target block(s) (default @Here; drops=false clears). @Vein/@Tunnel/@Trench for shapes.

- _affinity_: `REGION`
- _usage_: `{ BREAK_BLOCK: { drops: <bool=true>, use-held-tool: <bool=false> } }`
- _param_ `drops` `bool`
- _param_ `use-held-tool` `bool`
- _target_ `at`: selector `HERE`
- _example_: `{ BREAK_BLOCK: { drops: true } }`

### CAGE

Trap the target AND the activator in a temporary cage: floor/roof plates, a walls ring, an air width × height × depth interior, base-centred rise blocks above the midpoint between the two, reverting after ticks. The full volume is safety-checked (every cell must be air) before anything is placed; both parties teleport to opposite interior cells facing each other. Gate the ability on a target existing (e.g. %nearbyenemies% >= 1) so a no-target use fails BEFORE the cooldown arms.

- _affinity_: `REGION`
- _usage_: `{ CAGE: { floor: <material>, walls: <material>, roof: <material>, width: <int[1..8]=3>, height: <int[2..8]=4>, depth: <int[1..8]=3>, rise: <int[0..8]=2>, ticks: <ticks[0..]=150> } }`
- _param_ `floor` `material`
- _param_ `walls` `material`
- _param_ `roof` `material`
- _param_ `width` `int[1..8]`
- _param_ `height` `int[2..8]`
- _param_ `depth` `int[1..8]`
- _param_ `rise` `int[0..8]`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ CAGE: { floor: STONE_BRICKS, walls: IRON_BARS, roof: STONE_BRICKS, ticks: 150, who: "@NearestPlayer{r=10}" } }`

### CANCEL

Cancel the Bukkit event that triggered this activation.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ CANCEL: {} }`
- _example_: `{ CANCEL: {} }`

### CLEAR_BLEED

Clear Cosmic Bleed stacks, restoring player walk speed or removing mob Slowness.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ CLEAR_BLEED: {} }`
- _target_ `who`: selector `SELF`
- _example_: `{ CLEAR_BLEED: { who: "@Self" } }`

### CONVERT_SUMMON

Convert EVERY mob within `radius` blocks of the wearer to the wearer's side, permanently: each rebinds its ownership (a hit on it now fires the wearer's GUARDIAN_HURT) and tamed mobs re-tame; an enemy summon turns on its former owner, a wild mob on the wearer's nearest enemy player, and bat swarm clouds permanently swarm their former owner. Only players, armour stands and the wearer are exempt.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ CONVERT_SUMMON: { radius: <double[1..32]=12>, whiff-sound: <sound=BLOCK_ANVIL_LAND> } }`
- _param_ `radius` `double[1..32]`
- _param_ `whiff-sound` `sound` — played (low-pitched) when the ring converts nothing — the ring sound alone reads as success
- _target_ `who`: selector `SELF`
- _example_: `{ CONVERT_SUMMON: { radius: 12 } }`

### COSMIC_SILENCE

Roll Cosmic Silence at 2% per Silence+held-Solitude level, honor Dragon Slayer's 75% block, then suppress only defense procs for one second per combined level with exact feedback.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ COSMIC_SILENCE: { level: <int[1..4]>, solitude: <string=enchants/solitude>, sound: <sound>, enchantment-particle: <particle>, portal-particle: <particle> } }`
- _param_ `level` `int[1..4]`
- _param_ `solitude` `string`
- _param_ `sound` `sound`
- _param_ `enchantment-particle` `particle`
- _param_ `portal-particle` `particle`
- _target_ `who`: selector `VICTIM`
- _example_: `{ COSMIC_SILENCE: { level: 4, sound: WITHER_HURT, enchantment-particle: ENCHANTMENT_TABLE, portal-particle: PORTAL, who: '@Victim' } }`

### COUNT_TARGETS

Count resolved targets and store the integer on the actor as %name% (ttl ticks, 0 = forever).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ COUNT_TARGETS: { name: <string>, ttl: <ticks[0..]=0> } }`
- _param_ `name` `string`
- _param_ `ttl` `ticks[0..]`
- _target_ `who`: selector `AOE`
- _example_: `{ COUNT_TARGETS: { name: nearby-allies, ttl: 121, who: "@Aoe{r=7,filter=ALLIES}" } }`

### CURE

Clear active potion effects of one category from the target(s): ALL (default), HARMFUL, BENEFICIAL, or NEUTRAL. category HARMFUL strips only debuffs (positive effects untouched).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ CURE: { category: <enum{ALL|HARMFUL|BENEFICIAL|NEUTRAL}=ALL> } }`
- _param_ `category` `enum{ALL|HARMFUL|BENEFICIAL|NEUTRAL}`
- _target_ `who`: selector `SELF`
- _example_: `{ CURE: { category: HARMFUL } }`

### CURE_ONE_OF

Remove exactly one active potion effect, choosing the first effect encountered by the server whose type appears in effect-1 through effect-16. Unlisted effects remain. Optionally throttle the success message only while one authored armor set is active.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ CURE_ONE_OF: { effect-1: <potion_effect>, success-message: <string=>, message-throttle-set: <string=>, message-throttle: <ticks[0..]=0>, effect-2: <potion_effect>, effect-3: <potion_effect>, effect-4: <potion_effect>, effect-5: <potion_effect>, effect-6: <potion_effect>, effect-7: <potion_effect>, effect-8: <potion_effect>, effect-9: <potion_effect>, effect-10: <potion_effect>, effect-11: <potion_effect>, effect-12: <potion_effect>, effect-13: <potion_effect>, effect-14: <potion_effect>, effect-15: <potion_effect>, effect-16: <potion_effect> } }`
- _param_ `effect-1` `potion_effect`
- _param_ `success-message` `string`
- _param_ `message-throttle-set` `string`
- _param_ `message-throttle` `ticks[0..]`
- _param_ `effect-2` `potion_effect`
- _param_ `effect-3` `potion_effect`
- _param_ `effect-4` `potion_effect`
- _param_ `effect-5` `potion_effect`
- _param_ `effect-6` `potion_effect`
- _param_ `effect-7` `potion_effect`
- _param_ `effect-8` `potion_effect`
- _param_ `effect-9` `potion_effect`
- _param_ `effect-10` `potion_effect`
- _param_ `effect-11` `potion_effect`
- _param_ `effect-12` `potion_effect`
- _param_ `effect-13` `potion_effect`
- _param_ `effect-14` `potion_effect`
- _param_ `effect-15` `potion_effect`
- _param_ `effect-16` `potion_effect`
- _target_ `who`: selector `SELF`
- _example_: `{ CURE_ONE_OF: { effect-1: POISON, effect-2: WITHER, who: "@Self" } }`

### DAMAGE

Deal extra damage to the target: a flat amount and/or percent-of-max of the target's own maximum health (they sum when both are given).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE: { amount: <double[0..]=0>, percent-of-max: <double[0..100]=0>, attributed: <bool=true> } }`
- _param_ `amount` `double[0..]`
- _param_ `percent-of-max` `double[0..100]`
- _param_ `attributed` `bool` — false uses a generic damage event with no attacker attribution
- _target_ `who`: selector `VICTIM`
- _example_: `{ DAMAGE: { amount: 6, percent-of-max: 10 } }`

### DAMAGE_CAP

Cap the wearer's next incoming hit at factor times the last damage they took, for a duration in ticks; with reflect, the overflow above the cap is dealt back to the attacker (Diminish). Self-only; no cap is armed until at least one hit has been taken.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE_CAP: { factor: <double[0..]=0.5>, reflect: <bool=false>, duration: <ticks[0..]=100> } }`
- _param_ `factor` `double[0..]`
- _param_ `reflect` `bool`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ DAMAGE_CAP: { factor: 0.5, reflect: true, duration: 100 } }`

### DAMAGE_MOD

Contribute to the damage fold: side attack/defense; mode add uses a percentage, flat a raw amount, and multiply an exact direct factor. A NEGATIVE add amount is a self-nerf. cap clamps a positive evaluated amount when greater than zero (0 = uncapped).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE_MOD: { side: <enum{attack|defense}>, mode: <enum{add|flat|multiply}=add>, amount: <double>, cap: <double[0..]=0> } }`
- _param_ `side` `enum{attack|defense}`
- _param_ `mode` `enum{add|flat|multiply}`
- _param_ `amount` `double`
- _param_ `cap` `double[0..]`
- _example_: `{ DAMAGE_MOD: { side: attack, mode: add, amount: 25, cap: 75 } }`

### DAMAGE_SCALE

Contribute per resolved target in 'who' to the damage fold: total = per * count, clamped to cap (0 = uncapped). side attack/defense, mode add (percent, e.g. 10 = +10% each) or flat (raw). The count is the selector's resolved set, e.g. who: @AllPlayers{r=7}.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE_SCALE: { side: <enum{attack|defense}=attack>, mode: <enum{add|flat}=add>, per: <double>, cap: <double[0..]=0> } }`
- _param_ `side` `enum{attack|defense}`
- _param_ `mode` `enum{add|flat}`
- _param_ `per` `double`
- _param_ `cap` `double[0..]`
- _target_ `who`: selector `AOE`
- _example_: `{ DAMAGE_SCALE: { side: attack, mode: add, per: 10, cap: 100, who: "@AllPlayers{r=7}" } }`

### DIG_HOME

Mark the activator's location as a temporary home for `window` ticks: the next right-click of the same pet within `range` blocks teleports the activator back and consumes the window. Pets only — the pets service owns the recall; this effect emits no intent of its own.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DIG_HOME: { window: <ticks[0..]=600>, range: <double[0..]=50> } }`
- _param_ `window` `ticks[0..]`
- _param_ `range` `double[0..]`
- _example_: `{ DIG_HOME: { window: 600, range: 50 } }`

### DISARM

Make the target(s) drop their held (main-hand) item.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ DISARM: {} }`
- _target_ `who`: selector `VICTIM`
- _example_: `{ DISARM: {} }`

### DISARM_SHUFFLE

Arm an unhanding window on the wearer for `duration` ticks: their next landed melee hit on a player knocks the victim's held item into a random other hotbar slot (the victim can re-select it — shuffled, not locked; weapon-gated combos like Rage break naturally) and that hit deals `damage-malus`% less damage. One shot; a dodged/negated hit keeps the window armed.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DISARM_SHUFFLE: { duration: <ticks[0..]=80>, damage-malus: <double[0..100]=20> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `damage-malus` `double[0..100]`
- _target_ `who`: selector `SELF`
- _example_: `{ DISARM_SHUFFLE: { duration: 80, damage-malus: 20 } }`

### DISTANCE_DAMAGE

Multiply outgoing damage by one factor built from cumulative strict distance thresholds.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DISTANCE_DAMAGE: { distance: <double[0..]>, near-1: <double[0..]=0.25>, near-2: <double[0..]=0.5>, near-3: <double[0..]=0.75>, near-4: <double[0..]=1.0>, near-5: <double[0..]=1.5>, near-per-level: <double[0..]=0.01>, far-1: <double[0..]=2.0>, far-2: <double[0..]=2.5>, far-3: <double[0..]=3.0>, far-penalty-1: <double[0..]=0.01>, far-penalty-2: <double[0..]=0.0125>, far-penalty-3: <double[0..]=0.025> } }`
- _param_ `distance` `double[0..]`
- _param_ `near-1` `double[0..]`
- _param_ `near-2` `double[0..]`
- _param_ `near-3` `double[0..]`
- _param_ `near-4` `double[0..]`
- _param_ `near-5` `double[0..]`
- _param_ `near-per-level` `double[0..]`
- _param_ `far-1` `double[0..]`
- _param_ `far-2` `double[0..]`
- _param_ `far-3` `double[0..]`
- _param_ `far-penalty-1` `double[0..]`
- _param_ `far-penalty-2` `double[0..]`
- _param_ `far-penalty-3` `double[0..]`
- _example_: `{ DISTANCE_DAMAGE: { distance: '%distance%' } }`

### DROP_HEAD

Drop each selected player's skinned head, or mark one per channel for their next death when defer is true.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ DROP_HEAD: { defer: <bool=false>, channel: <string=default> } }`
- _param_ `defer` `bool`
- _param_ `channel` `string`
- _target_ `who`: selector `VICTIM`
- _example_: `{ DROP_HEAD: { defer: true, channel: headless, who: "@Victim" } }`

### DROP_ITEM

Drop a material as an item at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `{ DROP_ITEM: { material: <material>, count: <int[1..]=1> } }`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _example_: `{ DROP_ITEM: { material: DIAMOND, count: 1 } }`

### DURABILITY

Modify durability of the player's held item and/or worn armor: restore (amount<0 = full) or damage. Replaces ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ DURABILITY: { amount: <int=-1>, percent: <double[0..100]>, target: <enum{item|armor|all}=item>, mode: <enum{restore|damage}=restore>, selection: <enum{all|most-damaged|random}=all>, slot: <enum{boots|leggings|chestplate|helmet}> } }`
- _param_ `amount` `int` — durability points; negative fully restores (restore mode)
- _param_ `percent` `double[0..100]` — damage mode only: percent of each armor piece type's maximum durability
- _param_ `target` `enum{item|armor|all}`
- _param_ `mode` `enum{restore|damage}`
- _param_ `selection` `enum{all|most-damaged|random}` — armor selection: most-damaged for restore, random for one uniformly-selected damage slot
- _param_ `slot` `enum{boots|leggings|chestplate|helmet}` — damage exactly one armor slot instead of all armor
- _target_ `who`: selector `SELF`
- _example_: `{ DURABILITY: { amount: -1, target: item } }`

### ECHO_STRIKE

Re-run the attack activation once over the same hit (enchants can re-proc); all damage folds into the one event. No effect off the attack side.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ ECHO_STRIKE: {} }`
- _example_: `{ ECHO_STRIKE: {} }`

### EQUIP_SWAP

Temporarily replace the target's `slot` armour piece with `material`, restoring it after `duration` ticks (death-safe: the real piece drops / is kept). Default target the victim.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ EQUIP_SWAP: { slot: <enum{helmet|chestplate|leggings|boots}=helmet>, material: <material>, duration: <ticks[0..]=60> } }`
- _param_ `slot` `enum{helmet|chestplate|leggings|boots}`
- _param_ `material` `material`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ EQUIP_SWAP: { slot: helmet, material: CARVED_PUMPKIN, duration: 60, who: "@Victim" } }`

### EXPLODE

Create an explosion at the target.

- _affinity_: `REGION`
- _usage_: `{ EXPLODE: { power: <double[0..]>, breakBlocks: <bool=false> } }`
- _param_ `power` `double[0..]`
- _param_ `breakBlocks` `bool`
- _target_ `who`: selector `VICTIM`
- _example_: `{ EXPLODE: { power: 4, breakBlocks: false } }`

### EXP_DROP_MARK

Mark each target with a named death-XP multiplier. Re-marking replaces that channel; distinct channels multiply.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ EXP_DROP_MARK: { channel: <string>, multiplier: <double[0..]> } }`
- _param_ `channel` `string`
- _param_ `multiplier` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ EXP_DROP_MARK: { channel: inquisitive, multiplier: 1.5, who: "@Victim" } }`

### EXP_MULTIPLY

Multiply XP on the triggering EXP_GAIN or MINE event by a factor.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ EXP_MULTIPLY: { factor: <double[0..]=2.0> } }`
- _param_ `factor` `double[0..]`
- _example_: `{ EXP_MULTIPLY: { factor: 2 } }`

### EXTINGUISH

Put out the target's fire.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ EXTINGUISH: {} }`
- _target_ `who`: selector `SELF`
- _example_: `{ EXTINGUISH: {} }`

### FAKE_BLOCK

Show a client-only block at each target to players within the radius, then restore the real block.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FAKE_BLOCK: { block: <material>, duration: <ticks[0..]>, radius: <double[0..]=32>, anchor: <enum{body|feet|eye}=body> } }`
- _param_ `block` `material`
- _param_ `duration` `ticks[0..]`
- _param_ `radius` `double[0..]`
- _param_ `anchor` `enum{body|feet|eye}`
- _target_ `who`: selector `VICTIM`
- _example_: `{ FAKE_BLOCK: { block: WATER, duration: 45, radius: 32, anchor: eye, who: '@Victim' } }`

### FALLING_BLOCK

Spawn a (2*radius+1)² grid of falling blocks `height` blocks above each target (removed after `ttl` if they never land). A landing block fires the actor's IMPACT abilities on what it hit; `carry` is forwarded to that impact as %damage% (set carry: "%damage%").

- _affinity_: `REGION`
- _usage_: `{ FALLING_BLOCK: { material: <material>, radius: <int[0..4]=1>, height: <int[0..12]=4>, ttl: <ticks[0..]=40>, carry: <double=0> } }`
- _param_ `material` `material`
- _param_ `radius` `int[0..4]`
- _param_ `height` `int[0..12]`
- _param_ `ttl` `ticks[0..]`
- _param_ `carry` `double`
- _target_ `who`: selector `VICTIM`
- _example_: `{ FALLING_BLOCK: { material: GRASS_BLOCK, radius: 1, height: 4, carry: "%damage%", who: "@Victim" } }`

### FILL_OXYGEN

Add air ticks up to the target's maximum; omit amount for a full refill.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FILL_OXYGEN: { amount: <int=-1> } }`
- _param_ `amount` `int` — air ticks to add; negative refills completely
- _target_ `who`: selector `SELF`
- _example_: `{ FILL_OXYGEN: { amount: 20 } }`

### FIREWORK

Spawn a cosmetic firework at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `{ FIREWORK: { power: <int[0..3]=1> } }`
- _param_ `power` `int[0..3]`
- _example_: `{ FIREWORK: { power: 1 } }`

### FLY

Grant the player temporary flight.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FLY: { ticks: <ticks[0..]=200> } }`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ FLY: { ticks: 200 } }`

### FLY_MODE

Grant flight to the target(s) while NOT in combat, revoke it while in combat (survival/adventure only). Author on trigger [REPEATING, PASSIVE] with a repeat period so it re-checks and tears down on unequip.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FLY_MODE: {} }`
- _target_ `who`: selector `SELF`
- _example_: `{ FLY_MODE: { who: "@Self" } }`

### FREEZE

Fully freeze the target for a span of ticks (vanilla powder-snow visual: blue hearts + full vignette, held even while the victim burns), dealing dot damage every dot-period ticks (attributed to the activator; raw pre-armor half-hearts) and slowing them by slow percent. Re-procs refresh the window instead of stacking. neutralize-frost-slow cancels vanilla's own ~50% fully-frozen slow so the authored percent is the real one.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FREEZE: { duration: <ticks[0..]=60>, dot: <double[0..]=2>, dot-period: <ticks[0..]=20>, slow: <double[0..100]=5>, neutralize-frost-slow: <bool=true> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `dot` `double[0..]`
- _param_ `dot-period` `ticks[0..]`
- _param_ `slow` `double[0..100]`
- _param_ `neutralize-frost-slow` `bool`
- _target_ `who`: selector `VICTIM`
- _example_: `{ FREEZE: { duration: 100, dot: 2, dot-period: 20, slow: 5 } }`

### GIVE_ITEM

Give a material to the player target(s); overflow drops at their feet.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ GIVE_ITEM: { material: <material>, count: <int[1..]=1> } }`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `{ GIVE_ITEM: { material: DIAMOND, count: 1, who: "@Self" } }`

### GRAPPLE

Leviathan's Reach (reforges): throw a grappling line along your facing (range blocks). An enemy in sight closer than the first block is reeled to reel-distance blocks in front of you with a brief slow and no damage; otherwise you zip to the terrain point. Open air wastes the throw. Reforge-service-owned: this effect emits no intent of its own.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ GRAPPLE: { range: <double[1..]=14>, hook-speed: <double[0.5..]=2.0>, reel-distance: <double[0.5..]=2.0>, slow-effect: <potion_effect=SLOW>, slow-level: <int[1..10]=2>, slow-duration: <ticks[0..]=60>, zip-strength: <double[0..]=0.34>, zip-cap: <double[0..]=3.2>, zip-rise: <double[0..]=0.25>, particle: <particle=REDSTONE>, r: <int[0..255]=200>, g: <int[0..255]=220>, b: <int[0..255]=255>, size: <double[0..]=1>, density: <double[0..]=3> } }`
- _param_ `range` `double[1..]` — hook range in blocks (both rays)
- _param_ `hook-speed` `double[0.5..]` — hook flight speed, blocks per tick (cosmetic delay)
- _param_ `reel-distance` `double[0.5..]` — where a hooked enemy lands, blocks in front of you
- _param_ `slow-effect` `potion_effect` — the brief debuff a reeled enemy gets
- _param_ `slow-level` `int[1..10]` — its amplifier + 1 (authoring convention)
- _param_ `slow-duration` `ticks[0..]` — its length
- _param_ `zip-strength` `double[0..]` — terrain mode: velocity per block of distance (capped)
- _param_ `zip-cap` `double[0..]` — terrain mode: velocity magnitude cap
- _param_ `zip-rise` `double[0..]` — terrain mode: extra upward boost
- _param_ `particle` `particle`
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _param_ `size` `double[0..]`
- _param_ `density` `double[0..]` — line motes per block
- _example_: `{ GRAPPLE: { range: 14, hook-speed: 2.0, reel-distance: 2.0 } }`

### GRAVITY_WELL

Singularity (reforges): throw a particle beam onto the block in your sights (max range); a collapsing star forms rise blocks above it, drags every living thing within radius toward the core for duration ticks, then implodes for damage (linear falloff to falloff-floor at the edge). Pulls — and hurts — the caster too unless self-pull/self-damage are off. Reforge-service-owned: this effect emits no intent of its own.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ GRAVITY_WELL: { range: <double[1..]=12>, radius: <double[0.5..]=6>, rise: <double[0..]=2.5>, duration: <ticks[0..]=60>, period: <int[1..20]=2>, pull: <double[0..]=0.28>, damage: <double[0..]=8.0>, falloff-floor: <double[0..1]=0.25>, self-pull: <bool=true>, self-damage: <bool=true>, r: <int[0..255]=190>, g: <int[0..255]=120>, b: <int[0..255]=255> } }`
- _param_ `range` `double[1..]` — max block-selection distance (blocks)
- _param_ `radius` `double[0.5..]` — pull + implosion radius around the core
- _param_ `rise` `double[0..]` — core height above the selected block
- _param_ `duration` `ticks[0..]` — pull phase length before the implosion
- _param_ `period` `int[1..20]` — pull cadence in ticks
- _param_ `pull` `double[0..]` — per-pulse velocity magnitude toward the core
- _param_ `damage` `double[0..]` — implosion damage at the core, RAW health-space (8.0 = 4 hearts)
- _param_ `falloff-floor` `double[0..1]` — damage fraction kept at the radius edge
- _param_ `self-pull` `bool` — the caster is dragged too (the authored downside)
- _param_ `self-damage` `bool` — the implosion hits the caster too if inside
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _example_: `{ GRAVITY_WELL: { range: 12, radius: 6, duration: 60, damage: 8.0 } }`

### GUARD

Summon count guardian mobs of type at the activation location, each targeting the attacker, auto-removed after ttl ticks (default 200; 0 = permanent); optional custom name. A targeted SPAWN_ENTITY for retaliation — author on DEFENSE.

- _affinity_: `REGION`
- _usage_: `{ GUARD: { type: <entity_type>, count: <int[1..]=1>, ttl: <ticks[0..]=200>, name: <string=>, health: <double[0..]=0>, spawn-y-offset: <double[-16..16]=0>, chunk-cap: <int[0..]=0>, retarget-radius: <double[0..]=0>, retarget-period: <ticks[1..]=20>, fire-resistance: <bool=false>, regeneration: <bool=false>, strength: <bool=false>, speed: <bool=false>, resistance: <bool=false>, sound: <sound>, sound-volume: <double[0..]=1>, sound-pitch: <double[0..]=1> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `name` `string`
- _param_ `health` `double[0..]` — 0 keeps the entity default
- _param_ `spawn-y-offset` `double[-16..16]`
- _param_ `chunk-cap` `int[0..]` — 0 disables the cap
- _param_ `retarget-radius` `double[0..]` — 0 disables periodic retargeting
- _param_ `retarget-period` `ticks[1..]`
- _param_ `fire-resistance` `bool`
- _param_ `regeneration` `bool`
- _param_ `strength` `bool`
- _param_ `speed` `bool`
- _param_ `resistance` `bool`
- _param_ `sound` `sound`
- _param_ `sound-volume` `double[0..]`
- _param_ `sound-pitch` `double[0..]`
- _target_ `who`: selector `ATTACKER`
- _example_: `{ GUARD: { type: IRON_GOLEM, count: 1, ttl: 200, name: "&bGuardian" } }`

### HEALTH

Bonus maximum health. On PASSIVE/HELD @Self it is a maintained worn bonus (reconciled, additive across sources, removed on unequip); on event triggers it is a permanent base shift.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ HEALTH: { amount: <double[0..]> } }`
- _param_ `amount` `double[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ HEALTH: { amount: 4 } }`

### HERO_KILLER

Require active soul mode, positive carried souls, a settled held slot, no Soul Trap, and a victim wearing heroic armor; charge four souls at most once per second and multiply the hit by 1 + 0.10 * level.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ HERO_KILLER: { level: <int[1..3]>, cost: <int[0..]=4>, cost-period: <ticks[0..]=20>, anti-swap: <ticks[0..]=5> } }`
- _param_ `level` `int[1..3]`
- _param_ `cost` `int[0..]`
- _param_ `cost-period` `ticks[0..]`
- _param_ `anti-swap` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ HERO_KILLER: { level: 3 } }`

### HIT_TEMPO

Arm a hit-tempo window on the wearer for `duration` ticks: their melee victims' damage-immunity window is halved for the wearer's hits only (model MENTAL = the 1.8-combat half-window gate; VANILLA = the 1.9+ full-window cadence), each such hit deals `damage-percent`% of its normal damage, and on 1.9+ the wearer gains `attack-speed` (+1.0 = doubled) swing speed for the window. Third-party attackers are unaffected — their hits keep natural immunity.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ HIT_TEMPO: { duration: <ticks[0..]=100>, model: <enum{VANILLA|MENTAL}=VANILLA>, damage-percent: <double[0..100]=33.3>, attack-speed: <double[0..]=1.0> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `model` `enum{VANILLA|MENTAL}`
- _param_ `damage-percent` `double[0..100]`
- _param_ `attack-speed` `double[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ HIT_TEMPO: { duration: 100, model: MENTAL, damage-percent: 33.3, attack-speed: 1.0 } }`

### HURT_ANIMATION

Play the target's vanilla red hurt animation without causing another damage event.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ HURT_ANIMATION: {} }`
- _target_ `who`: selector `VICTIM`
- _example_: `{ HURT_ANIMATION: { who: '@Victim' } }`

### IGNITE

Set the target(s) on fire for a duration in ticks.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ IGNITE: { duration: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ IGNITE: { duration: 60 } }`

### IGNORE_ARMOR

Make the triggering hit ignore the victim's armor and enchant-protection reduction.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ IGNORE_ARMOR: {} }`
- _example_: `{ IGNORE_ARMOR: {} }`

### IGNORE_HEROIC

Make the triggering hit ignore the victim's heroic-upgrade damage reduction (percent and flat).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ IGNORE_HEROIC: {} }`
- _example_: `{ IGNORE_HEROIC: {} }`

### IMMUNE

Make the target player(s) immune to a damage cause (sword/axe/projectile/potion/all) for duration ticks; fishhook additionally kills the rod-bobber reel pull (ADR-0053).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ IMMUNE: { type: <enum{sword|axe|projectile|potion|all|fishhook}>, duration: <ticks[0..]=100> } }`
- _param_ `type` `enum{sword|axe|projectile|potion|all|fishhook}`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ IMMUNE: { type: potion, duration: 100 } }`

### INFINITE_LUCK_GATE

Halt the current armor-set ability when the victim has Infinite Luck at the required level. Each equipped heroic armor piece independently contributes 12.5% to the source-code counter-roll that lets the set bonus through.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ INFINITE_LUCK_GATE: { required-level: <int[1..5]> } }`
- _param_ `required-level` `int[1..5]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ INFINITE_LUCK_GATE: { required-level: 3, who: '@Victim' } }`

### INVERT_VAR

Numerically invert a per-player variable (0↔1), preserving its remaining TTL.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ INVERT_VAR: { name: <string> } }`
- _param_ `name` `string`
- _target_ `who`: selector `SELF`
- _example_: `{ INVERT_VAR: { name: rage, who: "@Self" } }`

### INVINCIBLE

Make the target invulnerable for a span of ticks, then restore.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ INVINCIBLE: { ticks: <ticks[0..]=100> } }`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ INVINCIBLE: { ticks: 100 } }`

### JAVELIN

Javelin (reforges): a straight particle javelin at speed blocks/tick along the FULL facing (pitch included), max-travel blocks. On the first living hit: one weapon-swing's damage (or FLAT damage), knockback × along the flight angle, a lock-tick control lock (view snapped back + walk/jump locked; driven down a tracked arc), then nausea. Speed is authored (sidestep to dodge); a miss is wasted. Reforge-service-owned: this effect emits no intent of its own.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ JAVELIN: { speed: <double[0.05..]=0.15>, max-travel: <double[1..]=12>, hit-radius: <double[0.1..]=0.9>, damage-mode: <enum{WEAPON|FLAT}=WEAPON>, damage: <double[0..]=7.0>, knockback: <double[0..]=1.3>, knockback-base: <double[0..]=0.45>, lock: <ticks[0..]=20>, lock-delay: <ticks[0..]=5>, nausea-effect: <potion_effect=CONFUSION>, nausea-duration: <ticks[0..]=100>, particle: <particle=REDSTONE>, r: <int[0..255]=120>, g: <int[0..255]=200>, b: <int[0..255]=255>, size: <double[0..]=1.2> } }`
- _param_ `speed` `double[0.05..]` — flight speed, blocks per tick (0.15 = 3 blocks/s)
- _param_ `max-travel` `double[1..]` — max flight distance in blocks
- _param_ `hit-radius` `double[0.1..]` — hit detection radius around the tip
- _param_ `damage-mode` `enum{WEAPON|FLAT}` — WEAPON = one swing of the held weapon (era-read); FLAT = the damage param
- _param_ `damage` `double[0..]` — FLAT mode damage, RAW health-space
- _param_ `knockback` `double[0..]` — knockback multiplier along the flight angle
- _param_ `knockback-base` `double[0..]` — base knockback velocity one multiplier buys
- _param_ `lock` `ticks[0..]` — control-lock length: view snapped, walk+jump locked, driven down a tracked arc
- _param_ `lock-delay` `ticks[0..]` — ticks after impact before the lock arms (lets the knock land first)
- _param_ `nausea-effect` `potion_effect` — the post-lock debuff
- _param_ `nausea-duration` `ticks[0..]` — its length (100 = 5 s)
- _param_ `particle` `particle`
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _param_ `size` `double[0..]`
- _example_: `{ JAVELIN: { speed: 0.15, max-travel: 12, knockback: 1.3, lock: 20 } }`

### KEEP_ON_DEATH

Keep the target's items + levels (no drops) if they die within duration ticks (default 200). Author on trigger REPEATING for an always-on death-keep while worn, or fire on a trigger for a timed grace window. A kept death never spends a holy scroll.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ KEEP_ON_DEATH: { duration: <ticks[0..]=200> } }`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ KEEP_ON_DEATH: { duration: 200 } }`

### KILL

Instantly kill the target.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ KILL: {} }`
- _target_ `who`: selector `VICTIM`
- _example_: `{ KILL: {} }`

### KNOCKBACK_CONTROL

Scale the target's incoming knockback for duration ticks: 0 cancels it, 0.5 halves it, 2 doubles it (default: cancel for 2 ticks). Use on DEFENSE for your own knockback, or on ATTACK with who: victim for the knockback you deal.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ KNOCKBACK_CONTROL: { multiplier: <double[0..]=0>, duration: <ticks[0..]=2> } }`
- _param_ `multiplier` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ KNOCKBACK_CONTROL: { multiplier: 0 } }`

### LIGHTNING

Strike the target(s) with lightning, optionally dealing extra damage (0 = cosmetic).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ LIGHTNING: { damage: <double[0..]=0>, real: <bool=false> } }`
- _param_ `damage` `double[0..]`
- _param_ `real` `bool`
- _target_ `who`: selector `VICTIM`
- _example_: `{ LIGHTNING: { damage: 6 } }`

### LIGHTNING_MOD

While worn (PASSIVE): the wearer's LIGHTNING effects deal amount% more authored damage (summed across worn sources, suppression-aware, read when the bolt fires). Negative values reduce, floored at a cosmetic bolt; the vanilla splash is untouched.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ LIGHTNING_MOD: { amount: <double[-100..]> } }`
- _param_ `amount` `double[-100..]`
- _example_: `{ LIGHTNING_MOD: { amount: 10 } }`

### MARK

Mark the target(s) so the actor deals an extra `amount`% damage to them for `duration` ticks. Applied by the damage fold on the actor's later hits; default target the combat victim.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ MARK: { amount: <double>, duration: <ticks[0..]=60> } }`
- _param_ `amount` `double`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ MARK: { amount: 25, duration: 60, who: "@Victim" } }`

### MARK_ZONE

Lay an actor-owned cylinder of `radius` blocks under each target for `duration` ticks. Read by the %victim.inzone% fact, so a condition-gated bonus can deal more to an enemy inside it.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ MARK_ZONE: { radius: <double[0..]=4>, duration: <ticks[0..]=100> } }`
- _param_ `radius` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ MARK_ZONE: { radius: 4, duration: 100, who: "@Victim" } }`

### MAX_HEALTH_DRAIN

Temporarily remove `fraction` of the target's overhealth (max health above `baseline`) plus a flat `amount`, restoring it after `duration` ticks. Default target the combat victim.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MAX_HEALTH_DRAIN: { fraction: <double[0..1]=0.5>, baseline: <double[0..]=20>, amount: <double[0..]=0>, duration: <ticks[0..]=60> } }`
- _param_ `fraction` `double[0..1]`
- _param_ `baseline` `double[0..]`
- _param_ `amount` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ MAX_HEALTH_DRAIN: { fraction: 0.5, baseline: 20, duration: 60, who: "@Victim" } }`

### MESSAGE

Send feedback on a channel: chat (default), actionbar, or title (with subtitle + fade/stay/fade timings). Default recipient self; `who` can name any party (e.g. @Victim). The `{ATTACKER}`/`{VICTIM}` tokens expand to the activating player and the other combat party. Replaces ACTIONBAR/TITLE.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ MESSAGE: { text: <string>, channel: <enum{chat|actionbar|title}=chat>, subtitle: <string=>, number: <double>, decimals: <int[0..12]=2>, grouping: <bool=false>, fadeIn: <ticks[0..]=10>, stay: <ticks[0..]=70>, fadeOut: <ticks[0..]=20> } }`
- _param_ `text` `string`
- _param_ `channel` `enum{chat|actionbar|title}`
- _param_ `subtitle` `string` — title channel only
- _param_ `number` `double` — optional expression exposed as {NUMBER}
- _param_ `decimals` `int[0..12]` — maximum decimal places for {NUMBER}
- _param_ `grouping` `bool` — group thousands in {NUMBER} with commas
- _param_ `fadeIn` `ticks[0..]` — title channel only
- _param_ `stay` `ticks[0..]` — title channel only
- _param_ `fadeOut` `ticks[0..]` — title channel only
- _target_ `who`: selector `SELF`
- _example_: `{ MESSAGE: { text: "&aCritical hit!" } }`

### MODIFY_EXP

Modify a player target's experience: give to them, take from them, or transfer (move at most the target's experience to the activator — never more than they hold). Replaces GIVE_EXP.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_EXP: { amount: <int[0..]>, mode: <enum{give|take|transfer}=give> } }`
- _param_ `amount` `int[0..]`
- _param_ `mode` `enum{give|take|transfer}`
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_EXP: { amount: 50, mode: give, who: "@Self" } }`

### MODIFY_FOOD

Modify a player target's hunger: give food points (clamped to 20) or take them (clamped to 0). Replaces FEED.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_FOOD: { amount: <int[0..]>, mode: <enum{give|take}=give> } }`
- _param_ `amount` `int[0..]`
- _param_ `mode` `enum{give|take}`
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_FOOD: { amount: 6, mode: give, who: "@Self" } }`

### MODIFY_HEALTH

Modify a target's health: give heals them, take deals direct health damage, transfer (lifesteal) damages the target and heals the activator by the same amount, set forces their health to the amount. Replaces HEAL.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_HEALTH: { amount: <double[0..]>, cap: <double[0..]=0>, mode: <enum{give|take|transfer|set}=give>, base-round: <enum{none|floor}=none>, success-sound: <sound>, sound-volume: <double[0..]=1>, sound-pitch: <double[0..]=1>, require-headroom: <bool=false>, strict-headroom: <bool=false>, success-particle: <particle>, particle-count: <int[0..]=1>, particle-spread: <double[0..]=0>, particle-speed: <double[0..]=0>, particle-anchor: <enum{body|feet|eye}=body>, particle-y-offset: <double[-16..16]=0> } }`
- _param_ `amount` `double[0..]`
- _param_ `cap` `double[0..]` — optional maximum resolved amount; 0 = uncapped
- _param_ `mode` `enum{give|take|transfer|set}`
- _param_ `base-round` `enum{none|floor}` — for give mode, floor uses floor(current health) + amount before clamping
- _param_ `success-sound` `sound` — private player cue emitted only when give mode actually raises health
- _param_ `sound-volume` `double[0..]`
- _param_ `sound-pitch` `double[0..]`
- _param_ `require-headroom` `bool` — heal only when current + amount does not exceed max
- _param_ `strict-headroom` `bool` — with require-headroom, require current + amount to be strictly below max
- _param_ `success-particle` `particle`
- _param_ `particle-count` `int[0..]`
- _param_ `particle-spread` `double[0..]`
- _param_ `particle-speed` `double[0..]`
- _param_ `particle-anchor` `enum{body|feet|eye}`
- _param_ `particle-y-offset` `double[-16..16]`
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_HEALTH: { amount: 4, mode: give, who: "@Self" } }`

### MODIFY_MONEY

Modify a player target's balance: give to them, take from them, transfer (move at most the target's balance to the activator — never more than they hold), steal_percent (give the activator that PERCENT of the target's balance — amount is a 0..100 percentage), or interest_percent (deposit the TARGET that percent of their OWN balance — minted income, ADR-0052 Fish; one deposit is ceilinged by the live pets.max-percent-money-cap). Replaces GIVE_MONEY/TAKE_MONEY/STEAL_MONEY[_PERCENT].

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_MONEY: { amount: <double[0..]>, mode: <enum{give|take|transfer|steal_percent|interest_percent}=give> } }`
- _param_ `amount` `double[0..]`
- _param_ `mode` `enum{give|take|transfer|steal_percent|interest_percent}`
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_MONEY: { amount: 100, mode: give, who: "@Self" } }`

### MOVEMENT_SPEED

Set the player target's walk speed for a span of ticks, then restore the default (0.2).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MOVEMENT_SPEED: { speed: <double[-1..1]>, ticks: <ticks[0..]=200> } }`
- _param_ `speed` `double[-1..1]`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ MOVEMENT_SPEED: { speed: 0.4, ticks: 200 } }`

### PARTICLE

Spawn particles at the activation location, or at each entity in `who` when given (centered on the body, not the feet). `block` carries a block material as crack/dust data. `spread` is the horizontal Gaussian offset (set 0 for a point burst); `spread-y` the vertical offset, where the -1 default means "use `spread`". No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `{ PARTICLE: { particle: <particle>, count: <int[0..]=1>, block: <material>, spread: <double[0..4]=0.4>, spread-y: <double[-1..4]=-1>, random-spread: <bool=false>, speed: <double[0..]=0>, anchor: <enum{body|feet|eye}=body>, y-offset: <double[-16..16]=0> } }`
- _param_ `particle` `particle`
- _param_ `count` `int[0..]`
- _param_ `block` `material`
- _param_ `spread` `double[0..4]`
- _param_ `spread-y` `double[-1..4]`
- _param_ `random-spread` `bool` — choose each authored axis spread independently in [0, spread)
- _param_ `speed` `double[0..]` — particle packet/API speed-extra value
- _param_ `anchor` `enum{body|feet|eye}` — entity target anchor; ignored for @Here
- _param_ `y-offset` `double[-16..16]` — vertical position offset after selecting the anchor
- _target_ `who`: selector `HERE`
- _example_: `{ PARTICLE: { particle: BLOCK_CRACK, count: 20, block: REDSTONE_BLOCK, who: "@Victim" } }`

### PARTICLE_LINE

Draw a coloured-dust line from each 'who' target's hip to the actor's hip, `density` motes per block, tinted r/g/b (0-255). Pair with who: @AllPlayers{r=N} for a fan of tethers.

- _affinity_: `REGION`
- _usage_: `{ PARTICLE_LINE: { particle: <particle>, r: <int[0..255]=255>, g: <int[0..255]=255>, b: <int[0..255]=255>, size: <double[0..]=1>, density: <double[0..]=2>, height: <double=1> } }`
- _param_ `particle` `particle`
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _param_ `size` `double[0..]`
- _param_ `density` `double[0..]`
- _param_ `height` `double`
- _target_ `who`: selector `AOE`
- _example_: `{ PARTICLE_LINE: { particle: REDSTONE, r: 255, g: 255, b: 255, density: 2, who: "@AllPlayers{r=7}" } }`

### PARTICLE_RING

Draw a horizontal ring of `count` coloured-dust motes of radius `radius` at `height` above the target's feet (default @Self), tinted r/g/b (0-255). A radius / aura indicator.

- _affinity_: `REGION`
- _usage_: `{ PARTICLE_RING: { particle: <particle>, r: <int[0..255]=255>, g: <int[0..255]=255>, b: <int[0..255]=255>, size: <double[0..]=1>, radius: <double[0..]=3>, count: <int[1..]=36>, height: <double=1> } }`
- _param_ `particle` `particle`
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _param_ `size` `double[0..]`
- _param_ `radius` `double[0..]`
- _param_ `count` `int[1..]`
- _param_ `height` `double`
- _target_ `who`: selector `SELF`
- _example_: `{ PARTICLE_RING: { particle: REDSTONE, r: 255, g: 255, b: 255, radius: 7, count: 60 } }`

### POTION

Apply a potion effect to the target(s) at the given LEVEL (1-based: level 1 = the I tier), for a duration in ticks. The effect name is resolved to a handle at compile time. On a HELD/PASSIVE source it is removed again when the item is unequipped (§B lifecycle).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ POTION: { effect: <potion_effect>, level: <int[1..]>, duration: <ticks[0..]>, duration-min: <ticks[0..]>, duration-max: <ticks[0..]>, duration-step: <int[1..]=1>, duration-random-base: <double[0..]>, duration-random-range: <double[0..]>, duration-random-scale: <int[1..]=1>, unless-present: <bool=false>, force: <bool=false>, chance: <double[0..100]=100>, particle: <particle>, particle-count: <int[0..]=1>, particle-speed: <double[0..]=0>, particle-anchor: <enum{body|feet|eye}=body>, particle-y-offset: <double[-16..16]=0> } }`
- _param_ `effect` `potion_effect`
- _param_ `level` `int[1..]`
- _param_ `duration` `ticks[0..]`
- _param_ `duration-min` `ticks[0..]`
- _param_ `duration-max` `ticks[0..]`
- _param_ `duration-step` `int[1..]`
- _param_ `duration-random-base` `double[0..]` — when paired with duration-random-range, floor(random*range+base)*scale
- _param_ `duration-random-range` `double[0..]`
- _param_ `duration-random-scale` `int[1..]`
- _param_ `unless-present` `bool` — skip the target when it already has this potion type
- _param_ `force` `bool` — replace the active potion using Bukkit's force-add path
- _param_ `chance` `double[0..100]` — independent roll per resolved target
- _param_ `particle` `particle` — particle emitted at a target only when its roll succeeds
- _param_ `particle-count` `int[0..]`
- _param_ `particle-speed` `double[0..]`
- _param_ `particle-anchor` `enum{body|feet|eye}`
- _param_ `particle-y-offset` `double[-16..16]`
- _target_ `who`: selector `SELF`
- _example_: `{ POTION: { effect: STRENGTH, level: 1, duration: 100 } }`

### POTION_LOCK

Strip a potion effect from the target(s) and continuously deny it for `ticks` — any re-application during the window is refused, so it cannot be maintained by a passive buff. Default target self.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ POTION_LOCK: { effect: <potion_effect>, ticks: <ticks[0..]=100> } }`
- _param_ `effect` `potion_effect`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ POTION_LOCK: { effect: SPEED, ticks: 100, who: "@Victim" } }`

### PROJECTILE

Launch count projectiles of a type from the activator's eye (covers SPAWN_ARROWS via the ARROW type). For an explosive projectile, yield sets the blast (-1 = vanilla default) and incendiary lights fires.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ PROJECTILE: { type: <entity_type>, count: <int[1..]=1>, speed: <double[0..]=1.5>, yield: <double=-1>, incendiary: <bool=false> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `speed` `double[0..]`
- _param_ `yield` `double`
- _param_ `incendiary` `bool`
- _example_: `{ PROJECTILE: { type: FIREBALL, count: 1, speed: 1.5, yield: 2, incendiary: true } }`

### PROJECTILE_MARK

Attach an integer mark to the projectile fired by the current BOW_FIRE activation.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ PROJECTILE_MARK: { value: <int[1..]> } }`
- _param_ `value` `int[1..]`
- _example_: `{ PROJECTILE_MARK: { value: 3 } }`

### REFLECT

Mark the target so a percent of their own outgoing damage is reflected back onto them for a duration in ticks (Hex). Player targets only; default target the combat victim.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REFLECT: { percent: <double[0..]>, duration: <ticks[0..]=80> } }`
- _param_ `percent` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ REFLECT: { percent: 20, duration: 80, who: "@Victim" } }`

### REGENERATION

Start one non-overlapping regeneration window per player; heal at t=0 and every period through the inclusive uniformly-selected duration boundary. Particle feedback occurs only on a heal.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REGENERATION: { amount: <double[0..]>, period: <ticks[1..]>, duration-min: <ticks[0..]>, duration-max: <ticks[0..]>, particle: <particle>, count: <int[0..]=1>, speed: <double[0..]=0>, anchor: <enum{body|feet|eye}=body>, y-offset: <double[-16..16]=0> } }`
- _param_ `amount` `double[0..]`
- _param_ `period` `ticks[1..]`
- _param_ `duration-min` `ticks[0..]`
- _param_ `duration-max` `ticks[0..]`
- _param_ `particle` `particle`
- _param_ `count` `int[0..]`
- _param_ `speed` `double[0..]`
- _param_ `anchor` `enum{body|feet|eye}`
- _param_ `y-offset` `double[-16..16]`
- _target_ `who`: selector `SELF`
- _example_: `{ REGENERATION: { amount: 1, period: 60, duration-min: 110, duration-max: 125, particle: SPELL, count: 15, speed: 0.04, anchor: eye, y-offset: 0.5 } }`

### REMOVE_ARMOR

Strip one random worn armour piece from the target(s) and drop it.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_ARMOR: { destination: <enum{drop|inventory}=drop>, victim-message: <string=>, victim-description: <string=>, attacker-message: <string=>, blank-lines: <int[0..]=0>, sound: <sound>, sound-volume: <double[0..]=1>, sound-pitch: <double[0..]=1>, block: <material>, block-height: <int[0..]=1> } }`
- _param_ `destination` `enum{drop|inventory}`
- _param_ `victim-message` `string`
- _param_ `victim-description` `string`
- _param_ `attacker-message` `string`
- _param_ `blank-lines` `int[0..]`
- _param_ `sound` `sound`
- _param_ `sound-volume` `double[0..]`
- _param_ `sound-pitch` `double[0..]`
- _param_ `block` `material`
- _param_ `block-height` `int[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ REMOVE_ARMOR: {} }`

### REMOVE_ITEM

Remove up to count of a material from the player target(s)' inventory.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_ITEM: { material: <material>, count: <int[1..]=1> } }`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_ITEM: { material: DIAMOND, count: 1, who: "@Self" } }`

### REMOVE_POTION

Remove a potion effect from the target(s).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_POTION: { effect: <potion_effect> } }`
- _param_ `effect` `potion_effect`
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_POTION: { effect: POISON } }`

### REMOVE_POTION_UP_TO

Remove the selected potion only when its current 1-based strength is at or below max-level.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_POTION_UP_TO: { effect: <potion_effect>, max-level: <int[1..]> } }`
- _param_ `effect` `potion_effect`
- _param_ `max-level` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_POTION_UP_TO: { effect: BLINDNESS, max-level: 2, who: "@Self" } }`

### REMOVE_PROJECTILE

Request deletion of the raw projectile that caused the current damage event.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REMOVE_PROJECTILE: {} }`
- _example_: `{ REMOVE_PROJECTILE: {} }`

### REMOVE_SOULS

Debit souls from a soul gem: @Self (default) charges the activator's active gem, @Victim drains the target's own gem. A no-op when that player is not in soul mode.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REMOVE_SOULS: { amount: <int[1..]> } }`
- _param_ `amount` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_SOULS: { amount: 5 } }`

### REQUIRE_SOUL_TOTAL

Continue only when the actor's authoritative current soul total modulo divisor equals remainder; require-paid also rejects a live soul-cost waiver.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REQUIRE_SOUL_TOTAL: { divisor: <int[1..]=20>, remainder: <int[0..]=0>, require-paid: <bool=false> } }`
- _param_ `divisor` `int[1..]`
- _param_ `remainder` `int[0..]`
- _param_ `require-paid` `bool`
- _example_: `{ REQUIRE_SOUL_TOTAL: { divisor: 20, remainder: 0, require-paid: true } }`

### REQUIRE_VALUE

Continue the current ability only when value is between min and max, inclusive.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REQUIRE_VALUE: { value: <double>, min: <double=-1.7976931348623157E308>, max: <double=1.7976931348623157E308> } }`
- _param_ `value` `double`
- _param_ `min` `double`
- _param_ `max` `double`
- _example_: `{ REQUIRE_VALUE: { value: '%victim.isplayer%', min: 1 } }`

### REQUIRE_VAR

Continue only when the named per-player variable has the authored presence state.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REQUIRE_VAR: { name: <string>, present: <bool=true> } }`
- _param_ `name` `string`
- _param_ `present` `bool`
- _target_ `who`: selector `SELF`
- _example_: `{ REQUIRE_VAR: { name: feedback-throttle, present: false } }`

### RESISTED_POTION

Use one shared base-chance roll for all targets, subtracting each target's worn resistance enchant level independently before applying the potion.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ RESISTED_POTION: { chance: <double[0..100]>, resistance-enchant: <string>, resistance-enchant-alt: <string=>, resist-per-level: <double[0..]>, effect: <potion_effect>, level: <int[1..]>, duration: <ticks[0..]>, blocked-message: <string=>, cue-block: <enum{none|current-or-stone}=none>, cue-y-offset: <double=0> } }`
- _param_ `chance` `double[0..100]`
- _param_ `resistance-enchant` `string`
- _param_ `resistance-enchant-alt` `string`
- _param_ `resist-per-level` `double[0..]`
- _param_ `effect` `potion_effect`
- _param_ `level` `int[1..]`
- _param_ `duration` `ticks[0..]`
- _param_ `blocked-message` `string`
- _param_ `cue-block` `enum{none|current-or-stone}`
- _param_ `cue-y-offset` `double`
- _target_ `who`: selector `AOE`
- _example_: `{ RESISTED_POTION: { chance: 12, resistance-enchant: enchants/metaphysical, resist-per-level: 4, effect: SLOW, level: 2, duration: 60, who: '@Aoe{r=2,filter=NONALLIES}' } }`

### RESISTED_ROLL

Gate the remaining effects with one base-chance roll and the same roll against chance minus resistance-level*resist-per-level. Values are percentages.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ RESISTED_ROLL: { chance: <double[0..100]>, resistance-level: <double[0..]=0>, resist-per-level: <double[0..]=0>, min-chance: <double[0..100]=0>, blocked-message: <string=>, immune: <double[0..]=0>, immune-message: <string=>, immune-chance: <double[0..100]=0>, immune-chance-message: <string=> } }`
- _param_ `chance` `double[0..100]`
- _param_ `resistance-level` `double[0..]`
- _param_ `resist-per-level` `double[0..]`
- _param_ `min-chance` `double[0..100]`
- _param_ `blocked-message` `string`
- _param_ `immune` `double[0..]`
- _param_ `immune-message` `string`
- _param_ `immune-chance` `double[0..100]`
- _param_ `immune-chance-message` `string`
- _example_: `{ RESISTED_ROLL: { chance: 12, resistance-level: '%victim.metaphysical%', resist-per-level: 2.5, blocked-message: '&8&l** METAPHYSICAL blocked! **' } }`

### RUN_COMMAND

Run a command as the console (default) or as the activating player. The `{PLAYER}`/`{UUID}`/`{WORLD}` tokens expand to the actor's name, uuid, and world. Affinity GLOBAL — the console path runs on the global thread; the player path runs on the actor's own thread. The `{PLAYER}` token refuses to run the command when the actor's name falls outside the standard `[A-Za-z0-9_]` (1-16) username charset.

- _affinity_: `GLOBAL`
- _usage_: `{ RUN_COMMAND: { command: <string>, as: <enum{console|player}=console> } }`
- _param_ `command` `string`
- _param_ `as` `enum{console|player}` — who runs it: console (default) or the player
- _example_: `{ RUN_COMMAND: { command: "eco give {PLAYER} 100" } }`

### SABOTAGE

Require settled held weapon, active positive soul mode, and no Soul Trap; charge eight souls at most once per second and mark the victim for Rocket Escape's 10% per level failure roll.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SABOTAGE: { level: <int[1..5]>, duration: <ticks[0..]=20>, cost: <int[0..]=8>, cost-period: <ticks[0..]=20>, anti-swap: <ticks[0..]=5> } }`
- _param_ `level` `int[1..5]`
- _param_ `duration` `ticks[0..]`
- _param_ `cost` `int[0..]`
- _param_ `cost-period` `ticks[0..]`
- _param_ `anti-swap` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ SABOTAGE: { level: 5 } }`

### SEEK

Make the projectile fired by this BOW_FIRE activation home onto the nearest target in sight.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SEEK: {} }`
- _example_: `{ SEEK: {} }`

### SET_BLOCK

Set the target block(s) to a material (default @Here = the activation block).

- _affinity_: `REGION`
- _usage_: `{ SET_BLOCK: { material: <material> } }`
- _param_ `material` `material`
- _target_ `at`: selector `HERE`
- _example_: `{ SET_BLOCK: { material: OBSIDIAN } }`

### SET_VAR

Set a per-player variable readable in later conditions as %name% (ttl ticks, 0 = forever).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SET_VAR: { name: <string>, value: <string=>, ttl: <ticks[0..]=0>, clear-on-stop: <bool=false> } }`
- _param_ `name` `string`
- _param_ `value` `string`
- _param_ `ttl` `ticks[0..]`
- _param_ `clear-on-stop` `bool`
- _target_ `who`: selector `SELF`
- _example_: `{ SET_VAR: { name: rage, value: 1, ttl: 200, who: "@Self" } }`

### SMELT

Auto-smelt the block broken by this MINE activation, with an output amount, recipe profile, and up to two held-enchant exclusions.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SMELT: { amount: <int[1..]=1>, profile: <enum{RECIPES|IRON_GOLD_ORE}=RECIPES>, unless-held: <string=>, unless-held2: <string=> } }`
- _param_ `amount` `int[1..]`
- _param_ `profile` `enum{RECIPES|IRON_GOLD_ORE}`
- _param_ `unless-held` `string`
- _param_ `unless-held2` `string`
- _example_: `{ SMELT: {} }`

### SOUL_TRAP

Require active attacker soul mode and a settled held slot, enforce trap/grace windows, rate-limit the attacker cost, steal victim souls or deal fallback damage, suppress the authored soul group, disable victim soul mode, and emit exact private feedback.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SOUL_TRAP: { key: <string>, duration: <ticks[0..]>, retrap-grace: <ticks[0..]=100>, steal: <int[1..]>, fallback-damage: <double[0..]>, cost: <int[0..]=5>, cost-period: <ticks[0..]=20>, anti-swap: <ticks[0..]=5>, message: <string>, sound: <sound>, sound-volume: <double[0..]=1>, sound-pitch: <double[0..]=1>, particle-1: <particle>, particle-1-count: <int[0..]>, particle-1-speed: <double[0..]>, particle-2: <particle>, particle-2-count: <int[0..]>, particle-2-speed: <double[0..]> } }`
- _param_ `key` `string` — soul group key; compile-lowered into the shared suppression namespace
- _param_ `duration` `ticks[0..]`
- _param_ `retrap-grace` `ticks[0..]`
- _param_ `steal` `int[1..]`
- _param_ `fallback-damage` `double[0..]`
- _param_ `cost` `int[0..]`
- _param_ `cost-period` `ticks[0..]`
- _param_ `anti-swap` `ticks[0..]`
- _param_ `message` `string`
- _param_ `sound` `sound`
- _param_ `sound-volume` `double[0..]`
- _param_ `sound-pitch` `double[0..]`
- _param_ `particle-1` `particle`
- _param_ `particle-1-count` `int[0..]`
- _param_ `particle-1-speed` `double[0..]`
- _param_ `particle-2` `particle`
- _param_ `particle-2-count` `int[0..]`
- _param_ `particle-2-speed` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ SOUL_TRAP: { key: soul, duration: 80, steal: 10, fallback-damage: 2, message: "&9&l** SOUL TRAP &7[4s]&9&l**", sound: ENDERMAN_SCREAM, particle-1: SPELL_WITCH, particle-1-count: 60, particle-1-speed: 0.7, particle-2: SPELL, particle-2-count: 25, particle-2-speed: 0.4 } }`

### SOUND

Play a sound at the activation location. No-op if the activation has no location.

- _affinity_: `REGION`
- _usage_: `{ SOUND: { sound: <sound>, volume: <double[0..]=1>, pitch: <double[0..]=1>, dedupe: <bool=true>, audience: <enum{world|target}=world>, players-only: <bool=false>, at: <enum{target|activation}=target> } }`
- _param_ `sound` `sound`
- _param_ `volume` `double[0..]`
- _param_ `pitch` `double[0..]`
- _param_ `dedupe` `bool` — collapse the same sound id to one play per event sink
- _param_ `audience` `enum{world|target}`
- _param_ `players-only` `bool` — skip resolved non-player living targets
- _param_ `at` `enum{target|activation}` — where the sound is located when who resolves entities
- _target_ `who`: selector `HERE`
- _example_: `{ SOUND: { sound: ENTITY_GENERIC_EXPLODE, volume: 1, pitch: 1 } }`

### SPAWN_ENTITY

Spawn count entities of type at the target's (or activation) location; ttl ticks until removal (0 = permanent), optional starting health, and owner=activator to tame an owned summon to the activator. ADR-0052 summon flags: powered charges a creeper; ai=false disables mob AI; targeting=false stops the summon acquiring targets; saddled + mount=activator make a horse-type rideable and seat the activator; detonate=PLAYER_HIT makes a creeper explode ONLY when a player hits it (it never self-detonates); invincible=true zeroes all damage to the summon (it cannot die but still takes hits and knockback); speed is a multiplier on the spawned entity's vanilla movement-speed base (0 = untouched). Replaces SPAWN/TNT.

- _affinity_: `REGION`
- _usage_: `{ SPAWN_ENTITY: { type: <entity_type>, count: <int[1..]=1>, ttl: <ticks[0..]=0>, health: <double[0..]=0>, owner: <enum{none|activator}=none>, powered: <bool=false>, ai: <bool=true>, targeting: <bool=true>, saddled: <bool=false>, mount: <enum{none|activator}=none>, detonate: <enum{NONE|PLAYER_HIT}=NONE>, invincible: <bool=false>, speed: <double[0..]=0>, spawn-y-offset: <double[-16..16]=0>, custom-name: <string=>, plague-level: <int[0..]=0> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `health` `double[0..]`
- _param_ `owner` `enum{none|activator}`
- _param_ `powered` `bool`
- _param_ `ai` `bool`
- _param_ `targeting` `bool`
- _param_ `saddled` `bool`
- _param_ `mount` `enum{none|activator}`
- _param_ `detonate` `enum{NONE|PLAYER_HIT}`
- _param_ `invincible` `bool`
- _param_ `speed` `double[0..]`
- _param_ `spawn-y-offset` `double[-16..16]`
- _param_ `custom-name` `string`
- _param_ `plague-level` `int[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ SPAWN_ENTITY: { type: WOLF, count: 1, ttl: 0, health: 0, owner: activator } }`

### SPAWN_SWARM

Summon count entities of type evenly spaced on a radius-block ring around the activator, raised rise blocks (chest height), each facing directly outward, with VANILLA AI, auto-removed after ttl ticks. speed < 1 slows each to that fraction of its vanilla AI speed via a per-tick velocity damp (Bat-style AI ignores the speed attribute). cloud: true makes the summons orbit the 1x2x1 pillar directly in front of whoever attacked the activator most recently within cloud-range blocks (vision cloud); with no such attacker they keep vanilla AI. While clouding, the orbit's own pacing overrides speed.

- _affinity_: `REGION`
- _usage_: `{ SPAWN_SWARM: { type: <entity_type>, count: <int[1..]=1>, radius: <double[0..]=0.5>, rise: <double[0..]=1.2>, ttl: <ticks[0..]=300>, speed: <double[0..1]=1>, cloud: <bool=false>, cloud-range: <double[1..]=16> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `radius` `double[0..]`
- _param_ `rise` `double[0..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `speed` `double[0..1]`
- _param_ `cloud` `bool`
- _param_ `cloud-range` `double[1..]`
- _example_: `{ SPAWN_SWARM: { type: BAT, count: 10, radius: 0.5, ttl: 300, speed: 0.5 } }`

### STRIP_SCROLL

Remove one protection scroll marker from a random protected piece of the target's worn armour (+ held item unless hand: false): scroll HOLY strips a Holy White Scroll, WHITE a White Scroll (its guard flag included). A target with no protected piece is a no-op. Rate-limit with the ability's chance gate (the Anubis per-hit percent).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ STRIP_SCROLL: { scroll: <enum{HOLY|WHITE}=HOLY>, hand: <bool=true> } }`
- _param_ `scroll` `enum{HOLY|WHITE}`
- _param_ `hand` `bool`
- _target_ `who`: selector `VICTIM`
- _example_: `{ STRIP_SCROLL: { scroll: HOLY, who: "@Victim" } }`

### SUPPRESS

Disable a target's enchant/group/type (the key) for a duration in ticks (DISABLE_ENCHANT/GROUP/TYPE), or with scope KIND every ability carrying the keyed effect head (e.g. MODIFY_FOOD). mode: timed (the duration window) or next-hit (a one-shot that clears after the target's next `charges` incoming hits, Neutralize). Default target the combat victim.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUPPRESS: { scope: <enum{ENCHANT|GROUP|TYPE|KIND}>, key: <string>, duration: <ticks[0..]=200>, mode: <enum{timed|next-hit}=timed>, charges: <int[1..]=1> } }`
- _param_ `scope` `enum{ENCHANT|GROUP|TYPE|KIND}`
- _param_ `key` `string`
- _param_ `duration` `ticks[0..]`
- _param_ `mode` `enum{timed|next-hit}`
- _param_ `charges` `int[1..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ SUPPRESS: { scope: GROUP, key: lifesteal, duration: 200, who: "@Victim" } }`

### SUPPRESS_IMMUNE

Make the target(s) immune to suppression (DISABLE_ENCHANT/GROUP/TYPE) while worn — a maintained PASSIVE flag, armed on equip and lifted on unequip. An optional chance (default 100) makes it a per-suppression roll instead of absolute. Player-only.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUPPRESS_IMMUNE: { chance: <int[0..100]=100> } }`
- _param_ `chance` `int[0..100]`
- _target_ `who`: selector `SELF`
- _example_: `{ SUPPRESS_IMMUNE: { chance: 4, who: "@Self" } }`

### SWAP_POSITION

Castling (reforges): lock the enemy in your crosshair (range, line of sight), channel for channel ticks with audible countdown cues and a warning to the victim, then both of you swap positions — velocities zeroed, each keeps their own facing. Line of sight broken, range + slack exceeded, a world change or a death aborts it; the use stays spent. Reforge-service-owned: this effect emits no intent of its own.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SWAP_POSITION: { range: <double[1..]=12>, channel: <ticks[0..]=40>, check-period: <int[1..10]=2>, cue-period: <ticks[0..]=10>, range-slack: <double[0..]=2> } }`
- _param_ `range` `double[1..]` — crosshair target acquisition + max channel distance
- _param_ `channel` `ticks[0..]` — channel length (the 2-second countdown)
- _param_ `check-period` `int[1..10]` — LOS/validity re-check cadence, ticks
- _param_ `cue-period` `ticks[0..]` — countdown tick-cue cadence
- _param_ `range-slack` `double[0..]` — extra blocks past range before a drift aborts
- _example_: `{ SWAP_POSITION: { range: 12, channel: 40 } }`

### TELEBLOCK

Block the target player(s) from teleporting (ender pearl / chorus fruit) for duration ticks.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ TELEBLOCK: { duration: <ticks[0..]=400>, mode: <enum{BLOCK|CLEAR}=BLOCK> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `mode` `enum{BLOCK|CLEAR}`
- _target_ `who`: selector `VICTIM`
- _example_: `{ TELEBLOCK: { duration: 400 } }`

### TELEPORT

Teleport the target to the actor's or the victim's location.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ TELEPORT: { to: <enum{VICTIM|ACTOR}=VICTIM> } }`
- _param_ `to` `enum{VICTIM|ACTOR}` — destination party: the victim or the actor
- _target_ `who`: selector `SELF`
- _example_: `{ TELEPORT: { to: VICTIM } }`

### TELEPORT_BEHIND

Teleport the mover(s) `distance` blocks behind the reference (of: VICTIM — the attacker on a DEFENSE trigger — or ACTOR), facing as it faces. Unsafe (blocked / wall between) → onFail ONTOP lands on the reference, NONE cancels.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ TELEPORT_BEHIND: { of: <enum{VICTIM|ACTOR}=VICTIM>, distance: <double[0..]=1>, onFail: <enum{ONTOP|NONE}=ONTOP>, sound: <sound>, sound-volume: <double[0..]=1>, sound-pitch: <double[0..]=1>, departure-particle: <particle>, arrival-particle: <particle>, particle-count: <int[0..]=20>, particle-speed: <double[0..]=0.5>, particle-spread: <double[0..]=1> } }`
- _param_ `of` `enum{VICTIM|ACTOR}`
- _param_ `distance` `double[0..]`
- _param_ `onFail` `enum{ONTOP|NONE}`
- _param_ `sound` `sound`
- _param_ `sound-volume` `double[0..]`
- _param_ `sound-pitch` `double[0..]`
- _param_ `departure-particle` `particle`
- _param_ `arrival-particle` `particle`
- _param_ `particle-count` `int[0..]`
- _param_ `particle-speed` `double[0..]`
- _param_ `particle-spread` `double[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ TELEPORT_BEHIND: { of: VICTIM, distance: 1, onFail: ONTOP, who: "@Self" } }`

### TELEPORT_DROPS

Send the block's drops straight to the breaker's inventory (this MINE activation).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ TELEPORT_DROPS: {} }`
- _example_: `{ TELEPORT_DROPS: {} }`

### TEMP_BLOCK

Place a temporary block shape that reverts after `ticks`: shape POINT / FOOTPRINT (radius) / COLUMN (height, ahead in the target's facing) / BOX (width × height × depth filled volume horizontally centred on the target — the ADR-0052 Spider webs), at feet level + dy. airOnly only replaces air (safe placement); a non-airOnly FOOTPRINT replaces only the solid ground under the feet (never air, so a trail can't scaffold); other shapes replace anything and restore on revert. A radius-0 FOOTPRINT trails as a snake — consecutive stamps join into a gapless, 4-connected footprint path even at sprint speed and on diagonals. Give material2/3/4 to place a mixed palette: each block independently picks a material from a deterministic per-block hash of its coordinates — a noisy, random-looking scatter (re-placing the same block always picks the same material). A BOX is always single-material (palette[0]).

- _affinity_: `REGION`
- _usage_: `{ TEMP_BLOCK: { shape: <enum{POINT|FOOTPRINT|COLUMN|BOX}=POINT>, material: <material>, material2: <material>, material3: <material>, material4: <material>, ticks: <ticks[0..]=60>, radius: <int[0..4]=0>, width: <int[1..8]=3>, height: <int[1..8]=1>, depth: <int[1..8]=3>, ahead: <int[0..8]=0>, dy: <int[-4..4]=0>, airOnly: <bool=true> } }`
- _param_ `shape` `enum{POINT|FOOTPRINT|COLUMN|BOX}`
- _param_ `material` `material`
- _param_ `material2` `material`
- _param_ `material3` `material`
- _param_ `material4` `material`
- _param_ `ticks` `ticks[0..]`
- _param_ `radius` `int[0..4]`
- _param_ `width` `int[1..8]`
- _param_ `height` `int[1..8]`
- _param_ `depth` `int[1..8]`
- _param_ `ahead` `int[0..8]`
- _param_ `dy` `int[-4..4]`
- _param_ `airOnly` `bool`
- _target_ `who`: selector `VICTIM`
- _example_: `{ TEMP_BLOCK: { shape: COLUMN, material: ICE, height: 2, ahead: 1, ticks: 60, who: "@Attacker" } }`

### TRAP_BREAK

Break every confining trap currently on the wearer — encasing webs, web boxes, cage cells — restoring the trapped blocks to their true originals immediately. Area floors and trails are unaffected. Works through ability silence (it is a block restore, not an ability negation).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ TRAP_BREAK: { whiff-sound: <sound=BLOCK_ANVIL_LAND> } }`
- _param_ `whiff-sound` `sound` — played (low-pitched) when nothing confining was found — a silent no-op is indistinguishable from a broken feature
- _target_ `who`: selector `SELF`
- _example_: `{ TRAP_BREAK: { } }`

### VELOCITY

Apply velocity to the target(s): mode=add uses x/y/z; mode=away knocks them back from the activator with strength. Replaces THROW/LAUNCH/KNOCKBACK.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ VELOCITY: { mode: <enum{add|set|away}=add>, x: <double=0>, y: <double=0>, z: <double=0>, strength: <double[0..]=0>, from: <enum{actor|victim}=actor> } }`
- _param_ `mode` `enum{add|set|away}`
- _param_ `x` `double`
- _param_ `y` `double`
- _param_ `z` `double`
- _param_ `strength` `double[0..]`
- _param_ `from` `enum{actor|victim}`
- _target_ `who`: selector `VICTIM`
- _example_: `{ VELOCITY: { mode: add, x: 0, y: 1.2, z: 0 } }`

### VIRUS_MARK

Multiply subsequent Poison and Wither damage to each target for the given duration.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ VIRUS_MARK: { multiplier: <double[0..]>, duration: <ticks[0..]> } }`
- _param_ `multiplier` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ VIRUS_MARK: { multiplier: 3, duration: 60, who: "@Victim" } }`

### WALKER

Lay a temporary platform of a material under the target for a duration (then revert), out to a radius. replace = AIR_ONLY | REPLACEABLE (air/liquid) | ANY.

- _affinity_: `REGION`
- _usage_: `{ WALKER: { material: <material>, ticks: <ticks[0..]=60>, radius: <int[0..4]=1>, replace: <enum{AIR_ONLY|REPLACEABLE|ANY}=REPLACEABLE> } }`
- _param_ `material` `material`
- _param_ `ticks` `ticks[0..]`
- _param_ `radius` `int[0..4]`
- _param_ `replace` `enum{AIR_ONLY|REPLACEABLE|ANY}`
- _target_ `who`: selector `SELF`
- _example_: `{ WALKER: { material: ICE, ticks: 80, radius: 1 } }`

### WARD

Ward the target player(s) with a typed guard flag for duration ticks: mob-target (mobs don't aggro unless provoked), invsee (others can't open their inventory), near (hidden from the proximity listing), splash-heal (healing splash potions boosted by amount%).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ WARD: { type: <enum{mob-target|invsee|near|splash-heal}>, duration: <ticks[0..]=100>, amount: <double=0> } }`
- _param_ `type` `enum{mob-target|invsee|near|splash-heal}`
- _param_ `duration` `ticks[0..]`
- _param_ `amount` `double`
- _target_ `who`: selector `SELF`
- _example_: `{ WARD: { type: splash-heal, duration: 100, amount: 50 } }`

### WATER_SPEED

Underwater movement boost while worn (PASSIVE/HELD): efficiency feeds the vanilla water_movement_efficiency attribute through one reconciled plugin-owned modifier (1.21+ only; older servers and 1.8.9 keep everything else and skip the boost). 0.09 ~ +10%, 0.14 ~ +15%, 0.20 ~ +20%, 0.26 ~ +25% swim speed.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ WATER_SPEED: { efficiency: <double[0..1]> } }`
- _param_ `efficiency` `double[0..1]`
- _example_: `{ WATER_SPEED: { efficiency: 0.09 } }`

### WEAKEN

Debuff the target's outgoing damage by a percent for a duration in ticks (non-stacking). Player targets only; default target the combat victim.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ WEAKEN: { percent: <double[0..]>, duration: <ticks[0..]=100> } }`
- _param_ `percent` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ WEAKEN: { percent: 15, duration: 100, who: "@Victim" } }`

## Selectors

Choose WHO an effect targets (`@Self`, `@Victim`, `@Aoe`, …). Routing is the effect's; a selector carries no affinity.

### ADD

The activation location offset by (x, y, z).

- _usage_: `{ ADD: { x: <double=0>, y: <double=0>, z: <double=0> } }`
- _param_ `x` `double` — x offset in blocks
- _param_ `y` `double` — y offset in blocks
- _param_ `z` `double` — z offset in blocks
- _example_: `@Add{y=2}`

### ALLPLAYERS

Every player within r blocks of the target, except the activator.

- _usage_: `{ ALLPLAYERS: { r: <double[0..]=32> } }`
- _param_ `r` `double[0..]` — search radius in blocks
- _example_: `@AllPlayers{r=32}`

### AOE

Living entities within r blocks of the target, except the activator; optionally filtered, capped, and with the combat victim excluded.

- _usage_: `{ AOE: { r: <double[0..]=4>, filter: <enum{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|NONALLIES|ENEMY_PLAYERS|ALLIES}=ALL>, limit: <int[0..]=0>, exclude: <enum{none|victim}=none> } }`
- _param_ `r` `double[0..]` — radius in blocks
- _param_ `filter` `enum{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|NONALLIES|ENEMY_PLAYERS|ALLIES}` — which entities to include
- _param_ `limit` `int[0..]` — max targets, nearest first (0 = unlimited)
- _param_ `exclude` `enum{none|victim}` — remove the combat victim from the matches (Destruction hits everyone BUT the primary victim)
- _example_: `@Aoe{r=6, filter=MONSTERS, exclude=victim}`

### ATTACKER

The entity that damaged the activator (for defensive effects).

- _usage_: `{ ATTACKER: {} }`
- _example_: `@Attacker`

### BLOCK

The first solid block the activator is looking at, within distance.

- _usage_: `{ BLOCK: { distance: <double[0..]=5> } }`
- _param_ `distance` `double[0..]` — max look distance in blocks
- _example_: `@Block`

### BLOCKINDISTANCE

The first solid block along the activator's line of sight, within distance.

- _usage_: `{ BLOCKINDISTANCE: { distance: <double[0..]=30> } }`
- _param_ `distance` `double[0..]` — max look distance in blocks
- _example_: `@BlockInDistance{distance=50}`

### ENTITYINSIGHT

The living entity the activator is looking at within r blocks, or nothing.

- _usage_: `{ ENTITYINSIGHT: { r: <double[0..]=16> } }`
- _param_ `r` `double[0..]` — maximum line-of-sight distance in blocks
- _example_: `@EntityInSight{r=16}`

### EYEHEIGHT

The activator's eye location (their position at eye level).

- _usage_: `{ EYEHEIGHT: {} }`
- _example_: `@EyeHeight`

### HERE

The activation block location itself — the default target of block effects.

- _usage_: `{ HERE: {} }`
- _example_: `@Here`

### MARKED

Every nearby living entity the activator currently has an active MARK on.

- _usage_: `{ MARKED: { r: <double[0..]=32> } }`
- _param_ `r` `double[0..]` — search radius in blocks
- _example_: `@Marked{r=32}`

### NEAREST

The single nearest living entity within r blocks (optionally filtered), except the activator.

- _usage_: `{ NEAREST: { r: <double[0..]=5>, filter: <enum{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}=ALL> } }`
- _param_ `r` `double[0..]` — search radius in blocks
- _param_ `filter` `enum{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}` — which entities to consider
- _example_: `@Nearest{r=5, filter=PLAYERS}`

### NEARESTPLAYER

The single nearest player within r blocks, except the activator.

- _usage_: `{ NEARESTPLAYER: { r: <double[0..]=16> } }`
- _param_ `r` `double[0..]` — search radius in blocks
- _example_: `@NearestPlayer{r=16}`

### PLAYERFROMNAME

The online player with the given exact name, or nothing if they are not online.

- _usage_: `{ PLAYERFROMNAME: { name: <string> } }`
- _param_ `name` `string` — the exact name of the online player to target
- _example_: `@PlayerFromName{name=Steve}`

### SELF

The activating player themself.

- _usage_: `{ SELF: {} }`
- _example_: `@Self`

### TRENCH

The square of blocks perpendicular to the look direction, centred on the activation block.

- _usage_: `{ TRENCH: { radius: <int[0..]=1> } }`
- _param_ `radius` `int[0..]` — half-width of the face (1 = 3x3)
- _example_: `@Trench{radius=1}`

### TUNNEL

The blocks directly ahead of the activation block, along the look direction.

- _usage_: `{ TUNNEL: { depth: <int[1..]=3> } }`
- _param_ `depth` `int[1..]` — blocks ahead along the look direction
- _example_: `@Tunnel{depth=4}`

### VEIN

Up to `limit` blocks contiguous with and matching the activation block (vein miner).

- _usage_: `{ VEIN: { limit: <int[1..]=64> } }`
- _param_ `limit` `int[1..]` — max blocks in the vein
- _example_: `@Vein{limit=32}`

### VICTIM

The combat victim (the entity the activator hit).

- _usage_: `{ VICTIM: {} }`
- _example_: `@Victim`

## Triggers

The event that fires an ability (an enchant/set/crystal's `trigger:`). Triggers take no arguments.

| Trigger | Direction | Uses held | Scans equipment | Needs target |
| --- | --- | --- | --- | --- |
| `ATTACK` | ATTACK | false | true | true |
| `BOW` | ATTACK | false | true | true |
| `TRIDENT` | ATTACK | false | true | true |
| `KILL` | ATTACK | false | true | true |
| `BOW_FIRE` | ATTACK | false | true | false |
| `DEFENSE` | DEFENSE | false | true | true |
| `FALL` | DEFENSE | false | true | false |
| `FIRE` | DEFENSE | false | true | false |
| `PASSIVE` | NEUTRAL | false | true | false |
| `MINE` | NEUTRAL | false | true | false |
| `DEATH` | NEUTRAL | false | true | false |
| `HELD` | NEUTRAL | true | false | false |
| `BREAK` | NEUTRAL | true | false | false |
| `ITEM_DAMAGE` | NEUTRAL | false | true | false |
| `EAT` | NEUTRAL | true | false | false |
| `FISHING` | NEUTRAL | true | false | false |
| `INTERACT` | NEUTRAL | true | false | false |
| `INTERACT_LEFT` | NEUTRAL | true | false | false |
| `INTERACT_RIGHT` | NEUTRAL | true | false | false |
| `REPEATING` | NEUTRAL | false | true | false |
| `COMMAND` | NEUTRAL | false | true | false |
| `IMPACT` | ATTACK | false | true | true |
| `EXP_GAIN` | NEUTRAL | false | true | false |
| `USE` | NEUTRAL | true | false | false |
| `GUARDIAN_HURT` | NEUTRAL | false | true | false |
| `ALLY_DEATH` | NEUTRAL | false | true | false |
| `BLOCK_DAMAGE` | NEUTRAL | true | false | false |

## Conditions

Boolean expressions over `%scope.name%` variables, combined with `&& || ! ( )` and the operators below (an ability's `condition:`).

### Relational operators

| Operator | Name |
| --- | --- |
| `==` | eq |
| `!=` | ne |
| `<` | lt |
| `<=` | le |
| `>` | gt |
| `>=` | ge |

### String operators

| Operator | Name |
| --- | --- |
| `contains` | contains |
| `matchesregex` | matches_regex |

### Flow / chance clauses

A condition may end in a clause `<test> : <outcome>` whose outcome is applied when the test is true (a bare condition with no clause is a gate that stops the activation when false).

| Clause | Effect when the test is true |
| --- | --- |
| `%continue%` | proceed to the chance roll as normal |
| `%stop%` | block this activation |
| `%force%` | force activation, skipping the chance roll |
| `%allow%` | allow activation regardless of the chance roll |
| `±N %chance%` | add N percentage points to the chance roll |

## Variables

The `%scope.name%` facts a condition (or a `MESSAGE`/`SET_VAR`) can read.

| Variable | Type |
| --- | --- |
| `%actor.behindvictim%` | BOOL |
| `%actor.belowvictim%` | NUM |
| `%actor.environment%` | STR |
| `%actor.falldistance%` | NUM |
| `%actor.food%` | NUM |
| `%actor.gamemode%` | STR |
| `%actor.groundblock%` | STR |
| `%actor.health%` | NUM |
| `%actor.healthpercent%` | NUM |
| `%actor.helditem%` | STR |
| `%actor.heldsettled5%` | BOOL |
| `%actor.level%` | NUM |
| `%actor.maxhealth%` | NUM |
| `%actor.totalexp%` | NUM |
| `%actor.type%` | STR |
| `%actor.walkspeed%` | NUM |
| `%actor.world%` | STR |
| `%aegisattackerindex%` | NUM |
| `%antigankattackers%` | NUM |
| `%attackerindex%` | NUM |
| `%block.type%` | STR |
| `%blocking%` | BOOL |
| `%combo%` | NUM |
| `%damage%` | NUM |
| `%damagecause%` | STR |
| `%distance%` | NUM |
| `%finaldamage%` | NUM |
| `%flying%` | BOOL |
| `%gliding%` | BOOL |
| `%isblock%` | BOOL |
| `%itemdamage.armor%` | BOOL |
| `%nearbyenemies%` | NUM |
| `%onfire%` | BOOL |
| `%onground%` | BOOL |
| `%projectileheight%` | NUM |
| `%projectilemark%` | NUM |
| `%ragestacks%` | NUM |
| `%recentattackers%` | NUM |
| `%sneaking%` | BOOL |
| `%sprinting%` | BOOL |
| `%swimming%` | BOOL |
| `%victim.bleedstacks%` | NUM |
| `%victim.blocking%` | BOOL |
| `%victim.boss%` | BOOL |
| `%victim.devouraffected%` | BOOL |
| `%victim.dragonslayer%` | NUM |
| `%victim.flying%` | BOOL |
| `%victim.food%` | NUM |
| `%victim.frommobspawner%` | BOOL |
| `%victim.gliding%` | BOOL |
| `%victim.health%` | NUM |
| `%victim.healthpercent%` | NUM |
| `%victim.helditem%` | STR |
| `%victim.induel%` | BOOL |
| `%victim.inzone%` | BOOL |
| `%victim.isplayer%` | NUM |
| `%victim.maxhealth%` | NUM |
| `%victim.metaphysical%` | NUM |
| `%victim.mobtype%` | STR |
| `%victim.necromancermask%` | BOOL |
| `%victim.poltergeist%` | NUM |
| `%victim.polymorphicmetaphysical%` | NUM |
| `%victim.rageaffected%` | BOOL |
| `%victim.slowed%` | BOOL |
| `%victim.sneaking%` | BOOL |
| `%victim.sprinting%` | BOOL |
| `%victim.sticky%` | NUM |
| `%victim.swimming%` | BOOL |
| `%victim.type%` | STR |
| `%victim.walkspeed%` | NUM |
| `%world.raining%` | BOOL |
| `%world.thundering%` | BOOL |
| `%world.time%` | NUM |
