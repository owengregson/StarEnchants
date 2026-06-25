# The item-data model

StarEnchants keeps exactly **one** item-state layer. An item carries only
*identity + counters* in its PDC (Persistent Data Container); the *behaviour*
those names point at lives in the compiled `Snapshot`, never on the item. Lore
and display names are **rendered from state, never parsed back** — the on-item
bytes are the source of truth, the text is a projection. This document maps the
`se/item` module: the compact PDC codec, the versioned keys, the content-hash
`ItemView` cache, the stable-key ↔ dense-id indirection, the component stores,
the `WornState` resolver, render-from-state lore, and lazy legacy migration.

It implements [`docs/architecture.md`](../../architecture.md) §4–§5 and
[ADR-0005](../../decisions/0005-item-data-pdc.md) (item data in PDC) and
[ADR-0016](../../decisions/0016-content-format-v2.md) (the on-item format).

> For *what* an item carries and *how* it interacts with combat, see the
> sibling docs and the operator-facing
> [docs site](https://owengregson.github.io/StarEnchants/). For the compiler
> that produces the `Snapshot`, see
> [config-packs.md](config-packs.md) and the architecture spec §10.

## Where it lives

| Concern | File |
| --- | --- |
| Versioned `NamespacedKey`s | `se/item/src/item/codec/ItemKeys.java` |
| Combat state record | `se/item/src/item/codec/CombatState.java` |
| The one compact codec | `se/item/src/item/codec/CombatCodec.java` |
| Soul codec (separate key) | `se/item/src/item/codec/SoulCodec.java` |
| Carrier / crystal / scroll / heroic / slot codecs | `se/item/src/item/codec/*Codec.java` |
| Content-hash cache | `se/item/src/item/view/ItemViewCache.java`, `ItemView.java` |
| Worn-equipment resolver | `se/item/src/item/worn/WornResolver.java` |
| Multi-set + omni resolver | `se/item/src/item/worn/SetResolver.java` |
| Pre-flatten into hit arrays | `se/item/src/item/worn/WornFlattener.java` |
| The immutable resolved state | `se/item/src/item/worn/WornState.java` |
| Per-player store | `se/item/src/item/worn/WornStateStore.java` |
| Render lore from state | `se/item/src/item/render/LoreRenderer.java` |
| Mint identity items | `se/item/src/item/mint/ItemFactory.java` |

## The core invariants

These are the non-negotiables; everything below follows from them.

- **PDC is state only — never DSL or behaviour.** The item names *which*
  definitions apply (by key); the compiled programs live in the `Snapshot`.
- **Stable string keys, never a dense index.** Dense ids are reassigned every
  reload; an item written years ago must still resolve.
- **Cache on content-hash + generation, never on `ItemMeta` identity.** Meta is
  copy-on-write, so an identity key both misses constantly and can alias a stale
  view.
- **Lore renders from state, never parses back.** Display is a deterministic
  projection rebuilt from scratch.
- **`WornState.activeSets` is a SET (`BitSet`), not a single id.** Multi-set and
  omni completion are unrepresentable with one `activeSetId`.
- **Resolve `WornState` on equip change, never per hit.** Source unification is
  paid once at equip time; the hit walks one pre-merged array.
- **Migrate legacy NBT lazily through the same alias maps.** Read legacy on a
  miss, write modern on the next mutation; an unknown key is skipped, never a
  crash.

## Versioned keys: the single key authority

Every on-item PDC entry is keyed by a `NamespacedKey` built once at boot from
`ItemKeys#of(Plugin)`. These strings are the one place the namespace is
authored; they must never drift, or items written under the old namespace stop
resolving.

```java
// se/item/src/item/codec/ItemKeys.java
return new ItemKeys(new NamespacedKey(plugin, "combat"), new NamespacedKey(plugin, "soul"),
        new NamespacedKey(plugin, "carrier"), new NamespacedKey(plugin, "guarded"),
        ...);
```

State is **split across keys by churn**, not by convenience:

- `combat` — the combat-relevant blob (`CombatState`): enchants, crystals, set
  membership, omni flag, heroic stats, purchased slots. This is the only key the
  combat hot path decodes.
- `soul` — soul-gem state (`SoulCodec`). Kept separate because souls change on
  every spend/gain, which would otherwise thrash the content-hash cache below.
- `carrier`, `scroll`, `unopened`, `crystalitem`, `crystalextractor`,
  `heroicupgrade`, `slotitem`, `godlytransmog` — identity/economy items that
  never decode on the combat hot path.
- `guarded` — the white-scroll protection flag, consumed on a failed apply.

`ItemKeys` is the only `Plugin`-aware class in the module; the rest of `se/item`
takes the resolved keys and stays `Plugin`-free.

## The combat codec: one read, one decode

`CombatCodec` (`se/item/src/item/codec/CombatCodec.java`) stores the *whole*
`CombatState` as **one** PDC `STRING` entry, not N keyed reads. On a combat-
relevant miss the path does one read and one decode.

The blob is a self-delimiting, version-tagged, stable-string-keyed string:

```text
v1 US e US <key:level RS key:level …> US c US <key RS key …> US s US <setKey> …
```

where `US` is the unit separator (between sections) and `RS` is the record
separator (between list entries). Labels: `e` enchants, `c` crystals, `s`
armour-set key, `w` weapon-set key, `o` omni flag, `h` heroic flat stats, `a`
purchased slot count. The format is forward-compatible by design:

```java
// se/item/src/item/codec/CombatCodec.java#decodeBlob
String[] tokens = splitOn(blob, US);
if (tokens.length == 0 || !VERSION.equals(tokens[0])) {
    return CombatState.EMPTY; // unknown/legacy format — migrated lazily on next write
}
// ...
// any other label is a newer field this reader does not know — ignore it
```

Decoding **never throws**. A malformed enchant level, a bad heroic field, a
non-numeric slot count — each is warn-and-skipped to a default; one bad field
never poisons the whole item (`CombatCodec#parseEnchants`,
`CombatCodec#parseHeroic`, `CombatCodec#parseAdded`).

`CombatState` (`se/item/src/item/codec/CombatState.java`) is the decoded record.
Two details matter for the cache: enchant order is preserved (an unmodifiable
`LinkedHashMap`, not `Map.copyOf`) so the encoded blob is deterministic, and
crystals are a **`List`** of keys, not a map — crystals stack, fixing a Cosmic
Enchants-style "last of type wins" collapse.

## The `ItemView` cache: content-hash + generation

The combat hot path must not clone-and-parse every armour slot on every hit.
`ItemViewCache` (`se/item/src/item/view/ItemViewCache.java`) caches a decoded
`ItemView` keyed by the **full raw combat blob within the current generation**.

```java
// se/item/src/item/view/ItemViewCache.java#ofBlob
Generation g = current;
if (blob == null || blob.isEmpty()) {
    return g.empty;                         // no-state item: shared empty view, zero allocation
}
ItemView cached = g.byBlob.get(blob);
if (cached != null) {
    return cached;                          // hit: one hash + lookup
}
ItemView decoded = new ItemView(g.gen, codec.decode(blob));
ItemView raced = g.byBlob.putIfAbsent(blob, decoded);
return raced != null ? raced : decoded;     // miss: decode once, intern the contention winner
```

Three choices are load-bearing:

- **Key on content, not `ItemMeta` identity.** Meta is copy-on-write, so an
  identity key misses constantly *and* can alias a stale view.
- **Use the full blob, not a truncated hash.** A truncated-hash collision could
  serve a stale view for a different item.
- **A fresh per-generation map on reload.** `ItemViewCache#reload(int)` swaps a
  whole new `Generation`, so prior views vanish atomically — no stale reads and
  no unbounded growth across reloads. A read racing a reload decodes into the
  doomed old map and simply recomputes next time.

The cache is lock-free across Folia region threads: `ItemView` is immutable, the
generation holder is `volatile`, and the per-generation map is a
`ConcurrentHashMap`. Inspect a live item's decoded view with `/se item dump`.

## Stable-key ↔ dense-id

PDC stores **stable string keys**; the runtime walks **dense int ids**. The
bridge is the `Snapshot`'s `StableKeyIndex`
(`se/compile/src/compile/model/StableKeyIndex.java`), whose `idOf(String)`
returns the dense id for a stable key, or `-1` if the content no longer exists.

Dense ids are a per-snapshot accelerator, reassigned freely on every reload. An
absent key resolves to `-1` and is simply skipped — never a crash. This is why a
years-old item still works after a reload reorders everything: the item names
keys, and the index re-maps those keys to whatever ids this generation chose.

## Component stores: where mutable runtime state lives

Mutable per-player runtime state is never scattered across effect objects — it
lives in a small, enumerable set of **component stores**, each concurrent,
UUID-keyed, TTL-evicting, and cleared on quit and `onDisable`
(architecture §5.4). `WornStateStore` (below) is the canonical example;
cooldowns, soul-mode flags, and disabled-enchant timers follow the same shape.

## `WornState`: the equip-time flatten

`WornState` (`se/item/src/item/worn/WornState.java`) is a player's resolved
equipment — immutable, multi-set, and **pre-flattened** so source unification
costs nothing per hit:

```java
public record WornState(
        int gen,                       // snapshot generation it was built against
        BitSet activeSets,             // SET of active sets — not a single id
        int[] activeCrystalAbilityIds, // crystals as a list source
        HeroicStat heroic,             // heroic flat stats as a source
        int[][] byTrigger,             // per-trigger dense ids, union over ALL sources, ordered
        int[] combatAttack,            // attacker-direction ids, pre-merged
        int[] combatDefense) {}        // defender-direction ids — the safe cross-region read
```

`byTrigger` / `combatAttack` / `combatDefense` are the pre-flattened union over
*all* active sources — enchants, the set bonus, the held weapon bonus, crystals,
heroic. The hit walks one array per direction and never knows there were five
sources.

### Resolution (once per equip change)

`WornResolver#resolve(LivingEntity, Snapshot)` reads each worn + held item once
through the `ItemViewCache`, composes a per-level stable key
(`<base>/<level>`), resolves it to a dense id via the snapshot's
`StableKeyIndex`, and merges:

```java
// se/item/src/item/worn/WornResolver.java#resolveFrom
for (Map.Entry<String, Integer> enchant : combat.enchants().entrySet()) {
    int id = keys.idOf(enchant.getKey() + "/" + enchant.getValue());
    if (id >= 0) {
        mergedIds.add(id);   // unknown content → -1, skipped, never a crash
    }
}
```

Multiplicity is preserved — the same enchant on two pieces contributes twice.
Crystals resolve through `CrystalItemData.componentsOf` so a multi-crystal
(`a+b`) fires each component independently. Sets resolve here too: each piece's
`setKey`/`omni` feeds `SetResolver` (below), and a held weapon adds the
`<setKey>/weapon` bonus only when its parent set is complete *and* the weapon is
held. Per-feature master toggles (`config.yml` `features:`) are read live, so a
disabled feature's source is skipped on the next resolve.

### Multi-set + omni

`SetResolver#activeSets` (`se/item/src/item/worn/SetResolver.java`) computes the
*set* of active sets. Several can be active at once — a single-`activeSetId`
model cannot represent that. Omni pieces are wildcards that count toward every
*partially-worn* set, but an omni alone cannot conjure a set from nothing:

```java
// only sets with ≥1 real piece are eligible
int worn = entry.getValue() + Math.max(0, omniCount);
if (required > 0 && worn >= required) {
    active.set(setId);
}
```

### Pre-flatten

`WornFlattener#flatten` (`se/item/src/item/worn/WornFlattener.java`) organises
the merged ids into the per-trigger index and the two combat-direction arrays,
preserving the resolver's merge order and multiplicity (a stacked crystal runs —
and folds — once per stack).

### Storage and the cross-region read

`WornStateStore` (`se/item/src/item/worn/WornStateStore.java`) is a concurrent
`UUID → WornState` map. `refresh` re-resolves on the entity's **own** thread and
stores the immutable result; the hot path only `get`s it. Because `WornState` is
immutable, an attacker on Folia reads the victim's `combatDefense` with no lock
and no wrong-thread access — it reads the stored snapshot, never the victim's
live equipment. Resolution is event-driven (`PlayerArmorChangeEvent` + held-item
change), debounced per tick — see [folia-scheduling.md](folia-scheduling.md).

## Rendering lore from state

`LoreRenderer` (`se/item/src/item/render/LoreRenderer.java`) projects
`CombatState` into lore lines and never the reverse. It is rebuilt from scratch
on every render, so there is nothing to "round-trip" and corrupt:

```java
// se/item/src/item/render/LoreRenderer.java#lines
String name = nameOr(enchant.getKey(), style);          // unknown key → unknownLabel, never a crash
String level = style.roman() ? Numerals.roman(enchant.getValue()) : Integer.toString(enchant.getValue());
String tierColor = enchantColorOf.apply(enchant.getKey());
String color = tierColor != null && !tierColor.isBlank() ? tierColor : style.enchantColor();
out.add(Colors.translate(color + name + " " + style.levelColor() + level));
```

The display lookup and style are injected (`Supplier<LoreStyle>` re-read per
render so a `/se reload` takes effect next render), keeping `lines` pure and
server-free; only `apply` touches Bukkit. An unknown stored key renders as the
configured unknown label.

## Lazy legacy migration

Legacy NBT is migrated **lazily and losslessly** through the *same* alias maps
the compiler uses (see [cross-version-api.md](cross-version-api.md)). On a read
that does not recognise the version tag, the codec returns `CombatState.EMPTY`
and the item is left untouched; the modern blob is written only on the next
mutation. An unknown key is ignored, never fatal. This means an old item keeps
working across the whole 1.17.1 → 26.1.x range without a migration sweep.

## Gotchas

- **Never widen the combat blob to a second key for convenience.** The single
  blob is the cache key; splitting it would mean two reads on the hot path.
- **Souls (and other high-churn state) must stay on their own key.** Folding
  them into `combat` would invalidate the content-hash cache on every spend.
- **Never store a dense id on an item.** Ids are per-generation; only stable
  keys survive a reload.
- **Never parse lore back into state.** Lore is output-only; mutate
  `CombatState` and re-render.
- **`activeSets` is a `BitSet`.** Resist the temptation to "simplify" it to one
  id — omni and multi-set break immediately.
- **Resolve `WornState` off the hit.** Calling `WornResolver#resolve` per hit
  defeats the entire flatten design and, on Folia, risks a cross-region read.
