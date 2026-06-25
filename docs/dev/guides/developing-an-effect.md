# Developing an effect

An **effect** is one action an ability runs (`IGNITE`, `DAMAGE_MOD`, `POTION`, …).
It is the most common thing you will add. Adding one is the project's "one
interface + one registration" pattern at its smallest: implement `EffectKind`,
declare an `EffectSpec`, add a single `.register(...)` line, write a mock-host
test, and regenerate the docs. No parser, no listener, no codegen.

This guide walks the real `IGNITE` effect end to end and points at
`DAMAGE_MOD` for the arbiter case.

## The contract

An `EffectKind` is **stateless** — one shared instance is reused across every
activation and every thread — and it **never touches an entity, block, world, or
scheduler**. It reads typed args and pre-resolved targets from an `EffectCtx` and
emits *intents* through the `Sink`. The `Sink` is the only mutation boundary; it
batches the intents and flushes them, routed to the correct Folia thread by the
ability's declared `Affinity`. Removing the scheduler door (rather than asking
you not to use it) is what makes Folia-correctness structural — a CI lint bans
`Bukkit.getScheduler()` and direct entity mutation inside `engine/effect/`.

The interface is tiny — `se/engine/src/engine/effect/EffectKind.java`:

```java
public interface EffectKind {
    EffectSpec spec();                       // self-describing signature
    void run(EffectCtx ctx, Sink sink);      // hot path: read args, emit intents
    default void stop(EffectCtx ctx, Sink sink) { }   // teardown for maintained buffs
}
```

`stop` is the deactivation half of the HELD/PASSIVE lifecycle: a maintained-buff
kind (e.g. `POTION`) overrides it to emit the inverse of `run`; a one-shot kind
(like `IGNITE`) leaves it a no-op. It is called unconditionally on unequip, so a
buff can never leak.

## Step 1 — write the class

Effects live in `se/engine/src/engine/effect/kind/`. Here is the entire real
`IgniteEffect.java`:

```java
package engine.effect.kind;

import compile.model.Affinity;
import engine.effect.EffectCtx;
import engine.effect.EffectKind;
import engine.sink.Sink;
import engine.spec.EffectSpec;
import engine.spec.T;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;

/** {@code IGNITE} — set the target(s) on fire for a duration in ticks (§7). */
public final class IgniteEffect implements EffectKind {

    static final EffectSpec SPEC = EffectSpec.of("IGNITE")
            .param("duration", D.TICKS)
            .target("who", T.VICTIM)
            .affinity(Affinity.TARGET_ENTITY)
            .doc("Set the target(s) on fire for a duration in ticks.")
            .example("IGNITE:60")
            .build();

    @Override
    public EffectSpec spec() {
        return SPEC;
    }

    @Override
    public void run(EffectCtx ctx, Sink sink) {
        int duration = ctx.integer("duration");
        for (LivingEntity target : ctx.targets("who")) {
            sink.ignite(target, duration);
        }
    }
}
```

That is the whole effect. Everything else is wiring.

## Step 2 — the EffectSpec (one declaration, four uses)

`EffectSpec` (`se/engine/src/engine/spec/EffectSpec.java`) wraps a schema
`ParamSpec` and adds the two facts a bare signature lacks: the **affinity** and
the **target slots**. The single declaration drives four things — typecheck and
range validation at compile, tab-completion, the `/se docs` and generated DSL
reference, and the migrator. They cannot drift because there is one source.

Declare params with the `D.` vocabulary
(`se/schema/src/schema/spec/D.java`):

| Builder call | What it accepts |
| --- | --- |
| `D.DOUBLE.min(0).max(100)` | a finite decimal, range-checked |
| `D.INT.min(0).def(0)` | a whole number with a default (optional param) |
| `D.TICKS` | a non-negative tick duration |
| `D.BOOL` | true/false/yes/no/on/off/1/0 |
| `D.enumOf("attack", "defense")` | a closed, case-insensitive value set |
| `D.material()` / `D.sound()` / `D.potionEffect()` / `D.particle()` / `D.entityType()` | a version-volatile token resolved to an interned id at compile (§9) |

A param with a `.def(...)` is optional; a param without one is required and a
missing argument is a `file:line:col` diagnostic at load, never a runtime
exception.

Declare a target slot with `.target(name, selectorType)`. The name is what
`ctx.targets(name)` reads back; the selector type is a constant from
`se/engine/src/engine/spec/T.java` (`T.SELF`, `T.VICTIM`, `T.ATTACKER`, `T.AOE`,
`T.NEAREST`, `T.HERE`). The first declared slot becomes the effect's **default**
selector, so `IGNITE:60` targets the victim with no `@`-selector written; an
author can still override it inline (`IGNITE:60 @Aoe{r=4}`).

Declare the **affinity** — this is how the compiler routes your intents:

| Affinity | Use when |
| --- | --- |
| `CONTEXT_LOCAL` | the work is on the firing thread's actor (the default; runs inline, zero hop) |
| `TARGET_ENTITY` | the work is on a target entity that may live in another region |
| `AOE` | the work fans out over an area |

The compiler folds the MAX affinity of all an ability's effects to the ability
level and routes the flush accordingly (`§3.6`). `DAMAGE_MOD` is
`CONTEXT_LOCAL` because it only adds to the actor's damage fold; `IGNITE` is
`TARGET_ENTITY` because it sets a (possibly cross-region) victim on fire.

## Step 3 — emit intents through the Sink

`run` reads typed args (`ctx.integer`, `ctx.dbl`, `ctx.str`, `ctx.bool`,
`ctx.lng`), targets (`ctx.targets(name)` for entities,
`ctx.targetLocations(name)` for block/coordinate selectors), and the firing
actor (`ctx.actor()`, `ctx.victim()`, `ctx.location()`, `ctx.level()`). It then
calls one or more `Sink` methods. There is **no parsing** and **no entity touch**
here — `sink.ignite(target, duration)` records an intent; the dispatcher applies
it later on the right thread.

The full `Sink` surface lives in `se/engine/src/engine/sink/Sink.java`. If your
new effect needs a mutation the `Sink` does not yet expose, add the intent method
to the `Sink` interface and implement it in `DispatchSink`
(`se/engine/src/engine/sink/DispatchSink.java`) — that is where threading and
interned-handle resolution live, and the only place that is allowed to know about
either. Version-volatile referents (potions, sounds, materials, entity types)
cross the `Sink` as **interned ids**, not Bukkit constants, so the runtime never
sees a renamed enum.

For the damage path specifically, **never** call `event.setDamage`. Effects
contribute deltas to one additive fold that commits once (ADR-0012); see the
arbiter example in `DamageModEffect.java`, which routes `attack`/`defense` ×
`add`/`flat` to `sink.addOutgoingDamage` / `addDamageReduction` /
`addFlatDamage` / `addFlatReduction`.

## Step 4 — register it (the one line)

Add one `.register(new ...)` line to the explicit, greppable registry —
`se/engine/src/engine/effect/kind/BuiltinEffects.java`:

```java
            .register(new IgniteEffect())
```

That is the entire registration. There is no annotation scan and no generated
table by design: a reviewer reads the registry top to bottom and sees every
effect that exists. Heads are case-insensitive and a duplicate fails fast at
build time (`EffectRegistry.Builder#register`). At boot the registry also hands
the pure compiler a `specRegistry()` (for validation), an `affinityOf()` (for the
routing fold), and a `defaultSelectorOf()` (your first target slot) — so
`se-compile` stays Bukkit-free yet affinity-aware.

## Step 5 — a mock-host unit test

A unit test mocks the `Sink` and the `EffectCtx`, runs the effect, and verifies
the exact intents — no server. Tests live next to the kind, under
`se/engine/test/engine/effect/kind/`. The real `IgniteEffectTest.java`:

```java
@Test
void emitsOneIgniteIntentPerResolvedTarget() {
    LivingEntity target = mock(LivingEntity.class);

    EffectCtx ctx = mock(EffectCtx.class);
    when(ctx.integer("duration")).thenReturn(60);
    when(ctx.targets("who")).thenReturn(List.of(target));

    Sink sink = mock(Sink.class);
    new IgniteEffect().run(ctx, sink);

    verify(sink).ignite(target, 60);
    verifyNoMoreInteractions(sink);
}
```

`verifyNoMoreInteractions(sink)` is the load-bearing assertion: it proves the
effect emitted *only* the intent it should and touched nothing else. For an
effect with branches (modes, sides), test each branch the way
`DamageModEffectTest.java` covers all four `(side, mode)` pairs.

## Step 6 — regenerate the docs

The DSL reference (`docs/reference/dsl-reference.md`) and the in-game catalog are
generated from the live registries by `ReferenceDoc`
(`se/engine/src/engine/doc/ReferenceDoc.java`), so your new effect's doc, usage,
params, target, and example appear automatically. Regenerate:

```bash
./gradlew regenDocs
```

A drift test (`ReferenceDocDriftTest`) fails the build if the committed file does
not match the registries, so an un-regenerated change cannot merge.

## Verify

```bash
./gradlew build
```

`build` runs the pure unit tests **and** the drift tests, so it both checks your
mock-host test and forces the doc regen above. A new effect is pure logic, so the
unit gate is enough — reserve the live Paper + Folia integration matrix for
changes that touch the `Sink` dispatcher's threading, a new cross-version handle,
or anything version/NMS-specific. When you do touch dispatch, run the matrix:
running a *new effect kind* through a real server is rarely necessary, but a new
`Sink` intent that schedules work is.

See also:

- [Effect engine internals](../internals/effect-engine.md) — the pipeline, gate
  sequence, `Affinity` routing, and `Sink` dispatch.
- [Compiler and config internals](../internals/compiler-and-config.md) — how an
  authored `effects:` line becomes a compiled `Ability`.
- [Decision records](../../decisions/) — ADR-0011 (engine architecture),
  ADR-0012 (additive damage), ADR-0022 (HELD/PASSIVE lifecycle).
