# The effect engine

The engine is a **data-oriented runtime**: a handful of stateless *systems*
walk a player's pre-flattened, immutable `WornState`, run **source-erased
`Ability` records** through a **fixed gate sequence**, and let each effect emit
**intents** into a single mutation boundary — the `Sink` — which batches them and
routes each one to the correct Folia thread by the ability's declared `Affinity`.
Nothing in the engine parses a string, looks up a map, or touches an entity on
the hit path; all of that was resolved at compile time (see
[the compiler](compiler-and-config.md)). This page is the runtime: what a system
is, what an `Ability` is, the gate order, the `Sink`, and how one activation runs
end to end.

The unifying idea is **erasure**: an enchant, a set bonus, a weapon ability, a
crystal, and a heroic stat all compile to the *same* `Ability` record. A system
never knows which kind of content it is running, so there is no per-source branch
to drift — the routing flags a Cosmic Enchants-style design forgets to re-check
(a helmet enchant firing on attack) cannot happen here, because the trigger and
slot are baked into the data before a system ever sees it.

## Systems, not interpreters

A *system* is the small amount of server-side code that turns a Bukkit event
into an `Activation` and drives the gate walk for one trigger family. The systems
live in [`se/feature`](../../../se/feature) — `CombatDispatch` for the
attack/defense families, `TriggerRunner` for the shared "run one trigger pass"
primitive, and the lifecycle/repeat drivers for held and passive abilities. They
all funnel into one execution core in
[`se/engine`](../../../se/engine): `AbilityExecutor#run`.

The core never branches on content. It receives a dense `int[]` of candidate
ability ids (already filtered by trigger and slot — see
[the hot path](performance-hot-paths.md)), indexes the contiguous `Ability[]`,
and walks the gates — `AbilityExecutor#run`, in
`se/engine/src/engine/run/AbilityExecutor.java`:

```java
public int run(Ability[] abilities, int[] candidateIds, Activation activation,
               ActivationContext context, DispatchSink sink, StableKeyIndex stableKeys) {
    int activated = 0;
    for (int id : candidateIds) {
        if (id < 0 || id >= abilities.length) continue; // stale/foreign id (e.g. across a reload)
        Ability ability = abilities[id];
        try {
            if (pipeline.evaluate(ability, activation).activated()) {
                runEffects(ability, context, sink, activation.activeGem(), activation.facts());
                activated++;
                notifyActivation(ability, context, stableKeys);
            }
        } catch (Throwable failed) {
            LOG.log(Level.WARNING, "ability " + id + " failed during execution", failed);
        }
    }
    return activated;
}
```

Two things to notice. The per-ability `try/catch` is the **warn-and-skip**
contract: one broken ability is logged and skipped, never able to abort the
combat event. And `run` does **not** flush the `Sink` — the caller flushes once,
after both the attacker and victim passes, so a whole hit collapses to one round
of scheduling.

## The `Ability` — one record, five sources

Everything a system runs is a `compile.model.Ability`
(`se/compile/src/compile/model/Ability.java`). It is a flat record of primitives,
interned ids, and bitsets — never strings — so every gate is an integer compare:

```java
public record Ability(
        int id, int defId, SourceKind sourceKind, int triggerMask, int level,
        double baseChance, int cooldownTicks, int soulCost, long worldBlacklist,
        CompiledCondition condition, CompiledEffect[] effects, int repeatTicks,
        Affinity affinity, int cdScopeEnchant, int cdScopeGroup, int cdScopeType,
        int suppressKey, int setPieces) {

    public boolean firesOn(int triggerId) {
        return (triggerMask & (1 << triggerId)) != 0;
    }
    public boolean blockedInWorld(int worldId) {
        // worldId -1 (never blacklisted) must short-circuit: 1L << -1 wraps to bit 63 in Java.
        return worldId >= 0 && (worldBlacklist & (1L << worldId)) != 0;
    }
}
```

`sourceKind` is a **tag, not a subtype** — `SourceKind` is the enum
`ENCHANT / SET / WEAPON / CRYSTAL / HEROIC` and nothing dispatches on it on the
hit path. Because all five sources are the same record, they feed every arbiter
uniformly with no special-casing; that uniformity is the subject of
[feature interactions](feature-interactions.md). `triggerMask` and
`worldBlacklist` are bitsets (32 triggers, 64 worlds); `condition` is a pre-built
AST; `effects` is the lowered, typed-arg effect list with cumulative wait ticks
already stamped on each one.

## The gate sequence — never reorder

Every candidate ability runs through the same ordered gates in
`ActivationPipeline#evaluate`
(`se/engine/src/engine/pipeline/ActivationPipeline.java`). The order is identical
for every source so no per-source path can drift, and each gate is a primitive /
bitset / interned-id check:

```java
// 1. world blacklist — primitive AND
if (ability.blockedInWorld(act.worldId())) return GateOutcome.BLOCKED_WORLD;
// 2. protection / region — injected Guard, cached per tick in production
if (!protection.allows(ability, act)) return GateOutcome.BLOCKED_PROTECTION;
// 3. trigger-match (slot applicability is pre-filtered into WornState.byTrigger)
if (!ability.firesOn(act.triggerId())) return GateOutcome.WRONG_TRIGGER;
// 4. level bounds — compile-guaranteed; a negative level can never fire
if (ability.level() < 0) return GateOutcome.OUT_OF_LEVEL;
// 5. suppression — per-activation set OR per-player timed DISABLE_* across 3 scopes
if (act.suppression().contains(ability.suppressKey()) || suppressed(ability, act)) return GateOutcome.SUPPRESSED;
// 6. cooldown (three scopes) — primitive long map
if (!cooldownsReady(ability, act)) return GateOutcome.ON_COOLDOWN;
// 7. condition + chanceΔ — AST walk over the primitive FactBuffer, no alloc
ConditionResult cond = ConditionEvaluator.eval(ability.condition(), act.facts());
if (cond.flow() == Flow.STOP) return GateOutcome.CONDITION_FAILED;
// 8. chance roll — roll [0,100) < (base + Δ); FORCE/ALLOW skip the roll
if (!rollPasses(ability, cond, act)) return GateOutcome.CHANCE_FAILED;
// 9. PreActivate — injected; cancellable
if (!preActivate.allows(ability, act)) return GateOutcome.CANCELLED;
// 10. soul cost — only if a gem is active; single-authority debit
if (!consumeSouls(ability, act)) return GateOutcome.NO_SOULS;
// 11. start cooldown
armCooldowns(ability, act);
return GateOutcome.ACTIVATED;
```

The return type is `GateOutcome`, an enum whose constants are named after the
gate that stopped the ability (`BLOCKED_WORLD`, `SUPPRESSED`, `CHANCE_FAILED`, …
`ACTIVATED`). Naming the stop gate lets a runtime trace explain *why* an ability
did or did not fire and lets tests assert the exact gate. Only `ACTIVATED` runs
effects (the unnumbered "gate 12" is `AbilityExecutor#runEffects`).

A few ordering invariants worth internalizing:

- **Suppression (gate 5) before cooldown and chance.** `Ability#suppressKey` is a
  single interned id; gate 5 is an `O(1)` membership test against a `BitSet`. The
  key is case-folded at compile time, killing a Cosmic Enchants-style
  case-sensitivity divergence. See
  [feature interactions](feature-interactions.md#suppression) for the
  defender-vs-activator role rule.
- **Cooldown has three scopes** (enchant / group / type), packed so they never
  collide; `armCooldowns` only runs when every earlier gate passed.
- **Chance is the last cheap gate** before the expensive ones (`PreActivate`,
  souls). A condition can return `FORCE`/`ALLOW` flow to skip the roll, or a
  `chanceΔ` to bias it — `rollPasses` adds `cond.chanceDelta()` to
  `ability.baseChance()` and rolls `act.chanceRoll().getAsDouble() < chance`.
- **`PreActivate` (gate 9) is only consulted if a listener is wired.** In
  production the injected `Guard` defaults to `Guard.ALLOW`, so no add-on means no
  cost.

Gates 2 and 9 are injected `Guard`s — `ActivationPipeline.Guard` is a
functional interface defaulting to `Guard.ALLOW`. Protection (region/claim
plugins) and the cancellable `PreActivate` event plug in here without the engine
depending on them.

## The `Activation` and the `FactBuffer`

An `Activation` (`se/engine/src/engine/pipeline/Activation.java`) is the
per-event context a single ability is evaluated against — built once per Bukkit
event by the firing system, never per ability. It carries the actor UUID, the
interned `worldId`/`triggerId`, `nowTicks`, the `FactBuffer` of condition facts,
the per-role `SuppressionSet`, the chance-roll supplier, and the active soul gem
(or null when not in soul mode).

Conditions read a **primitive `FactBuffer`**
(`se/engine/src/engine/condition/FactBuffer.java`) — numbers, a `long`-bitset for
flags, and string slots, all keyed by a compiled slot index so there is zero
boxing and zero parsing in a condition:

```java
public void setFlag(int slot, boolean value) {
    long bit = 1L << (slot & 63);
    if (slot < Long.SIZE) flags0 = value ? (flags0 | bit) : (flags0 & ~bit);
    else                  flags1 = value ? (flags1 | bit) : (flags1 & ~bit);
}
```

The buffer is **one instance per worker thread**, cleared and refilled in place
by `FactPopulator#populate` (`se/engine/src/engine/run/FactPopulator.java`) at the
start of an activation, so the per-hit pipeline allocates nothing for facts. The
populator is built at boot from the *same* `%scope.name%` vocabulary the compiler
lowered conditions against, so a fact's slot and the buffer agree by
construction. Every entity read is wrapped so a cross-region victim on Folia
leaves a fact defaulted rather than aborting the activation — gating is
**fail-closed**: an unresolved value (NaN) fails every numeric comparison except
`!=`. `ConditionEvaluator#eval` walks the pre-built `Cond` AST and returns a
`ConditionResult` carrying both a `Flow` (`CONTINUE`/`STOP`/`FORCE`/`ALLOW`) and a
chance delta.

## The `Sink` — the only door to the world

An `EffectKind#run` never touches an entity, block, world, or scheduler. It reads
typed args and pre-resolved targets from an `EffectCtx` and emits **intents**
through the `Sink` (`se/engine/src/engine/sink/Sink.java`). Removing the
scheduler door — rather than discouraging it — is what makes Folia-correctness
**structural**: an author *cannot* write a threading bug because there is no API
to misuse.

Effects come in two flavours through the Sink. **Inline feedback** — the damage
fold and `cancelEvent` — accumulates synchronously on the firing thread and is
read back by the firing system; it never schedules. **World mutations** —
everything that touches an entity, block, or region — are captured into a
`DispatchPlan` and flushed after the gate walk. The real `IGNITE` and `DAMAGE`
effects show the pattern; `DamageEffect#run`
(`se/engine/src/engine/effect/kind/DamageEffect.java`):

```java
@Override
public void run(EffectCtx ctx, Sink sink) {
    double amount = ctx.dbl("amount");
    for (LivingEntity target : ctx.targets("who")) {
        sink.damage(target, amount);
    }
}
```

Crucially, the damage *arbiter* methods (`addOutgoingDamage`,
`addDamageReduction`, the flat and heroic variants) are forbidden from calling
`event.setDamage` — they contribute deltas that fold once. That rule and the
fold formula are owned by [feature interactions](feature-interactions.md). All
version-volatile referents the Sink takes — potions, sounds, particles,
materials, entity types — are passed as **interned ids resolved at compile
time**, so the runtime never sees a renamed constant; only the "who/where"
(entities and locations) are live Bukkit handles.

## `Affinity` routing and the dispatch plan

Each `EffectKind` declares an `Affinity` in its `EffectSpec`; the compiler folds
the MAX over an ability's effects onto `Ability#affinity`
(`se/compile/src/compile/model/Affinity.java`, ordered by increasing dispatch
reach so the fold is an ordinal `max`):

```java
public enum Affinity {
    CONTEXT_LOCAL,   // inline on firing region thread — zero hop, Paper & Folia
    TARGET_ENTITY,   // routed to resolved target entity's thread (1 hop on Folia)
    REGION,          // routed to a block/location's owning region thread
    AOE,             // area targets, each batched to its own region thread
    GLOBAL,          // the global / region-agnostic thread
    ASYNC;           // off the main threads (async-safe work only)
}
```

But `Affinity` is **advisory, not a licence to skip the hop**. The concrete
`DispatchSink` routes every world mutation by the *owner of its target*, so even
a mis-declared affinity can never put an entity mutation on the wrong thread.
`DispatchPlan` (`se/engine/src/engine/sink/DispatchPlan.java`) batches by owner —
an `Entity` intent is owned by that entity, a `Location` intent by the region
ticking its chunk (keyed `(world, chunkX >> 4, chunkZ >> 4)`), the rest by the
global region thread — and flushes each batch with one scheduler hop:

```java
private void entityOp(Entity target, Runnable op) {
    if (target != null) plan.onEntity(target, op, delayTicks); // always the entity's own thread — never inline
}
private void regionOp(Location at, Runnable op) {
    if (at != null) plan.onRegion(at, op, delayTicks); // always the location's region thread — never inline
}
```

`CONTEXT_LOCAL` effects (`DAMAGE`, `REDUCTION`, self-`POTION`, `MESSAGE`,
`SOUND`) run inline with zero hops; cross-region work costs roughly one hop per
distinct target thread, batched. `WAIT:n` is not a continuation — it lowers at
compile time to a cumulative `waitTicks` stamped on each following effect, and
`DispatchPlan` defers that effect's mutations into a delay tier flushed on the
region/entity timer. Per-effect, `AbilityExecutor#runEffects` calls
`sink.delay(effect.cumulativeWaitTicks())` before `kind.run`, so the targets are
resolved *now* on the firing thread while the mutation lands later. The threading
contract is the subject of the operator-facing
[Folia notes](https://owengregson.github.io/StarEnchants/).

## One activation, end to end

1. A Bukkit listener fires; a system builds one `ActivationContext` (actors and
   payload) and one `Activation` (interned ids, facts, suppression, chance roll)
   on the firing thread.
2. `FactPopulator#populate` clears and refills the thread-local `FactBuffer` from
   the live context.
3. `AbilityExecutor#run` walks the dense `int[]` of candidate ids; each ability
   goes through `ActivationPipeline#evaluate`'s gate sequence.
4. On `ACTIVATED`, `runEffects` looks up each `EffectKind`, resolves its selector
   targets, builds a `RuntimeEffectCtx`, sets the wait delay, and calls
   `kind.run(ctx, sink)` — which emits intents.
5. After every sibling pass (attacker, then victim), the system folds damage
   once, honours a cancel, and calls `sink.flush()` — `DispatchPlan` dispatches
   each batch to its owner thread.

## Where it lives

| Concern | File |
| --- | --- |
| Per-hit gate walk | `se/engine/src/engine/run/AbilityExecutor.java` |
| Gate sequence | `se/engine/src/engine/pipeline/ActivationPipeline.java` |
| Gate outcomes | `se/engine/src/engine/pipeline/GateOutcome.java` |
| Per-event context | `se/engine/src/engine/pipeline/Activation.java` |
| Actors / payload | `se/engine/src/engine/run/ActivationContext.java` |
| Condition facts | `se/engine/src/engine/condition/FactBuffer.java`, `engine/run/FactPopulator.java` |
| Condition AST eval | `se/engine/src/engine/condition/ConditionEvaluator.java` |
| Effect contract | `se/engine/src/engine/effect/EffectKind.java`, `effect/EffectCtx.java` |
| Effect registry | `se/engine/src/engine/effect/EffectRegistry.java` |
| The mutation boundary | `se/engine/src/engine/sink/Sink.java`, `sink/DispatchSink.java`, `sink/DispatchPlan.java` |
| Trigger vocabulary | `se/engine/src/engine/trigger/Trigger.java`, `trigger/TriggerKind.java` |
| Combat / trigger systems | `se/feature/src/feature/combat/CombatDispatch.java`, `feature/trigger/TriggerRunner.java` |

## Gotchas and invariants

- **Never reorder the gate sequence.** Suppression precedes cooldown precedes
  chance precedes souls for correctness, not style; tests assert the exact
  `GateOutcome` per gate.
- **`run` is the hot path.** No string ops, no boxing, no entity touch, no
  scheduling, no parsing. Emit intents; the Sink does the rest. The constraints
  are in [the hot-path budget](performance-hot-paths.md).
- **An `EffectKind` is stateless.** One shared instance is reused across every
  activation and thread; keep all state in the `EffectCtx` and the `Sink`.
- **`stop` is unconditional.** A maintained-buff kind (e.g. `POTION`) overrides
  `EffectKind#stop` to emit the inverse of `run`; it runs on unequip outside the
  gate pipeline so a buff can never leak (ADR
  [0022](../../decisions/0022-held-passive-lifecycle-and-command-trigger.md)).
- **`sourceKind` is a tag.** Do not branch on it on the hit path — that
  re-introduces the per-source drift erasure exists to prevent.
- **Adding a kind is one class + one registration.** Implement
  `EffectKind`/`ConditionFn`/`TriggerKind`/`SelectorKind`, declare its spec and
  affinity, and add one `.register(...)` line to the explicit registry. A new
  enchant, set, or crystal is **pure YAML** — no code. See
  [developing an effect](../guides/developing-an-effect.md).

Adjacent reading: [the compiler](compiler-and-config.md) for how YAML becomes the
`Ability[]` this engine runs; [feature interactions](feature-interactions.md) for
the arbiters the Sink feeds; [the hot-path budget](performance-hot-paths.md) for
what the per-hit loop may and may not do; and the approved
[architecture spec](../../architecture.md) (§3, §6, §8) for the rationale.
