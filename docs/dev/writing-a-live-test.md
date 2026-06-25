# Writing a live test

A **live test** verifies behavior against the **real game**, not mocks: an
in-server harness boots inside an actual Paper (and Folia) server, stages a
scenario, asserts the resulting server-side state, writes PASS/FAIL, and the
matrix fails the build on anything but a fresh PASS. Unit tests pin pure logic
(DSL parsing, math, interaction rules); the live suite pins that the engine
actually does the right thing to the world.

Read [verification-gate.md](verification-gate.md) first for how to *run* the
matrix; this doc is about *writing* a suite.

## When to write a live test vs. a unit test

- **Unit** (`./gradlew build`): DSL parse round-trips, chance/cooldown math,
  interaction-precedence resolution, config-snapshot parsing, resolver alias maps,
  and effect kinds via a mocked `Sink`.
- **Live**: an ATTACK enchant actually applies its effect on hit; an armor-set
  bonus turns on at the required piece count and off when a piece is removed;
  applying a book/scroll/crystal mutates item PDC **and** rendered lore; souls
  drain; slots gate; a GUI click performs its action; `DISABLE_ENCHANT` actually
  suppresses; integrations gate in protected regions.

If a synthetic event or a mocked object can prove it, write the unit test — it is
faster and runs on every `./gradlew build`. Reach for live only when the truth
lives in the real server.

## The `tester` module

Live suites live in the test-only `se/tester/` module (it is never shipped). The
shape:

- `se/tester/src/tester/SeTesterPlugin.java` — the harness plugin. On enable it
  probes `Capabilities`, initializes `Scheduling`, and registers every suite; it
  starts the suites on `ServerLoadEvent` (not `onEnable` — mid-startup the world is
  still loading and a freshly-spawned entity may not survive a few ticks).
- `se/tester/src/tester/harness/Harness.java` — the suite driver. You `expect(name)`
  each check, then resolve it with `pass(name)`, `fail(name, reason)`, or
  `guard(name, body)` (runs `body` and records PASS if it returns, FAIL if it
  throws — used inside a scheduled callback so a wrong-thread access on Folia is
  captured as a failure, not a silent stall). At the deadline, any unresolved
  expectation fails, and the harness writes a fresh `test-results.txt` /
  `test-failures.txt` and shuts the server down cleanly.
- `se/tester/src/tester/suite/*Suite.java` — one suite per area
  (`CombatSuite`, `SetSuite`, `CrystalSuite`, `GuiSuite`, `SoulSuite`,
  `TriggerSuite`, …). A suite implements `Harness.Scenario`
  (`Consumer<Harness>`): declare expectations, stage the scenario, resolve.

`SeTesterPlugin` is the registry of suites — adding a suite is one `.add(new
YourSuite(this))` line there. Copy the nearest sibling suite; `TriggerSuite` is a
clean, complete example of the full path.

## The clientless fake-player harness

The keystone that lets a headless matrix verify anything needing a real player
(combat triggers, equipment, GUIs) is `se/tester/src/tester/fake/FakePlayers.java`
— it spawns a clientless, NMS-backed `org.bukkit.entity.Player` with **no game
client**:

```java
Player p = FakePlayers.spawn(world, "se_combat_attacker");
// ... stage and assert ...
FakePlayers.despawn(p);   // best-effort teardown; never fails a test
```

It works across the whole range via two construction paths chosen once by probing
the runtime mapping: a mojang-mapped path (1.20.6 → 26.1.x) and a spigot-mapped
floor path (1.17.1 / 1.18.2 / 1.19.4). You don't need to know the reflection — but
you **do** need to respect the contract and its pitfalls:

- **`spawn` registers the player server-wide, so it must run on the world's owning
  thread** — the main thread on Paper, the **global region** on Folia. Route it
  through `Scheduling` (`Scheduling.onGlobal(...)` then `Scheduling.onRegion(at,
  ...)`), as `TriggerSuite` does.
- **Fake players join directly in Play state** — they never send
  handshake/login/configuration traffic. So packet/early-join logic that misbehaves
  *pre-Play* passes every live suite and explodes on real joins; pin that contract
  in **unit** tests against synthetic events, not here.
- **No client-side motion.** Effects that depend on client movement (knockback
  *feel*) cannot be asserted by position. **Assert the server-side state change
  instead**: a potion present, fire ticks set, a health delta, item NBT changed, an
  event fired.
- The fake connection **voids all outbound traffic** and `spawn` **clears
  join/spawn protection** — both are handled inside `FakePlayers`; just be aware
  that is why a clientless player survives and never wedges the tick thread.

## Staging a scenario

Walk `TriggerSuite` (`se/tester/src/tester/suite/TriggerSuite.java`) for the
pattern; the moving parts:

1. **Compile content for the scenario.** Build small inline YAML enchants, write
   them to a temp dir, and run them through the real production compiler
   (`ContentCompiler.production(resolvers)` + `LibraryLoader.load(...)`). Check
   `library.hasErrors()` and fail with the diagnostics if it didn't compile — that
   catches handle-name typos on *this* version.
2. **Resolve version-volatile referents through the live resolver** (e.g.
   `RuntimeHandles#resolveByName` for a `PotionEffectType`), and fail clearly if a
   referent doesn't resolve on the running version rather than NPE-ing later.
3. **Build the runtime pieces** (codec, `ItemViewCache`, `WornStateStore`,
   `AbilityExecutor`, dispatch) and register the real listeners.
4. **Stage on the right threads.** `Scheduling.onGlobal` to force-load the chunk
   and spawn the fake player, `Scheduling.onRegion(at, ...)` for region-owned work,
   `Scheduling.onEntity(player, ...)` to mutate the player, and
   `Scheduling.onEntityLater(player, ticks, ...)` to assert **after** the effect
   has had time to apply.
5. **Assert server-side state** inside `h.guard(name, () -> { ... })` so a thrown
   wrong-thread/wrong-region access becomes a recorded FAIL.

## Suite rules (the flake-proofing)

These are not style preferences — each one fixes a real class of matrix flake:

- **Wait in GAME ticks**, never wall time (`Scheduling.*Later`, await a
  condition). Wall time lies under concurrent matrix load.
- **Fresh actors per scenario**; despawn players and unregister listeners in a
  `finally`-style teardown — residual item/cooldown/soul state contaminates the
  next case.
- **Reset captors immediately before the staged action**, then await the
  condition — spawn/equip emit their own events that a fixed wait races.
- **Pin event COUNTS, not just values** — a suppressed/blocked activation can
  otherwise pass vacuously on setup state alone.
- **Sanitize the arena**: peaceful difficulty, no mob spawning, purge nearby mobs
  each prepare — a wandering zombie's hit reads like a phantom enchant activation.

## Folia: region ownership is the whole point

Run the **same** suites on a real Folia server, not just Paper. Cross-region
effects (AoE, steal-between-players, teleport-to-target) only reveal wrong-thread
bugs on Folia. Stage actors with attention to **region ownership**, and schedule
every staged mutation through the same `Scheduling` abstraction the plugin uses —
so the test exercises the production threading model, not a side door. `guard`
turns a wrong-region access (which throws on Folia) into a clear FAIL instead of a
hang. **A green Paper run does not imply Folia.** See
[internals/folia-scheduling.md](internals/folia-scheduling.md) for the threading
model.
