# ADR 0039: Linked kind ids at publish + demand-driven fact population

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** project owner + engine work
- **Relates to:** ADR-0011 (data-oriented runtime), ADR-0014 (transactional reload), ADR-0038 (add-on API,
  the `bindEffects` rebind this refines), docs/architecture.md §3.2–3.5 (kinds, conditions, facts), §8
  (hot-path budget); skills `effect-engine`, `performance-hot-paths`, `item-data-model`

## Context

The `performance-hot-paths` contract says the combat hit does "no string ops, no map lookups" and reads only
the facts a condition needs. Two places quietly violated it:

1. **Effect/selector dispatch went by string head.** `AbilityExecutor` resolved each effect with
   `effects.lookup(effect.head())` — an `Optional`-returning `Map` get behind a `head.toUpperCase(Locale.ROOT)`
   allocation — and each selector the same way, **per effect, per execution**. PR #202 added a `volatile
   bindEffects(EffectRegistry)` rebind so add-on kinds became runnable, but kept the per-execution string
   lookup.

2. **Facts were populated eagerly.** `FactPopulator` computed the WHOLE vocabulary on every trigger pass for
   every armored player — including an 8-block AABB entity scan (`%nearbyenemies%`), a block lookup
   (`%actor.groundblock%`), and the `%distance%`/`%victim.inzone%` geometry — even when no worn condition
   referenced any of them.

## Decision

### A. Dense kind ids linked at publish

`EffectRegistry` and `SelectorRegistry` assign a dense `kindId` per head in **registration order** and expose
`idOf(head)` + `kindsById()`/`selectorsById()`. The compiler stamps that id onto every `CompiledEffect`
(`kindId`) and `CompiledSelector` (`kindId`) at **lower time**, via an injected `ToIntFunction<String>` wired
through the same `Compiler.of` seam that already injects `affinityOf`/`defaultSelectorOf` — so `:compile`
stays pure and Bukkit-free. `AbilityExecutor` dispatches `kinds[effect.kindId()]` — zero string ops, zero map
lookups, zero `toUpperCase` on the execution path.

The effect + selector kind arrays are bound as **one atomic `LinkedContent` reference**, replacing the two
independent `volatile` fields (`effects` + `selectors`). A reader therefore can never see a torn
effects-from-build-N / selectors-from-build-N+1 mix. Add-on registration (ApiService) still triggers a full
reload → a fresh `LinkedContent` swapped by `bindContent` after the snapshot publishes.

A `kindId` of `-1` (a hand-built test effect, or the `SELF` sentinel) or an id out of the current array's
range **falls back to a head lookup** — then warn-and-skip if still unresolved, exactly the pre-change
behaviour. This matters for the sub-millisecond window on Folia between `holder.publish` (snapshot N+1 live on
a region thread) and `onPublished`→`bindContent` (on the global thread): built-in kind ids are **prefix-stable
across reloads** (built-ins register in a fixed order; add-ons only append — ADR-0038), so a gen-N+1 snapshot
that references only built-in ids indexes the still-bound gen-N array correctly, and a brand-new add-on head
in that window simply doesn't fire until the rebind lands — the same outcome the old head lookup produced
(the old registry lacked the head too).

### B. Demand-driven facts via per-ability masks

Each `Ability` carries a `FactMask` derived at compile time (`FactMasks.of`) by walking its condition AST and
its expression-valued effect args — the exact `FactBuffer` slots the only two runtime readers
(`ConditionEvaluator` for flags/strings, `NumExprEval` for numbers) can touch. `WornState` folds a **per-trigger
union** of these masks once per equip change (alongside `byTrigger[]`, the existing WornState idiom), and
`FactPopulator.populate` takes the mask and computes **only** the referenced slots. The expensive derived
facts are mask-gated; PAPI/dynamic-var resolution keeps its existing lazy path.

`FactBuffer.clear()` still zeroes every slot each pass, so an unreferenced slot reads its **default**, never a
stale value. A referenced slot is captured at the same point as before (event-entry victim facts, etc.), so a
populated value is byte-identical to the eager path. Nothing reads a slot outside the mask: the mask is the
union of exactly the `Var` nodes those two readers evaluate.

### Representation

`FactMask` is **three `long`s** — one bitset per fact space (numbers/flags/strings), because the three spaces
have independent slot indices. The built-in vocabulary is 15 numbers / 17 flags / 9 strings (`BuiltinVars`),
and add-ons contribute **effects, not variables** (ADR-0038), so 64 slots per space is generous headroom
without a `long[]`. A referenced slot `>= 64` is unrepresentable, so `FactMasks` degrades that ability's whole
mask to `FactMask.ALL` (populate everything) rather than aliasing a bit — correctness is preserved if the
vocabulary ever outgrows a word.

## Consequences

- `ExecutorBenchmark.effectExecution` roughly **doubled** (≈62M → ≈123M ops/s on the dev box) with allocation
  still ≈0 B/op; `PipelineBenchmark.gateWalk` unchanged (not on this path). The JMH gate stays green.
- An armored player whose worn conditions never mention `%nearbyenemies%`/`%distance%`/`%groundblock%`/
  `%victim.inzone%` pays **nothing** for them per trigger pass — the biggest saving is the AABB entity scan.
- Records `CompiledEffect`/`CompiledSelector`/`Ability`/`WornState` each grew one field; a backward-compatible
  secondary constructor keeps every hand-built test call site compiling (defaulting `kindId = -1` /
  `factMask = ALL` / no per-trigger masks → "populate everything"), so the fallback path is exercised by the
  existing suites and the fast path by the bench + new unit tests.
- ADR-0038's `AbilityExecutor.bindEffects` becomes `bindContent` (one atomic effect+selector swap).

## Alternatives considered

- **A mandatory generation gate** pairing the snapshot generation with the kinds array, skipping a hit on
  mismatch. Rejected — it would thread `generation` through every `executor.run` caller and break every unit
  and tester construction that builds an executor and runs without binding a generation. Prefix-stable ids +
  the bounds-checked head fallback give the same "never a torn/wrong dispatch" guarantee non-invasively.
- **A single combined 64-bit mask across all three fact spaces.** Rejected — flags alone can reach 128
  (`FactBuffer.MAX_FLAGS`), so a combined space could overflow one word; three per-space words are simpler and
  match how the populator already reads by (kind, slot).
- **Stamping ids in the erase stage instead of at lower time.** The mask IS derived in erase (where `Ability`
  is assembled), but the `kindId` is stamped at lower time per the design, since that is where each
  `CompiledEffect`/`CompiledSelector` is first built and the injected `idOf` is available.
