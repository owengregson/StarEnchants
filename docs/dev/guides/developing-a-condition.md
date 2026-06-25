# Developing a condition variable

A **condition** is a boolean expression over `%scope.name%` *facts* an ability
gates on (`condition: %victim.health% < 6 && %sneaking%`). You rarely add a new
operator — the relational, string, and arithmetic operators already exist (see
[Extending the DSL grammar](extending-the-dsl-grammar.md)). What you add is a new
**fact**: another `%scope.name%` an author can read.

This guide adds one real variable end to end. We will add `%victim.food%`-style
facts — using the actually-present `%nearbyenemies%` as the worked example of the
two halves you must keep in sync.

## The two halves that must agree

A condition is **compiled, not interpreted**. At compile time a `%scope.name%`
reference resolves to a typed, dense **slot**; at runtime the engine fills that
same slot in a primitive `FactBuffer`. The hot path then reads facts by integer
index — zero string parsing, zero boxing.

That works only because both halves read **one** vocabulary:

| Half | File | Role |
| --- | --- | --- |
| The vocabulary | `engine/condition/BuiltinVars.java` → `VarVocabulary` | declares which facts exist, their type, and assigns each a slot |
| The populator | `engine/run/FactPopulator.java` | fills each slot from one activation's live context |

`VarVocabulary.asResolver()` is injected into the compiler; `newFactBuffer()`
sizes the runtime buffer. A compiled condition's slot and the populated buffer
agree **by construction** because both come from the same vocabulary object.

> **Invariant — append only.** Slots are assigned per type in registration order
> (`VarVocabulary.Builder.number/flag/string` increment a counter). Reordering or
> inserting a fact shifts every later slot, which silently drifts a
> previously-compiled condition onto the wrong buffer cell. **Always append a new
> fact at the end of its block.**

## Step 1 — declare the fact in the vocabulary

`BuiltinVars.vocabulary()` (`se/engine/src/engine/condition/BuiltinVars.java`) is
the greppable list. Each line is `number(key)`, `flag(key)`, or `string(key)`.
The key is the canonical lower-case `scope.name`; a bare name (no dot) is the
activator. This is the real declaration of the derived combat facts — note they
are appended at the end of the number block:

```java
                .number("distance")        // actor↔victim distance in blocks
                .number("nearbyenemies")   // living entities within 8 blocks of the actor
                .flag("sneaking")
                .flag("blocking")
                ...
                .string("victim.helditem")
                .string("block.type")
```

Pick the right declarer for the value type — this decides which operators the
fact admits (`compile/cond/VarKind.java`):

| Declarer | `VarKind` | Operators it admits |
| --- | --- | --- |
| `.number(...)` | `NUM` | all six relational comparators (`== != < <= > >=`) |
| `.flag(...)` | `BOOL` | stands alone as a gate, or `==`/`!=` |
| `.string(...)` | `STR` | `==`/`!=`, plus `contains` / `matchesregex` |

There is a hard ceiling of `FactBuffer.MAX_FLAGS` (128) boolean facts; numbers
and strings are unbounded. A duplicate key fails fast at build.

## Step 2 — populate the slot at activation

The vocabulary says a fact *exists*; the `FactPopulator` makes it *true at
runtime*. It is built once at boot against the same vocabulary, looks up each
fact's slot, and writes it from the live `ActivationContext`. For an actor or
victim fact, there are one-liner registration helpers; for a context or derived
fact, you write the read directly.

Actor / victim facts — one helper line each (the read is a method reference over
the entity):

```java
addActorNum(vocabulary, "actor.health", Player::getHealth);
addActorFlag(vocabulary, "sneaking", Player::isSneaking);
addActorStr(vocabulary, "actor.world", actor -> actor.getWorld().getName());

addVictimNum(vocabulary, "victim.health", LivingEntity::getHealth);
addVictimStr(vocabulary, "victim.type", v -> v.getType().name());
```

Each helper resolves the slot (or `-1` if the vocabulary doesn't declare it, so
the populator and vocabulary cannot silently disagree) and registers a reader run
in `populateActor` / `populateVictim`.

Context and **derived** facts read the event payload or compute geometry. This is
the real `%nearbyenemies%` population in `populateDerived` — note it is wrapped so
a cross-region read on Folia leaves the fact at its default instead of aborting
the whole activation:

```java
private void populateDerived(FactBuffer facts, ActivationContext context) {
    ...
    try {
        if (nearbyEnemiesSlot >= 0) {
            int count = 0;
            for (Entity e : actor.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
                if (e instanceof LivingEntity && !e.equals(actor)) {
                    count++;
                }
            }
            facts.setNumber(nearbyEnemiesSlot, count);
        }
    } catch (RuntimeException unreadable) {
        // Cross-region actor (Folia) or a read failure — leave the derived facts defaulted.
    }
}
```

> **Folia rule.** Every fact read runs on the firing thread. An entity owned by
> another region (e.g. a cross-region projectile shooter) throws on read, so the
> actor/victim/derived/world reads are wrapped in `try { … } catch
> (RuntimeException)` — a wrong-thread read **defaults that fact**, it never
> aborts the activation. Wrap any new world or entity read the same way.

Resolve the slot once in the constructor (for context/derived facts) with the
`slot(vocabulary, key, kind)` helper, which returns `-1` when the vocabulary
omits the fact — your populate guard then becomes `if (slot >= 0)`.

## Step 3 — there is no runtime parse

You do not write any "how to evaluate this fact" code. The
`ConditionEvaluator` (`se/engine/src/engine/condition/ConditionEvaluator.java`)
already walks the compiled `Cond` tree and reads `f.number(slot)`,
`f.flag(slot)`, `f.string(slot)`. Numeric comparison uses IEEE ordering, so an
unresolved value (parsed to `NaN`) fails every numeric comparison except `!=` —
gating is **fail-closed** on missing data. String comparison is case-insensitive
and null-safe. Your fact just needs to land in its slot.

## Using it from a `condition:`

Once declared and populated, the fact is immediately usable in any ability's
`condition:` — no further code:

```yaml
condition: "%nearbyenemies% >= 3 && %victim.health% < 6 : +10 %chance%"
```

A bare condition (no trailing clause) is a **gate**: when it is false the
activation stops. A trailing `<test> : <outcome>` clause applies its outcome when
the test is true. The outcomes are `%continue%`, `%stop%`, `%force%` (skip the
chance roll), `%allow%` (pass regardless of the roll), and `±N %chance%` (shift
the roll by N points) — see `schema/grammar/expr/FlowKind.java`.

Unknown `%tokens%` are **not** errors: the compiler treats an unrecognised
`%name%` as a PlaceholderAPI passthrough, resolved at runtime via the populator's
PAPI delegate (and the `SET_VAR` dynamic-var store). So you only add a fact to
`BuiltinVars` when you want a first-class, typed, allocation-free fact rather than
a string placeholder.

## Verify

```bash
./gradlew build
```

`build` runs the pure unit tests, including the drift test that regenerates the
DSL reference's Variables table from `BuiltinVars` — your new fact appears in
`docs/reference/dsl-reference.md` after:

```bash
./gradlew regenDocs
```

A new fact is pure logic, so the unit gate covers it. Run the live Paper + Folia
matrix only if the fact reads world/entity state in a way that could behave
differently across regions — that is exactly the Folia-default-on-cross-region
path to confirm on a real server.

See also:

- [Effect engine internals](../internals/effect-engine.md) — the `FactBuffer`,
  the gate sequence, and where conditions sit in it.
- [Compiler and config internals](../internals/compiler-and-config.md) — how a
  `condition:` string lowers to the slot-resolved `Cond` IR.
- [Decision records](../../decisions/) — ADR-0011 (engine architecture).
