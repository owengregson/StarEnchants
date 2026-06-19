# StarEnchants DSL reference

_Generated from the engine's effect / selector / trigger / condition / variable vocabularies. Do not edit by hand — run_ `./gradlew :engine:test --tests "*ReferenceDocDriftTest" -Dse.doc.regen=true` _to regenerate; the build fails if this file drifts from the code._

## Effects

The actions an ability runs. Each is a `HEAD:args` token in an enchant/set/crystal's `effects:` list.

### BREAK_BLOCK

Break the block at the activation location (drops=false clears it). No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `BREAK_BLOCK[:drops:bool=true]`
- _param_ `drops` `bool`
- _example_: `BREAK_BLOCK:true`

### CANCEL

Cancel the Bukkit event that triggered this activation.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `CANCEL`
- _example_: `CANCEL`

### CURE

Clear every active potion effect from the target(s).

- _affinity_: `TARGET_ENTITY`
- _usage_: `CURE`
- _target_ `who`: selector `SELF`
- _example_: `CURE`

### DAMAGE

Deal a flat amount of extra damage to the target.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `DAMAGE:<amount:double[0..]>`
- _param_ `amount` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `DAMAGE:6`

### DAMAGE_MOD

Contribute to the damage fold: side attack/defense, mode add (percent 0-100) or flat (raw amount). Replaces ADD_DAMAGE/REDUCE_DAMAGE/FLAT_DAMAGE/FLAT_REDUCE.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `DAMAGE_MOD:<side:enum{attack|defense}>[:mode:enum{add|flat}=add]:<amount:double[0..]>`
- _param_ `side` `enum{attack|defense}`
- _param_ `mode` `enum{add|flat}`
- _param_ `amount` `double[0..]`
- _example_: `DAMAGE_MOD:attack:add:25`

### DISARM

Make the target(s) drop their held (main-hand) item.

- _affinity_: `TARGET_ENTITY`
- _usage_: `DISARM`
- _target_ `who`: selector `VICTIM`
- _example_: `DISARM`

### DROP_ITEM

Drop a material as an item at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `DROP_ITEM:<material:material>[:count:int[1..]=1]`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _example_: `DROP_ITEM:DIAMOND:1`

### DURABILITY

Modify durability of the player's held item and/or worn armor: restore (amount<0 = full) or damage. Replaces ADD_DURABILITY/ADD_DURABILITY_ITEM/REPAIR/DAMAGE_ARMOR.

- _affinity_: `TARGET_ENTITY`
- _usage_: `DURABILITY[:amount:int=-1][:target:enum{item|armor|all}=item][:mode:enum{restore|damage}=restore]`
- _param_ `amount` `int` — durability points; negative fully restores (restore mode)
- _param_ `target` `enum{item|armor|all}`
- _param_ `mode` `enum{restore|damage}`
- _target_ `who`: selector `SELF`
- _example_: `DURABILITY:-1:item`

### EXPLODE

Create an explosion at the target.

- _affinity_: `REGION`
- _usage_: `EXPLODE:<power:double[0..]>[:breakBlocks:bool=false]`
- _param_ `power` `double[0..]`
- _param_ `breakBlocks` `bool`
- _target_ `who`: selector `VICTIM`
- _example_: `EXPLODE:4:false`

### EXTINGUISH

Put out the target's fire.

- _affinity_: `TARGET_ENTITY`
- _usage_: `EXTINGUISH`
- _target_ `who`: selector `SELF`
- _example_: `EXTINGUISH`

### FILL_OXYGEN

Refill the target's air supply.

- _affinity_: `TARGET_ENTITY`
- _usage_: `FILL_OXYGEN`
- _target_ `who`: selector `SELF`
- _example_: `FILL_OXYGEN`

### FIREWORK

Spawn a cosmetic firework at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `FIREWORK[:power:int[0..3]=1]`
- _param_ `power` `int[0..3]`
- _example_: `FIREWORK:1`

### FLY

Grant the player temporary flight.

- _affinity_: `TARGET_ENTITY`
- _usage_: `FLY[:ticks:ticks[0..]=200]`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `FLY:200`

### GIVE_ITEM

Give a material to the player target(s); overflow drops at their feet.

- _affinity_: `TARGET_ENTITY`
- _usage_: `GIVE_ITEM:<material:material>[:count:int[1..]=1]`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `GIVE_ITEM:DIAMOND:1:@Self`

### GUARD

Summon count guardian mobs of type at the activation location, each targeting the attacker, auto-removed after ttl ticks (default 200; 0 = permanent); optional custom name. A targeted SPAWN_ENTITY for retaliation — author on DEFENSE.

- _affinity_: `REGION`
- _usage_: `GUARD:<type:entity_type>[:count:int[1..]=1][:ttl:ticks[0..]=200][:name:string=]`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `name` `string`
- _target_ `who`: selector `ATTACKER`
- _example_: `GUARD:IRON_GOLEM:1:200:&bGuardian`

### HEALTH

Add to the target's maximum health (restored on unequip).

- _affinity_: `TARGET_ENTITY`
- _usage_: `HEALTH:<amount:double[0..]>`
- _param_ `amount` `double[0..]`
- _target_ `who`: selector `SELF`
- _example_: `HEALTH:4`

### IGNITE

Set the target(s) on fire for a duration in ticks.

- _affinity_: `TARGET_ENTITY`
- _usage_: `IGNITE:<duration:ticks[0..]>`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `IGNITE:60`

### IGNORE_ARMOR

Make the triggering hit ignore the victim's armor and enchant-protection reduction.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `IGNORE_ARMOR`
- _example_: `IGNORE_ARMOR`

### INVERT_VAR

Numerically invert a per-player variable (0↔1), preserving its remaining TTL.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `INVERT_VAR:<name:string>`
- _param_ `name` `string`
- _target_ `who`: selector `SELF`
- _example_: `INVERT_VAR:rage:@Self`

### INVINCIBLE

Make the target invulnerable for a span of ticks, then restore.

- _affinity_: `TARGET_ENTITY`
- _usage_: `INVINCIBLE[:ticks:ticks[0..]=100]`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `INVINCIBLE:100`

### KEEP_ON_DEATH

Keep the target's items + levels (no drops) if they die within duration ticks (default 200). Author on trigger REPEATING for an always-on death-keep while worn, or fire on a trigger for a timed grace window. A kept death never spends a holy scroll.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `KEEP_ON_DEATH[:duration:ticks[0..]=200]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `KEEP_ON_DEATH:200`

### KILL

Instantly kill the target.

- _affinity_: `TARGET_ENTITY`
- _usage_: `KILL`
- _target_ `who`: selector `VICTIM`
- _example_: `KILL`

### KNOCKBACK_CONTROL

Scale the target's incoming knockback for duration ticks: 0 cancels it, 0.5 halves it, 2 doubles it (default: cancel for 2 ticks). Use on DEFENSE for your own knockback, or on ATTACK with who: victim for the knockback you deal.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `KNOCKBACK_CONTROL[:multiplier:double[0..]=0][:duration:ticks[0..]=2]`
- _param_ `multiplier` `double[0..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `KNOCKBACK_CONTROL:0`

### LIGHTNING

Strike the target(s) with lightning, optionally dealing extra damage (0 = cosmetic).

- _affinity_: `TARGET_ENTITY`
- _usage_: `LIGHTNING[:damage:double[0..]=0]`
- _param_ `damage` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `LIGHTNING:6`

### MESSAGE

Send feedback to the activating player on a channel: chat (default), actionbar, or title (with subtitle + fade/stay/fade timings). Replaces ACTIONBAR/TITLE.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `MESSAGE:<text:string>[:channel:enum{chat|actionbar|title}=chat][:subtitle:string=][:fadeIn:ticks[0..]=10][:stay:ticks[0..]=70][:fadeOut:ticks[0..]=20]`
- _param_ `text` `string`
- _param_ `channel` `enum{chat|actionbar|title}`
- _param_ `subtitle` `string` — title channel only
- _param_ `fadeIn` `ticks[0..]` — title channel only
- _param_ `stay` `ticks[0..]` — title channel only
- _param_ `fadeOut` `ticks[0..]` — title channel only
- _example_: `MESSAGE:&aCritical hit!`

### MODIFY_EXP

Modify a player target's experience: give to them, take from them, or transfer (take from the target and grant the total to the activator). Replaces GIVE_EXP.

- _affinity_: `TARGET_ENTITY`
- _usage_: `MODIFY_EXP:<amount:int[0..]>[:mode:enum{give|take|transfer}=give]`
- _param_ `amount` `int[0..]`
- _param_ `mode` `enum{give|take|transfer}`
- _target_ `who`: selector `SELF`
- _example_: `MODIFY_EXP:50:give:@Self`

### MODIFY_FOOD

Modify a player target's hunger: give food points (clamped to 20) or take them (clamped to 0). Replaces FEED.

- _affinity_: `TARGET_ENTITY`
- _usage_: `MODIFY_FOOD:<amount:int[0..]>[:mode:enum{give|take}=give]`
- _param_ `amount` `int[0..]`
- _param_ `mode` `enum{give|take}`
- _target_ `who`: selector `SELF`
- _example_: `MODIFY_FOOD:6:give:@Self`

### MODIFY_HEALTH

Modify a target's health: give heals them, take deals direct health damage, transfer (lifesteal) damages the target and heals the activator by the same amount. Replaces HEAL.

- _affinity_: `TARGET_ENTITY`
- _usage_: `MODIFY_HEALTH:<amount:double[0..]>[:mode:enum{give|take|transfer}=give]`
- _param_ `amount` `double[0..]`
- _param_ `mode` `enum{give|take|transfer}`
- _target_ `who`: selector `SELF`
- _example_: `MODIFY_HEALTH:4:give:@Self`

### MODIFY_MONEY

Modify a player target's balance: give to them, take from them, or transfer (take from the target and give the total to the activator). Replaces GIVE_MONEY/TAKE_MONEY/STEAL_MONEY.

- _affinity_: `TARGET_ENTITY`
- _usage_: `MODIFY_MONEY:<amount:double[0..]>[:mode:enum{give|take|transfer}=give]`
- _param_ `amount` `double[0..]`
- _param_ `mode` `enum{give|take|transfer}`
- _target_ `who`: selector `SELF`
- _example_: `MODIFY_MONEY:100:give:@Self`

### MOVEMENT_SPEED

Set the player target's walk speed for a span of ticks, then restore the default (0.2).

- _affinity_: `TARGET_ENTITY`
- _usage_: `MOVEMENT_SPEED:<speed:double[-1..1]>[:ticks:ticks[0..]=200]`
- _param_ `speed` `double[-1..1]`
- _param_ `ticks` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `MOVEMENT_SPEED:0.4:200`

### PARTICLE

Spawn particles at the activation location. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `PARTICLE:<particle:particle>[:count:int[0..]=1]`
- _param_ `particle` `particle`
- _param_ `count` `int[0..]`
- _example_: `PARTICLE:FLAME:20`

### POTION

Apply a potion effect to the target(s) at the given LEVEL (1-based: level 1 = the I tier), for a duration in ticks. The effect name is resolved to a handle at compile time. On a HELD/PASSIVE source it is removed again when the item is unequipped (§B lifecycle).

- _affinity_: `TARGET_ENTITY`
- _usage_: `POTION:<effect:potion_effect>:<level:int[1..]>:<duration:ticks[0..]>`
- _param_ `effect` `potion_effect`
- _param_ `level` `int[1..]`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `POTION:STRENGTH:1:100`

### PROJECTILE

Launch count projectiles of a type from the activator's eye (covers SPAWN_ARROWS as PROJECTILE:ARROW).

- _affinity_: `TARGET_ENTITY`
- _usage_: `PROJECTILE:<type:entity_type>[:count:int[1..]=1][:speed:double[0..]=1.5]`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `speed` `double[0..]`
- _example_: `PROJECTILE:ARROW:3:1.5`

### REMOVE_ITEM

Remove up to count of a material from the player target(s)' inventory.

- _affinity_: `TARGET_ENTITY`
- _usage_: `REMOVE_ITEM:<material:material>[:count:int[1..]=1]`
- _param_ `material` `material`
- _param_ `count` `int[1..]`
- _target_ `who`: selector `SELF`
- _example_: `REMOVE_ITEM:DIAMOND:1:@Self`

### REMOVE_POTION

Remove a potion effect from the target(s).

- _affinity_: `TARGET_ENTITY`
- _usage_: `REMOVE_POTION:<effect:potion_effect>`
- _param_ `effect` `potion_effect`
- _target_ `who`: selector `SELF`
- _example_: `REMOVE_POTION:POISON`

### REMOVE_SOULS

Debit souls from the activator's active soul gem (a no-op when they are not in soul mode).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `REMOVE_SOULS:<amount:int[1..]>`
- _param_ `amount` `int[1..]`
- _example_: `REMOVE_SOULS:5`

### RUN_COMMAND

Run a command from the console. Affinity GLOBAL — runs on the global thread.

- _affinity_: `GLOBAL`
- _usage_: `RUN_COMMAND:<command:string>`
- _param_ `command` `string`
- _example_: `RUN_COMMAND:eco give %player% 100`

### SET_BLOCK

Set the block at the activation location to a material. No-op if there is no location.

- _affinity_: `REGION`
- _usage_: `SET_BLOCK:<material:material>`
- _param_ `material` `material`
- _example_: `SET_BLOCK:OBSIDIAN`

### SET_VAR

Set a per-player variable readable in later conditions as %name% (ttl ticks, 0 = forever).

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `SET_VAR:<name:string>[:value:string=][:ttl:ticks[0..]=0]`
- _param_ `name` `string`
- _param_ `value` `string`
- _param_ `ttl` `ticks[0..]`
- _target_ `who`: selector `SELF`
- _example_: `SET_VAR:rage:1:200:@Self`

### SOUND

Play a sound at the activation location. No-op if the activation has no location.

- _affinity_: `REGION`
- _usage_: `SOUND:<sound:sound>[:volume:double[0..]=1][:pitch:double[0..]=1]`
- _param_ `sound` `sound`
- _param_ `volume` `double[0..]`
- _param_ `pitch` `double[0..]`
- _example_: `SOUND:ENTITY_GENERIC_EXPLODE:1:1`

### SPAWN_ENTITY

Spawn count entities of type at the target's (or activation) location; ttl ticks until removal (0 = permanent), optional starting health. Replaces SPAWN/TNT.

- _affinity_: `REGION`
- _usage_: `SPAWN_ENTITY:<type:entity_type>[:count:int[1..]=1][:ttl:ticks[0..]=0][:health:double[0..]=0]`
- _param_ `type` `entity_type`
- _param_ `count` `int[1..]`
- _param_ `ttl` `ticks[0..]`
- _param_ `health` `double[0..]`
- _target_ `who`: selector `SELF`
- _example_: `SPAWN_ENTITY:ZOMBIE:3:0:20`

### SUPPRESS

Disable a target's enchant/group/type (the key) for a duration in ticks (DISABLE_ENCHANT/GROUP/TYPE). Default target the combat victim.

- _affinity_: `CONTEXT_LOCAL`
- _usage_: `SUPPRESS:<scope:enum{ENCHANT|GROUP|TYPE}>:<key:string>[:duration:ticks[0..]=200]`
- _param_ `scope` `enum{ENCHANT|GROUP|TYPE}`
- _param_ `key` `string`
- _param_ `duration` `ticks[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `SUPPRESS:GROUP:lifesteal:200:@Victim`

### TELEPORT

Teleport the target to the actor's or the victim's location.

- _affinity_: `TARGET_ENTITY`
- _usage_: `TELEPORT[:to:enum{VICTIM|ACTOR}=VICTIM]`
- _param_ `to` `enum{VICTIM|ACTOR}` — destination party: the victim or the actor
- _target_ `who`: selector `SELF`
- _example_: `TELEPORT:VICTIM`

### VELOCITY

Apply velocity to the target(s): mode=add uses x/y/z; mode=away knocks them back from the activator with strength. Replaces THROW/LAUNCH/KNOCKBACK.

- _affinity_: `TARGET_ENTITY`
- _usage_: `VELOCITY[:mode:enum{add|away}=add][:x:double=0][:y:double=0][:z:double=0][:strength:double[0..]=0]`
- _param_ `mode` `enum{add|away}`
- _param_ `x` `double`
- _param_ `y` `double`
- _param_ `z` `double`
- _param_ `strength` `double[0..]`
- _target_ `who`: selector `VICTIM`
- _example_: `VELOCITY:add:0:1.2:0`

### WALKER

Lay a temporary platform of a material under the target for a duration (then revert), out to a radius. replace = AIR_ONLY | REPLACEABLE (air/liquid) | ANY.

- _affinity_: `REGION`
- _usage_: `WALKER:<material:material>[:ticks:ticks[0..]=60][:radius:int[0..4]=1][:replace:enum{AIR_ONLY|REPLACEABLE|ANY}=REPLACEABLE]`
- _param_ `material` `material`
- _param_ `ticks` `ticks[0..]`
- _param_ `radius` `int[0..4]`
- _param_ `replace` `enum{AIR_ONLY|REPLACEABLE|ANY}`
- _target_ `who`: selector `SELF`
- _example_: `WALKER:ICE:80:1`

## Selectors

Choose WHO an effect targets (`@Self`, `@Victim`, `@Aoe`, …). Routing is the effect's; a selector carries no affinity.

### AOE

Living entities within r blocks of the target, except the activator; optionally filtered and capped.

- _usage_: `AOE[:r:double[0..]=4][:filter:enum{ALL|PLAYERS|MONSTERS|MOBS}=ALL][:limit:int[0..]=0]`
- _param_ `r` `double[0..]` — radius in blocks
- _param_ `filter` `enum{ALL|PLAYERS|MONSTERS|MOBS}` — which entities to include
- _param_ `limit` `int[0..]` — max targets, nearest first (0 = unlimited)
- _example_: `@Aoe{r=6, filter=MONSTERS}`

### ATTACKER

The entity that damaged the activator (for defensive effects).

- _usage_: `ATTACKER`
- _example_: `@Attacker`

### NEAREST

The single nearest living entity within r blocks (optionally filtered), except the activator.

- _usage_: `NEAREST[:r:double[0..]=5][:filter:enum{ALL|PLAYERS|MONSTERS|MOBS}=ALL]`
- _param_ `r` `double[0..]` — search radius in blocks
- _param_ `filter` `enum{ALL|PLAYERS|MONSTERS|MOBS}` — which entities to consider
- _example_: `@Nearest{r=5, filter=PLAYERS}`

### SELF

The activating player themself.

- _usage_: `SELF`
- _example_: `@Self`

### VICTIM

The combat victim (the entity the activator hit).

- _usage_: `VICTIM`
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
| `%actor.helditem%` | STR |
| `%actor.level%` | NUM |
| `%actor.maxhealth%` | NUM |
| `%actor.totalexp%` | NUM |
| `%actor.world%` | STR |
| `%blocking%` | BOOL |
| `%combo%` | NUM |
| `%damage%` | NUM |
| `%flying%` | BOOL |
| `%gliding%` | BOOL |
| `%sneaking%` | BOOL |
| `%sprinting%` | BOOL |
| `%swimming%` | BOOL |
| `%victim.blocking%` | BOOL |
| `%victim.flying%` | BOOL |
| `%victim.health%` | NUM |
| `%victim.maxhealth%` | NUM |
| `%victim.sneaking%` | BOOL |
| `%victim.type%` | STR |
