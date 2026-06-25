# A 1.8.9 fork with minimal separation — feasibility & blueprint

> **Status: research / hypothetical.** This records a focused investigation (three
> independent critical agents, 2026-06-25) into the *narrow* question: **if** a
> 1.8.9-supporting StarEnchants ever shipped, how could it share the **maximum
> possible source** with the modern plugin — including auto-downgrading the Java 17
> source to Java 8 — rather than being a hand-written second plugin? It does **not**
> change the shipping floor, which stays at **1.17.1** (ADR-0002, ADR-0018). It
> supersedes the blunt "separate fork, don't bother" framing of the earlier
> feasibility note with a measured share-ratio and a concrete seam blueprint.

## TL;DR

A minimal-separation 1.8.9 fork is **more plausible than "a whole second plugin,"
but it is not free and it is not one jar.** Two walls are independent:

1. **The language wall** (Java 17 → Java 8) — *mechanically solvable.* An automated
   bytecode downgrader (JvmDowngrader) can lower the existing record/sealed/
   switch-expression-saturated bytecode to loadable-on-Java-8 classes. This
   eliminates the *re-language*, so the source tree can be shared.
2. **The platform wall** (Bukkit/NMS APIs that don't exist pre-1.14/1.13/1.9) — *not*
   solved by any tool. PDC, the flattening, attributes/off-hand/particles, and the
   armour-change event all need version-specific implementations.

The good news the prior note missed: StarEnchants' own architecture — interned
**int** handles on the `Sink`, a version-neutral **String blob** in the item codec,
resolver/Scheduling seams — means most of the platform wall is **shimmable behind
the existing interfaces without changing their signatures.** Measured against the
real code, **~60–64% of production LOC is genuinely shareable**, **~8% is a thin
forked impl behind an unchanged interface**, and **~31% is a true rewrite** (the
feature shells, integrations, command/lifecycle, tester).

**The one genuine fork-maker is the item-data layer** — not PDC's absence (cleanly
shimmable to NMS NBT on the single fixed `v1_8_R3` package) but the 1.8 behaviour
where `CraftItemStack.setItemMeta()` **wipes custom NBT**, which collides head-on
with the "lore is rendered from state" invariant that re-stamps ItemMeta constantly.

Realistic effort for a 1.8.9 fork built this way: still **multi-person-week**
(the rewrite third + the item-pipeline surgery + standing up a 1.8 integration
gate from scratch), but with a *shared, auto-downgraded core* rather than a parallel
codebase. 1.7.10 remains not worth it. **Recommendation: do not lower the floor;
if legacy reach is ever required, this blueprint is how to share the most code.**

---

## 1. The language wall is mechanically solvable (auto-downgrade)

Measured at HEAD: **103 files declare a `record`, 5 declare `sealed` interfaces,
~96 use switch-arrow, 30 use text blocks, 189 call sites use `List/Map/Set.of`.**
Java 17 class files do not load on the Java 6–8 JVMs of 1.8.9 servers, and
`--release 8` *rejects* records/sealed outright — so the language features that are
the load-bearing fabric of the data-oriented design block a naïve recompile.

**JvmDowngrader (JDG)** — by wagyourtail / William Gray under the `unimined` org, a
Minecraft-ecosystem tool; Gradle plugin `xyz.wagyourtail.jvmdowngrader` 1.3.6
(2026-01-21). *(Note: not "Lenni0451" — that was the now-discontinued predecessor,
RaphiMC's JavaDowngrader, which points users to JDG.)* It transforms **compiled**
Java 22→8 bytecode: records lowered to ordinary classes (synthesised canonical
ctor / accessors / `equals`/`hashCode`/`toString`, `java.lang.Record` supertype
removed), sealed `PermittedSubclasses` stripped, switch-expressions / pattern-
`instanceof` / text blocks desugared, and a shaded **API shim jar** that backports
the common post-8 runtime calls (`List.of`, `Optional.isEmpty`, `String.isBlank/
strip/repeat`, `Stream.toList`, NIO additions).

**The decisive good-news fact for THIS codebase:** the usual record-downgrade
landmine is record-component *reflection* (`getRecordComponents`/`isRecord`),
which has no Java 8 equivalent — and the codebase uses **zero** of it (verified).
So records downgrade faithfully here.

What auto-downgrade does **not** do:

- It does not provide *missing server APIs* (PDC, Attribute, modern Particle) — those
  are Bukkit/NMS, invisible to a bytecode tool. **It eliminates the re-language, not
  the re-platform.**
- Coverage of the runtime-API shim is *by-used-method shading*; with 189 `*.of`
  sites plus scattered `isBlank/strip/toList`, **a single un-shimmed API is a
  `NoSuchMethodError` that compiles, downgrades and shades green and only crashes at
  runtime** — and there is no 1.8.9 integration gate today to catch it. This is the
  defining hazard of the approach.

Alternatives ruled out: **Jabel** (source-level) accepts Java 9+ *syntax* targeting
Java 8 but **cannot do records or sealed** (both reified) and backports no APIs;
**Retrolambda** predates records entirely; **D8/R8 desugaring** emits DEX, not JVM
classfiles; **ProGuard/R8** are not version downgraders. JDG is the only viable path.

Build shape: compile the core once at Java 17 (today's jar), then a second pipeline
runs JDG `DowngradeJar` **per module** and `ShadeJar` on the merged mono-jar (this
order is mandatory for a multi-module project, per the plugin's own warning), with
the shim relocated under its own root to respect the flat single-segment-package
invariant. Output: one extra `*-java8.jar`. Cheap in CPU; pin JDG exactly for
determinism.

## 2. What shares, measured against the real code

Total production source: **~39.6k LOC across 13 modules.** Classification (grep-verified):

| Bucket | What | ~LOC | ~% of shareable |
| --- | --- | --- | --- |
| **SHARE-AS-IS** (compile to 8 via downgrader) | `schema`, `compile`, `migrate`, `pack`, `api`, engine pure subpkgs (condition/stores/spec/trigger/interact/boot/doc) | ~12,500 | ~38% |
| **SHARE-BEHIND-SEAM** (shared source, impl forks behind an *unchanged* interface) | engine effect/selector kinds (only `LivingEntity`/`Player`/`Location` + `Sink`), `item` codec blob logic, `platform` interfaces, Sink/run wiring | ~8,500 | ~26% |
| **THIN FORKED IMPL** (behind a shared interface) | `DispatchSink`, `RegistrySupport`, codec PDC accessors, `RuntimeHandles` casts, `WornResolver` body | ~2,500 | ~8% |
| **TRUE REWRITE** | `feature` (listeners + 20 GUIs + services), `integrate`, `bootstrap` cmd+main, `api` recompile | ~10,300 | ~31% |

**Bottom line: ~60–64% genuinely shareable, ~8% thin fork, ~31% rewrite** — a
material improvement over the prior note's "only `schema`+`compile` reuse." The
difference is that the prior note counted only zero-interface-contact code; the real
seam discipline lets the entire engine *kind* layer and the codec *format* layer
share too, because the interfaces they cross **already don't leak version types.**

What makes it possible — the seam discipline already in the tree:

- **`Sink` interns every volatile referent to an `int`** (`potion(target,int effectId)`,
  `particle(at,int particleId)`, `spawnEntity(at,int entityTypeId)`, `blockChange(at,
  int blockDataId)`). Only `LivingEntity`/`Player`/`Entity`/`Location`/`UUID` appear —
  all present on 1.8.9. A 1.8.9 `DispatchSink` is a fresh impl behind the **unchanged**
  `Sink`.
- **The item codec produces a self-delimiting String blob** (`CombatCodec.encodeBlob/
  decodeBlob`, pure, unit-tested). The blob format and lazy-migration share verbatim.
- **`SchedulerBackend`/Scheduling already degrade to plain Bukkit** — the Folia branch
  simply never fires on 1.8 (Capabilities.folia()==false). Zero change.

Irreducibly fork-only files: `engine/sink/DispatchSink.java`,
`platform/resolve/RegistrySupport.java` (NamespacedKey/Registry are 1.14),
`item/codec/ItemKeys.java` + the PDC accessor halves of the codecs, all of
`feature/**` and `integrate/**`, `bootstrap` `SeCommand`+plugin main,
`feature/combat/EquipListener.java` (no `PlayerArmorChangeEvent`), `compat-folia/**`
(dropped), `tester/**` (re-forked against 1.8 server jars).

## 3. The API walls: 4 of 5 shim cleanly; 1 forces a fork

| Wall | Exists 1.8.9? | Shim behind existing iface? | Admin-visible degrade | Effort |
| --- | --- | --- | --- | --- |
| **PDC + NamespacedKey** (1.14) | No (raw NBT) | **Partial — poisoned** (see below) | enchants vanish on any re-render unless pipeline changes | **L** |
| **Flattening / Material-by-name** (1.13) | No (id+data byte) | **Yes (mostly)** — resolver already name-keyed; data byte hides in the interned handle | post-1.8 materials no-op (warn+skip) | **M** |
| **Attributes** (1.9) | No (NMS `GenericAttributes`) | **Yes** — behind `Sink.addMaxHealth`/`movementSpeed` | rougher modifiers, fewer kinds | **M** |
| **Off-hand** (1.9) | No | **Partial** — needs a `WornAccess` seam; returns AIR | off-hand carries no enchants | **S** |
| **Particles** (1.9) | No (`PacketPlayOutWorldParticles`) | **Yes** — behind `Sink.particle` | smaller palette, nearest-or-none | **M** |
| **Action bar** | No (Spigot API) | **Yes** — `PacketPlayOutChat(component,(byte)2)` | none if shimmed | **S** |
| **PlayerArmorChangeEvent** (Paper 1.15+) | No | **Yes** — synthesise (inv-click + interact + tick poll) → same `refresh(player)` | 1-tick/poll latency; exotic equip vectors miss | **M** |
| **Scheduling / Folia** | Bukkit yes; Folia N/A | **Yes — already the degraded form** | none | **free** |

A counter-intuitive simplification: on 1.8.9 there is exactly **one** server package,
`v1_8_R3`, that never moves — so the NMS shims can hard-reference
`net.minecraft.server.v1_8_R3.*` / `org.bukkit.craftbukkit.v1_8_R3.*` directly, no
reflection, no capability gating. That half is *easier* than the modern 1.17→26.1
range with its 1.20.5 mapping flip.

### The one genuine fork-maker: `setItemMeta` strips NBT on 1.8

PDC itself shims cleanly to NMS NBT. The problem is behavioural: on 1.8,
`CraftItemStack.setItemMeta()` **rebuilds the tag from the ItemMeta alone and
discards any custom NBT.** StarEnchants renders lore from state and re-stamps
ItemMeta constantly (every lore re-render, name change, durability touch) — so on
1.8 each of those calls **silently wipes the combat blob the codec just wrote.** A
read/write-blob shim isn't enough; you must (a) re-plumb item mutation to preserve
NMS tags across every ItemMeta reset, and (b) widen the codec's public surface off
the concrete `PersistentDataContainer`/`NamespacedKey` types. That is a behavioural
**and** signature change in `se/item` — a fork of the data layer, not a hidden shim.

### Fidelity if built: ~70% faithful / ~20% silently degraded / ~10% impossible

Faithful: all scheduling; the combat math/arbiters (pure, version-agnostic); damage/
heal/kill/knockback/teleport; sounds; action bar/title/message; max-health & speed via
NMS; potions; the entire compiler/snapshot/key machinery; set/omni/crystal resolution;
persistent NBT **if** the pipeline is fixed. Degraded: particles (small palette),
material-dependent effects, equip latency, fewer attribute kinds, block/blockdata
effects. Impossible: off-hand enchants & off-hand scroll apply (no slot), full-fidelity
`BlockData` effects.

## 4. The highest-leverage change to the MAIN plugin (do this regardless)

**Hide PDC behind a version-neutral `ItemBlobStore` keyed by a logical String, so
`PersistentDataContainer`/`PersistentDataType`/`NamespacedKey` never appear on any
signature crossing the `item` module boundary:**

```java
interface ItemBlobStore {              // PDC impl on modern, NMS-NBT impl on 1.8
    String read(ItemStack stack, String logicalKey);     // "combat" | "soul" | …
    void   write(ItemStack stack, String logicalKey, String blob);
    void   remove(ItemStack stack, String logicalKey);
}
```

The codecs already emit a pure String blob; today only the ~3-line accessor pairs and
`ItemKeys` touch PDC/`NamespacedKey`. Routing them through `ItemBlobStore` converts
**~1,500 of the ~1,964 `item` LOC from "fork the accessor" to "share verbatim,"**
removes the only place `NamespacedKey` leaks onto a shared contract, and collapses the
fork's item-data work to one ~50-line impl. It is also a clean modernisation on its own
merits (testability, a single mutation seam). Runner-up: make `RuntimeHandles` expose
only `resolve(HandleCategory,int)→Object` publicly and move the typed `material()/
attribute()/sound()` casts call-site-local, so the platform seam carries no Bukkit enum
types.

## 5. Proposed minimal-separation structure (if ever built)

- **One source tree.** Keep `se/<module>/` as is. The modern jar builds as today.
- **`:compat-legacy`** — a new edge module (sibling to `compat-folia`) holding the 1.8
  forked impls: `LegacyDispatchSink` (behind `Sink`), `LegacyRegistrySupport`,
  `NbtItemBlobStore` (behind `ItemBlobStore`), `WornAccess`/equip-poll, particle/
  action-bar packet shims. Compiled against a 1.8 server jar; selected at boot by a
  capability probe.
- **A second Gradle output** that JDG-downgrades the shared modules + `:compat-legacy`
  to a Java-8 `starenchants-legacy.jar`. The modern jar never changes.
- **A 1.8.9 integration gate** (CraftBukkit/Spigot 1.8.9 + the fake-player harness
  re-forked) — non-optional, because the downgrade hazard (§1) and the `setItemMeta`
  trap (§3) are both invisible to unit tests and to the current 1.17+ matrix.
- **Pre-work on the modern plugin:** land `ItemBlobStore` and the `RuntimeHandles`
  cast-localisation (§4) first — they raise shareability and are good changes anyway.

## 6. Verdict

- A 1.8.9 fork *can* share ~60% of source via auto-downgrade + the existing seams —
  far better than "a second plugin," and the architecture is unusually well-positioned
  for it.
- It is still **not one jar and not free**: ~31% rewrite, the `se/item` pipeline
  surgery, a from-scratch 1.8 integration gate, and a permanent downgrade-coverage
  hazard. Realistically multi-person-week, with ongoing maintenance on the forked edge.
- **1.7.10** adds a pre-1.8 API/JVM era on top with near-zero extra reach — not worth it.
- **The floor stays at 1.17.1.** Do the `ItemBlobStore` refactor on its own merits;
  treat this doc as the blueprint to consult **if** legacy reach is ever mandated.

### Sources

JvmDowngrader <https://github.com/unimined/JvmDowngrader>, plugin
<https://plugins.gradle.org/plugin/xyz.wagyourtail.jvmdowngrader>; Jabel
<https://github.com/bsideup/jabel> (records/sealed unsupported: issue #3); PDC=1.14
<https://docs.papermc.io/paper/dev/pdc/>; Particle/off-hand/Attribute=1.9
<https://helpch.at/docs/1.9/org/bukkit/Particle.html>; the Flattening
<https://minecraft.wiki/w/Java_Edition_1.13/Flattening>; `setItemMeta` strips NBT on 1.8
<https://www.spigotmc.org/threads/custom-nbt-tags-getting-cleared-when-itemmeta-is-set.106262/>;
action-bar packet `(byte)2`
<https://github.com/Attano/Spigot-1.8/blob/master/net/minecraft/server/v1_8_R3/PacketPlayOutChat.java>.
Key repo seams referenced: `engine/sink/{Sink,DispatchSink}.java`,
`item/codec/{CombatCodec,ItemKeys}.java`, `platform/resolve/{RuntimeHandles,RegistrySupport,HandleResolver}.java`,
`item/worn/WornResolver.java`, `feature/combat/EquipListener.java`, `platform/sched/*`.
