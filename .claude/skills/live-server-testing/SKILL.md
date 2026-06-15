---
name: live-server-testing
description: Use when writing or debugging StarEnchants integration tests that boot real Paper or Folia servers, or when a live suite is flaky, hangs, or passes on Paper but fails on Folia.
---

# Real-server integration testing

StarEnchants behavior is verified against the REAL game, not mocks: an
in-server tester plugin boots inside an actual Paper (and Folia) server,
stages scenarios, asserts outcomes, writes PASS/FAIL, and the build fails on
anything but PASS. This is the one practice we deliberately adopt from
Mental's approach — adapted to our domain (enchants, armor sets, items, GUIs).
Unit tests pin pure logic (DSL parsing, math, interaction rules); the live
suite pins that the engine actually does the right thing to the world.

## What to test live vs. unit

- **Unit (`common`)**: DSL parse round-trips, chance/cooldown math, interaction
  precedence resolution, config snapshot parsing, resolver alias maps.
- **Live**: an ATTACK enchant actually applies its effect on hit; an armor set
  bonus turns on at the required piece count and off when removed; applying a
  book/scroll/crystal mutates item PDC + rendered lore correctly; souls drain;
  slots gate; a GUI click performs its action; `DISABLE_ENCHANT` actually
  suppresses; integrations gate in protected regions.

## Fake-player harness facts (the pitfalls)

- Fake players are built on NMS and join **directly in Play state** — they
  never send handshake/login/configuration traffic. Packet/early-join logic
  that misbehaves pre-Play passes every live suite and explodes on real joins —
  pin that contract in UNIT tests against synthetic events.
- The fake connection must **VOID all outbound traffic** (release buffers,
  complete promises): the embedded channel is single-threaded and some server
  versions write to player connections cross-thread mid-tick; without the void
  handler the buffer corrupts and wedges the tick thread.
- Spawn must **clear join/spawn protection**, which lives in different fields
  across the range — treat "field stopped existing" as a relocation
  (`nms-archaeology`) and read state back off the live player object.
- Clientless players don't receive client-side movement; effects that depend on
  client motion (knockback feel) can't be asserted by position — assert the
  server-side state change instead (potion present, fire ticks set, health
  delta, item NBT changed, event fired).
- Pass `-Ddisable.watchdog=true` to run tasks: a slow suite tick can trip the
  legacy watchdog whose forced shutdown deadlocks old servers.

## Suite rules

- **Wait in GAME ticks** (`awaitTicks`/`awaitUntil(condition)`), never wall
  time; stamp timing with the server tick, not `System.nanoTime` — wall time
  lies under concurrent matrix load.
- **Fresh actors per scenario**; remove players and unregister listeners in
  `finally` — residual item/cooldown/soul state contaminates across cases.
- **Reset captors immediately before the staged action**, then await the
  condition — spawn/equip emit their own events that a fixed wait races.
- **Pin event COUNTS, not just values** — a suppressed/blocked activation can
  otherwise pass vacuously on setup state alone.
- **Sanitize the arena**: `difficulty peaceful`, disable mob spawning, purge
  nearby mobs each prepare — a wandering zombie's hit reads like a phantom
  enchant activation.

## Folia in the suite (non-negotiable)

Run the SAME suites on a real **Folia** server, not just Paper. Cross-region
effects (AoE, steal-between-players, teleport-to-target) only reveal
wrong-thread bugs on Folia. Stage actors with attention to region ownership;
schedule staged mutations through the same `Scheduling` abstraction the plugin
uses (`folia-scheduling`). A green Paper run does not imply Folia.
