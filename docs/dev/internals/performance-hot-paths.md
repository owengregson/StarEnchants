# Performance hot paths

A busy PvP server resolves thousands of damage events per second, and a single
helmet can be hit twenty times a second. The combat and item paths are therefore
held to a strict budget: the per-hit loop is an **allocation-light array walk over
primitives** — no string ops, no boxing, no item re-read, no map lookups, no
YAML/DSL parse. Everything expensive happens on *cold* paths (load, reload, menus,
commands, item-apply), which are unconstrained and may parse and allocate freely.
This page is the budget the [effect engine's](effect-engine.md) per-hit loop runs
under: the item-view cache, the worn-set resolver, interning, declaring an
`Affinity`, and the enforcement gate.

The discipline rests on two ideas: **decode once** (an item is parsed into a cache
once per content change, then read by reference), and **resolve once** (a player's
worn sources are flattened into one immutable per-direction array at equip time,
so the hit reads it and pays nothing for set/omni/crystal resolution).

## The per-hit loop

The hot loop is `AbilityExecutor#run`
(`se/engine/src/engine/run/AbilityExecutor.java`): a walk over a dense `int[]` of
candidate ability ids, indexing a contiguous `Ability[]` directly — no maps, no
strings.

```java
for (int id : candidateIds) {
    if (id < 0 || id >= abilities.length) continue; // stale/foreign id across a reload
    Ability ability = abilities[id];
    try {
        if (pipeline.evaluate(ability, activation).activated()) {
            runEffects(ability, context, sink, activation.activeGem(), activation.facts());
            …
        }
    } catch (Throwable failed) { LOG.log(Level.WARNING, …, failed); }
}
```

The `candidateIds` array is exactly the `int[]` from
`WornState.byTrigger(triggerId)` / `combatAttack` / `combatDefense` — already
filtered by trigger and slot, so there is no per-ability applicability check on
the hit. `TriggerRunner` (`se/feature/src/feature/trigger/TriggerRunner.java`) is
the shared "run one trigger pass" primitive that fetches the resolved `WornState`,
early-returns on a stale generation or an empty candidate array, and supplies the
per-use chance roll from `ThreadLocalRandom`:

```java
WornState wornState = worn.get(actor.getUniqueId());
if (wornState == null || wornState.gen() != generation) {
    return; // unresolved or stale across a reload — contribute nothing
}
runResolved(…, wornState, wornState.byTrigger(triggerId), applyHeroic);
```

A nice consequence: `PreActivate` (gate 9) and activation observers do real work
*only when wired*. `AbilityExecutor#notifyActivation` short-circuits to a no-op
when no listener is registered, so the common case skips stable-key resolution
entirely.

## Banned from the inner loop

These are the things that must never appear in a `se-engine` hot-path package, and
the cheap mechanism that replaces each:

| Banned on the hit path | Use instead |
| --- | --- |
| `Bukkit.getScheduler()` / direct entity mutation | declare an `Affinity`; emit a `Sink` intent |
| `new NBTItem`, `ItemStack#clone`, Gson, NBT clone | `ItemViewCache#of(stack)`: one lookup, decode on miss |
| `String#split`, regex compile, YAML/DSL parse, map lookups | compiled at load; dense-int indices at runtime |
| string ops / boxing in conditions or effect args | the thread-local primitive `FactBuffer` by slot |

Damage folds **once** — never `event.setDamage` from an effect; contribute a
delta (see [feature interactions](feature-interactions.md#damage-fully-additive-one-fold)).
Effects emit intents and never schedule, so an author *cannot* write a Folia or
allocation bug through the normal API. That structural guarantee — the
[`Sink`](effect-engine.md#the-sink--the-only-door-to-the-world) removing the
scheduler door — is the primary line of defense.

## Read once, resolve once

### The item-view cache

`ItemViewCache` (`se/item/src/item/view/ItemViewCache.java`) replaces a Cosmic
Enchants-style clone-and-Gson-parse per slot per hit with one decode per distinct
item content, per generation. It is the single biggest combat CPU win. The cache
is keyed by the item's **raw combat blob within the current generation**, decoding
on a miss and serving by reference thereafter:

```java
ItemView ofBlob(String blob) {
    Generation g = current;
    if (blob == null || blob.isEmpty()) return g.empty;   // no-state item: shared empty view, zero alloc
    ItemView cached = g.byBlob.get(blob);
    if (cached != null) return cached;
    ItemView decoded = new ItemView(g.gen, codec.decode(blob));
    ItemView raced = g.byBlob.putIfAbsent(blob, decoded);
    return raced != null ? raced : decoded;
}
```

Three design points matter for anyone touching this:

- **The key is content + generation, never `ItemMeta` identity.** Meta is
  copy-on-write, so an identity key would miss constantly *and* could alias a
  stale view. Keying on the full content blob also makes a collision impossible —
  it is the full string, not a truncated hash, so a collision can never serve a
  stale view. (The [architecture spec](../../architecture.md) describes this as a
  "content-hash + generation" key; the implementation uses the full blob, which
  is strictly stronger.)
- **A reload swaps the whole generation.** `reload(generation)` installs a fresh,
  empty `Generation` holder, dropping every prior `ItemView`; `ItemView#gen()`
  records its decode generation so a pre-reload view is never read as current.
- **It is lock-free across region threads.** `ItemView` is immutable, the
  generation holder is `volatile`, and the per-generation map is a
  `ConcurrentHashMap`. A read racing a reload decodes into the doomed old map and
  recomputes next time — never stale or corrupt.

An `ItemView` (`se/item/src/item/view/ItemView.java`) is a tiny immutable carrier
of `(gen, CombatState)` — combat-only; identity and economy state are decoded
separately, off the hot path.

### The worn-set resolver

A player's worn sources are flattened **once per equipment change, never per
hit**. `WornResolver#resolve`
(`se/item/src/item/worn/WornResolver.java`) reads each armour slot plus the hands
once through the item-view cache, resolves each item's stable enchant/crystal keys
to dense ids against the *current* snapshot, and hands off to the flattener. The
key resolution is where stable keys become dense ids:

```java
for (Map.Entry<String, Integer> enchant : combat.enchants().entrySet()) {
    int id = keys.idOf(enchant.getKey() + "/" + enchant.getValue());
    if (id >= 0) mergedIds.add(id);   // unknown key → -1, skipped, never a crash
}
```

`WornFlattener#flatten` (`se/item/src/item/worn/WornFlattener.java`) then computes
the per-trigger and per-direction split *once*, so the hit reads a single
pre-merged ordered array:

```java
for (int id : activeAbilityIds) {
    Ability ability = abilities[id];
    boolean isAttack = false, isDefense = false;
    for (int t = 0; t < triggerCount; t++) {
        if (ability.firesOn(t)) {
            perTrigger.get(t).add(id);
            if (attackTrigger.test(t))  isAttack = true;
            if (defenseTrigger.test(t)) isDefense = true;
        }
    }
    if (isAttack)  attack.add(id);
    if (isDefense) defense.add(id);
}
```

The result is an immutable `WornState` (`se/item/src/item/worn/WornState.java`):

```java
public record WornState(
        int gen, BitSet activeSets, int[] activeCrystalAbilityIds, HeroicStat heroic,
        int[][] byTrigger, int[] combatAttack, int[] combatDefense) { … }
```

`byTrigger`, `combatAttack`, and `combatDefense` are the pre-flattened arrays the
loop walks; `activeSets` is a multi-set bitset and `heroic` rides along for the
fold. Because `WornState` is immutable, it is the **safe cross-region victim read
on Folia**: an attacker's thread reads the victim's `combatDefense` with no lock
and no wrong-thread access. Set, omni, and crystal resolution all happen here, so
they cost **nothing** per hit. The resolver must be given the `Snapshot` whose
generation matches its `ItemViewCache` — both advance together on reload.

## Interning

Every name — enchant, group, world, material, potion, sound, trigger,
cooldown-scope — is a **dense int at runtime**; stable strings exist only at the
PDC boundary. The build-time mapping is `Interner`
(`se/compile/src/compile/model/Interner.java`), which assigns sequential ids in
first-seen order:

```java
public int intern(String key) {          // assigns next sequential id if unseen
    Integer existing = ids.get(key);
    if (existing != null) return existing;
    int id = names.size();
    ids.put(key, id);
    names.add(key);
    return id;
}
```

It is **build-time only and single-thread-confined**; callers normalize (case-fold,
trim) before interning. The per-namespace tables are bundled into `Interners`
(world / trigger / suppress / cooldown-scope) and frozen into the `Snapshot`. At
runtime, the stable-key → dense-id lookup against the frozen snapshot is
[`StableKeyIndex#idOf`](compiler-and-config.md#stable-keys-vs-dense-ids), used by
the worn resolver above. The net effect: every hot-path comparison is an int
compare, and `Ability#firesOn` / `blockedInWorld` are single bitset AND-tests.

## Declaring an `Affinity`

An effect's [`Affinity`](effect-engine.md#affinity-routing-and-the-dispatch-plan)
declares where its work may run; the compiler folds the MAX over an ability's
effects and the dispatcher routes by it. The per-hop *cost* is the perf concern:

| Affinity | Hops (Paper + Folia) |
| --- | --- |
| `CONTEXT_LOCAL` (`DAMAGE`, `REDUCTION`, self-`POTION`, `MESSAGE`, `SOUND`) | **0** — inline on the firing region thread |
| `TARGET_ENTITY` / `REGION` / `AOE` | **~1 per distinct target thread**, batched |
| defense-side victim mutation (heal-on-hit, dodge, warp) | **1 hop on Folia** — stated honestly, not "zero" |

Declare the *lowest* affinity that is correct for your effect: a self-buff is
`CONTEXT_LOCAL` and costs nothing; reaching another entity is `TARGET_ENTITY`.
Note the routing is by *target ownership* regardless of the declared value, so a
mis-declared affinity is a perf bug, not a correctness bug. Dynamic victim facts
(`%victim.health%`, pose) are captured at event entry on the firing region or read
from the immutable `WornState` — **never** a live cross-region victim read.
Deferred/cross-region intents snapshot the primitives they need into an immutable
carrier before the plan flushes, so a pooled intent is never aliased after `run`
returns.

## The enforcement gate

The [architecture spec](../../architecture.md) §8 specifies a hard gate — *"the
number is the spec"* — with two halves:

- An **ArchUnit / CI lint** that rejects the banned symbols above
  (`Bukkit.getScheduler()`, `new NBTItem`, `ItemStack#clone`, `String#split`,
  regex compile, YAML access, direct entity mutation) inside the `se-engine`
  hot-path packages.
- A **JMH bench** asserting roughly zero steady-state allocation on the per-hit
  pipeline plus a throughput floor; a regression fails the build.

Status, stated honestly: **these two are specified design intent, not yet wired
into the build.** There is currently no ArchUnit dependency and no `@Benchmark`
code. What enforces the budget *today* is structural and conventional:

- The `Sink` / `Affinity` design **removes** the scheduler door — the door to
  `Bukkit.getScheduler()` lives only in the sanctioned scheduler backend
  (`se/platform/src/platform/sched/`), not in effect code — so a Folia bug is hard
  to write in the first place.
- `se-tester` auto-generates a real cross-region integration test for every
  `TARGET_ENTITY` / `REGION` / `AOE` effect, run on the Paper **and** Folia
  matrix (see [the matrix gate](https://owengregson.github.io/StarEnchants/)).
- javac runs with `-Xlint:all`, and the doc-drift tests keep generated artifacts
  honest.

When you add hot-path code, treat the banned-symbols table as the contract even
though the lint is not yet mechanical — and verify on the real matrix, never on a
green unit-test banner.

## Where it lives

| Concern | File |
| --- | --- |
| Per-hit loop | `se/engine/src/engine/run/AbilityExecutor.java` |
| Trigger-pass primitive | `se/feature/src/feature/trigger/TriggerRunner.java` |
| Item-view cache | `se/item/src/item/view/ItemViewCache.java`, `item/view/ItemView.java` |
| Worn-set resolve + flatten | `se/item/src/item/worn/WornResolver.java`, `worn/WornFlattener.java` |
| Worn state | `se/item/src/item/worn/WornState.java`, `worn/WornStateStore.java` |
| Interning | `se/compile/src/compile/model/Interner.java`, `model/Interners.java` |
| Affinity | `se/compile/src/compile/model/Affinity.java` |
| Sink / dispatcher | `se/engine/src/engine/sink/{Sink,DispatchSink,DispatchPlan}.java` |
| Scheduler door | `se/platform/src/platform/sched/Scheduling.java` |
| Perf spec | [`docs/architecture.md`](../../architecture.md) §8 |

## Gotchas and invariants

- **No allocation on the hit.** No `new` of significant size, no boxing, no
  collections in the loop. The `FactBuffer` is reused per thread; intents are the
  only sanctioned per-hit allocation, and they are batched and routed.
- **Never re-read an item on the hit.** Use `ItemViewCache#of`; the worn state is
  resolved at equip time, not per hit.
- **Key the cache by content + generation, never `ItemMeta` identity.** Identity
  misses and can alias a stale view.
- **Resolve worn sources once per equip change**, never per hit. Set/omni/crystal
  cost nothing on the hit because they are pre-flattened.
- **Stable strings live only at the PDC boundary.** Everything inside is a dense
  int.
- **Declare the lowest correct `Affinity`.** It is the per-hop cost; routing is by
  target ownership regardless, so over-declaring just adds hops.
- **The banned-symbols table is the contract** even though the ArchUnit/JMH gate
  is not yet mechanical. Verify on the real Paper + Folia matrix.

Adjacent reading: [the effect engine](effect-engine.md) for the loop and the Sink;
[feature interactions](feature-interactions.md) for the single-thread fold scratch;
[the compiler](compiler-and-config.md) for interning and stable-key resolution;
and [architecture spec](../../architecture.md) §5 and §8 for the cache, resolver,
and performance model rationale.
