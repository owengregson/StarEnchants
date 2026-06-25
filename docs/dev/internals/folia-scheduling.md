# Folia scheduling

Folia shards the world into independently-ticked regions, each on its own
thread. There is **no single main thread**. Code that assumes one — most Bukkit
plugins, including legacy enchant and armour plugins — throws
`IllegalStateException: cannot access … from the wrong region/thread` or, worse,
silently corrupts state on Folia. StarEnchants must be correct on Paper **and**
Folia from one jar. The rule that makes this work: all entity, block, world, and
inventory mutation goes through one `Scheduling` abstraction, never
`Bukkit.getScheduler()`. This document maps `se/platform/src/platform/sched` and
the `se/compat-folia` backend.

It implements [`docs/architecture.md`](../../architecture.md) §3.6 and §9 and
[ADR-0008](../../decisions/0008-cross-version-and-folia.md)
(cross-version + Folia).

> For the version half of `se/platform`, see
> [cross-version-api.md](cross-version-api.md). The cross-region victim read this
> doc describes is produced by the `WornState` design in
> [item-data-model.md](item-data-model.md).

## Where it lives

| Concern | File |
| --- | --- |
| The static scheduling entry point | `se/platform/src/platform/sched/Scheduling.java` |
| The backend strategy interface | `se/platform/src/platform/sched/SchedulerBackend.java` |
| Paper / Spigot backend | `se/platform/src/platform/sched/BukkitSchedulerBackend.java` |
| Cancellable handle | `se/platform/src/platform/sched/TaskHandle.java` |
| Folia backend (loaded reflectively) | `se/compat-folia/src/compatfolia/FoliaSchedulerBackend.java` |
| Folia / version probe | `se/platform/src/platform/caps/Capabilities.java` |

## The thread model: what owns what

Folia's schedulers map to four task *owners*. The abstraction mirrors them
exactly:

| Owner | Use for | Folia thread |
| --- | --- | --- |
| Global | world day-time, weather, server-wide timers, registries | the global region thread |
| Region | a location/chunk with no entity in hand | the thread ticking that location |
| Entity | anything done *to* an entity (the common case) | the entity's region; follows it across regions, teleports, dimensions |
| Async | I/O, no Bukkit API | any thread |

On Paper all four collapse to the main thread, so a single abstraction compiles
and runs identically on both.

## The entry point

`Scheduling` (`se/platform/src/platform/sched/Scheduling.java`) is a static
facade over a `SchedulerBackend`. `Bukkit.getScheduler()` is *removed* from the
plugin's reach (lint-enforced to one file), so an effect author cannot
accidentally write a Folia bug. The surface is one owner family of methods:

```java
// entity work — region-correct on Folia, main-thread on Paper, the SAME code
Scheduling.onEntity(player, () -> player.addPotionEffect(effect));
Scheduling.onEntityLater(target, 20L, () -> target.setFireTicks(0));
Scheduling.onRegion(location, () -> world.strikeLightning(location));
Scheduling.onGlobal(() -> world.setStorm(true));
Scheduling.async(() -> economy.deposit(uuid, amount));   // no Bukkit API inside
```

Each owner has an immediate, a `…Later(delayTicks, …)`, and a
`repeating…(initialDelayTicks, periodTicks, …)` form. Only the `repeating…`
methods return a `TaskHandle`; the one-shot forms return `void`. Every facade
method simply delegates to the installed backend, which throws if scheduling is
used before `Scheduling.init(plugin, capabilities)` runs.

## Picking the backend

The choice is made once in `Scheduling#init`, driven entirely by
`Capabilities.folia()` — `Scheduling` itself does no probing:

```java
// se/platform/src/platform/sched/Scheduling.java#init
backend = capabilities.folia() ? loadFoliaBackend(plugin) : new BukkitSchedulerBackend(plugin);
```

The Folia backend is instantiated **reflectively** by fully-qualified name
(`compatfolia.FoliaSchedulerBackend`), so its Folia-API-referencing class is
never linked on Paper. The probe that decides this is the class probe in
`Capabilities` for `io.papermc.paper.threadedregions.RegionizedServer` (see
[cross-version-api.md](cross-version-api.md)). Note the two distinct class names:
the **detection marker** `RegionizedServer` and the **reflectively-loaded
backend** `compatfolia.FoliaSchedulerBackend`.

## The Folia backend

`FoliaSchedulerBackend` (`se/compat-folia/src/compatfolia/FoliaSchedulerBackend.java`)
dispatches each owner to its Folia scheduler. Entity work goes to the entity's
own scheduler, with a `null` retired-callback meaning "drop the task if the
entity is gone":

```java
// se/compat-folia/src/compatfolia/FoliaSchedulerBackend.java
@Override
public void onEntity(Entity entity, Runnable task) {
    // null retired-callback: drop the task if the entity is gone before it runs.
    entity.getScheduler().run(plugin, consume(task), null);
}

@Override
public void onRegion(Location location, Runnable task) {
    Bukkit.getRegionScheduler().execute(plugin, location, task);
}

@Override
public void onGlobal(Runnable task) {
    Bukkit.getGlobalRegionScheduler().execute(plugin, task);
}

@Override
public void async(Runnable task) {
    Bukkit.getAsyncScheduler().runNow(plugin, consume(task));
}
```

Two adapters bridge the API gap: Folia's callbacks take
`Consumer<ScheduledTask>`, so `consume` wraps a `Runnable`, and Folia rejects
sub-tick delays, so `atLeastOne` floors them to one tick. The only Folia import
in the whole module is
`io.papermc.paper.threadedregions.scheduler.ScheduledTask`.

## The Paper backend

`BukkitSchedulerBackend` (`se/platform/src/platform/sched/BukkitSchedulerBackend.java`)
is the floor-API backend and the *only* class allowed to call
`Bukkit.getScheduler()`. All three owner families ignore the `Entity`/`Location`
argument and collapse to the main thread, running inline when already on it to
avoid needless task churn:

```java
// se/platform/src/platform/sched/BukkitSchedulerBackend.java#runSync
if (Bukkit.isPrimaryThread()) {
    task.run();
} else {
    Bukkit.getScheduler().runTask(plugin, task);
}
```

`async` is the one genuinely off-thread path
(`runTaskAsynchronously`), identical in intent to the Folia async scheduler.

## Task handles

`TaskHandle` (`se/platform/src/platform/sched/TaskHandle.java`) hides the native
task type (`BukkitTask` vs Folia `ScheduledTask`) behind `cancel()` (idempotent)
and `isCancelled()`. A `TaskHandle.CANCELLED` sentinel represents a task that
never started — the Folia backend returns it when the scheduler returns `null`
(the entity or region was already gone), so callers never get a null handle. On
Folia a repeating task may also stop on its own when its owning entity is removed
or its region unloads; `isCancelled()` reflects that as well as an explicit
`cancel()`.

## Folia traps for an enchant / armour plugin

These are the concrete failure modes the abstraction guards against.

- **Event handlers fire on the firing region's thread, not "main".** Inside a
  combat / interact / break listener you may touch the event's *own* entity or
  block synchronously, but touching a *different* entity — an AoE arc, a wrath
  burst, a throw, rain hitting bystanders — requires hopping to each target's
  scheduler with `Scheduling.onEntity(target, …)`.
- **Cross-entity effects** (steal exp/money between two players, teleport to a
  hit target, spawn a guardian near another player) must schedule on the *other*
  entity's thread; never read its location or inventory cross-region.
- **The cross-region victim read.** An attacker on one region needs the victim's
  defensive state. Do not read the victim's live equipment — read the immutable
  `WornState` the victim's own thread already resolved and stored
  (`WornStateStore#get`). It is a lock-free snapshot, safe from any region
  thread. This is why `WornState` is immutable and pre-flattened (see
  [item-data-model.md](item-data-model.md)).
- **Teleports are async on Folia.** Do not assume a player is at the destination
  on the next line.
- **No global mutable combat state** read/written from multiple region threads
  without concurrency-safe structures — cooldowns, soul-mode flags, and
  disabled-enchant timers must be concurrent maps keyed by UUID.
- **Chunk / entity lookups** (`getNearbyEntities`, falling-block spawns) must run
  on the region owning that location (`Scheduling.onRegion`).

## Gotchas

- **Never call `Bukkit.getScheduler()` outside `BukkitSchedulerBackend`.** It is
  lint-enforced to that one file; the abstraction is the only legal path.
- **Pick the owner by what the task touches, not by convenience.** Work *on* an
  entity is `onEntity`; work at a *location* is `onRegion`; I/O is `async`.
- **`async` must touch no Bukkit API.** It runs off any game thread on both
  platforms.
- **A green Paper run proves nothing about Folia.** Folia behaviour is verified
  on a real Folia server in the test matrix — run both
  ([`docs/development.md`](../../development.md)).
