# Developing a selector

A **selector** answers *who* (or *where*) an effect acts on. An author attaches it
to an effect via the `who:` key: `{ IGNITE: { duration: 60, who: "@Aoe{r=6, filter=MONSTERS}" } }`. The built-ins
cover `@Self`, `@Victim`, `@Attacker`, `@Nearest`, `@Aoe`, the player selectors,
and the block/location selectors. Adding one is, again, **one interface + one
registration**.

This guide walks the real `@Aoe` selector and points at `@Nearest` for the
single-target case.

## The contract

A `SelectorKind` is **stateless** (one shared instance across all activations and
threads) and reaches the world only through helpers on its `SelectorCtx` — never
a raw `World`. Everything it sees is firing-thread safe: the actor and the
activation's captured victim/attacker/location, never a live cross-region entity.

The interface (`se/engine/src/engine/selector/SelectorKind.java`) has two
resolution channels — one for entities, one for locations:

```java
public interface SelectorKind {
    SelectorSpec spec();
    default List<LivingEntity> resolve(SelectorCtx ctx) { return List.of(); }
    default List<Location> resolveLocations(SelectorCtx ctx) { return List.of(); }
}
```

An **entity** selector (`@Aoe`, `@Nearest`, `@Victim`) overrides `resolve` and
returns living entities, read back by the effect as `ctx.targets(slot)`. A
**location** selector (`@Here`, `@Block`, `@Vein`, `@Trench`) overrides
`resolveLocations` and returns block coordinates, read back as
`ctx.targetLocations(slot)`; a block-mutating effect like `SET_BLOCK` /
`BREAK_BLOCK` consumes that channel. A selector implements *one* of the two and
leaves the other returning the empty list. Both return non-null, always.

Unlike an effect, a selector carries **no `Affinity`** (routing follows the
*effect's*) and **no nested target slots** (a selector *is* a target).

## Step 1 — write the class

Selectors live in `se/engine/src/engine/selector/kind/`. Here is the real
`AoeSelector.java`:

```java
package engine.selector.kind;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import engine.spec.SelectorSpec;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import schema.spec.D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AoeSelector implements SelectorKind {

    static final SelectorSpec SPEC = SelectorSpec.of("AOE")
            .param("r", D.DOUBLE.min(0).def(4), "radius in blocks")
            .param("filter", D.enumOf("ALL", "PLAYERS", "MONSTERS", "MOBS").def("ALL"), "which entities to include")
            .param("limit", D.INT.min(0).def(0), "max targets, nearest first (0 = unlimited)")
            .doc("Living entities within r blocks of the target, except the activator; optionally filtered and capped.")
            .example("@Aoe{r=6, filter=MONSTERS}")
            .build();

    @Override
    public SelectorSpec spec() {
        return SPEC;
    }

    @Override
    public List<LivingEntity> resolve(SelectorCtx ctx) {
        Location center = Centers.of(ctx);
        if (center == null) {
            return List.of();
        }
        Targets.Filter filter = Targets.of(ctx);
        int limit = ctx.integer("limit");
        List<LivingEntity> matched = new ArrayList<>();
        for (LivingEntity e : ctx.nearbyLiving(center, ctx.dbl("r"))) {
            if (!e.equals(ctx.actor()) && filter.accepts(e)) {
                matched.add(e);
            }
        }
        if (limit > 0 && matched.size() > limit) {
            matched.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(center)));
            return new ArrayList<>(matched.subList(0, limit));
        }
        return matched;
    }
}
```

`@Nearest` (`NearestSelector.java`) is the same shape returning at most one
entity — a useful template when your selector picks a single target.

## Step 2 — the SelectorSpec and params

`SelectorSpec` (`se/engine/src/engine/spec/SelectorSpec.java`) wraps the schema
`ParamSpec`, just like `EffectSpec` does, so one declaration drives validation,
tab-completion, the generated reference, and migration. Params use the same `D.`
vocabulary (`D.DOUBLE.min(0).def(4)`, `D.enumOf(...)`, `D.INT.min(0).def(0)`).

> **Every selector param must be optional.** Give each one a `.def(...)`. A
> builtin used as an effect's *default* target (e.g. `@Victim` for `IGNITE`) is
> validated on the no-argument path, so a required param would fail that path.
> This is noted on `SelectorSpec` itself.

Reach the world only through the `SelectorCtx`
(`se/engine/src/engine/selector/SelectorCtx.java`) helpers:

| Helper | Returns |
| --- | --- |
| `ctx.actor()` / `ctx.victim()` / `ctx.attacker()` / `ctx.location()` | the firing-thread actors / centre (victim/attacker/location may be `null`) |
| `ctx.dbl(name)` / `ctx.integer(name)` / `ctx.args()` | typed, pre-validated args (no parsing) |
| `ctx.nearbyLiving(center, radius)` | living entities near `center`, on that location's region thread |
| `ctx.playerByName(name)` / `ctx.entityInSight(maxDistance)` | a roster / raytrace lookup, or `null` |
| `ctx.targetBlock(maxDistance)` / `ctx.vein(start, limit)` | block-channel reads for location selectors |

The shared `Centers` and `Targets` helpers in the `kind` package give you a
common centre (location → victim → actor) and the `ALL/PLAYERS/MONSTERS/MOBS`
filter, so you don't re-derive them per selector.

## Step 3 — register it (the one line)

Add one `.register(new ...)` line to the explicit registry —
`se/engine/src/engine/selector/kind/BuiltinSelectors.java`:

```java
                .register(new AoeSelector())
```

No annotation scan, no generated table. Heads are case-insensitive and a
duplicate fails fast at build (`SelectorRegistry.Builder`). The registry exposes
each selector's `ParamSpec` via `specRegistry()` so the pure compiler validates
inline `@Sel{...}` arguments without depending on `se-engine`.

## Step 4 — a mock-host unit test

Mock the `SelectorCtx`, stub the helper reads, and assert the resolved set.
Selector tests live under `se/engine/test/engine/selector/kind/`. A sketch in the
shape of the effect tests:

```java
@Test
void aoeReturnsNearbyLivingExceptActor() {
    Player actor = mock(Player.class);
    LivingEntity near = mock(LivingEntity.class);

    SelectorCtx ctx = mock(SelectorCtx.class);
    when(ctx.actor()).thenReturn(actor);
    when(ctx.location()).thenReturn(someLocation);   // becomes the centre via Centers.of
    when(ctx.dbl("r")).thenReturn(6.0);
    when(ctx.integer("limit")).thenReturn(0);
    when(ctx.args()).thenReturn(argsWith("filter", "ALL"));
    when(ctx.nearbyLiving(any(), eq(6.0))).thenReturn(List.of(actor, near));

    List<LivingEntity> hit = new AoeSelector().resolve(ctx);

    assertEquals(List.of(near), hit);   // actor filtered out
}
```

Cover the empty-centre path (returns `List.of()`) and, for a location selector,
the `resolveLocations` channel.

## Step 5 — regenerate the docs

The reference's Selectors section is generated from the registry by
`ReferenceDoc` (`se/engine/src/engine/doc/ReferenceDoc.java`):

```bash
./gradlew regenDocs
```

`ReferenceDocDriftTest` fails the build if `docs/reference/dsl-reference.md`
drifts from the registry, so the regen is enforced.

## Verify

```bash
./gradlew build
```

A new selector is pure logic over the `SelectorCtx`, so the unit gate (plus the
drift test) is enough. Run the live Paper + Folia matrix only if your selector
adds a new world/entity read path — that is the place to confirm the region
threading holds on real Folia, since `nearbyLiving` runs on the centre's region.

See also:

- [Effect engine internals](../internals/effect-engine.md) — selector resolution
  in the activation pipeline and the entity-vs-location channels.
- [Compiler and config internals](../internals/compiler-and-config.md) — how an
  inline `@Sel{...}` is parsed and lowered to a pre-resolved selector.
- [Decision records](../../decisions/) — ADR-0011 (engine architecture).
