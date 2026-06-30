# StarEnchants DSL reference

_Generated from the engine's effect / selector / trigger / condition / variable vocabularies. Do not edit by hand — run_ `./gradlew :engine:test --tests "*ReferenceDocDriftTest" -Dse.doc.regen=true` _to regenerate; the build fails if this file drifts from the code._

## Effects

The actions an ability runs. Each is a block map `{ HEAD: { param: value, who:, wait: } }` in an enchant/set/crystal's `effects:` list.

### BREAK_BLOCK

Break the target block(s) (default @Here; drops=false clears). @Vein/@Tunnel/@Trench for shapes.

- _affinity_: `REGION`
- _usage_: `{ BREAK_BLOCK: { drops: <bool=true> } }`
- _param_ `drops` `bool`
- _target_ `at`: selector `HERE`
- _example_: `{ BREAK_BLOCK: { drops: true } }`

### CANCEL

Cancel the Bukkit event that triggered this activation.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ CANCEL: {} }`
- _example_: `{ CANCEL: {} }`

### CURE

Clear active potion effects of one category from the target(s): ALL (default), HARMFUL, BENEFICIAL, or NEUTRAL. category HARMFUL strips only debuffs (positive effects untouched).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ CURE: { category: <enum{ALL|HARMFUL|BENEFICIAL|NEUTRAL}=ALL> } }`
- _param_ `category` `enum{ALL|HARMFUL|BENEFICIAL|NEUTRAL}`
- _target_ `who`: selector `SELF`
- _example_: `{ CURE: { category: HARMFUL } }`

### DAMAGE

Deal a flat amount of extra damage to the target.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ DAMAGE: { amount: <double[0..]> } }`
- _param_ `amount` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ DAMAGE: { amount: 6 } }`

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
- _usage_: `{ DAMAGE_SCALE: { side: <enum{attack|defense}=attack>, mode: <enum{add|flat}=add>, per: <double>, cap: <double[0..]=0> } }`
- _param_ `side` `enum{attack|defense}`
- _param_ `mode` `enum{add|flat}`
- _param_ `per` `double`
- _param_ `cap` `double[0..]`
- _target_ `who`: selector `AOE`
- _example_: `{ DAMAGE_SCALE: { side: attack, mode: add, per: 10, cap: 100, who: "@AllPlayers{r=7}" } }`

### DISARM

Make the target(s) drop their held (main-hand) item.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ DISARM: {} }`
- _target_ `who`: selector `VICTIM`
- _example_: `{ DISARM: {} }`

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
- _usage_: `{ DURABILITY: { amount: <int=-1>, target: <enum{item|armor|all}=item>, mode: <enum{restore|damage}=restore> } }`
- _param_ `amount` `int` — durability points; negative fully restores (restore mode)
- _param_ `target` `enum{item|armor|all}`
- _param_ `mode` `enum{restore|damage}`
- _target_ `who`: selector `SELF`
- _example_: `{ DURABILITY: { amount: -1, target: item } }`

### EXPLODE

Create an explosion at the target.

- _affinity_: `REGION`
- _usage_: `{ EXPLODE: { power: <double[0..]>, breakBlocks: <bool=false> } }`
- _param_ `power` `double[0..]`
- _param_ `breakBlocks` `bool`
- _target_ `who`: selector `VICTIM`
- _example_: `{ EXPLODE: { power: 4, breakBlocks: false } }`

### EXTINGUISH

Put out the target's fire.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ EXTINGUISH: {} }`
- _target_ `who`: selector `SELF`
- _example_: `{ EXTINGUISH: {} }`

### FILL_OXYGEN

Refill the target's air supply.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ FILL_OXYGEN: {} }`
- _target_ `who`: selector `SELF`
- _example_: `{ FILL_OXYGEN: {} }`

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

### GIVE_ITEM

Give a material to the player target(s); overflow drops at their feet.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ GIVE_ITEM: { material: <material>, count: <int[1..]=1> } }`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `{ GIVE_ITEM: { material: DIAMOND, count: 1, who: "@Self" } }`

### GUARD

Summon count guardian mobs of type at the activation location, each targeting the attacker, auto-removed after ttl ticks (default 200; 0 = permanent); optional custom name. A targeted SPAWN_ENTITY for retaliation — author on DEFENSE.

- _affinity_: `REGION`
- _usage_: `{ GUARD: { type: <entity_type>, count: <int[1..]=1>, ttl: <ticks[0..]=200>, name: <string=> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `name` `string`
- _target_ `who`: selector `ATTACKER`
- _example_: `{ GUARD: { type: IRON_GOLEM, count: 1, ttl: 200, name: "&bGuardian" } }`

### HEALTH

Add to the target's maximum health (restored on unequip).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ HEALTH: { amount: <double[0..]> } }`
- _param_ `amount` `double[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ HEALTH: { amount: 4 } }`

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

### IMMUNE

Make the target player(s) immune to a damage cause (sword/axe/projectile/potion/all) for duration ticks.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ IMMUNE: { type: <enum{sword|axe|projectile|potion|all}>, duration: <ticks[0..]=100> } }`
- _param_ `type` `enum{sword|axe|projectile|potion|all}`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ IMMUNE: { type: potion, duration: 100 } }`

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
- _usage_: `{ LIGHTNING: { damage: <double[0..]=0> } }`
- _param_ `damage` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ LIGHTNING: { damage: 6 } }`

### MESSAGE

Send feedback to the activating player on a channel: chat (default), actionbar, or title (with subtitle + fade/stay/fade timings). Replaces ACTIONBAR/TITLE.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ MESSAGE: { text: <string>, channel: <enum{chat|actionbar|title}=chat>, subtitle: <string=>, fadeIn: <ticks[0..]=10>, stay: <ticks[0..]=70>, fadeOut: <ticks[0..]=20> } }`
- _param_ `text` `string`
- _param_ `channel` `enum{chat|actionbar|title}`
- _param_ `subtitle` `string` — title channel only
- _param_ `fadeIn` `ticks[0..]` — title channel only
- _param_ `stay` `ticks[0..]` — title channel only
- _param_ `fadeOut` `ticks[0..]` — title channel only
- _example_: `{ MESSAGE: { text: "&aCritical hit!" } }`

### MODIFY_EXP

Modify a player target's experience: give to them, take from them, or transfer (take from the target and grant the total to the activator). Replaces GIVE_EXP.

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
- _usage_: `{ MODIFY_HEALTH: { amount: <double[0..]>, mode: <enum{give|take|transfer|set}=give> } }`
- _param_ `amount` `double[0..]`
- _param_ `mode` `enum{give|take|transfer|set}`
- _target_ `who`: selector `SELF`
- _example_: `{ MODIFY_HEALTH: { amount: 4, mode: give, who: "@Self" } }`

### MODIFY_MONEY

Modify a player target's balance: give to them, take from them, transfer (take from the target and give the total to the activator), or steal_percent (give the activator that PERCENT of the target's balance — amount is a 0..100 percentage). Replaces GIVE_MONEY/TAKE_MONEY/STEAL_MONEY[_PERCENT].

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ MODIFY_MONEY: { amount: <double[0..]>, mode: <enum{give|take|transfer|steal_percent}=give> } }`
- _param_ `amount` `double[0..]`
- _param_ `mode` `enum{give|take|transfer|steal_percent}`
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

Spawn particles at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `{ PARTICLE: { particle: <particle>, count: <int[0..]=1> } }`
- _param_ `particle` `particle`
- _param_ `count` `int[0..]`
- _example_: `{ PARTICLE: { particle: FLAME, count: 20 } }`

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
- _usage_: `{ POTION: { effect: <potion_effect>, level: <int[1..]>, duration: <ticks[0..]> } }`
- _param_ `effect` `potion_effect`
- _param_ `level` `int[1..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ POTION: { effect: STRENGTH, level: 1, duration: 100 } }`

### PROJECTILE

Launch count projectiles of a type from the activator's eye (covers SPAWN_ARROWS via the ARROW type).

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ PROJECTILE: { type: <entity_type>, count: <int[1..]=1>, speed: <double[0..]=1.5> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `speed` `double[0..]`
- _example_: `{ PROJECTILE: { type: ARROW, count: 3, speed: 1.5 } }`

### REMOVE_ARMOR

Strip one random worn armour piece from the target(s) and drop it.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ REMOVE_ARMOR: {} }`
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

### REMOVE_SOULS

Debit souls from a soul gem: @Self (default) charges the activator's active gem, @Victim drains the target's own gem. A no-op when that player is not in soul mode.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ REMOVE_SOULS: { amount: <int[1..]> } }`
- _param_ `amount` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `{ REMOVE_SOULS: { amount: 5 } }`

### RUN_COMMAND

Run a command from the console. Affinity GLOBAL — runs on the global thread.

- _affinity_: `GLOBAL`
- _usage_: `{ RUN_COMMAND: { command: <string> } }`
- _param_ `command` `string`
- _example_: `{ RUN_COMMAND: { command: "eco give %player% 100" } }`

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
- _usage_: `{ SET_VAR: { name: <string>, value: <string=>, ttl: <ticks[0..]=0> } }`
- _param_ `name` `string`
- _param_ `value` `string`
- _param_ `ttl` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `{ SET_VAR: { name: rage, value: 1, ttl: 200, who: "@Self" } }`

### SMELT

Auto-smelt the block broken by this MINE activation (ore→ingot, sand→glass, …).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SMELT: {} }`
- _example_: `{ SMELT: {} }`

### SOUND

Play a sound at the activation location. No-op if the activation has no location.

- _affinity_: `REGION`
- _usage_: `{ SOUND: { sound: <sound>, volume: <double[0..]=1>, pitch: <double[0..]=1> } }`
- _param_ `sound` `sound`
- _param_ `volume` `double[0..]`
- _param_ `pitch` `double[0..]`
- _example_: `{ SOUND: { sound: ENTITY_GENERIC_EXPLODE, volume: 1, pitch: 1 } }`

### SPAWN_ENTITY

Spawn count entities of type at the target's (or activation) location; ttl ticks until removal (0 = permanent), optional starting health, and owner=activator to tame an owned summon to the activator. Replaces SPAWN/TNT.

- _affinity_: `REGION`
- _usage_: `{ SPAWN_ENTITY: { type: <entity_type>, count: <int[1..]=1>, ttl: <ticks[0..]=0>, health: <double[0..]=0>, owner: <enum{none|activator}=none> } }`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `health` `double[0..]`
- _param_ `owner` `enum{none|activator}`
- _target_ `who`: selector `SELF`
- _example_: `{ SPAWN_ENTITY: { type: WOLF, count: 1, ttl: 0, health: 0, owner: activator } }`

### SUPPRESS

Disable a target's enchant/group/type (the key) for a duration in ticks (DISABLE_ENCHANT/GROUP/TYPE). Default target the combat victim.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUPPRESS: { scope: <enum{ENCHANT|GROUP|TYPE}>, key: <string>, duration: <ticks[0..]=200> } }`
- _param_ `scope` `enum{ENCHANT|GROUP|TYPE}`
- _param_ `key` `string`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ SUPPRESS: { scope: GROUP, key: lifesteal, duration: 200, who: "@Victim" } }`

### SUPPRESS_IMMUNE

Make the target(s) immune to all suppression (DISABLE_ENCHANT/GROUP/TYPE) while worn — a maintained PASSIVE flag, armed on equip and lifted on unequip. Player-only.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ SUPPRESS_IMMUNE: {} }`
- _target_ `who`: selector `SELF`
- _example_: `{ SUPPRESS_IMMUNE: { who: "@Self" } }`

### TELEBLOCK

Block the target player(s) from teleporting (ender pearl / chorus fruit) for duration ticks.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ TELEBLOCK: { duration: <ticks[0..]=400> } }`
- _param_ `duration` `ticks[0..]`
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
- _usage_: `{ TELEPORT_BEHIND: { of: <enum{VICTIM|ACTOR}=VICTIM>, distance: <double[0..]=1>, onFail: <enum{ONTOP|NONE}=ONTOP> } }`
- _param_ `of` `enum{VICTIM|ACTOR}`
- _param_ `distance` `double[0..]`
- _param_ `onFail` `enum{ONTOP|NONE}`
- _target_ `who`: selector `SELF`
- _example_: `{ TELEPORT_BEHIND: { of: VICTIM, distance: 1, onFail: ONTOP, who: "@Self" } }`

### TELEPORT_DROPS

Send the block's drops straight to the breaker's inventory (this MINE activation).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `{ TELEPORT_DROPS: {} }`
- _example_: `{ TELEPORT_DROPS: {} }`

### VELOCITY

Apply velocity to the target(s): mode=add uses x/y/z; mode=away knocks them back from the activator with strength. Replaces THROW/LAUNCH/KNOCKBACK.

- _affinity_: `TARGET_ENTITY`
- _usage_: `{ VELOCITY: { mode: <enum{add|away}=add>, x: <double=0>, y: <double=0>, z: <double=0>, strength: <double[0..]=0> } }`
- _param_ `mode` `enum{add|away}`
- _param_ `x` `double`
- _param_ `y` `double`
- _param_ `z` `double`
- _param_ `strength` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `{ VELOCITY: { mode: add, x: 0, y: 1.2, z: 0 } }`

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

Living entities within r blocks of the target, except the activator; optionally filtered and capped.

- _usage_: `{ AOE: { r: <double[0..]=4>, filter: <enum{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}=ALL>, limit: <int[0..]=0> } }`
- _param_ `r` `double[0..]` — radius in blocks
- _param_ `filter` `enum{ALL|PLAYERS|MONSTERS|MOBS|ENEMIES|ALLIES}` — which entities to include
- _param_ `limit` `int[0..]` — max targets, nearest first (0 = unlimited)
- _example_: `@Aoe{r=6, filter=MONSTERS}`

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
| `ITEM_DAMAGE` | NEUTRAL | true | false | false |
| `EAT` | NEUTRAL | true | false | false |
| `FISHING` | NEUTRAL | true | false | false |
| `INTERACT` | NEUTRAL | true | false | false |
| `INTERACT_LEFT` | NEUTRAL | true | false | false |
| `INTERACT_RIGHT` | NEUTRAL | true | false | false |
| `REPEATING` | NEUTRAL | false | true | false |
| `COMMAND` | NEUTRAL | false | true | false |

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
| `%actor.food%` | NUM |
| `%actor.gamemode%` | STR |
| `%actor.health%` | NUM |
| `%actor.healthpercent%` | NUM |
| `%actor.helditem%` | STR |
| `%actor.level%` | NUM |
| `%actor.maxhealth%` | NUM |
| `%actor.totalexp%` | NUM |
| `%actor.type%` | STR |
| `%actor.world%` | STR |
| `%block.type%` | STR |
| `%blocking%` | BOOL |
| `%combo%` | NUM |
| `%damage%` | NUM |
| `%distance%` | NUM |
| `%flying%` | BOOL |
| `%gliding%` | BOOL |
| `%isblock%` | BOOL |
| `%nearbyenemies%` | NUM |
| `%onfire%` | BOOL |
| `%onground%` | BOOL |
| `%sneaking%` | BOOL |
| `%sprinting%` | BOOL |
| `%swimming%` | BOOL |
| `%victim.blocking%` | BOOL |
| `%victim.flying%` | BOOL |
| `%victim.food%` | NUM |
| `%victim.gliding%` | BOOL |
| `%victim.health%` | NUM |
| `%victim.healthpercent%` | NUM |
| `%victim.helditem%` | STR |
| `%victim.maxhealth%` | NUM |
| `%victim.mobtype%` | STR |
| `%victim.sneaking%` | BOOL |
| `%victim.sprinting%` | BOOL |
| `%victim.swimming%` | BOOL |
| `%victim.type%` | STR |
| `%world.raining%` | BOOL |
| `%world.thundering%` | BOOL |
| `%world.time%` | NUM |
