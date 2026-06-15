---
name: folia-scheduling
description: Use when any StarEnchants code touches entities, blocks, worlds, inventories, or timers and must run on BOTH Paper and Folia — covers Folia's region/entity/global thread model and the scheduling abstraction that makes one codebase correct on both.
---

# Folia-correct scheduling

Folia shards the world into independently-ticked regions, each on its own
thread. There is NO single main thread. Code that assumes one (most Bukkit
plugins, including EE/EA) throws `IllegalStateException: cannot access ... from
the wrong region/thread` or silently corrupts state on Folia. StarEnchants
must be correct on Paper AND Folia from one jar.

## The thread model (what owns what)

| Scheduler | Use for | Folia thread |
| --- | --- | --- |
| GlobalRegion | world-day-time, weather, server-wide timers, registries | global thread |
| Region | a location/chunk with no entity in hand | the owning region |
| Entity | anything done TO an entity (the common case) | the entity's region; follows it across regions/teleports/dimensions |
| Async | I/O, no Bukkit API | any thread |

On Paper all of these collapse to the main thread — so a single abstraction
compiles and runs identically on both.

## The rule

**All entity/block/world/inventory mutation goes through a `Scheduling`
abstraction**, never `Bukkit.getScheduler()` directly. The abstraction detects
Folia once at load (class probe for `io.papermc.paper.threadedregions...`) and
dispatches to the Folia schedulers; on Paper it falls back to the Bukkit
scheduler.

```java
// entity work — region-correct on Folia, main-thread on Paper, same code
Scheduling.onEntity(player, () -> player.addPotionEffect(effect));
Scheduling.onEntityLater(target, 20L, () -> target.setFireTicks(0));
Scheduling.onRegion(location, () -> world.strikeLightning(location));
Scheduling.onGlobal(() -> world.setStorm(true));
Scheduling.async(() -> economy.deposit(uuid, amount)); // no Bukkit API inside
```

`runTaskTimer` repeating tasks become entity/region repeating tasks; cancel via
the returned handle, and accept that a task may stop when its entity unloads.

## Folia traps specific to an enchant/armor plugin

- **Event handlers fire on the firing region's thread, not "main".** Inside a
  combat/interact/break listener you may touch the event's own entity/block
  synchronously, but touching a DIFFERENT entity (e.g. an AoE `DAMAGE_ARC`,
  `WRATH`, `THROW`, `RAIN` hitting bystanders) requires hopping to each
  target's scheduler.
- **Cross-entity effects** (steal exp/money between two players, `TELEPORT` to a
  hit target, `SPAWN` a guardian near another player) must schedule on the
  OTHER entity's thread; never read its location/inventory cross-region.
- **Teleports are async on Folia** (`teleportAsync`) — never assume the player
  is at the destination on the next line.
- **No global mutable combat state** read/written from multiple region threads
  without concurrency-safe structures (cooldowns, soul-mode flags, disabled-
  enchant timers must be concurrent maps keyed by UUID).
- **Chunk/entity lookups** (`getNearbyEntities`, falling-block spawns) must run
  on the region owning that location.

## Verify, don't assume

Folia behavior is verified on a real Folia server in the test matrix
(`live-server-testing`, `matrix-gate`) — a feature that works on Paper proves
nothing about Folia. Run both.
