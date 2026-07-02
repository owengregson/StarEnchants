# Surviving 1.17.1 → 26.1.x

StarEnchants is content-heavy: every enchant, armour set, and item config names
Materials, Sounds, Particles, Enchantments, PotionEffectTypes, Attributes, and
EntityTypes as **strings**. Those Bukkit enums were renamed across the nine-year
version range, and several became registry-backed interfaces. A config authored
on one era — including imported Cosmic Enchants-style configs — must load on any
server in the range. The rule that makes this work: **never hard-reference a
volatile constant**; resolve every such token by name through a resolver that
knows the aliases, *at compile time*, and intern the result so the runtime only
ever sees a stable id. This document maps `se/platform`'s resolver layer.

It implements [`docs/architecture.md`](../../architecture.md) §9 and
[ADR-0008](../../decisions/0008-cross-version-and-folia.md)
(cross-version + Folia) and [ADR-0002](../../decisions/0002-version-targets.md)
(version targets).

> For the Folia half of `se/platform`, see
> [folia-scheduling.md](folia-scheduling.md). For how these alias maps are reused
> when importing legacy configs, see [the-migrator.md](the-migrator.md). For how
> the on-item bytes survive the range, see
> [item-data-model.md](item-data-model.md).

## Where it lives

| Concern | File |
| --- | --- |
| The alias maps (the rename knowledge) | `se/platform/src/platform/resolve/Aliases.java` |
| The bidirectional resolve strategy | `se/platform/src/platform/resolve/HandleResolver.java` |
| Resolve-and-intern machinery | `se/platform/src/platform/resolve/RenameResolvers.java` |
| Production resolver (live server) | `se/platform/src/platform/resolve/RegistryResolvers.java` |
| Test/pure resolver (fixed vocabulary) | `se/platform/src/platform/resolve/VocabularyResolvers.java` |
| The live reflective Bukkit lookup | `se/platform/src/platform/resolve/RegistrySupport.java` |
| Runtime id → object round-trip | `se/platform/src/platform/resolve/RuntimeHandles.java` |
| Folia + version probe | `se/platform/src/platform/caps/Capabilities.java` |

## The break table

The surfaces that move, the version they move at, and a few aliases as written
in `Aliases.java`:

| Surface | Break | Example (legacy → modern) |
| --- | --- | --- |
| `Enchantment` | enum → registry-backed; renamed at **1.20.5** | `DAMAGE_ALL → SHARPNESS`, `DURABILITY → UNBREAKING`, `PROTECTION_ENVIRONMENTAL → PROTECTION` |
| `PotionEffectType` | registry-backed; renamed at **1.20.5** | `CONFUSION → NAUSEA`, `SLOW → SLOWNESS`, `INCREASE_DAMAGE → STRENGTH` |
| `Particle` | renamed at **1.20.5** (still an enum) | `VILLAGER_HAPPY → HAPPY_VILLAGER`, `SPELL_WITCH → WITCH`, `REDSTONE → DUST` |
| `Attribute` | enum → registry interface at **1.21.3**; `GENERIC_` prefix dropped | `GENERIC_MAX_HEALTH → MAX_HEALTH`, `GENERIC_ATTACK_DAMAGE → ATTACK_DAMAGE` |
| `Sound` | enum → interface at **1.21.3** | names mostly stable; never store the constant |
| `Material` | pre-1.13 legacy aliases in configs | `SULPHUR → GUNPOWDER`, `GRASS → SHORT_GRASS`, `WOOL → WHITE_WOOL` |
| `EntityType` | a few renames | `PIG_ZOMBIE → ZOMBIFIED_PIGLIN`, `SNOWMAN → SNOW_GOLEM` |

Two structural events anchor the range. The **1.20.5 mapping flip** (runtimes
become Mojang-mapped) and the **1.21.3** type-shape change for `Attribute` and
`Sound`. The compiler routes all of this through resolvers so the runtime never
touches a renamed constant.

## The two-phase model

Resolution is split deliberately:

1. **Compile time — token → id.** The compiler runs every config token through
   `RenameResolvers`, which resolves the name (with aliases) and interns it to a
   dense int id. This happens once, at load, never on the hot path.
2. **Runtime — id → object.** `RuntimeHandles` turns an interned id back into a
   live Bukkit object, caching so the volatile reflective lookup runs at most
   once per handle.

The runtime only ever sees interned ids, never a renamed constant.

## The alias maps

`Aliases` (`se/platform/src/platform/resolve/Aliases.java`) is the single home
for rename knowledge. Each category is a `Map<String, String>` of upper-cased
legacy → modern names, retrieved through one switch:

```java
// se/platform/src/platform/resolve/Aliases.java
public static Map<String, String> forCategory(HandleCategory category) {
    return switch (category) {
        case POTION_EFFECT -> POTION_EFFECT;
        case ENCHANTMENT   -> ENCHANTMENT;
        case ENTITY_TYPE   -> ENTITY_TYPE;
        case MATERIAL      -> MATERIAL;
        case ATTRIBUTE     -> ATTRIBUTE;
        case SOUND         -> SOUND;
        case PARTICLE      -> PARTICLE;
    };
}
```

The `HandleCategory` enum keys every map and cache:
`MATERIAL, ENCHANTMENT, POTION_EFFECT, ENTITY_TYPE, ATTRIBUTE, SOUND, PARTICLE`.
The attribute map carries the 1.21.x prefix-drop entries with an explicit
comment (`GENERIC_` / `HORSE_` / `ZOMBIE_` prefixes dropped). The same maps are
reused by the migrator to normalise imported configs.

## The resolve-by-name strategy

`HandleResolver#resolve` (`se/platform/src/platform/resolve/HandleResolver.java`)
is the pure, bidirectional strategy. It tries, in order: the token as given, its
forward alias (legacy → modern), then a reverse scan (a modern token on an older
server). An unresolved token returns `Optional.empty()` — the caller
warn-and-skips, the resolver never warns or crashes:

```java
// se/platform/src/platform/resolve/HandleResolver.java#resolve
if (exists.test(norm)) {
    return Optional.of(norm);                   // 1. token as given
}
String forward = aliases.get(norm);
if (forward != null && exists.test(forward)) {
    return Optional.of(forward);                // 2. legacy → modern
}
for (Map.Entry<String, String> entry : aliases.entrySet()) {
    if (entry.getValue().equals(norm) && exists.test(entry.getKey())) {
        return Optional.of(entry.getKey());     // 3. modern token on an older server
    }
}
return Optional.empty();
```

The bidirectional reverse scan is what lets a config authored on a *new* server
load on an *old* one: a modern name with no direct match is mapped back to the
legacy spelling that does exist there.

## Resolve once, intern, cache

`RenameResolvers` (`se/platform/src/platform/resolve/RenameResolvers.java`) is
the shared machinery behind every resolver. It runs each token through
`HandleResolver`, interns the resolved name to a dense id (one `Interner` per
category, in an `EnumMap`), and keeps the id ↔ name mapping. A concrete resolver
supplies only what `exists` means:

```java
// se/platform/src/platform/resolve/RenameResolvers.java#resolve
Optional<String> resolved = HandleResolver.resolve(
        token, Aliases.forCategory(category), name -> exists(category, name));
return resolved.map(name -> OptionalInt.of(interners.get(category).intern(name)))
        .orElseGet(OptionalInt::empty);
```

It exposes one method per surface — `material`, `sound`, `potionEffect`,
`particle`, `enchantment`, `entityType`, `attribute` — each returning an
`OptionalInt` of the interned id, plus `nameOf(category, id)` for the reverse.

Two concrete implementations:

- **`VocabularyResolvers`** — `exists` is an explicit per-category vocabulary.
  The pure, server-free core the compiler uses in unit tests, so the whole
  bundled library can be compiled against a fake `PlatformResolvers` with
  realistic alias-aware behaviour (the bootstrap `CatalogValidationTest` /
  `CosmicPackValidationTest`, run inside `./gradlew build`).
- **`RegistryResolvers`** — `exists` is a real, live-server lookup
  (`RegistrySupport.exists`). One instance is built at boot and injected into the
  compiler, so every interned handle is guaranteed to exist on this exact server.

## The live reflective lookup

`RegistrySupport` (`se/platform/src/platform/resolve/RegistrySupport.java`) is
the single place that touches the live, version-volatile Bukkit surface. It
hard-references only what is stable across the whole range; for everything that
moved it looks up the `Registry` constant by reflection and falls back to a
reflective `valueOf` / static-field read so a now-interface type is never
hard-linked. Every probe degrades to `null`, never a crash:

```java
// se/platform/src/platform/resolve/RegistrySupport.java#lookup
case ATTRIBUTE -> firstNonNull(
        registryLookup("ATTRIBUTE", key(canonicalName)),
        registryLookup("ATTRIBUTE", "generic." + key(canonicalName)), // pre-1.21.3 prefix
        enumValueOf("org.bukkit.attribute.Attribute", canonicalName));
case SOUND -> firstNonNull(
        enumValueOf("org.bukkit.Sound", canonicalName),   // enum era (≤1.21.2)
        staticField("org.bukkit.Sound", canonicalName),   // interface-with-constants era (1.21.3+)
        registryLookup("SOUNDS", key(canonicalName).replace('_', '.')));
```

```java
} catch (Throwable failedProbe) {
    // any reflective/linkage failure means "not resolvable here" — caller warn-skips
    return null;
}
```

This is where each version break is *absorbed*: the 1.20.5 enum→registry flip
for `Enchantment`/`PotionEffectType` (registry probe with a `getByName` /
`getByKey` floor), and the 1.21.3 `Attribute`/`Sound` interface change
(`generic.` dual-probe; the `valueOf → staticField → registry` chain for Sound).

## The runtime round-trip

`RuntimeHandles` (`se/platform/src/platform/resolve/RuntimeHandles.java`)
completes `token → id (compile) → object (runtime)`. It caches id → object in a
per-category `ConcurrentHashMap` so the reflective lookup happens at most once
per handle, and is read concurrently from Folia region threads:

```java
// se/platform/src/platform/resolve/RuntimeHandles.java#resolve
Object cached = categoryCache.get(id);
if (cached != null) {
    return cached;
}
String name = resolvers.nameOf(category, id);
if (name == null) {
    return null;                       // unresolved → caller warn-skips
}
Object object = RegistrySupport.lookup(category, name);
if (object != null) {
    categoryCache.put(id, object);     // resolve once
}
return object;
```

Typed accessors (`material(int)`, `enchantment(int)`, `potionEffect(int)`, …)
cast the cached object. `resolveByName` is the uncached cold path for referents
needed by well-known name.

## Capabilities: probe, don't parse-and-branch

`Capabilities` (`se/platform/src/platform/caps/Capabilities.java`) is the
boot-time snapshot. Folia is detected by a **class probe**, not a version string;
version is a tolerant parse used for capability gates, never inline `if`s:

```java
// se/platform/src/platform/caps/Capabilities.java
static final String FOLIA_MARKER = "io.papermc.paper.threadedregions.RegionizedServer";

public static boolean foliaPresent() {
    try {
        Class.forName(FOLIA_MARKER);
        return true;
    } catch (ClassNotFoundException notFolia) {
        return false;
    }
}
```

`mojangMapped()` (`return atLeast(1, 20, 5);`) is the one explicit reference to
the mapping flip — the runtime only ever touches the Bukkit API and never needs
mappings, but cross-version tooling and the
[reference cache](../../reference/) do. `parseVersion` tolerates the
`-R0.1-SNAPSHOT` suffix and a missing patch, returning `{0,0,0}` on garbage,
never throwing.

## Gotchas

- **Never write `Particle.VILLAGER_HAPPY` (or any volatile constant) in a
  field.** It will not compile on the floor API or will not exist at runtime on
  some version. Resolve by name.
- **Resolution is a compile phase, not a runtime one.** Putting a name lookup on
  the combat hot path is the mistake the whole layer exists to prevent.
- **An unknown token is a warn-and-skip, never a fatal.** A single missing
  effect must never take the load down — degrade that one op.
- **Aliases are bidirectional.** Add new renames to `Aliases` in the
  legacy → modern direction; the reverse scan handles the other direction for
  free.
- **Confirm a genuine member miss before assuming an alias is wrong.** Read the
  actual server jar (the [reference cache](../../reference/)) rather than
  guessing a rename.
