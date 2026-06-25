# Developing a trigger

A **trigger** is the event family an ability fires on — an author's
`trigger: ATTACK` (or `MINE`, `DEFENSE`, `INTERACT`, `REPEATING`, …). Adding one
has two parts: declare the trigger in the registry (so the compiler and runtime
agree on its dense id), then bind a Bukkit event to it in the dispatch listener
set. The registry half is one line; the listener half is the only part that
touches Bukkit events.

This guide walks the real `MINE` and `INTERACT` triggers.

## The contract

A `TriggerKind` (`se/engine/src/engine/trigger/TriggerKind.java`) is a **pure
declaration** — the Bukkit-event-to-activation binding lives server-side, so the
vocabulary is unit-testable and add-ons can register triggers without a server.
It declares its DSL name and four routing facts:

```java
public interface TriggerKind {
    enum Direction { ATTACK, DEFENSE, NEUTRAL }
    String name();
    Direction direction();
    boolean usesHeld();         // read the ability from the HELD item only
    boolean scansEquipment();   // read from merged armor + main-hand
    boolean needsTarget();      // supplies a target entity for target-directed effects/selectors
}
```

| Fact | Decides |
| --- | --- |
| `direction` | which pre-flattened `WornState` array feeds it — `ATTACK` → `combatAttack`, `DEFENSE` → `combatDefense`, `NEUTRAL` → neither |
| `usesHeld` | the ability is read from the held item only (e.g. `HELD`, `BREAK`, `INTERACT`) |
| `scansEquipment` | the ability is read from the player's merged armor + main-hand (e.g. `ATTACK`, `MINE`) |
| `needsTarget` | the activation supplies a target, so `@Victim`-style selectors resolve |

These routing flags are the whole point: getting them right is what stops a
helmet enchant from firing on `ATTACK`. A `NEUTRAL` `MINE` that `scansEquipment`
but does **not** `needsTarget` reads worn gear and has no victim — correct for a
block break.

Most triggers are plain data, so you use the `Trigger` record
(`se/engine/src/engine/trigger/Trigger.java`) and its factory helpers rather than
writing a class:

```java
Trigger.attack("ATTACK")    // ATTACK, scans equipment, needs target
Trigger.defense("DEFENSE")  // DEFENSE, scans equipment, needs target
Trigger.neutral("MINE")     // NEUTRAL, scans equipment, no target
Trigger.held("INTERACT")    // NEUTRAL, reads the held item only, no target
```

Implement `TriggerKind` directly only for something the record can't express.

## Step 1 — register the trigger (and mind the id order)

Add one `.register(...)` line to
`se/engine/src/engine/trigger/BuiltinTriggers.java`. **Registration order is
canonical id order** — the compiler interns content `trigger:` names against this
list and bakes a `triggerMask` bit per trigger; the runtime routes the same bit.
So an existing trigger's id must never move.

> **Append new triggers at the end.** Inserting one mid-list shifts every later
> id and silently re-points every previously-compiled `triggerMask`. The
> existing `REPEATING` and `COMMAND` lines are explicitly appended last for this
> reason.

The real list shows the mix:

```java
                .register(Trigger.attack("ATTACK"))
                .register(Trigger.attack("BOW"))
                ...
                .register(Trigger.neutral("MINE"))
                ...
                .register(Trigger.held("INTERACT"))
                .register(Trigger.held("INTERACT_LEFT"))
                .register(Trigger.held("INTERACT_RIGHT"))
                // Appended last to keep prior ids unshifted.
                .register(Trigger.neutral("REPEATING"))
                .register(Trigger.neutral("COMMAND"))
```

There is a hard ceiling of `TriggerRegistry.MAX_TRIGGERS` (32) because the mask
is an `int`; the builder throws past it. The registry also exposes
`attackTriggers()` / `defenseTriggers()` predicates that the `WornFlattener` uses
to bucket abilities, and a `names()` list the compiler seeds its interner with.

## Step 2 — wire the dispatch listener

The trigger now *exists* but nothing fires it. A Bukkit listener maps a real
event to a dispatch call. Non-combat triggers are wired in
`se/feature/src/feature/trigger/TriggerListeners.java`; the attacker/defender
combat triggers (`ATTACK`/`DEFENSE` on `EntityDamageByEntityEvent`) are in
`feature/combat/CombatListener.java` + `CombatDispatch.java`.

A listener handler builds an `ActivationContext` (actor, victim, attacker,
location, plus optional damage and block payloads for facts) and calls the
matching `TriggerDispatch` method. The real `MINE` binding:

```java
@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
public void onMine(BlockBreakEvent event) {
    Player player = event.getPlayer();
    // the broken block backs the %block.type%/%isblock% facts (region-owned on this thread)
    dispatch.fireMine(player,
            new ActivationContext(player, null, null, event.getBlock().getLocation(), 0.0, event.getBlock()),
            event);
}
```

`INTERACT` shows direction fan-out from one event, and the off-hand de-dup:

```java
@EventHandler(priority = EventPriority.HIGH)
public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) {
        return; // one fire per interaction — the off-hand pass is a duplicate
    }
    Player player = event.getPlayer();
    ActivationContext context = new ActivationContext(player, null, null, player.getLocation());
    dispatch.fire(player, dispatch.interact, context, event);
    Action action = event.getAction();
    if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
        dispatch.fire(player, dispatch.interactLeft, context, event);
    } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
        dispatch.fire(player, dispatch.interactRight, context, event);
    }
}
```

`TriggerDispatch` resolves each trigger's interned id once at construction
(`this.mine = triggers.idOf("MINE").orElse(-1)`), so a `fire(..., id, ...)` for
an absent trigger is a no-op. Use the right `fire*` entry point for your event's
shape:

| Entry point | Use for |
| --- | --- |
| `fire(actor, id, ctx, cancellable)` | a neutral event — honours only a `cancelEvent` read-back |
| `fireDamage(actor, id, ctx, event, applyHeroic)` | a defender-side damage event — folds the damage deltas onto it |
| `fireMine(...)` / `fireBow(...)` | events with their own read-backs (smelt / teleport-drops, projectile homing) |

The handler runs on its firing region thread; the dispatch routes every mutation
through the `Sink`, so **no handler touches a cross-region entity directly** — it
captures the firing-thread actors into the `ActivationContext` and lets the
dispatcher route the work. Register your new listener with the others when the
plugin enables (the same place `TriggerListeners` is registered).

## Step 3 — a unit test for the declaration

Because the trigger is a pure declaration, the routing facts are unit-testable
with no server. Assert the registry assigns it an id and that its direction /
routing flags are right:

```java
@Test
void mineIsNeutralScansEquipmentNoTarget() {
    TriggerRegistry r = BuiltinTriggers.registry();
    int id = r.idOf("MINE").orElseThrow();
    TriggerKind mine = r.byId(id);

    assertEquals(TriggerKind.Direction.NEUTRAL, mine.direction());
    assertTrue(mine.scansEquipment());
    assertFalse(mine.needsTarget());
    assertFalse(r.isAttack(id));
}
```

The Bukkit-event binding itself is verified by the live integration suites, not a
unit test — booting a server and firing the real event is the only honest check
that the listener fires the right trigger with the right context.

## Step 4 — regenerate the docs

The reference's Triggers table (name + the four routing flags) is generated from
the registry by `ReferenceDoc`:

```bash
./gradlew regenDocs
```

`ReferenceDocDriftTest` fails the build if `docs/reference/dsl-reference.md`
drifts.

## Verify

```bash
./gradlew build
```

`build` covers the registry declaration and routing-flag tests plus the drift
test. A new trigger is the one case where the **live Paper + Folia matrix is
required, not optional**: the listener binds a real Bukkit event, the dispatch
runs on a region thread, and the only way to confirm it fires correctly — and
identically on Folia, where the firing thread is a region — is on real servers.
Run the matrix for any trigger change.

See also:

- [Effect engine internals](../internals/effect-engine.md) — the gate sequence,
  `WornState` per-trigger buckets, and the `triggerMask`.
- [Compiler and config internals](../internals/compiler-and-config.md) — how a
  `trigger:` name is interned to its dense id at compile.
- [Decision records](../../decisions/) — ADR-0011 (engine architecture),
  ADR-0022 (the HELD/PASSIVE lifecycle and the COMMAND trigger).
