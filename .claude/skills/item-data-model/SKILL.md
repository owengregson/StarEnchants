---
name: item-data-model
description: Use when working on item state, the PDC codec, the ItemView content-hash cache, the stable-key to dense-id map, component stores, the WornState resolver, lore/name rendering, or legacy item migration.
---

# Item data model

`se-item` is the one item-state layer. An item carries **identity + counters in
PDC** under versioned `NamespacedKey`s; behavior lives in the compiled `Snapshot`,
never on the item. Lore/name are **rendered from state, never parsed back** (§4.2).

## When to use / not

Use for the PDC codec, `ItemView` caching, stable-key↔dense-id, stores, `WornState`,
rendering, and legacy NBT migration. NOT for ability compilation (**effect-engine**)
or damage/suppression/soul/slot resolution (**feature-interaction-rules**). See also
**config-and-migration**, **performance-hot-paths**, **cross-version-item-api** (alias
maps), and **folia-scheduling** (the cross-region victim read).

## Core rules

| Rule | Why | § |
| --- | --- | --- |
| PDC = state only (identity + counters), never DSL/behavior | item names *which* defs by key; programs live in the Snapshot | 4.2 |
| **Stable string keys** in PDC, never a dense index | dense ids reorder every reload; an old item must still resolve | 4.2, 5.3 |
| Crystals are a **list** of keys; souls keyed by **PDC UUID** | fixes a Cosmic Enchants-style last-of-type collapse and slot-reorg loss | 4.2 |
| Cache on **content-hash + generation**, NOT ItemMeta identity | meta is copy-on-write → misses constantly *and* can alias (stale view) | 5.2 |
| Use a **full (untruncated) hash** or write-gen counter | a truncated hash collision could serve a stale view | 5.2 |
| Two records: combat vs economy/identity | identity items (scroll/dust/crate) never decode on the combat hot path | 5.1 |
| `WornState.activeSets` is a **SET** (BitSet) | multi-set + omni completion is unrepresentable with one `activeSetId` | 5.5 |
| `WornState` immutable, pre-flattened, swapped by ref | the safe cross-region victim read on Folia — read only this, never the live victim ItemStack | 5.5, 3.6 |
| Resolve `WornState` on **equip change**, never per hit | event-driven (`PlayerArmorChangeEvent` + held-item change); debounced per tick | 5.5 |
| Migrate legacy NBT lazily through the **same alias maps** | read legacy on miss, write modern on next mutation; unknown key → skip, never crash | 4.3, 5.3 |

`byTrigger` / `combatAttack` / `combatDefense` are the pre-flattened **union over
all active sources** (enchants + set + weapon + crystals + heroic), ordered — the
hit walks one array and never knows there were five sources (§5.5).

## The shape

```java
// se-item/worn — built once per equip change, IMMUTABLE (§5.5)
record WornState(
    int        gen,                     // Snapshot generation it was built against (§5.3)
    BitSet     activeSets,              // *** SET, not a single id *** → multi-set + omni (§6.6)
    int[]      activeCrystalAbilityIds, // crystals are a LIST source
    HeroicStat heroic,                  // flat reduction/damage/durability as a source
    int[][]    byTrigger,               // per-trigger dense ability ids, union over ALL sources, ordered
    int[]      combatAttack,            // attacker direction, pre-merged
    int[]      combatDefense) {}        // defender direction — safe cross-region read

// se-item/view — content-hash + generation cache (§5.2)
ItemView v = ItemView.of(stack);   // hit = one hash + lookup; miss = one compact decode
```

`ItemView.of` reads the relevant PDC bytes, computes the full content hash, and
returns `cache[hash, gen]` if present, else decodes once. PDC stays **stable-key**;
the dense `Ability.id` is a per-snapshot accelerator resolved through the persistent
stable-key→id map (§5.3), reassigned freely each reload. Inspect with `/se item dump`.

**Component stores** (§5.4) hold mutable runtime state — never scattered in
effect objects. Each of the seven (`CooldownStore`, `WornStateStore`, ...) is
concurrent, UUID-keyed, TTL-evicting, and cleared on quit + `onDisable`.
