# StarEnchants DSL reference

_Generated from the engine's effect / selector / trigger / condition / variable vocabularies. Do not edit by hand — run_ `./gradlew :engine:test --tests "*ReferenceDocDriftTest" -Dse.doc.regen=true` _to regenerate; the build fails if this file drifts from the code._

## Effects

The actions an ability runs. Each is a block map `{ HEAD: { param: value, who:, wait: } }` in an enchant/set/crystal's `effects:` list.

### BATTERY

Arm a damage battery on the wearer: the next `hits` landed hits they take each bank `bank-percent`% of the final damage; their next landed hit on an enemy unloads the entire bank as bonus damage on that hit, then the core resets — a hit with nothing banked still spends the core. No time limit; the ability cooldown paces re-arms. Cleared on death.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ BATTERY: { bank-percent: <double[0..100]=20>, hits: <int[1..10]=3>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `bank-percent` `double[0..100]`
- _param_ `hits` `int[1..10]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ BATTERY: { bank-percent: 20, hits: 3 } }`

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

### BOOK_RATE_MODIFIER

Arm a one-shot `percent`-point bonus on each target's next enchant-book roll at `site`: `generate` raises the success rate of the book a black scroll mints, `apply` raises the chance a book applies to gear. The charge is consumed by the next roll at that site whatever it returns — a failed apply spends it — and it survives a relog, since the roll it is waiting for may be days away. Both sites cap at the server's global books.max-success ceiling. Guard a second arm with %bookrate.generate% / %bookrate.apply%.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ BOOK_RATE_MODIFIER: { site: <enum{generate|apply}>, percent: <int[1..100]>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `site` `enum{generate|apply}` — which roll the charge waits for: generate (a scroll minting a book) or apply
- _param_ `percent` `int[1..100]` — percentage points added to that roll's success chance
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ BOOK_RATE_MODIFIER: { site: generate, percent: 5, who: "@Self" } }`

### BREAK_BLOCK

Break the target block(s) (default @Here; drops=false clears). @Vein/@Tunnel/@Trench/@Bore for shapes. void-materials is the per-block exception to `drops`: the listed types are destroyed dropless while everything else in the same volume still yields, which is how a bulk excavator keeps the ore and voids the stone. `smelt` is the volume's drop TRANSFORM — the excavation twin of the MINE-scoped SMELT read-back, which only ever addresses the one block a MINE event names: a smeltable block yields that many of its smelted product instead of its raw drop. Being a number rather than a flag, it takes a fact expression, so a co-enchant rule ('only alongside Fuse') is one authored product and needs no second ability.

- _affinity_: `REGION`
- _usage_: `{ BREAK_BLOCK: { drops: <bool=true>, void-materials: <material list=>, smelt: <int[0..64]=0>, smelt-materials: <material list=> } }`
- _param_ `drops` `bool`
- _param_ `void-materials` `material list` — these block types break WITHOUT drops even when drops is true (empty = none)
- _param_ `smelt` `int[0..64]` — smelted products per smeltable block in the volume; 0 = no transform
- _param_ `smelt-materials` `material list` — restrict the smelt transform to these block types (empty = every type that smelts)
- _target_ `at`: selector `HERE`
- _example_: `{ BREAK_BLOCK: { drops: true } }`

### CAGE

Trap the target AND the activator in a temporary cage: floor/roof plates, a walls ring, an air width × height × depth interior, base-centred rise blocks above the midpoint between the two, reverting after ticks. The full volume is safety-checked (every cell must be air) before anything is placed; both parties teleport to opposite interior cells facing each other. Gate the ability on a target existing (e.g. %nearbyenemies% >= 1) so a no-target use fails BEFORE the cooldown arms.

- _affinity_: `REGION`
- _usage_: `{ CAGE: { floor: <material>, walls: <material>, roof: <material>, width: <int[1..8]=3>, height: <int[2..8]=4>, depth: <int[1..8]=3>, rise: <int[0..8]=2>, ticks: <ticks[0..]=150>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `floor` `material`
- _param_ `walls` `material`
- _param_ `roof` `material`
- _param_ `width` `int[1..8]`
- _param_ `height` `int[2..8]`
- _param_ `depth` `int[1..8]`
- _param_ `rise` `int[0..8]`
- _param_ `ticks` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ CAGE: { floor: STONE_BRICKS, walls: IRON_BARS, roof: STONE_BRICKS, ticks: 150, who: "@NearestPlayer{r=10}" } }`

### CANCEL

Cancel the Bukkit event that triggered this activation.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ CANCEL: {} }`
- _example_: `{ CANCEL: {} }`

### CONVERT_SUMMON

Convert EVERY mob within `radius` blocks of the wearer to the wearer's side, permanently: each rebinds its ownership (a hit on it now fires the wearer's GUARDIAN_HURT) and tamed mobs re-tame; an enemy summon turns on its former owner, a wild mob on the wearer's nearest enemy player, and bat swarm clouds permanently swarm their former owner. Only players, armour stands and the wearer are exempt.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ CONVERT_SUMMON: { radius: <double[1..32]=12>, whiff-sound: <sound=BLOCK_ANVIL_LAND>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `radius` `double[1..32]`
- _param_ `whiff-sound` `sound` — played (low-pitched) when the ring converts nothing — the ring sound alone reads as success
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ CONVERT_SUMMON: { radius: 12 } }`

### CURE

Clear active potion effects of one category from the target(s): ALL (default), HARMFUL, BENEFICIAL, or NEUTRAL. category HARMFUL strips only debuffs (positive effects untouched). count bounds how many matching effects are removed (0 = all of them) in the server's own enumeration order — a count: 1 HARMFUL cure strips exactly one debuff, whichever the server lists first.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ CURE: { category: <enum{ALL|HARMFUL|BENEFICIAL|NEUTRAL}=ALL>, count: <int[0..]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `category` `enum{ALL|HARMFUL|BENEFICIAL|NEUTRAL}`
- _param_ `count` `int[0..]` — remove at most this many effects; 0 = every match
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ CURE: { category: HARMFUL } }`

### DAMAGE

Deal extra damage to the target: a flat amount and/or percent-of-max of the target's own maximum health (they sum when both are given).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE: { amount: <double[0..]=0>, percent-of-max: <double[0..100]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `double[0..]`
- _param_ `percent-of-max` `double[0..100]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ DAMAGE: { amount: 6, percent-of-max: 10 } }`

### DAMAGE_CAP

Cap the wearer's next incoming hit at factor times the last damage they took, for a duration in ticks; with reflect, the overflow above the cap is dealt back to the attacker (Diminish). Self-only; no cap is armed until at least one hit has been taken. feedback is an optional line sent when the cap arms, with {damage} filled in with the cap value.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE_CAP: { factor: <double[0..]=0.5>, reflect: <bool=false>, duration: <ticks[0..]=100>, feedback: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `factor` `double[0..]`
- _param_ `reflect` `bool`
- _param_ `duration` `ticks[0..]`
- _param_ `feedback` `string` — line sent on arming; {damage} = the cap value just committed
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ DAMAGE_CAP: { factor: 0.5, reflect: true, duration: 100 } }`

### DAMAGE_MOD

Contribute to the damage fold: side attack/defense, mode add (percent) or flat (raw amount). A NEGATIVE amount is a self-nerf — attack:add:-50 halves your own outgoing damage. Replaces ADD_DAMAGE/REDUCE_DAMAGE/FLAT_DAMAGE/FLAT_REDUCE.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE_MOD: { side: <enum{attack|defense}>, mode: <enum{add|flat}=add>, amount: <double> } }`
- _param_ `side` `enum{attack|defense}`
- _param_ `mode` `enum{add|flat}`
- _param_ `amount` `double`
- _example_: `{ DAMAGE_MOD: { side: attack, mode: add, amount: 25 } }`

### DAMAGE_SCALE

Contribute per resolved target in 'who' to the damage fold: total = per * count, clamped to cap (0 = uncapped). side attack/defense, mode add (percent, e.g. 10 = +10% each) or flat (raw). The count is the selector's resolved set, e.g. who: @AllPlayers{r=7}.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE_SCALE: { side: <enum{attack|defense}=attack>, mode: <enum{add|flat}=add>, per: <double>, cap: <double[0..]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `side` `enum{attack|defense}`
- _param_ `mode` `enum{add|flat}`
- _param_ `per` `double`
- _param_ `cap` `double[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `AOE`
- _example_: `{ DAMAGE_SCALE: { side: attack, mode: add, per: 10, cap: 100, who: "@AllPlayers{r=7}" } }`

### DELAYED_STRIKE_FIELD

Mark `points` ground spots around each target, at an independent per-axis offset of offset-min..offset-max blocks (a spot over lower ground snaps down onto it, but never rises above the origin), play the `cue-` telegraph at each one and shout `warning` ({caster}) at everyone the `filter` admits within target-range. `delay` ticks later every spot detonates together: a damage-free lightning visual (unless lightning: false), the `strike-` cue, and `damage` raw half-hearts subtracted from every body within hit-radius of it — floored at health-floor, so the field cannot kill, and the filter is RE-CHECKED then, so walking into a spot during the delay gets you hit. Spots are independent: overlapping ones each land their own hit.

- _affinity_: `REGION`
- _usage_: `{ DELAYED_STRIKE_FIELD: { points: <int[1..64]=16>, offset-min: <int[0..64]=2>, offset-max: <int[0..64]=9>, delay: <ticks[1..]=20>, hit-radius: <double[0..]=1.4142135623730951>, target-range: <double[0..]=32>, filter: <enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}=ENEMIES>, damage: <double[0..]=16>, health-floor: <double[0..]=1>, warning: <string=>, cue-sound: <sound>, cue-volume: <double[0..]=1>, cue-pitch: <double[0..]=1>, cue-particle: <particle>, cue-particle-count: <int[0..]=1>, strike-sound: <sound>, strike-volume: <double[0..]=1>, strike-pitch: <double[0..]=1>, strike-particle: <particle>, strike-particle-count: <int[0..]=1>, lightning: <bool=true>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `points` `int[1..64]` — how many ground spots are marked
- _param_ `offset-min` `int[0..64]` — closest a spot lands, per axis
- _param_ `offset-max` `int[0..64]` — furthest a spot lands, per axis
- _param_ `delay` `ticks[1..]` — ticks between the telegraph and the strike
- _param_ `hit-radius` `double[0..]` — how far from a spot the strike reaches
- _param_ `target-range` `double[0..]` — how far the warning carries from the origin
- _param_ `filter` `enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}` — who the warning and the strike admit; re-checked at the strike, not carried from the warning
- _param_ `damage` `double[0..]` — raw half-hearts subtracted from a struck body's health
- _param_ `health-floor` `double[0..]` — health a strike can never take a body below — the reason the field cannot kill
- _param_ `warning` `string` — line shouted at everyone in range ({caster}); empty = no warning
- _param_ `cue-sound` `sound` — telegraph cue at each spot; omit for silence
- _param_ `cue-volume` `double[0..]`
- _param_ `cue-pitch` `double[0..]`
- _param_ `cue-particle` `particle` — telegraph burst at each spot; omit for none
- _param_ `cue-particle-count` `int[0..]`
- _param_ `strike-sound` `sound` — detonation cue at each spot; omit for silence
- _param_ `strike-volume` `double[0..]`
- _param_ `strike-pitch` `double[0..]`
- _param_ `strike-particle` `particle` — detonation burst at each spot; omit for none
- _param_ `strike-particle-count` `int[0..]`
- _param_ `lightning` `bool` — strike each spot with a damage-free lightning visual
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ DELAYED_STRIKE_FIELD: { points: 16, offset-min: 2, offset-max: 9, delay: 20, damage: 16, health-floor: 1, filter: ENEMIES, target-range: 32, cue-sound: ENTITY_WITHER_SPAWN, cue-pitch: 0.4, cue-particle: SPELL_WITCH, cue-particle-count: 32, strike-sound: ENTITY_WITHER_DEATH, strike-pitch: 0.4, strike-particle: EXPLOSION_LARGE, strike-particle-count: 4, who: "@Self" } }`

### DESPAWN

Silently remove the target mob(s) — no drops, no experience, no death event, so nothing downstream (kill counters, other plugins' death hooks) sees a kill. Players are never removed. Pair with @Aoe{filter=MOBS} for an area mob-clear; use KILL when the drops and the death are the point.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ DESPAWN: { each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ DESPAWN: { who: "@Aoe{r=8, filter=MOBS}" } }`

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
- _usage_: `{ DISARM: { each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ DISARM: {} }`

### DISARM_SHUFFLE

Arm an unhanding window on the wearer for `duration` ticks: their next landed melee hit on a player knocks the victim's held item into a random other hotbar slot (the victim can re-select it — shuffled, not locked; weapon-gated combos like Rage break naturally) and that hit deals `damage-malus`% less damage. One shot; a dodged/negated hit keeps the window armed.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DISARM_SHUFFLE: { duration: <ticks[0..]=80>, damage-malus: <double[0..100]=20>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `damage-malus` `double[0..100]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ DISARM_SHUFFLE: { duration: 80, damage-malus: 20 } }`

### DOT_AMPLIFY_MARK

Mark the target so their incoming wither and/or poison damage is multiplied by factor for duration ticks. Amplifies EVERY source of those causes, not just the marker's own. Re-marking refreshes the window outright, weaker factor included — a re-infection is a fresh infection. Player targets only.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DOT_AMPLIFY_MARK: { causes: <enum{wither|poison|dot}=dot>, factor: <double[1..]=2>, duration: <ticks[0..]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `causes` `enum{wither|poison|dot}` — which damage-over-time causes are amplified; dot = both
- _param_ `factor` `double[1..]`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ DOT_AMPLIFY_MARK: { causes: dot, factor: 3, duration: 60, who: "@Victim" } }`

### DROP_ITEM

Drop a material as an item at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `{ DROP_ITEM: { material: <material>, count: <int[1..]=1> } }`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _example_: `{ DROP_ITEM: { material: DIAMOND, count: 1 } }`

### DURABILITY

Modify durability of the player's held item and/or worn armor: restore (amount<0 = full) or damage, flat by amount or proportionally by percent of each item's max durability (percent-restore/percent-damage). select addresses ONE worn piece — a named slot, the most/least damaged, or a random one — instead of the whole set; skip-undamaged leaves pieces at full durability alone and out of that pick. Replaces ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ DURABILITY: { amount: <int=-1>, target: <enum{item|armor|all}=item>, mode: <enum{restore|damage|percent-restore|percent-damage}=restore>, percent: <double[0..]=0>, select: <enum{whole-set|slot:helmet|slot:chestplate|slot:leggings|slot:boots|most-damaged|least-damaged|random-piece}=whole-set>, skip-undamaged: <bool=false>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `int` — durability points; negative fully restores (restore mode)
- _param_ `target` `enum{item|armor|all}`
- _param_ `mode` `enum{restore|damage|percent-restore|percent-damage}`
- _param_ `percent` `double[0..]` — percent-* modes only: how much of each item's MAX durability to move
- _param_ `select` `enum{whole-set|slot:helmet|slot:chestplate|slot:leggings|slot:boots|most-damaged|least-damaged|random-piece}` — which worn piece target: armor addresses
- _param_ `skip-undamaged` `bool` — leave pieces already at full durability untouched
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
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
- _usage_: `{ EQUIP_SWAP: { slot: <enum{helmet|chestplate|leggings|boots}=helmet>, material: <material>, duration: <ticks[0..]=60>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `slot` `enum{helmet|chestplate|leggings|boots}`
- _param_ `material` `material`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ EQUIP_SWAP: { slot: helmet, material: CARVED_PUMPKIN, duration: 60, who: "@Victim" } }`

### EXPLODE

Create an explosion at the target.

- _affinity_: `REGION`
- _usage_: `{ EXPLODE: { power: <double[0..]>, breakBlocks: <bool=false>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `power` `double[0..]`
- _param_ `breakBlocks` `bool`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ EXPLODE: { power: 4, breakBlocks: false } }`

### EXP_MULTIPLY

Multiply the XP gained by a factor, on EXP_GAIN and on MINE. EXP_GAIN scales the amount already granted and ROUNDS to the nearest whole XP; MINE scales the broken block's own yield and TRUNCATES, because a block yields whole orbs.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ EXP_MULTIPLY: { factor: <double[0..]=2.0> } }`
- _param_ `factor` `double[0..]`
- _example_: `{ EXP_MULTIPLY: { factor: 2 } }`

### EXTINGUISH

Put out the target's fire.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ EXTINGUISH: { each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ EXTINGUISH: {} }`

### FACING_SET

Turn each target to face toward (or away from) the anchor, without moving them. Pitch is set too, so an anchor above or below is genuinely looked at. A target sharing the anchor's exact column keeps its current look — there is no direction to turn to — and an activation whose anchor does not resolve turns nobody.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FACING_SET: { mode: <enum{toward|away}=toward>, anchor: <enum{activator|attacker|victim}=activator>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `mode` `enum{toward|away}` — whether the target ends up looking at the anchor or directly away from it
- _param_ `anchor` `enum{activator|attacker|victim}` — which combat party the direction is measured from
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ FACING_SET: { mode: away, anchor: activator, who: "@AOE{radius: 8, filter: ENEMIES}" } }`

### FALLING_BLOCK

Spawn a (2*radius+1)² grid of falling blocks `height` blocks above each target (removed after `ttl` if they never land). A landing block fires the actor's IMPACT abilities on what it hit; `carry` is forwarded to that impact as %damage% (set carry: "%damage%"). The block-field profile turns the grid into a storm and is entirely opt-in: layers-min/max stack that many grids, each layer index rising by its own draw from layer-step-min..max (so layer 0 is always `height`, and a layer above the world simply does not rain); density below 100 rains only that percent of positions, drawn fresh per position per layer, so a re-cast field never falls in the same holes; material2/3/4 give the storm a palette, drawn per block. damage-percent adds that percent of the target's max health — capped at health-cap — to `carry`, so one field hurts a 20-heart player and a boss proportionally. rehit-max/rehit-window cap how many impacts ONE victim can take in a fixed window shared across every wearer raining on them (the field's lethality ceiling), and kill-material names a block that kills a falling block mid-flight, so standing in it is real counterplay.

- _affinity_: `REGION`
- _usage_: `{ FALLING_BLOCK: { material: <material>, material2: <material>, material3: <material>, material4: <material>, radius: <int[0..4]=1>, height: <int[0..64]=4>, ttl: <ticks[0..]=40>, carry: <double=0>, layers-min: <int[1..8]=1>, layers-max: <int[1..8]=1>, layer-step-min: <int[0..24]=0>, layer-step-max: <int[0..24]=0>, density: <double[0..100]=100>, damage-percent: <double[0..]=0>, health-cap: <double[0..]=0>, rehit-max: <int[0..]=0>, rehit-window: <ticks[0..]=200>, kill-material: <material>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `material` `material`
- _param_ `material2` `material`
- _param_ `material3` `material`
- _param_ `material4` `material`
- _param_ `radius` `int[0..4]`
- _param_ `height` `int[0..64]`
- _param_ `ttl` `ticks[0..]`
- _param_ `carry` `double`
- _param_ `layers-min` `int[1..8]` — fewest grids stacked above the target
- _param_ `layers-max` `int[1..8]` — most grids stacked above the target
- _param_ `layer-step-min` `int[0..24]` — fewest blocks one layer rises per layer index
- _param_ `layer-step-max` `int[0..24]` — most blocks one layer rises per layer index
- _param_ `density` `double[0..100]` — percent of grid positions that actually rain, drawn per position per layer
- _param_ `damage-percent` `double[0..]` — percent of the target's (capped) max health added to carry — a victim-scaled impact
- _param_ `health-cap` `double[0..]` — ceiling on the max health damage-percent reads; 0 = uncapped
- _param_ `rehit-max` `int[0..]` — most impacts one victim can take per rehit-window, shared across every wearer; 0 = uncapped
- _param_ `rehit-window` `ticks[0..]` — length of that fixed bucket, anchored at the first impact
- _param_ `kill-material` `material` — a block falling through this material dies without ever landing — the field's counterplay
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ FALLING_BLOCK: { material: END_STONE, material2: NETHERRACK, radius: 4, height: 10, layers-min: 3, layers-max: 4, layer-step-min: 12, layer-step-max: 19, density: 50, damage-percent: 15, health-cap: 44, rehit-max: 4, rehit-window: 200, kill-material: COBWEB, ttl: 100, who: "@Aoe{r=25, filter=ENEMIES}" } }`

### FALL_SHIELD

Arm a ONE-SHOT cancel of each target's next fall damage within `window` ticks. The target need not carry any enchant — this is how a proc that displaces someone pays for their landing. Re-arming refreshes the window; it never banks a second shield.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ FALL_SHIELD: { window: <ticks[1..]=200>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `window` `ticks[1..]` — how long the unspent shield waits for a fall
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ FALL_SHIELD: { window: 200, who: "@AOE{radius: 8, filter: ENEMIES}" } }`

### FILL_OXYGEN

Refill the target's air supply. amount adds that many air ticks instead, clamped to the target's maximum air (0, the default, refills the bar outright).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FILL_OXYGEN: { amount: <ticks[0..]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `ticks[0..]` — air ticks to add; 0 refills the bar outright
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ FILL_OXYGEN: {} }`

### FIREWORK

Spawn a cosmetic firework at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `{ FIREWORK: { power: <int[0..3]=1> } }`
- _param_ `power` `int[0..3]`
- _example_: `{ FIREWORK: { power: 1 } }`

### FLY

Grant the player temporary flight. speed overrides their fly speed for the window and is restored with it (0, the default, leaves the server's own fly speed alone).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FLY: { ticks: <ticks[0..]=200>, speed: <double[0..1]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `ticks` `ticks[0..]`
- _param_ `speed` `double[0..1]` — fly speed while the window holds; 0 keeps the server's
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ FLY: { ticks: 200 } }`

### FLY_MODE

Grant flight to the target(s) while NOT in combat, revoke it while in combat (survival/adventure only). Author on trigger [REPEATING, PASSIVE] with a repeat period so it re-checks and tears down on unequip.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FLY_MODE: { each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ FLY_MODE: { who: "@Self" } }`

### FREEZE

Fully freeze the target for a span of ticks (vanilla powder-snow visual: blue hearts + full vignette, held even while the victim burns), dealing dot damage every dot-period ticks (attributed to the activator; raw pre-armor half-hearts) and slowing them by slow percent. Re-procs refresh the window instead of stacking. neutralize-frost-slow cancels vanilla's own ~50% fully-frozen slow so the authored percent is the real one. breakout-chance rolls once per DoT pulse: on a hit the root shatters there and then, so a long freeze becomes a struggle the victim can win early instead of a fixed sentence. no-jump additionally pins the victim to the ground: a frozen player cannot jump out of the root. It is off by default because it re-tunes the feel of every freeze it is added to, and it is MODERN-ONLY — the 1.8.9 lane has no cancellable jump event, so a freeze there keeps its DoT and slow and the victim can still hop (the recorded era degrade, as with the powder-snow visual).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FREEZE: { duration: <ticks[0..]=60>, dot: <double[0..]=2>, dot-period: <ticks[0..]=20>, slow: <double[0..100]=5>, neutralize-frost-slow: <bool=true>, breakout-chance: <double[0..100]=0>, no-jump: <bool=false>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `dot` `double[0..]`
- _param_ `dot-period` `ticks[0..]`
- _param_ `slow` `double[0..100]`
- _param_ `neutralize-frost-slow` `bool`
- _param_ `breakout-chance` `double[0..100]` — percent chance per DoT pulse that the victim shatters the root early
- _param_ `no-jump` `bool` — also stop the victim jumping for the window (modern lane only; inert on 1.8.9)
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ FREEZE: { duration: 100, dot: 2, dot-period: 20, slow: 5 } }`

### GIVE_ITEM

Give a material to the player target(s); overflow drops at their feet.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ GIVE_ITEM: { material: <material>, count: <int[1..]=1>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
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

Summon count guardian mobs of type at the activation location, each targeting the attacker, auto-removed after ttl ticks (default 200; 0 = permanent); optional custom name. health sets each guard's starting and maximum health, speed multiplies its vanilla movement speed, and effects is a comma-separated potion loadout held for the guard's whole life, each entry optionally levelled with NAME*LEVEL (SPEED*3). A targeted SPAWN_ENTITY for retaliation — author on DEFENSE.

- _affinity_: `REGION`
- _usage_: `{ GUARD: { type: <entity_type>, count: <int[1..]=1>, ttl: <ticks[0..]=200>, name: <string=>, health: <double[0..]=0>, speed: <double[0..]=0>, effects: <potion_effect list=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `name` `string` — custom name shown above each guard; {OWNER} fills in the summoner
- _param_ `health` `double[0..]` — starting (and maximum) health; 0 keeps the vanilla one
- _param_ `speed` `double[0..]` — movement-speed multiplier; 0 keeps the vanilla one
- _param_ `effects` `potion_effect list` — potion effects held for the guard's whole life
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `ATTACKER`
- _example_: `{ GUARD: { type: IRON_GOLEM, count: 1, ttl: 200, name: "&bGuardian" } }`

### HEAD_TROPHY

Arm a head trophy on the target: on their next death from ANY cause a player head owned by them joins the drops, named and lored from these templates, and the mark clears. Tokens resolve at the death: {VICTIM}, {KILLER}, {MONTH}, {DAY}, {YEAR}, {X}, {Y}, {Z}, {ITEM} (the killer's held item, else Fists). Lore lines are separated by '|'. A killer-less death drops the bare head with no lore, since every lore token would be empty. Player targets only.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ HEAD_TROPHY: { name: <string=>, lore: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `name` `string` — display-name template for the dropped skull
- _param_ `lore` `string` — lore template; '|' separates lines
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ HEAD_TROPHY: { name: "&fSkull of {VICTIM}", lore: "&7Defeated by &f{KILLER}|&f{MONTH} {DAY}, {YEAR}" } }`

### HEALTH

Bonus maximum health. On PASSIVE/HELD @Self it is a maintained worn bonus (reconciled, additive across sources, removed on unequip); on event triggers it is a permanent base shift.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ HEALTH: { amount: <double[0..]>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `double[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ HEALTH: { amount: 4 } }`

### HIT_TEMPO

Arm a hit-tempo window on the wearer for `duration` ticks: their melee victims' damage-immunity window is halved for the wearer's hits only (model MENTAL = the 1.8-combat half-window gate; VANILLA = the 1.9+ full-window cadence), each such hit deals `damage-percent`% of its normal damage, and on 1.9+ the wearer gains `attack-speed` (+1.0 = doubled) swing speed for the window. Third-party attackers are unaffected — their hits keep natural immunity.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ HIT_TEMPO: { duration: <ticks[0..]=100>, model: <enum{VANILLA|MENTAL}=VANILLA>, damage-percent: <double[0..100]=33.3>, attack-speed: <double[0..]=1.0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `model` `enum{VANILLA|MENTAL}`
- _param_ `damage-percent` `double[0..100]`
- _param_ `attack-speed` `double[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ HIT_TEMPO: { duration: 100, model: MENTAL, damage-percent: 33.3, attack-speed: 1.0 } }`

### IGNITE

Set the target(s) on fire for a duration in ticks.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ IGNITE: { duration: <ticks[0..]>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
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
- _usage_: `{ IMMUNE: { type: <enum{sword|axe|projectile|potion|all|fishhook}>, duration: <ticks[0..]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `type` `enum{sword|axe|projectile|potion|all|fishhook}`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ IMMUNE: { type: potion, duration: 100 } }`

### INVENTORY_CONVERT

Replace up to `limit` of the activator's `from` items with `to`, walking the whole inventory. With `plain` only meta-less stacks are touched. A stack straddling the remaining limit converts up to the limit and returns the overflow as `from`; anything that no longer fits is dropped at their feet, pickable only by them for `protect-seconds`. The number converted lands in `count-var`, so the zero-converted failure branch and any count-scaled follow-up read it as %converted%.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ INVENTORY_CONVERT: { from: <material>, to: <material>, limit: <int[1..]>, plain: <bool=true>, protect-seconds: <int[0..]=0>, count-var: <string=converted> } }`
- _param_ `from` `material` — the material consumed
- _param_ `to` `material` — the material handed back in its place
- _param_ `limit` `int[1..]` — the most items one activation may convert
- _param_ `plain` `bool` — true = skip any stack carrying meta (a named/enchanted/plugin item is never raw material)
- _param_ `protect-seconds` `int[0..]` — how long items that no longer fit stay owner-locked on the ground (0 = unprotected)
- _param_ `count-var` `string` — per-player variable the converted count is written to, read back as %name%
- _example_: `{ INVENTORY_CONVERT: { from: BUCKET, to: LAVA_BUCKET, limit: 1152, plain: true, protect-seconds: 60, count-var: converted } }`

### INVERT_VAR

Numerically invert a per-player variable (0↔1), preserving its remaining TTL.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ INVERT_VAR: { name: <string>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `name` `string`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ INVERT_VAR: { name: rage, who: "@Self" } }`

### INVINCIBLE

Make the target invulnerable for a span of ticks, then restore.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ INVINCIBLE: { ticks: <ticks[0..]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `ticks` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ INVINCIBLE: { ticks: 100 } }`

### ITEM_XP_TRACK

Credit `amount` experience to the item the activator is holding — the item whose ability fired. At most ONE level per grant: the remainder is banked toward the next, and at the item's cap the bank simply keeps growing. `window` gates the grant to once per that many minutes using a stamp carried BY the item, so the gate follows it through a trade and a freshly minted item earns straight away; a grant inside the window is skipped whole, never scaled. Per-level thresholds and the level cap come from the item's own definition (a pet's `exp-curve` / `max-level`), falling back to the universal `pets.exp-per-level` and `pets.max-level`.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ ITEM_XP_TRACK: { amount: <int[1..]>, window: <int[0..]=0>, message: <string=>, level-up-message: <string=> } }`
- _param_ `amount` `int[1..]` — experience credited to the held item
- _param_ `window` `int[0..]` — MINUTES between grants for this item (0 = ungated); 1440 = once a day
- _param_ `message` `string` — line sent on a grant ({xp}, {exp}, {needed}); empty = silent
- _param_ `level-up-message` `string` — line sent when the grant levels the item ({item} = its name BEFORE the level-up, {level})
- _example_: `{ ITEM_XP_TRACK: { amount: 500, window: 1440, message: "&a&l+ &a{xp} Pet EXP &a&l[&7{exp}/{needed}&a&l]" } }`

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
- _usage_: `{ KEEP_ON_DEATH: { duration: <ticks[0..]=200>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ KEEP_ON_DEATH: { duration: 200 } }`

### KILL

Instantly kill the target.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ KILL: { each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ KILL: {} }`

### KNOCKBACK_CONTROL

Scale the target's incoming knockback for duration ticks: 0 cancels it, 0.5 halves it, 2 doubles it (default: cancel for 2 ticks). Use on DEFENSE for your own knockback, or on ATTACK with who: victim for the knockback you deal.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ KNOCKBACK_CONTROL: { multiplier: <double[0..]=0>, duration: <ticks[0..]=2>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `multiplier` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ KNOCKBACK_CONTROL: { multiplier: 0 } }`

### LIGHTNING

Strike the target(s) with lightning, optionally dealing extra damage (0 = cosmetic).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ LIGHTNING: { damage: <double[0..]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `damage` `double[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
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
- _usage_: `{ MARK: { amount: <double>, duration: <ticks[0..]=60>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `double`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ MARK: { amount: 25, duration: 60, who: "@Victim" } }`

### MARK_ZONE

Lay an actor-owned cylinder of `radius` blocks under each target for `duration` ticks. Read by the %victim.inzone% fact, so a condition-gated bonus can deal more to an enemy inside it.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ MARK_ZONE: { radius: <double[0..]=4>, duration: <ticks[0..]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `radius` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ MARK_ZONE: { radius: 4, duration: 100, who: "@Victim" } }`

### MAX_HEALTH_DRAIN

Temporarily remove `fraction` of the target's overhealth (max health above `baseline`) plus a flat `amount`, restoring it after `duration` ticks. Default target the combat victim.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MAX_HEALTH_DRAIN: { fraction: <double[0..1]=0.5>, baseline: <double[0..]=20>, amount: <double[0..]=0>, duration: <ticks[0..]=60>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `fraction` `double[0..1]`
- _param_ `baseline` `double[0..]`
- _param_ `amount` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ MAX_HEALTH_DRAIN: { fraction: 0.5, baseline: 20, duration: 60, who: "@Victim" } }`

### MESSAGE

Send feedback on a channel: chat (default), actionbar, or title (with subtitle + fade/stay/fade timings). Default recipient self; `who` can name any party (e.g. @Victim). The `{ATTACKER}`/`{VICTIM}` tokens expand to the activating player and the other combat party, `{SELF}` to the name of whoever receives that copy, and `{RELATION_COLOR}` to `ally-color` or `enemy-color` depending on how that recipient stands to the actor — so one broadcast reads correctly to friend and foe. Replaces ACTIONBAR/TITLE.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ MESSAGE: { text: <string>, channel: <enum{chat|actionbar|title}=chat>, subtitle: <string=>, fadeIn: <ticks[0..]=10>, stay: <ticks[0..]=70>, fadeOut: <ticks[0..]=20>, tokens: <expr map=>, ally-color: <string=&a>, enemy-color: <string=&c>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `text` `string`
- _param_ `channel` `enum{chat|actionbar|title}`
- _param_ `subtitle` `string` — title channel only
- _param_ `fadeIn` `ticks[0..]` — title channel only
- _param_ `stay` `ticks[0..]` — title channel only
- _param_ `fadeOut` `ticks[0..]` — title channel only
- _param_ `tokens` `expr map` — name=expression bindings; each {name} in the text becomes the evaluated number
- _param_ `ally-color` `string` — the {RELATION_COLOR} value for a recipient allied to the actor
- _param_ `enemy-color` `string` — the {RELATION_COLOR} value for every other recipient
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ MESSAGE: { text: "&aCritical hit!" } }`

### MODIFY_EXP

Modify a player target's experience: give to them, take from them, or transfer (move at most the target's experience to the activator — never more than they hold). Replaces GIVE_EXP.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_EXP: { amount: <int[0..]>, mode: <enum{give|take|transfer}=give>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `int[0..]`
- _param_ `mode` `enum{give|take|transfer}`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_EXP: { amount: 50, mode: give, who: "@Self" } }`

### MODIFY_FOOD

Modify a player target's hunger. give/take move the bar now (clamped to 20 / to 0). scale-gain multiplies the next food GAIN by factor for duration ticks; absolute instead multiplies the RESULTING food level (a bigger claim, so it wins if both are armed); cancel-drain cancels hunger LOSS for duration ticks. Author the window modes on REPEATING with duration at least the period for an always-on effect while worn — the engine has no unequip teardown, so the window lapses shortly after re-arming stops. Replaces FEED.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_FOOD: { amount: <int[0..]=0>, mode: <enum{give|take|scale-gain|cancel-drain|absolute}=give>, factor: <double[0..]=1>, duration: <ticks[0..]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `int[0..]`
- _param_ `mode` `enum{give|take|scale-gain|cancel-drain|absolute}`
- _param_ `factor` `double[0..]` — scale-gain: what a food-level gain is multiplied by; absolute: what the RESULTING level is
- _param_ `duration` `ticks[0..]` — scale-gain/cancel-drain/absolute: ticks the armed window lasts
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_FOOD: { amount: 6, mode: give, who: "@Self" } }`

### MODIFY_HEALTH

Modify a target's health: give heals them, take deals direct health damage, transfer (lifesteal) damages the target and heals the activator by the same amount, set forces their health to the amount. Replaces HEAL.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_HEALTH: { amount: <double[0..]>, mode: <enum{give|take|transfer|set}=give>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `double[0..]`
- _param_ `mode` `enum{give|take|transfer|set}`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_HEALTH: { amount: 4, mode: give, who: "@Self" } }`

### MODIFY_MONEY

Modify a player target's balance: give to them, take from them, transfer (move at most the target's balance to the activator — never more than they hold), steal_percent (give the activator that PERCENT of the target's balance — amount is a 0..100 percentage), or interest_percent (deposit the TARGET that percent of their OWN balance — minted income, ADR-0052 Fish; one deposit is ceilinged by the live pets.max-percent-money-cap). Replaces GIVE_MONEY/TAKE_MONEY/STEAL_MONEY[_PERCENT].

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_MONEY: { amount: <double[0..]>, mode: <enum{give|take|transfer|steal_percent|interest_percent}=give>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `double[0..]`
- _param_ `mode` `enum{give|take|transfer|steal_percent|interest_percent}`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_MONEY: { amount: 100, mode: give, who: "@Self" } }`

### MOVEMENT_SPEED

Set the player target's walk speed for a span of ticks, then restore the default (0.2). hold keeps it instead of scheduling that restore, for a debuff whose life is a STACK COUNT rather than a clock — the caller then owns handing it back, and the only thing the engine still guarantees is that a logout restores 0.2 rather than persisting an altered speed to disk. ticks is ignored when hold is set.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MOVEMENT_SPEED: { speed: <double[-1..1]>, ticks: <ticks[0..]=200>, hold: <bool=false>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `speed` `double[-1..1]`
- _param_ `ticks` `ticks[0..]`
- _param_ `hold` `bool` — keep the speed with NO timed revert; something else must hand it back
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ MOVEMENT_SPEED: { speed: 0.4, ticks: 200 } }`

### OUTGOING_DEBUFF

Debuff the target's outgoing damage by a percent for a duration in ticks, priced only on their melee hits, their projectile hits, or both (cause). feedback is sent to them on every hit the window actually prices. Non-stacking with itself and with WEAKEN: a re-debuff keeps the stronger window and the later expiry, never the sum. Player targets only.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ OUTGOING_DEBUFF: { percent: <double[0..]>, duration: <ticks[0..]=100>, cause: <enum{all|melee|projectile}=all>, feedback: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `percent` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _param_ `cause` `enum{all|melee|projectile}` — which of the target's own hits the nerf prices
- _param_ `feedback` `string` — line sent to the debuffed player on every hit it prices
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ OUTGOING_DEBUFF: { percent: 50, duration: 80, cause: projectile, feedback: "&2** UNFOCUSED **" } }`

### PARTICLE

Spawn particles at the activation location, or at each entity in `who` when given (centered on the body, not the feet). `block` carries a block material as crack/dust data. `spread` is the horizontal Gaussian offset (set 0 for a point burst); `spread-y` the vertical offset, where the -1 default means "use `spread`". `dy` moves the whole burst up, which is not what widening `spread-y` does. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `{ PARTICLE: { particle: <particle>, count: <int[0..]=1>, block: <material>, spread: <double[0..4]=0.4>, spread-y: <double[-1..4]=-1>, dy: <double[-16..16]=0> } }`
- _param_ `particle` `particle`
- _param_ `count` `int[0..]`
- _param_ `block` `material`
- _param_ `spread` `double[0..4]`
- _param_ `spread-y` `double[-1..4]`
- _param_ `dy` `double[-16..16]` — blocks to raise the anchor before the burst spawns
- _target_ `who`: selector `HERE`
- _example_: `{ PARTICLE: { particle: BLOCK_CRACK, count: 20, block: REDSTONE_BLOCK, who: "@Victim" } }`

### PARTICLE_LINE

Draw a coloured-dust line from each 'who' target's hip to the actor's hip, `density` motes per block, tinted r/g/b (0-255). Pair with who: @AllPlayers{r=N} for a fan of tethers.

- _affinity_: `REGION`
- _usage_: `{ PARTICLE_LINE: { particle: <particle>, r: <int[0..255]=255>, g: <int[0..255]=255>, b: <int[0..255]=255>, size: <double[0..]=1>, density: <double[0..]=2>, height: <double=1>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `particle` `particle`
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _param_ `size` `double[0..]`
- _param_ `density` `double[0..]`
- _param_ `height` `double`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `AOE`
- _example_: `{ PARTICLE_LINE: { particle: REDSTONE, r: 255, g: 255, b: 255, density: 2, who: "@AllPlayers{r=7}" } }`

### PARTICLE_RING

Draw a horizontal ring of `count` coloured-dust motes of radius `radius` at `height` above the target's feet (default @Self), tinted r/g/b (0-255). A radius / aura indicator.

- _affinity_: `REGION`
- _usage_: `{ PARTICLE_RING: { particle: <particle>, r: <int[0..255]=255>, g: <int[0..255]=255>, b: <int[0..255]=255>, size: <double[0..]=1>, radius: <double[0..]=3>, count: <int[1..]=36>, height: <double=1>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `particle` `particle`
- _param_ `r` `int[0..255]`
- _param_ `g` `int[0..255]`
- _param_ `b` `int[0..255]`
- _param_ `size` `double[0..]`
- _param_ `radius` `double[0..]`
- _param_ `count` `int[1..]`
- _param_ `height` `double`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ PARTICLE_RING: { particle: REDSTONE, r: 255, g: 255, b: 255, radius: 7, count: 60 } }`

### PERIODIC_DAMAGE

Burn the target for amount raw half-hearts every period ticks over duration ticks, attributed to the activator (kill credit, era-combat delivery). replace is a comma-separated set of potion effects the burn converts — each named DoT's DAMAGE is cancelled for the whole window while the effect itself is left on the target, icon and particles intact; only WITHER and POISON tick damage, so any other name converts nothing. feedback is sent to a player target on every pulse, and tick-sound / tick-particle play there too (once per pulse, never deduped against the hit's other cues). Two burns on one victim both run: unlike FREEZE, this is not a refreshed window.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ PERIODIC_DAMAGE: { amount: <double[0..]>, period: <ticks[0..]=20>, duration: <ticks[0..]=100>, replace: <potion_effect list=>, feedback: <string=>, tick-sound: <sound>, tick-volume: <double[0..]=1>, tick-pitch: <double[0..]=1>, tick-particle: <particle>, tick-particle-count: <int[0..]=1>, tick-particle-2: <particle>, tick-particle-2-count: <int[0..]=1>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `double[0..]` — raw pre-armor half-hearts per pulse (never attack-scaled)
- _param_ `period` `ticks[0..]`
- _param_ `duration` `ticks[0..]`
- _param_ `replace` `potion_effect list` — vanilla DoTs this burn converts: their damage ticks are cancelled, the effect stays visible
- _param_ `feedback` `string` — line sent to a player target on every pulse
- _param_ `tick-sound` `sound` — cue played at the target on every pulse; omit for silence
- _param_ `tick-volume` `double[0..]`
- _param_ `tick-pitch` `double[0..]`
- _param_ `tick-particle` `particle` — burst spawned on the target every pulse; omit for none
- _param_ `tick-particle-count` `int[0..]`
- _param_ `tick-particle-2` `particle` — a SECOND burst on the same pulse, for a cue built from two particle types
- _param_ `tick-particle-2-count` `int[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ PERIODIC_DAMAGE: { amount: 6, period: 20, duration: 120, replace: WITHER, tick-particle: FLAME, tick-particle-count: 20 } }`

### PHANTOM_BLOCKS

Show every nearby player a client-only overlay across the qualifying surface of the (2*radius+1)^2 patch under each target for `duration` ticks: material-ally to the actor and anyone allied to them, material-enemy to everyone else. A column qualifies when its first solid block down from the target's own level is a full opaque cube with a passable cell above it — see-through floors, roofed columns and anything more than a few steps below are skipped. NOTHING is written to the world: the patch blocks no movement, breaks nothing and survives no reload, and the window's close re-sends the ground as it really is then (so a block mined meanwhile is not stranded). A viewer who relogs is served the true chunk by the server. The patch IS the actor's owned ground for the window, so %actor.ownedground% and a STACKING_DOT laid over it both see who is standing in it.

- _affinity_: `REGION`
- _usage_: `{ PHANTOM_BLOCKS: { radius: <int[0..8]=3>, material-ally: <material=GLOWSTONE>, material-enemy: <material=END_STONE>, duration: <ticks[1..]=200>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `radius` `int[0..8]` — blocks each way from the target the overlay covers
- _param_ `material-ally` `material` — what the actor and their allies are shown
- _param_ `material-enemy` `material` — what everyone else is shown
- _param_ `duration` `ticks[1..]` — ticks before the real ground is sent back
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ PHANTOM_BLOCKS: { radius: 4, material-ally: GLOWSTONE, material-enemy: END_STONE, duration: 100, who: "@Self" } }`

### POTION

Apply a potion effect to the target(s) at the given LEVEL (1-based: level 1 = the I tier), for a duration in ticks. The effect name is resolved to a handle at compile time. On a HELD/PASSIVE source it is removed again when the item is unequipped (§B lifecycle).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ POTION: { effect: <potion_effect>, level: <int[1..]>, duration: <ticks[0..]>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `effect` `potion_effect`
- _param_ `level` `int[1..]`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ POTION: { effect: STRENGTH, level: 1, duration: 100 } }`

### POTION_AMP_REDUCE

Reduce the LEVEL of a potion effect the target already has by `amount` for `duration` ticks, then restore it. Re-applications during the window are held to the same reduced ceiling, and a reduction that leaves nothing denies the effect for the window. A target without the effect is untouched. Unlike POTION_LOCK this takes only part of the buff, so a Health Boost VI sapped by 2 keeps four of its six tiers.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ POTION_AMP_REDUCE: { effect: <potion_effect>, amount: <int[1..]=1>, duration: <ticks[0..]=60>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `effect` `potion_effect`
- _param_ `amount` `int[1..]` — levels to sap; at or above the source the effect is denied
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ POTION_AMP_REDUCE: { effect: HEALTH_BOOST, amount: 2, duration: 48, who: "@Victim" } }`

### POTION_LOCK

Strip a potion effect from the target(s) and continuously deny it for `ticks` — any re-application during the window is refused, so it cannot be maintained by a passive buff. Default target self.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ POTION_LOCK: { effect: <potion_effect>, ticks: <ticks[0..]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `effect` `potion_effect`
- _param_ `ticks` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ POTION_LOCK: { effect: SPEED, ticks: 100, who: "@Victim" } }`

### PROC_REBOUND

While worn, give incoming enchant activations a chance to be taken off you and re-run with the roles swapped — the attacker eats their own proc, and it is NOT applied to you for that hit. Gated by the attacking enchant's tier WEIGHT — the number its rung carries in tiers.yml, not a rung index — which must fall in tier-min..tier-max, and by level: this enchant's level must be at least the incoming one's (a levelless source — set, crystal, mask, pet — has no level to compare and answers its whole band). Several worn grades compose — the one whose band reaches the incoming weight with the highest tier-min wins. A maintained PASSIVE marker, armed on equip and lifted on unequip. Player-only.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ PROC_REBOUND: { chance: <double[0..100]>, tier-max: <int[0..]>, tier-min: <int[0..]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `chance` `double[0..100]`
- _param_ `tier-max` `int[0..]`
- _param_ `tier-min` `int[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ PROC_REBOUND: { chance: 4, tier-min: 10, tier-max: 70, who: "@Self" } }`

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

### PROJECTILE_DRESSING

Ride an entity of type on the projectile this BOW_FIRE activation is loosing — the rider is removed the moment the arrow lands, dies or unloads, and unconditionally after ttl ticks. invulnerable spares it from damage for that many ticks so its own flight cannot kill it; no-pickup stops it hoovering up items in mid-air. One rider per shot: a second PROJECTILE_DRESSING on the same shot replaces the first. fire-ticks lights the ARROW itself, which nothing else can reach — IGNITE takes its targets from a selector and no selector names a shot in flight — and needs no rider, so a flaming arrow is this effect with type omitted. Inert outside a bow shot.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ PROJECTILE_DRESSING: { type: <entity_type>, ttl: <ticks[0..]=200>, invulnerable: <ticks[0..]=200>, no-pickup: <bool=true>, fire-ticks: <ticks[0..]=0> } }`
- _param_ `type` `entity_type` — rider to seat on the shot; omit to dress the arrow only
- _param_ `ttl` `ticks[0..]` — hard cap on the rider's life; the backstop when nothing reports a landing
- _param_ `invulnerable` `ticks[0..]` — how long the rider ignores damage (0 = never)
- _param_ `no-pickup` `bool`
- _param_ `fire-ticks` `ticks[0..]` — set the ARROW alight for this long (0 = as loosed); no rider needed
- _example_: `{ PROJECTILE_DRESSING: { type: COW, ttl: 200, invulnerable: 200 } }`

### PROXIMITY_ANNOUNCE

Fire PROXIMITY_EVENT on every player within radius of each target — never the target themselves — with tag readable as %proximityevent%. The observer's activation carries the target as its victim, so %distance%, %victim.relation% and every %victim.*% read (including %victim.var.<name>%) describes the subject rather than the observer. The tag exists because one trigger carries several unrelated observations: without it an ally-death watcher and an ally-bleeding watcher would each proc on the other's event.

- _affinity_: `REGION`
- _usage_: `{ PROXIMITY_ANNOUNCE: { tag: <string>, radius: <double[1..64]=7>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `tag` `string` — what happened, read by an observer as %proximityevent%
- _param_ `radius` `double[1..64]` — how far the news carries
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ PROXIMITY_ANNOUNCE: { tag: "bleed", radius: 7, who: "@Victim" } }`

### REFLECT

Mark the target so a percent of their own outgoing damage is reflected back onto them for a duration in ticks (Hex). Player targets only; default target the combat victim. cap is a flat per-hit ceiling on the health returned (0 = uncapped); feedback is an optional chat line sent to the afflicted on each reflected hit, with {damage} filled in.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REFLECT: { percent: <double[0..]>, duration: <ticks[0..]=80>, cap: <double[0..]=0>, feedback: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `percent` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _param_ `cap` `double[0..]` — flat per-hit ceiling on the health returned; 0 = uncapped
- _param_ `feedback` `string` — per-hit line to the afflicted; {damage} = health returned
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ REFLECT: { percent: 20, duration: 80, who: "@Victim" } }`

### REFUND_COOLDOWN

Hand back the cooldown this ability's own gate-6 reservation armed, unless `unless` evaluates non-zero. Author it AFTER the payload whose outcome decides the refusal — a condition cannot, because the fact it would read does not exist until the payload has run. It can only ever release THIS ability's window, and only in the tick the gate reserved it, so a WAIT tier between the payload and the refund silently forfeits it.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REFUND_COOLDOWN: { unless: <double=0> } }`
- _param_ `unless` `double` — skip the refund when this evaluates non-zero; the default (0) always refunds
- _example_: `{ REFUND_COOLDOWN: { unless: "%lavapet.filled%" } }`

### REMOVE_ARMOR

Strip one random worn armour piece from the target(s) and drop it.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_ARMOR: { each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ REMOVE_ARMOR: {} }`

### REMOVE_ITEM

Remove up to count of a material from the player target(s)' inventory.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_ITEM: { material: <material>, count: <int[1..]=1>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_ITEM: { material: DIAMOND, count: 1, who: "@Self" } }`

### REMOVE_POTION

Remove a potion effect from the target(s).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_POTION: { effect: <potion_effect>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `effect` `potion_effect`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_POTION: { effect: POISON } }`

### REMOVE_SOULS

Debit souls from a soul gem: @Self (default) charges the activator's active gem, @Victim drains the target's own gem. A no-op when that player is not in soul mode.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REMOVE_SOULS: { amount: <int[1..]>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `amount` `int[1..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_SOULS: { amount: 5 } }`

### RUN_COMMAND

Run a command as the console (default) or as the activating player. The `{PLAYER}`/`{UUID}`/`{WORLD}` tokens expand to the actor's name, uuid, and world, and `{VICTIM}` to the other combat party's name (empty on a victimless activation). Affinity GLOBAL — the console path runs on the global thread; the player path runs on the actor's own thread. `{PLAYER}` and `{VICTIM}` both refuse to run the command when the name they would embed falls outside the standard `[A-Za-z0-9_]` (1-16) username charset.

- _affinity_: `GLOBAL`
- _usage_: `{ RUN_COMMAND: { command: <string>, as: <enum{console|player}=console> } }`
- _param_ `command` `string`
- _param_ `as` `enum{console|player}` — who runs it: console (default) or the player
- _example_: `{ RUN_COMMAND: { command: "f focus {VICTIM}", as: player } }`

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

Set (or with op=increment, add to) a variable on the target, readable in later conditions as %name% on the activator or %victim.var.name% on the victim. ttl ticks, 0 = forever; cap 0 = uncapped. Any living entity can carry one, so a mob holds its own stacks. clear-on-death ends the var when its CARRIER dies: a mob's vars always go, but a player's deliberately survive their death (a mark meant to outlast one, a window somebody else armed), so a counter that should not — a bleed ladder — has to say so.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SET_VAR: { name: <string>, value: <string=>, ttl: <ticks[0..]=0>, op: <enum{set|increment}=set>, step: <int=1>, cap: <int[0..]=0>, clear-on-death: <bool=false>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `name` `string`
- _param_ `value` `string`
- _param_ `ttl` `ticks[0..]`
- _param_ `op` `enum{set|increment}`
- _param_ `step` `int`
- _param_ `cap` `int[0..]`
- _param_ `clear-on-death` `bool` — the carrier's own death ends this var, not just its ttl
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ SET_VAR: { name: bleedstacks, op: increment, step: 1, cap: 20, ttl: 200, who: "@Victim" } }`

### SMELT

Auto-smelt the block broken by this MINE activation (ore→ingot, sand→glass, …).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SMELT: {} }`
- _example_: `{ SMELT: {} }`

### SOUL_COST_EXEMPT

Waive every soul cost charged to each target for `duration`. Both debit paths are covered: a soul-cost ability's gate charge and a REMOVE_SOULS aimed at the holder's own gem. While exempt a soul-cost ability fires even with no gem active, and its escalating price stops advancing — a free activation cannot raise the next one. Each waiver above `feedback-threshold` sends `message` with {souls} filled in, so the small change stays quiet. Re-arming replaces the window rather than extending it.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SOUL_COST_EXEMPT: { duration: <ticks[1..]>, feedback-threshold: <int[0..]=10>, message: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[1..]` — how long the holder's soul costs are waived
- _param_ `feedback-threshold` `int[0..]` — a waiver must EXCEED this many souls to send `message`
- _param_ `message` `string` — line sent on each waiver above the threshold ({souls} = the amount waived); empty = silent
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ SOUL_COST_EXEMPT: { duration: 300, feedback-threshold: 10, message: "&a&lPET (&aTesla&a&l): &a+{souls} souls!", who: "@Self" } }`

### SOUL_MODE_DISABLE

Force the target out of soul mode: pending spends settle to their gems, the pool is dropped and they are told, with the same lines and cues a manual toggle-off sends. A no-op on a target who is not in soul mode. Pair with REMOVE_SOULS to drain the wallet AND flip the switch — draining alone leaves the mode running.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ SOUL_MODE_DISABLE: { each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ SOUL_MODE_DISABLE: { who: "@Victim" } }`

### SOUL_TRANSFER

Move min(target's souls, cap) souls out of the target's gems and credit the actor floor(ratio x stolen) — the remainder is destroyed, not banked. Unlike REMOVE_SOULS this does not require either party to be in soul mode: it reads the gems themselves. overflow=mint gives the actor a fresh gem carrying the credit when they carry none; overflow=discard loses it. A target with no souls is a silent no-op, so the authored condition decides what a dry victim costs.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ SOUL_TRANSFER: { cap: <int[1..]>, ratio: <double[0..1]=1.0>, overflow: <enum{mint|discard}=mint>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `cap` `int[1..]` — the most souls one activation may take
- _param_ `ratio` `double[0..1]` — fraction of the take the actor keeps; the rest is destroyed
- _param_ `overflow` `enum{mint|discard}` — what happens when the actor carries no gem to credit
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ SOUL_TRANSFER: { cap: 50, ratio: 0.5, who: "@Victim" } }`

### SOUND

Play a sound at the activation location, or at each entity in `who` when given — world-audible there at the same volume and pitch. `dy` raises that anchor (an overhead cue is `dy: 4`). No-op if `who` resolves nothing and the activation has no location.

- _affinity_: `REGION`
- _usage_: `{ SOUND: { sound: <sound>, volume: <double[0..]=1>, pitch: <double[0..]=1>, dy: <double[-16..16]=0> } }`
- _param_ `sound` `sound`
- _param_ `volume` `double[0..]`
- _param_ `pitch` `double[0..]`
- _param_ `dy` `double[-16..16]` — blocks to raise the anchor before the cue plays
- _target_ `who`: selector `HERE`
- _example_: `{ SOUND: { sound: ENTITY_GENERIC_EXPLODE, volume: 1, pitch: 1 } }`

### SPAWNER_YIELD

While worn (PASSIVE): every spawner spawn near the wearer rolls `chance`% to add `extra` copies of the same mob at the same spot. `scope: chunk` counts a wearer standing in the spawn's own chunk; `scope: radius` counts one within `radius` blocks of it. The wearer test is asked PER SPAWN against live worn state, so walking away stops it immediately. Grants do NOT stack — two wearers at one spawner get the stronger one's yield, not both. The added copies spawn as CUSTOM, so they never re-trigger this, but they DO count toward the spawner's nearby-entity cap: a wearer fills that cap sooner and the spawner then idles until the area clears.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SPAWNER_YIELD: { chance: <double[0..100]=65>, extra: <int[1..8]=1>, scope: <enum{chunk|radius}=chunk>, radius: <double[0..]=16> } }`
- _param_ `chance` `double[0..100]` — percent of spawns that come out multiplied
- _param_ `extra` `int[1..8]` — copies added on a winning roll
- _param_ `scope` `enum{chunk|radius}` — where the wearer counts as present
- _param_ `radius` `double[0..]` — blocks, for scope: radius; ignored for scope: chunk
- _example_: `{ SPAWNER_YIELD: { chance: 65, extra: 1, scope: chunk } }`

### SPAWN_ENTITY

Spawn count entities of type at the target's (or activation) location; ttl ticks until removal (0 = permanent), optional starting health, and owner=activator to tame an owned summon to the activator. ADR-0052 summon flags: powered charges a creeper; ai=false disables mob AI; targeting=false stops the summon acquiring targets; saddled + mount=activator make a horse-type rideable and seat the activator; detonate=PLAYER_HIT makes a creeper explode ONLY when a player hits it (it never self-detonates); invincible=true zeroes all damage to the summon (it cannot die but still takes hits and knockback); speed is a multiplier on the spawned entity's vanilla movement-speed base (0 = untouched); name is shown above each summon and effects is a comma-separated potion loadout held for its whole life, each entry optionally levelled with NAME*LEVEL (SPEED*3) — the same styling GUARD takes, so the choice between the two is only about targeting. payload-phase attaches the owner's SUMMON_PAYLOAD abilities to a point in the summon's life: detonate REPLACES the vanilla explosion (no terrain damage, no vanilla entity damage), death fires as it dies, and periodic pulses every payload-period ticks. The payload runs once per entity in a payload-radius x payload-height box around the summon (height 0 reuses the radius), filtered by payload-filter and capped nearest-first by payload-max-targets; a payload needs owner=activator, since the owner is who runs it. payload-phase=strike is the odd rung out: it fires when the summon lands a MELEE hit on a player (its projectiles never count) and runs the owner's IMPACT abilities on the player struck, NOT their SUMMON_PAYLOAD ones — so the box params above do not apply and the target is always the one player hit. payload-cancel drops the summon's own melee damage so only the authored IMPACT lands, and payload-consume despawns the summon on that hit, which makes it a one-shot courier: exactly one strike per summon, never a second. Visuals for the strike belong on those IMPACT abilities, where they are fully authored. scatter spreads the summons over a random offset, air-scanned so none spawns inside terrain. fuse shortens (or lengthens) a primed TNT's countdown; it is a separate knob from ttl because ttl DESPAWNS, so any ttl at or under the fuse would remove the charge before it could ever explode. Replaces SPAWN/TNT.

- _affinity_: `REGION`
- _usage_: `{ SPAWN_ENTITY: { type: <entity_type>, count: <int[1..]=1>, ttl: <ticks[0..]=0>, health: <double[0..]=0>, owner: <enum{none|activator}=none>, powered: <bool=false>, ai: <bool=true>, targeting: <bool=true>, saddled: <bool=false>, mount: <enum{none|activator}=none>, detonate: <enum{NONE|PLAYER_HIT}=NONE>, invincible: <bool=false>, speed: <double[0..]=0>, name: <string=>, effects: <potion_effect list=>, payload-phase: <enum{none|detonate|death|periodic|strike}=none>, payload-period: <ticks[0..]=40>, payload-radius: <double[0..]=4>, payload-height: <double[0..]=0>, payload-filter: <enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}=ALL>, payload-max-targets: <int[0..]=0>, payload-consume: <bool=true>, payload-cancel: <bool=true>, scatter: <int[0..8]=0>, fuse: <ticks[0..]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
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
- _param_ `name` `string` — custom name shown above each summon; {OWNER} fills in the summoner
- _param_ `effects` `potion_effect list` — potion effects held for the summon's whole life
- _param_ `payload-phase` `enum{none|detonate|death|periodic|strike}` — when the summon runs its owner's abilities (strike runs IMPACT, the rest SUMMON_PAYLOAD)
- _param_ `payload-period` `ticks[0..]` — ticks between payload pulses (periodic phase only)
- _param_ `payload-radius` `double[0..]` — XZ half-extent of the payload's target box
- _param_ `payload-height` `double[0..]` — Y half-extent; 0 reuses payload-radius
- _param_ `payload-filter` `enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}` — which entities the payload targets; A+B keeps only what both admit
- _param_ `payload-max-targets` `int[0..]` — nearest-first cap on payload targets (0 = all)
- _param_ `payload-consume` `bool` — strike phase only: the hit also despawns the summon
- _param_ `payload-cancel` `bool` — strike phase only: the summon's own melee damage is dropped
- _param_ `scatter` `int[0..8]` — spread each summon over a random ±N XZ offset, air-scanned (0 = the exact point)
- _param_ `fuse` `ticks[0..]` — primed TNT only: ticks until it detonates (0 = vanilla's 80). NOT ttl, which despawns
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ SPAWN_ENTITY: { type: WOLF, count: 1, ttl: 0, health: 0, owner: activator } }`

### SPAWN_SWARM

Summon count entities of type evenly spaced on a radius-block ring around the activator, raised rise blocks (chest height), each facing directly outward, with VANILLA AI, auto-removed after ttl ticks. speed < 1 slows each to that fraction of its vanilla AI speed via a per-tick velocity damp (Bat-style AI ignores the speed attribute). cloud: true makes the summons orbit the 1x2x1 pillar directly in front of whoever attacked the activator most recently within cloud-range blocks (vision cloud); with no such attacker they keep vanilla AI. While clouding, the orbit's own pacing overrides speed. name is shown above each summon and effects is a comma-separated potion loadout held for its whole life, each entry optionally levelled with NAME*LEVEL (SPEED*3) — the same styling GUARD and SPAWN_ENTITY take. owner: activator binds every summon to the summoner, so vanilla AI never turns the ring on the player standing inside it, and {OWNER} in name fills with their name.

- _affinity_: `REGION`
- _usage_: `{ SPAWN_SWARM: { type: <entity_type>, count: <int[1..]=1>, radius: <double[0..]=0.5>, rise: <double[0..]=1.2>, ttl: <ticks[0..]=300>, speed: <double[0..1]=1>, cloud: <bool=false>, cloud-range: <double[1..]=16>, owner: <enum{none|activator}=none>, name: <string=>, effects: <potion_effect list=> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `radius` `double[0..]`
- _param_ `rise` `double[0..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `speed` `double[0..1]`
- _param_ `cloud` `bool`
- _param_ `cloud-range` `double[1..]`
- _param_ `owner` `enum{none|activator}` — activator binds each summon to the summoner: vanilla AI can no longer target them, and {OWNER} fills
- _param_ `name` `string` — custom name shown above each summon; {OWNER} fills in the summoner
- _param_ `effects` `potion_effect list` — potion effects held for each summon's whole life
- _example_: `{ SPAWN_SWARM: { type: BAT, count: 10, radius: 0.5, ttl: 300, speed: 0.5 } }`

### STACKING_DOT

Watch each target for `duration` and, every `period` ticks they spend standing on ground the ACTIVATOR placed with `TEMP_BLOCK`, deal `step` x their live stack count as real (pre-armour) damage credited to the activator. Stacks climb one per damaging pulse to `cap` and lapse `stack-ttl` after the last one, so leaving the field pauses the ramp rather than resetting it. The ladder is PER VICTIM and shared across every attacker — two overlapping fields ramp one ladder, not two. The first pulse waits `lead-in`, which is what lets one activation lay its field and its watcher together.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ STACKING_DOT: { step: <double[0..]=2>, period: <ticks[1..]=10>, cap: <int[1..]=6>, stack-ttl: <ticks[1..]=60>, lead-in: <ticks[1..]=20>, duration: <ticks[1..]=200>, message: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `step` `double[0..]` — damage added per live stack, in raw half-hearts
- _param_ `period` `ticks[1..]` — ticks between pulses
- _param_ `cap` `int[1..]` — the most stacks one victim's ladder can reach
- _param_ `stack-ttl` `ticks[1..]` — how long a ladder survives after its last pulse — the grace for stepping off the ground
- _param_ `lead-in` `ticks[1..]` — delay before the first pulse reads the ground
- _param_ `duration` `ticks[1..]` — how long each target is watched
- _param_ `message` `string` — line sent to the victim on each damaging pulse ({damage}, {stacks}); empty = silent
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ STACKING_DOT: { step: 2, period: 10, cap: 6, stack-ttl: 60, lead-in: 20, duration: 200, message: "&c&l* DECAYING [&7-{damage}HP ({stacks} stacks)&c&l] *", who: "@Aoe" } }`

### STATUS_CLEAR

Remove an active engine status window from each target: TELEBLOCK (the teleport denial), POTION_LOCK (every potion denial held on them), DISARM (the armed disarm window), or FREEZE (a live freeze, with its damage-over-time and both movement modifiers). Unlike CURE this touches no potion EFFECT — it lifts the plugin state that was denying one. Clearing a window nobody holds is a silent no-op, so the authored condition decides what a wasted use costs.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ STATUS_CLEAR: { status: <enum{TELEBLOCK|POTION_LOCK|DISARM|FREEZE}>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `status` `enum{TELEBLOCK|POTION_LOCK|DISARM|FREEZE}` — which engine window to lift
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ STATUS_CLEAR: { status: TELEBLOCK, who: "@Self" } }`

### STRIP_SCROLL

Remove one protection scroll marker from a random protected piece of the target's worn armour (+ held item unless hand: false): scroll HOLY strips a Holy White Scroll, WHITE a White Scroll (its guard flag included). A target with no protected piece is a no-op. Rate-limit with the ability's chance gate (the Anubis per-hit percent).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ STRIP_SCROLL: { scroll: <enum{HOLY|WHITE}=HOLY>, hand: <bool=true>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `scroll` `enum{HOLY|WHITE}`
- _param_ `hand` `bool`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ STRIP_SCROLL: { scroll: HOLY, who: "@Victim" } }`

### SUMMON_PURGE

Despawn every summon within `radius` blocks of the wearer whose owner the `filter` does not spare, leaving the particle / extra-particle burst where each one stood. The filter is a ladder of exemptions: not-own spares only the wearer's summons, not-own-or-ally also spares an ONLINE ally's, and not-own-or-ally-or-offline additionally spares one whose owner has logged off (an abandoned summon is left to its own TTL). Only summons the engine can attribute to a player are touched — a wild mob, and a summon spawned with owner=none, are not summons anyone owns and are left alone. The removal is a DESPAWN: no drops, no experience and no death event, so nothing the owner hung on a death fires. An invincible summon survives, exactly as it survives DESPAWN and KILL. CONVERT_SUMMON is the inverse — it keeps the summons and flips them onto your side.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUMMON_PURGE: { radius: <double[1..32]=15>, filter: <enum{not-own|not-own-or-ally|not-own-or-ally-or-offline}=not-own-or-ally-or-offline>, particle: <particle>, particle-count: <int[0..]=1>, particle-spread: <double[0..]=0>, extra-particle: <particle>, extra-particle-count: <int[0..]=1>, extra-particle-spread: <double[0..]=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `radius` `double[1..32]` — how far the sweep reaches from the wearer
- _param_ `filter` `enum{not-own|not-own-or-ally|not-own-or-ally-or-offline}` — which owners are SPARED, weakest sweep last
- _param_ `particle` `particle` — burst left where each purged summon stood; omit for none
- _param_ `particle-count` `int[0..]`
- _param_ `particle-spread` `double[0..]` — per-axis spread of the burst (0 = a point)
- _param_ `extra-particle` `particle` — second burst layered on the first; omit for none
- _param_ `extra-particle-count` `int[0..]`
- _param_ `extra-particle-spread` `double[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ SUMMON_PURGE: { radius: 15, filter: not-own-or-ally-or-offline, particle: LARGE_SMOKE, particle-count: 10, particle-spread: 0.3, extra-particle: SPELL_WITCH, extra-particle-count: 12, extra-particle-spread: 0.7 } }`

### SUMMON_REBIND

Replace each target summon the activator OWNS with a fresh one of type, rise blocks above it: the old body is removed silently (no death, no drops, no kill credit) and the replacement spawns at full health with a restarted ttl. health, speed, name and effects are GUARD's loadout params. A summon the activator does not own is skipped unless steal is set, which widens the precondition from 'mine' to 'somebody's' — the target must still be a tracked summon, so a farmed wild mob can never be turned into a free top-tier guardian. steal-message is the only place both names exist at once, which is why the broadcast rides the effect instead of a MESSAGE line. CONVERT_SUMMON rebinds ownership in place; this replaces the body.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ SUMMON_REBIND: { type: <entity_type>, ttl: <ticks[0..]=600>, name: <string=>, health: <double[0..]=0>, speed: <double[0..]=0>, effects: <potion_effect list=>, rise: <double[0..8]=2>, steal: <bool=false>, steal-message: <string=>, steal-radius: <double[0..64]=24>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `type` `entity_type`
- _param_ `ttl` `ticks[0..]`
- _param_ `name` `string` — custom name shown above the replacement; {OWNER} fills in the summoner
- _param_ `health` `double[0..]` — starting (and maximum) health; 0 keeps the vanilla one
- _param_ `speed` `double[0..]` — movement-speed multiplier; 0 keeps the vanilla one
- _param_ `effects` `potion_effect list` — potion effects held for the replacement's whole life
- _param_ `rise` `double[0..8]` — blocks above the old body to place the replacement
- _param_ `steal` `bool` — also take summons owned by SOMEONE ELSE (a summon it must still be — never a wild mob)
- _param_ `steal-message` `string` — steal only: broadcast near the replacement; {FROM} is the robbed owner, {OWNER} the thief
- _param_ `steal-radius` `double[0..64]` — how far the steal-message carries
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ SUMMON_REBIND: { type: IRON_GOLEM, ttl: 600, health: 90, name: "&b&l{OWNER}'s Guardian" } }`

### SUPPRESS

Disable a target's enchant/group/type (the key) for a duration in ticks (DISABLE_ENCHANT/GROUP/TYPE), or with scope KIND every ability carrying the keyed effect head (e.g. MODIFY_FOOD). scope TYPE keys the ability's combat direction (DEFENSE / ATTACK) unless it authored a suppress-type of its own, so key: DEFENSE silences everything a victim's gear does back. mode: timed (the duration window) or next-hit (a one-shot that clears after the target's next `charges` incoming hits, Neutralize). The consumed-* params are emitted at the moment the suppression blocks something, not when it is armed, and fill {ATTACKER} with whoever armed it and {VICTIM} with the player it silenced. Default target the combat victim.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUPPRESS: { scope: <enum{ENCHANT|GROUP|TYPE|KIND}>, key: <string>, duration: <ticks[0..]=200>, mode: <enum{timed|next-hit}=timed>, charges: <int[1..]=1>, consumed-message-actor: <string=>, consumed-message-victim: <string=>, consumed-sound: <sound>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `scope` `enum{ENCHANT|GROUP|TYPE|KIND}`
- _param_ `key` `string`
- _param_ `duration` `ticks[0..]`
- _param_ `mode` `enum{timed|next-hit}`
- _param_ `charges` `int[1..]`
- _param_ `consumed-message-actor` `string` — line to whoever armed this, when it blocks
- _param_ `consumed-message-victim` `string` — line to the suppressed player, when it blocks
- _param_ `consumed-sound` `sound` — cue played at the block; omit for silence
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ SUPPRESS: { scope: GROUP, key: lifesteal, duration: 200, who: "@Victim" } }`

### SUPPRESS_IMMUNE

Make the target(s) immune to suppression (DISABLE_ENCHANT/GROUP/TYPE) while worn — a maintained PASSIVE flag, armed on equip and lifted on unequip. An optional chance (default 100) makes it a per-suppression roll instead of absolute. Player-only.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUPPRESS_IMMUNE: { chance: <int[0..100]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `chance` `int[0..100]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ SUPPRESS_IMMUNE: { chance: 4, who: "@Self" } }`

### SUPPRESS_INCOMING

Make each target IMMUNE to abilities aimed at them: for `duration` ticks, an ability whose enchant/group/type (or, with scope KIND, whose effect head) matches `key` is blocked whenever it lands on the holder. Aimed at them directly it is stopped outright; when they are merely one of several bodies a chain or area effect resolved onto, they alone are skipped and the rest still take it. `chance` rolls per incoming target application. The mirror of SUPPRESS, which silences what its target DOES; this silences what is done TO them, including the opening proc a defensive SUPPRESS can never reach. Re-arming extends the window, so a PASSIVE may hold it open. The consumed-* lines fill {ATTACKER} with whoever armed the window — here, the protected holder — and {VICTIM} with the activator it blocked.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUPPRESS_INCOMING: { scope: <enum{ENCHANT|GROUP|TYPE|KIND}>, key: <string>, duration: <ticks[0..]=200>, chance: <int[1..100]=100>, consumed-message-actor: <string=>, consumed-message-victim: <string=>, consumed-sound: <sound>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `scope` `enum{ENCHANT|GROUP|TYPE|KIND}`
- _param_ `key` `string`
- _param_ `duration` `ticks[0..]`
- _param_ `chance` `int[1..100]` — percent rolled per incoming target application; 100 is absolute
- _param_ `consumed-message-actor` `string` — line to the protected holder, when it blocks
- _param_ `consumed-message-victim` `string` — line to the blocked activator, when it blocks
- _param_ `consumed-sound` `sound` — cue played at the block; omit for silence
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ SUPPRESS_INCOMING: { scope: GROUP, key: lifesteal, duration: 100, who: "@Self" } }`

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
- _usage_: `{ TELEBLOCK: { duration: <ticks[0..]=400>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ TELEBLOCK: { duration: 400 } }`

### TELEPORT

Teleport the target to the actor's or the victim's location.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ TELEPORT: { to: <enum{VICTIM|ACTOR}=VICTIM>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `to` `enum{VICTIM|ACTOR}` — destination party: the victim or the actor
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ TELEPORT: { to: VICTIM } }`

### TELEPORT_BEHIND

Teleport the mover(s) `distance` blocks behind the reference (of: VICTIM — the attacker on a DEFENSE trigger — or ACTOR), facing as it faces. Unsafe (blocked / wall between) → onFail ONTOP lands on the reference, NONE cancels.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ TELEPORT_BEHIND: { of: <enum{VICTIM|ACTOR}=VICTIM>, distance: <double[0..]=1>, onFail: <enum{ONTOP|NONE}=ONTOP>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `of` `enum{VICTIM|ACTOR}`
- _param_ `distance` `double[0..]`
- _param_ `onFail` `enum{ONTOP|NONE}`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ TELEPORT_BEHIND: { of: VICTIM, distance: 1, onFail: ONTOP, who: "@Self" } }`

### TELEPORT_DROPS

Send the block's drops straight to the breaker's inventory (this MINE activation).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ TELEPORT_DROPS: {} }`
- _example_: `{ TELEPORT_DROPS: {} }`

### TEMP_BLOCK

Place a temporary block shape that reverts after `ticks`: shape POINT / FOOTPRINT (radius) / COLUMN (height, ahead in the target's facing) / BOX (width × height × depth filled volume horizontally centred on the target — the ADR-0052 Spider webs), at feet level + dy. airOnly only replaces air (safe placement); a non-airOnly FOOTPRINT replaces only the solid ground under the feet (never air, so a trail can't scaffold); other shapes replace anything and restore on revert. A radius-0 FOOTPRINT trails as a snake — consecutive stamps join into a gapless, 4-connected footprint path even at sprint speed and on diagonals. Give material2/3/4 to place a mixed palette: each block independently picks a material from a deterministic per-block hash of its coordinates — a noisy, random-looking scatter (re-placing the same block always picks the same material). A BOX is always single-material (palette[0]). fill-chance below 100 places only that percent of the shape's columns, for a ragged, partial field instead of a solid one; the choice is per column and stable for a given coordinate, so re-stamping the same ground extends the same field rather than filling in its holes. A radius-0 FOOTPRINT trail ignores it — a snake with gaps is not a path.

- _affinity_: `REGION`
- _usage_: `{ TEMP_BLOCK: { shape: <enum{POINT|FOOTPRINT|COLUMN|BOX}=POINT>, material: <material>, material2: <material>, material3: <material>, material4: <material>, ticks: <ticks[0..]=60>, radius: <int[0..5]=0>, width: <int[1..8]=3>, height: <int[1..8]=1>, depth: <int[1..8]=3>, ahead: <int[0..8]=0>, dy: <int[-4..4]=0>, airOnly: <bool=true>, fill-chance: <double[0..100]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `shape` `enum{POINT|FOOTPRINT|COLUMN|BOX}`
- _param_ `material` `material`
- _param_ `material2` `material`
- _param_ `material3` `material`
- _param_ `material4` `material`
- _param_ `ticks` `ticks[0..]`
- _param_ `radius` `int[0..5]`
- _param_ `width` `int[1..8]`
- _param_ `height` `int[1..8]`
- _param_ `depth` `int[1..8]`
- _param_ `ahead` `int[0..8]`
- _param_ `dy` `int[-4..4]`
- _param_ `airOnly` `bool`
- _param_ `fill-chance` `double[0..100]` — percent of columns actually placed — a partial, scattered field
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ TEMP_BLOCK: { shape: COLUMN, material: ICE, height: 2, ahead: 1, ticks: 60, who: "@Attacker" } }`

### TRAP_BREAK

Break every confining trap currently on the wearer — encasing webs, web boxes, cage cells — restoring the trapped blocks to their true originals immediately. Area floors and trails are unaffected. Works through ability silence (it is a block restore, not an ability negation).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ TRAP_BREAK: { whiff-sound: <sound=BLOCK_ANVIL_LAND>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `whiff-sound` `sound` — played (low-pitched) when nothing confining was found — a silent no-op is indistinguishable from a broken feature
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ TRAP_BREAK: { } }`

### TURRET_RING

Stand `count` invulnerable `type` emplacements on open ground, evenly spaced on a ring-radius ring around each target, for `ttl` ticks. A site with no open ground, or one the protection gate denies the actor, is SKIPPED (logged) — the ring is gated spot by spot, not once for the cast. After `initial-delay` ticks each emplacement fires a `projectile` at `projectile-speed` toward the nearest body the `filter` admits within acquire-range that has line of sight to it, then re-fires every period-min..period-max ticks (a fresh draw per volley, so a ring never fires as one salvo). A shot that strikes a body runs the ACTOR's IMPACT abilities on it ONCE — that payload is the whole damage; emplacements take no damage and neither they nor their shots ever break blocks. The `spawn-` cue plays where each one lands (plus a damage-free lightning flash unless spawn-lightning: false) and the `despawn-` cue where it expires. Era note: a fireball-family projectile is propelled with setDirection, whose scaling changed in the 1.21 line — the shot flies everywhere, but reads faster there than the authored speed.

- _affinity_: `REGION`
- _usage_: `{ TURRET_RING: { type: <entity_type=ENDER_CRYSTAL>, count: <int[1..16]=3>, ring-radius: <double[0..]=7>, ttl: <ticks[1..]=200>, acquire-range: <double[0..]=8>, initial-delay: <ticks[0..]=30>, period-min: <ticks[1..]=8>, period-max: <ticks[1..]=13>, filter: <enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}=ENEMIES>, projectile: <entity_type=WITHER_SKULL>, projectile-speed: <double[0..]=0.06>, spawn-sound: <sound>, spawn-volume: <double[0..]=1>, spawn-pitch: <double[0..]=1>, spawn-particle: <particle>, spawn-particle-count: <int[0..]=1>, spawn-particle-spread: <double[0..]=0>, spawn-lightning: <bool=true>, despawn-sound: <sound>, despawn-volume: <double[0..]=1>, despawn-pitch: <double[0..]=1>, despawn-particle: <particle>, despawn-particle-count: <int[0..]=16>, despawn-particle-spread: <double[0..]=0.75>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `type` `entity_type` — what each emplacement is
- _param_ `count` `int[1..16]` — how many emplacements the ring tries to place
- _param_ `ring-radius` `double[0..]` — blocks from the actor to each emplacement
- _param_ `ttl` `ticks[1..]` — how long the ring stands before it despawns
- _param_ `acquire-range` `double[0..]` — how far an emplacement looks for a target
- _param_ `initial-delay` `ticks[0..]` — ticks before the FIRST volley — the arming window
- _param_ `period-min` `ticks[1..]` — shortest gap between volleys
- _param_ `period-max` `ticks[1..]` — longest gap between volleys
- _param_ `filter` `enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}` — who an emplacement will shoot at
- _param_ `projectile` `entity_type` — what an emplacement fires
- _param_ `projectile-speed` `double[0..]` — how hard each shot is launched
- _param_ `spawn-sound` `sound` — cue as the ring lands; omit for silence
- _param_ `spawn-volume` `double[0..]`
- _param_ `spawn-pitch` `double[0..]`
- _param_ `spawn-particle` `particle` — burst at each emplacement; omit for none
- _param_ `spawn-particle-count` `int[0..]`
- _param_ `spawn-particle-spread` `double[0..]`
- _param_ `spawn-lightning` `bool` — flash a damage-free lightning visual at each emplacement
- _param_ `despawn-sound` `sound` — cue as an emplacement expires; omit for silence
- _param_ `despawn-volume` `double[0..]`
- _param_ `despawn-pitch` `double[0..]`
- _param_ `despawn-particle` `particle` — burst as an emplacement expires; omit for none
- _param_ `despawn-particle-count` `int[0..]`
- _param_ `despawn-particle-spread` `double[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ TURRET_RING: { type: ENDER_CRYSTAL, count: 5, ring-radius: 8, ttl: 300, acquire-range: 11, initial-delay: 30, period-min: 8, period-max: 13, filter: ENEMIES, projectile: WITHER_SKULL, projectile-speed: 0.065, spawn-sound: ENTITY_GHAST_SHOOT, spawn-volume: 3.0, spawn-pitch: 0.9, spawn-particle: FLAME, spawn-particle-count: 24, spawn-lightning: true, despawn-particle: SPELL_WITCH, despawn-particle-count: 16, despawn-particle-spread: 0.75, who: "@Self" } }`

### VANISH

Hide the target from EVERY online player for `duration` ticks — a packet-level hide, so worn armour vanishes with the body. The window breaks early once `break-hits` of the target's own hits LAND (0 = never); damage they take never spends one, so hiding survives being hit but not hitting back. A player who joins mid-window is re-synced, so a vanish cannot be beaten by relogging. While it is live `var` reads 1, and it drops to 0 the moment it ends by any route (timer, hit, quit). A re-proc REPLACES the window: fresh duration, fresh hit allowance. `end-message` is sent to the target once the window ends, whichever route ended it — exactly once, never on the refusal path.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ VANISH: { duration: <ticks[1..]=30>, break-hits: <int[0..]=1>, var: <string=>, end-message: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[1..]` — ticks the target stays hidden from every player
- _param_ `break-hits` `int[0..]` — landed outgoing hits the window absorbs before it breaks; 0 = only the timer ends it
- _param_ `var` `string` — player variable reading 1 while the window is live; empty = none
- _param_ `end-message` `string` — line sent to the target when the window ends, by ANY route; empty = silent
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ VANISH: { duration: 60, break-hits: 2, end-message: "&4&l* Feign Death - UNVANISHED *", who: "@Self" } }`

### VELOCITY

Apply velocity to the target(s): mode=add uses x/y/z; mode=away shoves them back from the anchor with strength and mode=toward drags them to it. anchor picks the point — the activator (default), the attacker that hit them, or the combat victim — so a defensive proc can launch the wearer away from whoever struck. Replaces THROW/LAUNCH/KNOCKBACK.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ VELOCITY: { mode: <enum{add|away|toward}=add>, x: <double=0>, y: <double=0>, z: <double=0>, strength: <double[0..]=0>, anchor: <enum{activator|attacker|victim}=activator>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `mode` `enum{add|away|toward}`
- _param_ `x` `double`
- _param_ `y` `double`
- _param_ `z` `double`
- _param_ `strength` `double[0..]`
- _param_ `anchor` `enum{activator|attacker|victim}` — the point away/toward is measured from
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ VELOCITY: { mode: add, x: 0, y: 1.2, z: 0 } }`

### VIEWER_HIDE

Hide the target player from the attacker (viewer=attacker) or from every online player (viewer=all) for duration ticks, restoring them at the window's close. A packet-level hide: worn armour vanishes with the body, unlike an INVISIBILITY potion. A relog on either side ends it early. viewer=attacker with no attacker in scope hides nothing.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ VIEWER_HIDE: { duration: <ticks[0..]=20>, viewer: <enum{attacker|all}=attacker>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `duration` `ticks[0..]`
- _param_ `viewer` `enum{attacker|all}`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ VIEWER_HIDE: { duration: 60, viewer: attacker } }`

### VULNERABILITY

Mark each player target to take `percent`% more damage from EVERY source (fall, fire and the void included) for `duration`. NON-STACKING: a re-mark keeps the stronger window and the later expiry, never the sum. The contribution is additive with the victim's own reductions, so armour still counts — this is a fragility mark, not a bypass.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ VULNERABILITY: { percent: <double[0..]>, duration: <ticks[1..]=60>, hit-message: <string=>, expiry-message: <string=>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `percent` `double[0..]` — extra incoming damage, e.g. 100 for double
- _param_ `duration` `ticks[1..]` — how long the mark holds
- _param_ `hit-message` `string` — line sent to the marked player on each hit the mark amplifies ({damage} = the hit); empty = silent
- _param_ `expiry-message` `string` — line sent when the mark lapses; empty = silent
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `VICTIM`
- _example_: `{ VULNERABILITY: { percent: 100, duration: 60, hit-message: "&cmarked (-{damage})", expiry-message: "&7mark off", who: "@Victim" } }`

### WALKER

Lay a temporary platform of a material under the target for a duration (then revert), out to a radius. replace = AIR_ONLY | REPLACEABLE (air/liquid) | ANY.

- _affinity_: `REGION`
- _usage_: `{ WALKER: { material: <material>, ticks: <ticks[0..]=60>, radius: <int[0..4]=1>, replace: <enum{AIR_ONLY|REPLACEABLE|ANY}=REPLACEABLE>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `material` `material`
- _param_ `ticks` `ticks[0..]`
- _param_ `radius` `int[0..4]`
- _param_ `replace` `enum{AIR_ONLY|REPLACEABLE|ANY}`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
- _target_ `who`: selector `SELF`
- _example_: `{ WALKER: { material: ICE, ticks: 80, radius: 1 } }`

### WARD

Ward the target player(s) with a typed guard flag for duration ticks: mob-target (mobs don't aggro unless provoked), invsee (others can't open their inventory), near (hidden from the proximity listing), splash-heal (healing splash potions boosted by amount%).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ WARD: { type: <enum{mob-target|invsee|near|splash-heal}>, duration: <ticks[0..]=100>, amount: <double=0>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `type` `enum{mob-target|invsee|near|splash-heal}`
- _param_ `duration` `ticks[0..]`
- _param_ `amount` `double`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
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
- _usage_: `{ WEAKEN: { percent: <double[0..]>, duration: <ticks[0..]=100>, each-if: <condition>, each-chance: <double[0..100]>, each-cooldown: <ticks[0..]> } }`
- _param_ `percent` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _param_ `each-if` `condition` — Per-target filter: each resolved target is tested with the %target.*% subject bound, and a target that fails is dropped from THIS effect only. It cannot un-activate the ability, release its cooldown or refund its souls.
- _param_ `each-chance` `double[0..100]` — Per-target chance, sugar for each-if: "%target.roll% < <this>" over the ONE draw each body carries for the whole ability — so this row and its complement partition instead of rolling twice. Declaring each-if too ANDs them.
- _param_ `each-cooldown` `ticks[0..]` — Per-target cooldown in ticks: a target hit within its own window is dropped. Keyed on the ability's cooldown scope, so declaring it without one is a load error.
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

Every player within r blocks of the target, except the activator and except allies. allies: true takes allied players back, for a broadcast audience rather than a target list.

- _usage_: `{ ALLPLAYERS: { r: <double[0..]=32>, allies: <bool=false> } }`
- _param_ `r` `double[0..]` — search radius in blocks
- _param_ `allies` `bool` — include allied players — set it for a broadcast AUDIENCE; a target list wants the default
- _example_: `@AllPlayers{r=32}`

### AOE

Living entities within r blocks of the target, except the activator; optionally filtered, capped, and with the combat victim excluded. filter admits a + conjunction (ENEMIES+PLAYERS = hostile players only).

- _usage_: `{ AOE: { r: <double[0..]=4>, filter: <enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}=ALL>, limit: <int[0..]=0>, exclude: <enum{none|victim}=none> } }`
- _param_ `r` `double[0..]` — radius in blocks
- _param_ `filter` `enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}` — which entities to include; A+B keeps only what both admit
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

### BORE

A half-width x half-height cross-section centred on the activation block, repeated depth layers into the mined face. depth=1 is a flat face; materials keeps only the listed block types and exclude-materials drops them, both written [STONE,DIRT] so the comma survives the selector body. A type on both lists is dropped. left/right/up/down override their axis's half-* for an ASYMMETRIC cross-section — the only way to reach an even width or height (left=1, right=2 is 4 blocks across).

- _usage_: `{ BORE: { half-width: <int[0..]=1>, half-height: <int[0..]=1>, depth: <int[1..]=1>, left: <int[-1..]=-1>, right: <int[-1..]=-1>, up: <int[-1..]=-1>, down: <int[-1..]=-1>, materials: <material list=>, exclude-materials: <material list=> } }`
- _param_ `half-width` `int[0..]` — half the cross-section across (1 = 3 blocks wide)
- _param_ `half-height` `int[0..]` — half the cross-section up and down (1 = 3 blocks tall)
- _param_ `depth` `int[1..]` — layers into the face, counting the activation block's own
- _param_ `left` `int[-1..]` — blocks left of centre; -1 = half-width
- _param_ `right` `int[-1..]` — blocks right of centre; -1 = half-width
- _param_ `up` `int[-1..]` — blocks above centre; -1 = half-height
- _param_ `down` `int[-1..]` — blocks below centre; -1 = half-height
- _param_ `materials` `material list` — keep only these block types (empty = every block)
- _param_ `exclude-materials` `material list` — drop these block types (empty = drop none)
- _example_: `@Bore{half-width=1, half-height=1, depth=3, exclude-materials=[BEDROCK,OBSIDIAN]}`

### ENTITYINSIGHT

The living entity the activator is looking at within r blocks, or nothing. An allied player is skipped unless allies: true; mobs are never filtered.

- _usage_: `{ ENTITYINSIGHT: { r: <double[0..]=16>, allies: <bool=false> } }`
- _param_ `r` `double[0..]` — maximum line-of-sight distance in blocks
- _param_ `allies` `bool` — include an allied player in the crosshair; the default skips one
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

The single nearest living entity within r blocks (optionally filtered), except the activator. filter admits a + conjunction (ENEMIES+PLAYERS = hostile players only).

- _usage_: `{ NEAREST: { r: <double[0..]=5>, filter: <enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}=ALL> } }`
- _param_ `r` `double[0..]` — search radius in blocks
- _param_ `filter` `enum set{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}` — which entities to consider; A+B keeps only what both admit
- _example_: `@Nearest{r=5, filter=PLAYERS}`

### NEARESTPLAYER

The single nearest player within r blocks, except the activator and except allies.

- _usage_: `{ NEARESTPLAYER: { r: <double[0..]=16>, allies: <bool=false> } }`
- _param_ `r` `double[0..]` — search radius in blocks
- _param_ `allies` `bool` — include allied players; the default skips them
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

The square of blocks perpendicular to the look direction, centred on the activation block. materials keeps only the listed block types and exclude-materials drops them, both written [STONE,DIRT] so the comma survives the selector body. A type on both lists is dropped.

- _usage_: `{ TRENCH: { radius: <int[0..]=1>, materials: <material list=>, exclude-materials: <material list=> } }`
- _param_ `radius` `int[0..]` — half-width of the face (1 = 3x3)
- _param_ `materials` `material list` — keep only these block types (empty = every block)
- _param_ `exclude-materials` `material list` — drop these block types (empty = drop none)
- _example_: `@Trench{radius=1}`

### TUNNEL

The blocks directly ahead of the activation block, along the look direction. materials keeps only the listed block types and exclude-materials drops them, both written [STONE,DIRT] so the comma survives the selector body. A type on both lists is dropped.

- _usage_: `{ TUNNEL: { depth: <int[1..]=3>, materials: <material list=>, exclude-materials: <material list=> } }`
- _param_ `depth` `int[1..]` — blocks ahead along the look direction
- _param_ `materials` `material list` — keep only these block types (empty = every block)
- _param_ `exclude-materials` `material list` — drop these block types (empty = drop none)
- _example_: `@Tunnel{depth=4}`

### VEIN

Up to `limit` blocks contiguous with and matching the activation block (vein miner). materials restricts which struck blocks vein at all and exclude-materials names ones that never do, both written [IRON_ORE,GOLD_ORE] so the comma survives the selector body. Both gate the STRUCK block, since the fill is same-material by construction.

- _usage_: `{ VEIN: { limit: <int[1..]=64>, materials: <material list=>, exclude-materials: <material list=> } }`
- _param_ `limit` `int[1..]` — max blocks in the vein
- _param_ `materials` `material list` — only vein these block types (empty = whatever was struck)
- _param_ `exclude-materials` `material list` — never vein these block types (empty = none)
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
| `EAT` | NEUTRAL | false | true | false |
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
| `HURT` | DEFENSE | false | true | false |
| `EQUIP_CHANGE` | NEUTRAL | false | true | false |
| `PROJECTILE_LAND` | NEUTRAL | false | true | false |
| `PROXIMITY_EVENT` | NEUTRAL | false | true | true |
| `SUMMON_PAYLOAD` | NEUTRAL | false | true | true |

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

### Numeric functions

Callable anywhere a number is legal — inside a `condition:` and as an expression-valued numeric parameter (`{ DAMAGE: { amount: "min(%combo% * 2, 12)" } }`). Arguments are themselves expressions, so calls nest.

| Function | Result |
| --- | --- |
| `min(a, b)` | the smaller of `a` and `b` |
| `max(a, b)` | the larger of `a` and `b` |
| `clamp(x, lo, hi)` | `x` confined to `[lo, hi]` |
| `floor(x)` | `x` rounded down (toward negative infinity) |
| `rand(lo, hi)` | a uniform random value in `[lo, hi)`, drawn once per evaluation |

A parameter that declares a range clamps an expression to it at evaluation, so a `double[0..100]` parameter written as `"%combo% * 40"` can never exceed 100 however large the variable grows. A constant outside the range is still a load error.

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
| `%actor.food%` | NUM |
| `%actor.gamemode%` | STR |
| `%actor.groundblock%` | STR |
| `%actor.health%` | NUM |
| `%actor.healthpercent%` | NUM |
| `%actor.helditem%` | STR |
| `%actor.heroicpieces%` | NUM |
| `%actor.level%` | NUM |
| `%actor.maxhealth%` | NUM |
| `%actor.ownedground%` | BOOL |
| `%actor.setweapon%` | BOOL |
| `%actor.souls%` | NUM |
| `%actor.totalexp%` | NUM |
| `%actor.type%` | STR |
| `%actor.world%` | STR |
| `%actor.y%` | NUM |
| `%attackerindex%` | NUM |
| `%block.type%` | STR |
| `%blocking%` | BOOL |
| `%bookrate.apply%` | BOOL |
| `%bookrate.generate%` | BOOL |
| `%combo%` | NUM |
| `%damage%` | NUM |
| `%damagecause%` | STR |
| `%distance%` | NUM |
| `%equipchange%` | STR |
| `%flying%` | BOOL |
| `%gliding%` | BOOL |
| `%heldticks%` | NUM |
| `%impactheight%` | NUM |
| `%isblock%` | BOOL |
| `%item.durabilitypercent%` | NUM |
| `%itemdamage.armor%` | BOOL |
| `%nearbyallies%` | NUM |
| `%nearbyenemies%` | NUM |
| `%onfire%` | BOOL |
| `%onground%` | BOOL |
| `%posthit.health%` | NUM |
| `%projectilekind%` | STR |
| `%proximityevent%` | STR |
| `%ragestacks%` | NUM |
| `%recentattackers%` | NUM |
| `%selected%` | NUM |
| `%sneaking%` | BOOL |
| `%soulcost%` | NUM |
| `%sprinting%` | BOOL |
| `%status.freeze%` | BOOL |
| `%status.teleblock%` | BOOL |
| `%swimming%` | BOOL |
| `%victim.blocking%` | BOOL |
| `%victim.flying%` | BOOL |
| `%victim.food%` | NUM |
| `%victim.fromspawner%` | BOOL |
| `%victim.gliding%` | BOOL |
| `%victim.health%` | NUM |
| `%victim.healthpercent%` | NUM |
| `%victim.helditem%` | STR |
| `%victim.heroicpieces%` | NUM |
| `%victim.inzone%` | BOOL |
| `%victim.maxhealth%` | NUM |
| `%victim.mobtype%` | STR |
| `%victim.relation%` | STR |
| `%victim.sneaking%` | BOOL |
| `%victim.souls%` | NUM |
| `%victim.sprinting%` | BOOL |
| `%victim.swimming%` | BOOL |
| `%victim.type%` | STR |
| `%world.raining%` | BOOL |
| `%world.thundering%` | BOOL |
| `%world.time%` | NUM |

Five families take a name rather than being fixed facts, and read as NUM:

- `%victim.var.<name>%` — a counter `SET_VAR` wrote on the victim; `0` when unset.
- `%actor.potion.<effect>%` / `%victim.potion.<effect>%` — the active level of one potion effect, as amplifier + 1, so `> 0` means "active" and `> 1` means "at least II"; `0` when absent. `<effect>` is resolved when the pack loads, so a name unknown on this version is a load error, not a condition that silently never matches.
- `%actor.enchlevel.<key>%` / `%victim.enchlevel.<key>%` — that side's worn level of one custom enchant, so `> 0` means "has it" and `>= 3` means "at least III"; `0` when not worn. `<key>` is the enchant's file name (its stable-key stem), and an enchant absent from the pack simply reads `0` rather than failing the load.
- `%actor.crystals.<key>%` / `%victim.crystals.<key>%` — how many of that side's four worn ARMOUR pieces carry one crystal, so `> 0` means "socketed somewhere" and `== 4` means "the whole set"; `0` when none. `<key>` is the crystal's file name (its stable-key stem). A piece counts ONCE however many times it names the crystal, a merged `a+b` socket counts for both components, and a socketed weapon is never counted — it is a count of worn pieces, which is what per-piece scaling needs.

### `%target.*%` — the per-target subject

Inside an effect row, `%target.*%` names **one body of that effect's resolved target list**, re-bound as the list is walked. It is readable ONLY from an effect row — its `each-if:` / `each-chance:` or an expression-valued argument. Reading it from the ability's `condition:` or `chance:` is a load error, because those gates run before any selector resolves; use `%victim.*%` there for the combat victim.

| Subject fact | Type | Reads |
| --- | --- | --- |
| `%target.enchlevel.<key>%` | NUM | that body's worn level of one custom enchant |
| `%target.crystals.<key>%` | NUM | that body's worn armour pieces carrying one crystal |
| `%target.var.<name>%` | NUM | a counter `SET_VAR` wrote on that body |
| `%target.souls%` | NUM | that body's cross-gem soul total (`0` for a mob) |
| `%target.heroicpieces%` | NUM | that body's worn heroic armour pieces |
| `%target.type%` | STR | that body's entity type |
| `%target.relation%` | STR | `ALLY` / `ENEMY` / `NEUTRAL` vs the activator |
| `%target.roll%` | NUM | the ONE `[0,100)` draw this body carries |

Health, pose and geometry are deliberately absent, and naming one is a load error rather than a silent zero: the per-target pass decides ABOUT a body without ever touching it, which is what keeps a 20-body sweep free of cross-region entity reads.

`%target.roll%` is drawn once per body per ABILITY and shared by every `each-*` read of it — including on later effect rows — so a filter and its complement partition: one body cannot pass both rows, nor neither.
