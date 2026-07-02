---
name: effect-engine
description: Use when implementing or modifying the runtime engine — stateless systems, the activation pipeline and gate order, effect/condition/trigger/selector kinds, the Ability record, the Sink, declared Affinity, the dispatch plan, or how one activation executes.
---

# The effect engine (data-oriented runtime)

A handful of stateless **systems** (one per trigger family) walk a player's
pre-flattened immutable `WornState` and execute **source-erased `Ability`
records** through a **`Sink`** that owns all mutation. Effects emit **intents**;
they never touch entities or schedulers. See **item-data-model** for `WornState`
/ PDC, **feature-interaction-rules** for arbiters, **performance-hot-paths** for
the budget.

## When to use / not

Use for `se-engine` (systems, pipeline, effect, condition, selector, trigger,
interact, sink, stores). NOT for compiling YAML→`Ability` (that's the compiler /
**config-and-migration**) or for item state / lore (**item-data-model**).

## Core rules

| Rule | Why (§) |
| --- | --- |
| Systems iterate, effects are kinds, content is rows — a system never knows what `DAMAGE` does | §3.1 |
| No string/opcode at runtime: flyweight `EffectKind` + typed-args record; conditions = pre-built AST over int slots | §3.2, §4.1 |
| All 5 sources lower to ONE `Ability` record; `sourceKind` is a tag, not a type | §4.1 |
| Conditions read a thread-local primitive `FactBuffer`, populated lazily once per activation — zero boxing | §3.4 |
| Effects emit INTENTS into the dispatch plan; CI lint bans `Bukkit.getScheduler()` / direct entity mutation in `effect/` | §3.5 |
| `Affinity` declared per `EffectKind`, folded MAX to ability level; `CONTEXT_LOCAL` runs INLINE (zero hop) | §3.6 |
| Victim/defense facts come from the immutable `WornState` snapshot or event payload — never a live cross-region victim read | §3.4, §5.5 |

## The gate sequence (§3.3) — never reorder

world-blacklist → protection → trigger-match + slot → level →
**SUPPRESSION** (O(1) interned-id set, §6.2) → cooldown (3 scopes) → condition +
chanceΔ (AST, no alloc) → chance roll (injected supplier `< base+Δ`; a condition
`FORCE`/`ALLOW` flow skips the roll) → `PreActivate` (injected `Guard`, cancellable)
→ soul cost (gate 10 = `SoulSpender` debiting the cross-gem pool, only when a gem
is active, §6.3) → arm cooldown → run effects (intents, cumulative WAIT). No gate
is skippable; a gate stops the walk, it never "starts at gate K."

## Adding a kind — one class, one ParamSpec, one registration

Implement `EffectKind` / `ConditionFn` / `TriggerKind` / `SelectorKind`, declare
its `ParamSpec` (the one spec used four ways: validate / complete / `/se docs` /
migrate, §7) and its `Affinity`, then register it in the explicit greppable
registry (no annotation-processor codegen as primary). A new set/crystal/enchant
is PURE YAML — no code. `run` is hot-path: typed args, pre-resolved selectors,
intents only — it never parses and never touches an entity (`sink.lightningAndDamage(e,
ctx.dbl("damage"))` emits an intent). See **config-and-migration** for the full
`ParamSpec`/SPI sample.

## One activation, end to end

```
for (int aid : wornState.byTrigger[trigger]) {   // dense int[] from WornState (§5.5)
    Ability ab = snapshot.abilities[aid];         // contiguous record array
    if (!gates.pass(ab, activation, plan)) continue;   // §3.3 order, primitives/bitsets
    runEffects(ab.effects, activation, plan);     // emit intents into the plan (§3.5)
}
dispatcher.flush(plan);                           // §3.6 batched, affinity-routed
```

Damage is never `setDamage` — effects `sink.damageDelta(...)`; one additive fold
commits once (fully-additive, ADR-0012; §6.1). WAIT = deferred-intent batches on
the region/entity timer (never a continuation), cumulative `waitTicks` at compile
time. Cross-version handles are pre-resolved at compile, so the runtime never sees
a renamed constant (**cross-version-item-api**); thread routing obeys
**folia-scheduling**.
