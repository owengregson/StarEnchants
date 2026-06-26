# StarEnchants 1.8.9 Code-Share Design — Overlay + Dual-Compile Gate

> **Status: panel-reviewed, red-teamed design.** This document supersedes and extends
> `docs/legacy-1.8.9-minimal-fork.md`. It is the result of a full grounding audit
> (Bukkit/NMS leakage, the item-data/NBT pipeline, the Sink/engine seams, the build
> system), a synthesis pass, and two adversarial red-team passes whose fatal/serious
> findings are folded in below. It is a *design and an option*, not a commitment: the
> floor stays **1.17.1**, and 1.8.9 is an **optional second jar** built by a `:compat-legacy`
> module that should not be stood up until its integration gate is funded (§9, §11).

---

## TL;DR — the recommendation

Adopt a **Gradle source-set overlay** as the primary fork mechanism, hardened with a
**dual-compile gate** (the legacy seam classes compile directly against a real
Spigot-1.8.8 + `v1_8_R3` jar, so a 1.8-absent platform symbol is a `javac` error, not a
runtime `NoSuchMethodError`), with **JvmDowngrader** used only as a bytecode-floor
lowering step (61→52) on the assembled legacy jar. The fork is a small set of same-FQN
overlay classes — `DispatchSink`, `RegistrySupport`/`RuntimeHandles`, an NBT
`ItemBlobStore` impl, a `LegacyEquipSource` — layered over a byte-for-byte-shared `main`;
artifact selection happens at build-assembly time, **with no runtime probe** for the
forked edge.

**Honest share:** **~88–90% of the cross-version core (≈22.3k LOC) is shareable *after*
the Phase-0 parent refactors** (it is not "share-as-is" — see §5 and the FactPopulator
correction), with ~2k LOC of thin overlay fork. **Whole production (≈39.4k LOC incl.
tester): ~60–63% — a tie with the bytecode-downgrade baseline, not a win.** The real win
is elsewhere: (1) core-share *quality*, (2) per-feature fork cost is **nil for any
feature expressible as a recombination of the existing 54 Sink intents**, and (3) the
platform wall is **compile-enforced**.

**What to do now:** execute **Phase 0** (the parent refactors) unconditionally — it pays
for itself on the *modern* plugin and makes the seams real and CI-enforced. Treat
**Phases 1–3** (the actual 1.8 fork) as a spec on the shelf, gated behind a hard
ownership/funding precondition (§11), because an unfunded legacy lane's silent runtime
rot is worse than no fork.

---

## 1. Architecture — the chosen hybrid

The legacy/modern split is a **compile-time fact**, not a runtime probe. Each forking
core module (`engine`, `item`, `platform`) keeps its flat `src/` as the shared `main` and
gains `overlay/legacy/` + `overlay/modern/` source sets containing **only the seam
classes**, under identical single-segment FQNs. The composition root
(`StarEnchantsPlugin.java`) is unchanged — `new RuntimeHandles(...)` resolves to whichever
overlay class the assembled artifact carries.

Mechanisms, and why each beats the alternatives the panel rejected:

- **Source-set overlay (primary).** Per-feature fork cost is nil for Sink-only features
  because `Sink` carries **zero** registry-backed referents across its 54 methods (every
  volatile crossing is an interned `int` id resolved inside `DispatchSink`). Chosen over a
  runtime-probed `compat-legacy` *sibling* (like `compat-folia`) precisely because an
  unforced leaf rots — `compat-modern` is the cautionary tale (it is an empty stub today,
  referenced nowhere). Overlay source sets are compile-coupled to `main`: adding a `Sink`
  method is a build error in the legacy overlay until implemented.

- **Dual-compile gate (the decisive safety net, from Strategy C).** The legacy overlay
  compiles against the **real 1.8 server jar**, so missing platform symbols fail at
  `javac` — no hand-maintained Bukkit allowlist needed, because the jar *is* the
  allowlist. This is the single improvement over the pure-bytecode-downgrade baseline,
  which compiles + downgrades + shades **green** and crashes at runtime on a 1.8-absent
  symbol.

- **JvmDowngrader (from Strategy A, demoted to one job).** Used **only** as the language-
  wall lowering step (records/sealed/switch-expressions/`var` → Java-8 bytecode) on the
  assembled legacy jar, plus shading the JDK-9+ stdlib shim. It never touches the platform
  wall.

**Explicitly rejected** (killed by the panel and confirmed by red-team):
- The `Sink` `EntityRef` swap — adds a per-hit map lookup, damages invariant 7 (fully
  additive damage on the hot path), zero 1.8 benefit (those Bukkit types exist on 1.8.9).
- The `EffectCtx`/`SelectorCtx` `Player`/`LivingEntity` ref-purge — ~80 files across 68
  kind impls, zero 1.8 benefit (the compile gate *proves* they're safe without touching
  them).
- Demoting `RuntimeHandles`' typed accessors to `Object` — spreads unchecked casts and
  worsens invariant 4; the accessors already concentrate the cast in one class consumed
  only inside `DispatchSink`.
- Reflection as a fork mechanism — 1.8 has one fixed `v1_8_R3` package; direct typed
  calls against the real jar are strictly better and debuggable. Keep only the boot-time
  `RegistrySupport` registry reflection that already exists.

---

## 2. The seam surface

All seams are **typed interfaces over neutral or 1.8-safe types** — never reflective
handles, never a generic boxed key-value store (a generic store regresses invariant 2 and
boxes on invariant 7).

```java
// se/item/src/item/codec/ItemBlobStore.java  (NEW — the one item-data layer, made physical)
// STRING-canonical to preserve one-blob-per-logical-codec discipline (invariant 2).
public interface ItemBlobStore {
    String  read(ItemStack stack, String logicalKey);            // null if absent
    void    write(ItemStack stack, String logicalKey, String blob);
    void    remove(ItemStack stack, String logicalKey);
    boolean has(ItemStack stack, String logicalKey);
}

// se/item/src/item/codec/ItemFlagStore.java  (NEW — the 5 BYTE/INTEGER marker codecs)
// CODEC/MINT-TIME ONLY. Primitive in/out so there is no boxing even if ever read on a path.
public interface ItemFlagStore {
    byte readByte(ItemStack stack, String logicalKey, byte dflt);
    void writeByte(ItemStack stack, String logicalKey, byte v);
    int  readInt (ItemStack stack, String logicalKey, int  dflt);
    void writeInt (ItemStack stack, String logicalKey, int  v);
    void remove  (ItemStack stack, String logicalKey);
}

// se/item/src/item/worn/EquipSource.java  (NEW — neutralizes the off-hand read out of main)
// Returns [helmet, chest, legs, boots, mainHand, offHand?]; offHand may be null on 1.8.
public interface EquipSource { ItemStack[] snapshot(LivingEntity e); }

// se/item/src/item/ItemTransfer.java  (NEW — SE-owned-state-only carry; replaces whole-meta copy)
public final class ItemTransfer { public static void copyState(ItemStack from, ItemStack to); }

// engine.sink.Sink            — UNCHANGED (already fully interned; the proof the rest forks cleanly)
// engine.sink.SinkReadback    — NEW interface; the COMPLETE cross-module readback set (see §3.5)
// platform.resolve.RuntimeHandles — typed accessors KEPT (do NOT demote to Object)
```

Everything else the core touches — `EffectCtx`/`SelectorCtx` (`Player`/`LivingEntity`/
`Location`), `WornState`/`ItemView` (int-keyed), the entire `Sink` interface — uses only
types present on 1.8.9 and stays **un-ported**, proven by the compile gate.

---

## 3. Required parent-codebase changes (Phase 0), ordered by leverage

### 3.1 `ItemBlobStore` + `ItemFlagStore` — make the one item-data layer physical
- **Change:** the 11 codecs lose their duplicated `getItemMeta()→getPersistentDataContainer()
  →get/set/has/remove→setItemMeta()` plumbing (~23 + 23 PDC statements across 11 files)
  and call the store. The 5 String-payload codecs (`CombatCodec`, `SoulCodec`,
  `CrystalItemCodec`, `ScrollCodec`, `UnopenedBookCodec`) → `ItemBlobStore`; the 4 BYTE +
  1 INTEGER markers (`CarrierCodec` guarded, `CrystalExtractorCodec`, `GodlyTransmogCodec`,
  `HeroicUpgradeCodec`, `SlotItemCodec`) → `ItemFlagStore`. **Public codec surfaces
  (`read(ItemStack)→T`) stay identical**; the seam slots underneath. `NamespacedKey`/
  `PersistentDataType`/`PersistentDataContainer` leave every codec boundary; only
  `ItemKeys` + the store impl keep them.
- **Independent merit:** collapses ~46 duplicated PDC statements into one ~50-LOC class;
  makes invariant 2 enforced *by a class*, not 11 copies of a convention; makes codecs
  unit-testable with an in-memory fake store, no server.
- **YAGNI note (red-team):** the *deduplication* is the modern win; the *interface* is only
  required when a second (NBT) impl exists. **Decision (§11):** if Phase 1 is not
  committed, land `ItemBlobStore`/`ItemFlagStore` as concrete classes and extract the
  interface in Phase 1 — OR land the interface now *and* check in the legacy stub impl
  under a cron-gated `-Pse.target=legacy` lane (§3.6). Do **not** ship a seam interface
  with only a modern impl and no CI exercising the contract; that is the `compat-modern`
  rot pattern.
- **Blast radius:** 11 codecs lightly edited; `ItemViewCache.of()` consumes
  `store.read(stack, COMBAT)` instead of `codec.readBlob(stack)`, zero semantic change.

### 3.2 `ItemTransfer.copyState` — carry only SE-owned state across a cross-Material upgrade
- **Change:** `HeroicService.java:115` does `upgraded.setItemMeta(gear.getItemMeta())` — a
  whole-meta copy from a source Material onto a different-Material fresh stack, dragging
  attribute modifiers / unbreakable / can-destroy / lore when the copy *succeeds*. Replace
  with `ItemTransfer.copyState(gear, upgraded)`, which copies only StarEnchants-owned state
  via the codecs.
- **Red-team correction (claim downgraded):** this is a **correctness improvement, not a
  latent bug**. The existing code already fails safe — it checks `setItemMeta()`'s boolean
  return and wraps the block in try/catch with a documented fallback that keeps the
  original material on incompatibility (`HeroicService.java:104–122`). The narrow real
  issue is the *attribute/flag bleed when the cross-Material copy succeeds*, not a silent
  crash.
- **Blast radius:** 1 helper + 1 line. TRIVIAL.

### 3.3 `EquipSource` — move the off-hand read out of shared `main`
- **Change:** `WornResolver.java:81–82` calls `getItemInMainHand()`/`getItemInOffHand()`
  **unconditionally in shared `main`** (1.9+ symbols; 1.8 uses `getItemInHand()` and has no
  off-hand slot). Extract `EquipSource`; `WornResolver.resolveFrom(List<CombatState>)`
  (already pure) is unchanged. Modern impl reads main+off-hand; legacy impl reads main only.
  **Also route `FactPopulator.java:126` and `:328`** which call `getItemInMainHand()` in
  shared code, through `EquipSource` or an interned material id.
- **Independent merit:** makes `WornResolver` unit-testable without a mocked
  `EntityEquipment`; isolates the one 1.9+ API from the pure flattening core.
- **Blast radius:** `WornResolver` read-side + `FactPopulator` 2 sites. LOW-MED, but it is
  a **hot-path behavioral change** — see the Phase-0a/0b split (§9).

### 3.4 ArchUnit guards — convert invariants from convention to CI
- (a) `RuntimeHandles`' typed accessors are the **only** source of live registry-backed
  objects, consumed only inside `DispatchSink`.
- (b) Zero `org.bukkit`/`net.minecraft`/`io.papermc` import in `schema`/`compile`/`migrate`/
  `pack` (already true; lock it).
- (c) Forbid `RecordComponent`/record-component reflection (keeps the downgrade-clean
  property the tree already has — zero such sites today).
- (d) No FQN appears in both `main/` and `overlay/*/`.
- (e) **No cross-module call targets a concrete `DispatchSink` method not on `Sink` or
  `SinkReadback`** (closes the R5 gap — see §3.5; without this rule the "compile-checked"
  guarantee is itself a convention).
- (f) **Ban the specific un-shimmable JDK-9+ stdlib APIs in shared core, or require they
  come from an approved shimmed set**, so a stray `String.isBlank()`/`List.copyOf` fails
  the *fast* modern `./gradlew build` every developer runs — not only the slow legacy lane
  (the only thing that survives a solo maintainer six months in; converts R1 from
  downstream lane-rot into an immediate local failure).
- **Do-anyway? YES.** Pure upside, zero runtime code. Keep `RuntimeHandles` accessors typed
  — `bootstrap` and `feature.fx.ParticleFx` consume `particle()→Particle`; demotion spreads
  unchecked casts and worsens invariant 4.

### 3.5 `SinkReadback` — the COMPLETE concrete-readback interface
- **Change:** `DispatchSink` exposes public concrete read-backs consumed cross-module:
  `cancelled()`, `armorIgnored()`, `smeltRequested()`, `teleportDropsRequested()`,
  `seekRequested()` — verified consumers at `TriggerDispatch.java:191` (smelt +
  teleportDrops) and `:212` (seek). Promote the **entire** set onto a `SinkReadback`
  interface (red-team: the original list under-scoped it to smelt/seek). Pair with ArchUnit
  rule (e) so drift is a build error, not silent.
- **Blast radius:** 1 interface + the consumers re-typed. LOW.

### 3.6 Fold the 2 static side-channels into the composition root
- **Change:** `DispatchSink.movementExemption(Consumer<Player>)` and
  `FactPopulator.entityTypeResolver(Function<Entity,String>)` become boot-wired instance
  hooks.
- **Honest framing (red-team):** justification is **testability** (removes mutable global
  statics), **not** reload-safety — these are boot-time installers that already correctly
  survive a Snapshot swap. Do not oversell.
- **Blast radius:** 2 one-liners + 1–2 call sites. TRIVIAL.

**Explicitly NOT done in Phase 0** (killed by panel/red-team): the `Sink` `EntityRef` swap;
the `EffectCtx`/`SelectorCtx` ref-purge; demoting `RuntimeHandles` to `Object`; a `Platform`
aggregate in the `platform` module (would invert the acyclic `platform → compile`,
`engine → platform` edges).

---

## 4. The module graph

There is **no runtime-probed `compat-legacy` sibling** of `compat-folia` for the forked
*core* edge. The forked seam classes live as **overlay source sets** inside the modules
that fork (`overlay/legacy/` in `engine`, `item`, `platform`), compile-coupled to `main`.

**Overlay contents (the entire irreducible fork of the core):**
- `engine/overlay/legacy/engine/sink/DispatchSink.java` (~1,075 LOC) — apply bodies via
  `v1_8_R3`: particles via packet, attributes via `setMaxHealth`/NMS `GenericAttributes`,
  no `Damageable` interface, `Effect` instead of `Particle`. Implements the unchanged `Sink`
  and `SinkReadback`. The 3 `Damageable` durability re-stamps (modern `DispatchSink:1015/
  1030/1058`) live here for free.
- `platform/overlay/legacy/platform/resolve/{RegistrySupport,RuntimeHandles}.java` —
  pre-flattening name→(id,data) tables; no `Registry`/`Particle`/`Attribute` registries.
- `item/overlay/legacy/item/codec/{NbtBlobStore,NbtFlagStore}.java` — direct `v1_8_R3`
  `CraftItemStack` NMS-tag read/write that **never round-trips `ItemMeta`** (the
  setItemMeta-strips-NBT trap fix).
- `item/overlay/legacy/item/worn/LegacyEquipSource.java` — 4 armor + main hand only.

**The `:compat-legacy` Bukkit-edge module (NOT overlay — forked wholesale):** `feature/**`
(7,329 LOC: 22 listeners incl. the Paper-only `EntityKnockbackByEntityEvent` and
`PlayerArmorChangeEvent`, 27 GUIs, 9 services), a legacy `bootstrap` (no Brigadier),
`integrate` per-bridge, and a `v1_8_R3` `tester` fake-player. This is honest scope: overlay
deliberately does **not** try to share `feature/**`.

**Flat-layout compliance:** overlay source sets use the same single-segment package as
`main`; same-FQN substitution means no collision. The JvmDowngrader runtime shim is
relocated under its own root (`legacyshim`) **only in the legacy artifact**, so the modern
jar's "no shaded deps, no relocation" property is untouched.

---

## 5. Share accounting

Grounded in `wc -l`. **SHARE-AS-IS** = byte-identical *and* links on 1.8 with no parent
change. **BEHIND-PORT** = shared logic, needs a Phase-0 seam to link/resolve correctly on
1.8. **THIN-FORK** = overlay seam class. **TRUE-REWRITE** = `:compat-legacy` Bukkit edge.

| Module | src LOC | Share-as-is | Behind-port | Thin-fork (overlay) | True-rewrite |
|---|---|---|---|---|---|
| schema | 1,885 | 1,885 | — | — | — |
| migrate | 1,805 | 1,805 | — | — | — |
| pack | 495 | 495 | — | — | — |
| api | 90 | 90¹ | — | — | — |
| compile | 5,516 | — | 5,516² | — | — |
| platform | 1,586 | ~1,080 | — | ~506 (resolve overlay) | — |
| item | 1,964 | ~1,250 | ~300³ (WornResolver + LoreRenderer) | ~410 (codec/Nbt impls + EquipSource) | — |
| engine | 8,960 | ~6,990 | ~895⁴ (FactPopulator) | ~1,075 (DispatchSink overlay) | — |
| **core subtotal** | **22,301** | **~13,595** | **~7,206** | **~1,991** | — |
| feature | 7,329 | — | — | — | 7,329 |
| bootstrap | 2,836 | ~2,400 (Metrics + wiring) | — | — | ~436 |
| integrate | 1,024 | ~150 | — | — | ~874 |
| tester | 5,954 | — | — | — | 5,954 (new 1.8 harness) |

¹ **api Event base:** `EnchantActivateEvent`/`StarEnchantsReloadEvent` `extends
org.bukkit.event.Event` with static `HandlerList` — 1.8-safe (both exist on 1.8.9), so
share-as-is is correct. **Constraint (red-team):** the public api event surface must stay
**floor-typed** (no Component/Adventure/modern-only types on accessors) or it silently
becomes a behind-port row. Cheap to honor today; stated so it isn't violated later.

² **compile is BEHIND-PORT, not share-as-is (red-team, fatal).** The Java is byte-
identical, but the compiler resolves and validates version-volatile names (Material/
Particle/Sound/Attribute/Enchantment) to interned ids **at compile time** via an injected
`PlatformResolvers` (`DefaultResolveStage.java:82`, fed `RegistryResolvers` at
`StarEnchantsPlugin.java:170–171`). On 1.8.9 the name table is pre-flattening — no
`Particle` registry, no `Attribute` registry, hundreds of differing Material names
(`GRASS_BLOCK`, `WOOD_SWORD` vs `WOODEN_SWORD`, data-value wool/dyes). The **same config
that validates clean on modern throws resolve-stage diagnostics or interns a wrong id on
1.8**. Gate 1 cannot catch this — the resolver is *injected at runtime*, not linked. This
requires a **legacy resolver-table** (§6, Gate 3) — the single largest unbudgeted fork
cost, and it is the EE/EA/AE migrator alias problem reborn at the platform layer.

³ **item behind-port:** `WornResolver` read-side (off-hand, §3.3) **and** `LoreRenderer:134`
(red-team): `LoreRenderer` runs on the combat/equip path in shared `main` and calls
`setItemMeta`. On a 1.8 `CraftItemStack`, `getItemMeta()`/`setItemMeta()` round-trips
through `CraftMetaItem`, which **drops unknown NBT** — wiping the SE blob. Correctness
requires the blob write to be the *last* mutation (read blob → `setItemMeta(lore)` →
re-write blob), encapsulated so shared `main` does not branch. This is an ordering/semantic
fork the compile gate cannot judge; treat `LoreRenderer` as behind-port and pin it with the
Phase-2 100×-render-then-read-blob smoke assertion (§7).

⁴ **engine behind-port:** `FactPopulator.java:126/328` call `getItemInMainHand()` in shared
`main` (red-team: previously double-counted as share-as-is). It is shared *logic* that needs
the §3.3 `EquipSource`/interned-material seam to link on 1.8 — moved out of share-as-is.

**Honest totals:**
- **Cross-version core (22,301 LOC): ~88–90% shareable *after the §3 parent refactors***
  (share-as-is + behind-port), ~2k LOC thin overlay fork. This is **not** "share-as-is" —
  the §3 work is a *precondition* for the number, and `compile`'s share is conditional on
  the legacy resolver table existing.
- **Whole production (≈39.4k incl. tester): ~60–63% — a TIE with the baseline.** We do
  **not** quote the inflated "~73% ex-tester" figure; excluding the largest unshared module
  from the denominator is dishonest. The true-rewrite zone (feature + bootstrap-edge +
  integrate + a new tester ≈ 14.5k LOC) does not shrink under any strategy.

**The defensible headline:** this **matches the baseline on whole-tree share** and wins on
(1) core-share quality, (2) per-feature fork cost = **nil for features expressible as a
recombination of the existing 54 Sink intents** (a feature requiring a *new* Sink intent
costs one interface method + two overlay impls + one legacy NMS body — see §10), and (3)
platform errors fail at compile. It does **not** win on the headline percentage.

---

## 6. The platform wall + the three gates

| Hazard | Modern | 1.8.9 | Static gate |
|---|---|---|---|
| PDC / NBT | `PdcBlobStore` (overlay/modern) | `NbtBlobStore` on `v1_8_R3` tag | **Gate 1** — legacy overlay imports no PDC |
| setItemMeta NBT-wipe | benign | `NbtBlobStore` mutates the NMS tag directly; `ItemTransfer.copyState` + the 3 Damageable sites (overlay) + `LoreRenderer` blob-last ordering (§5 note 3) preserve the blob | **Gate 4 (runtime)** — behavioral, not a symbol |
| Flattening (Material/Particle enum shape) | reflective `Registry` | name→(id,data) table in legacy `RegistrySupport` overlay | **Gate 1** (overlay) + **Gate 3** (config names) |
| Attributes (1.9+) | `AttributeInstance` | `setMaxHealth`/NMS `GenericAttributes` (overlay) | **Gate 1** — `org.bukkit.attribute.*` absent from 1.8 jar |
| Off-hand | `EquipSource` reads slot | `LegacyEquipSource` main-only | **Gate 1b** — the leak is now in `main`, see below |
| Equip event | `PlayerArmorChangeEvent` (Paper) | inventory-click/poll driver in `:compat-legacy` feature fork | N/A — behavioral, Gate 4 |
| Scheduling | Bukkit/Folia backend swap | `BukkitSchedulerBackend` (Folia probe false); `compat-folia` not bundled | N/A — already the clean degrade path |

**Four gates, each closing a distinct hazard class:**

- **Gate 1 — compile the legacy OVERLAY against the real 1.8 jar.** `javac` is the
  platform-symbol allowlist. Catches the absent-API sites a bytecode downgrade ships green.

- **Gate 1b — compile the SHARED `main` core modules (`item`, `engine`, `platform`) ALSO
  against the 1.8 jar, in a verify-only task (no artifact).** *(Red-team, serious — the
  single most important addition.)* Shared `main` is compiled against paper-api 1.17 floor,
  so Gate 1 only sees code *in the overlay*. `FactPopulator`/`WornResolver`/`LoreRenderer`
  live in `main`. Without Gate 1b, the "compile-enforced platform wall" covers only the
  overlay, and the largest shared modules are checked only transitively. Gate 1b makes any
  1.8-absent symbol that slips into shared `main` a `javac` error, closing the gap between
  "overlay links on 1.8" and "shared core links on 1.8."

- **Gate 2 — ASM closed-world scan over the downgraded legacy jar (language wall).** Assert
  every `INVOKE`/`getstatic`/`new` resolves to (Java-8 rt.jar) ∪ (SE roots) ∪ (legacyshim
  root) ∪ (`v1_8_R3` allowlist). **The JDK-9+ stdlib hazard is large and only Gate 2 bounds
  it** — `--release 8` cannot be used on the shared modules (it would reject the records/
  sealed/switch-expressions the codebase depends on), so the ASM gate is the only static net
  for the language wall. *(Red-team, minor: the precise "406 sites" figure is not
  reproducible — a broad regex measures ~300, `compile` ~130–170 depending on the pattern.
  Do not quote a load-bearing integer; the authority is Gate 2's exact, reproducible set.
  The defensible claim is "hundreds of JDK-9+ call sites, compile-heavy; only Gate 2 bounds
  them." §3.4(f) additionally bans the un-shimmable subset on the fast modern build.)*

- **Gate 3 — legacy resolver-table conformance (config-name wall).** *(Red-team, fatal —
  the gate the synthesis lacked.)* Run the bundled config packs through a **1.8
  `RegistryResolvers`** and assert every authored name resolves or has an explicit legacy
  alias. This is what catches the `compile`-module hazard (note ² above): wrong/missing
  name tables produce silent wrong-id interning or resolve diagnostics that Gate 1 cannot
  see (the resolver is injected, not linked). Budget the legacy alias table (a few hundred
  name mappings) as **net-new fork work**.

- **Gate 4 — the from-scratch live 1.8.9 integration gate (mandatory, absent today).** A
  `v1_8_R3` fork of the `tester` fake-player; boot a real Spigot 1.8.8 under **JDK 8** (CI
  installs only 17+21 today — add 8); a smoke suite that mints an item (NBT write),
  **re-renders lore 100× then reads the blob back** (the named `LoreRenderer` regression for
  the setItemMeta trap), fires one activation per `EffectKind` *family* (drives DispatchSink
  legacy resolution), opens the main GUI. Per `matrix-gate`: a green modern run says nothing
  about 1.8. **Catches what no static gate can** — a semantically-wrong legacy mapping
  (right method, wrong id) and the equip-poller's behavioral divergence. **The legacy build
  is not releasable until this lane is green.**

**Stays impossible / degrades:** off-hand enchants (1.8 has no slot); the equip-event
refresh becomes a poller fork with different timing; pre-1.9 combat mechanics (attack
cooldown, sweep) differ and the feature fork must re-implement them.

---

## 7. Build & CI

```
:schema:jar … :pack:jar                          (no overlay — single output)
:engine:compileJava (Java 17, main)              compiled vs paper-api 1.17 floor
:engine:compileLegacyJava                        overlay/legacy, compiled vs spigot-1.8.8 + v1_8_R3
   └─ ★ GATE 1   (overlay platform symbols)
:item:verifyLegacyMain / :engine:… / :platform:…  main re-compiled vs the 1.8 jar, no artifact
   └─ ★ GATE 1b  (shared-main platform symbols)
:bootstrap:modernJar  (classifier "modern")      main + overlay/modern  → today's universal jar, untouched
:bootstrap:legacyJar  (classifier "legacy")      main + overlay/legacy + :compat-legacy
:bootstrap:downgradeLegacyJar (JvmDowngrader)    lowers legacyJar 61→52, shades the legacyshim
   └─ ★ GATE 2   (ASM closed-world, language wall)
:verifyLegacyResolverTable                       bundled packs through a 1.8 RegistryResolvers
   └─ ★ GATE 3   (config-name wall)
:legacySmoke (JDK-8 Spigot-1.8.8, fake-player)   live boot + smoke suite
   └─ ★ GATE 4   (runtime: semantic mappings + NBT-trap + equip poller)
```

**Gate-1 classpath skew (red-team, serious — R2).** Spigot 1.8.9 bundles **older** library
versions (e.g. SnakeYAML ~1.15; `YamlNode:51` calls `setAllowDuplicateKeys`, added in 1.18;
plus older Guava/Gson). Gate 1/1b's compile classpath **must include the 1.8-bundled library
versions**, not the modern ones, so version-skewed calls fail at compile. Inventory the 1.8
server's bundled libs when standing up the gate.

**Fast loop unaffected.** Phase-1 work is opt-in via `-Pse.target=legacy`; default
`./gradlew build` stays the modern build + the existing 13-target Paper+Folia matrix. The
pre-commit 2 MB size hook is unaffected (jars live in `build/`). The pre-commit `regenDocs`
auto-run still fires on `engine`/`schema`/`compile` source edits — expected.

**Release.** `release.yml:99–108` hard-codes one artifact path; add a parallel stage
emitting `StarEnchants-${VERSION}-legacy.jar` + `.sha256` as a second `gh release` asset.
The `v1_8_R3` NMS dep is BuildTools-local (not on Central) — a fork-only prerequisite the
modern build never needs.

---

## 8. Risk register

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **JDK-9+ stdlib shim gap** (hundreds of sites, compile-heavy) — an un-shimmed `List.copyOf`/`isBlank` ships green and `NoSuchMethodError`s at runtime | HIGH | Gate 2 makes it a build break; §3.4(f) ArchUnit ban makes the un-shimmable subset fail the *fast* modern build too. Pin JvmDowngrader exactly; a JDG bump is a gated change requiring a fresh Gate-4 run. |
| R2 | **Server-bundled library skew** (1.8 SnakeYAML ~1.15 vs `setAllowDuplicateKeys`@1.18; older Guava/Gson) | HIGH | Gate 1/1b classpath uses the **1.8-bundled** library versions; inventory them when standing up the gate. |
| R3 | **`compile`-module config-name divergence** (pre-flattening Material/Particle/Attribute tables) — wrong-id intern or resolve diagnostics on 1.8 | HIGH | Gate 3 (resolver-table conformance) + a budgeted legacy alias table (few hundred mappings). Unbudgeted in the headline share. |
| R4 | **Semantically-wrong legacy mapping** (right method, wrong id) — no static gate catches | MED-HIGH | Gate 4 only; per-category mapping tables unit-tested against a 1.8.9 name list. Non-skippable release dependency. |
| R5 | **`LoreRenderer`/setItemMeta NBT-wipe ordering** in shared `main` | MED-HIGH | Blob write is the last mutation, encapsulated; named 100×-render-then-read-blob Gate-4 assertion. Treated as behind-port, not share-as-is. |
| R6 | **JvmDowngrader unproven on records+sealed+switch-expressions** (126 records, 5 sealed, 29 switch incl. arrow-on-enum) | MED | **Two separate claims:** (a) zero record-component reflection removes the silent-divergence landmine on cached record keys — *proven*; (b) faithful lowering of records/sealed/switch-expressions — *unproven*, mitigated by a golden-file downgrade-then-classload-on-JDK-8 smoke over a switch-expression-heavy class (not only a record-heavy one). Pin JDG. |
| R7 | **Overlay drift on concrete `DispatchSink` readbacks** | MED | `SinkReadback` = the COMPLETE set (`cancelled`, `armorIgnored`, `smeltRequested`, `teleportDropsRequested`, `seekRequested`) + ArchUnit rule (e) so drift is a build error, not a convention. |
| R8 | **Seam rot before the legacy impl exists** (the `compat-modern` pattern) | MED | If Phase 0 ships seam *interfaces*, also check in legacy stub impls under a **weekly-cron** `-Pse.target=legacy` lane — OR ship the seams as concrete classes and extract the interface in Phase 1 (§3.1, §11). A seam with no legacy consumer is indistinguishable from `compat-modern`. |
| R9 | **Legacy lane set to continue-on-error** by a solo maintainer | MED | §3.4(f) puts the most common failure on the fast build; Phase 1 precondition (§11) requires the legacy lane be **blocking from commit one**. |
| R10 | **Sunk-cost momentum** to build Phases 1–3 because the gate is designed | MED | Phase 0 is the recommended *terminal* state; Phases 1–3 are a spec on the shelf behind the §11 go/no-go. |
| R11 | **Two-jar toolchain** (Java-8 lane vs the single-17 root convention) | LOW-MED | Per-module toolchain override on `:compat-legacy` only; modern build untouched. |

---

## 9. Phased implementation plan

**Phase 0a — pure-mechanical parent refactors (build-only, mergeable today).**
Ships: §3.1 `ItemBlobStore`/`ItemFlagStore` codec migration with fake-store unit tests;
§3.4 ArchUnit guards (incl. (e) and (f)); §3.5 `SinkReadback`; §3.6 static-hook fold.
Verified: `./gradlew build` (pure unit) — these are zero-risk modularity/lint changes.
Commit as its own PR so a matrix regression in 0b cannot block these safe wins.

**Phase 0b — hot-path behavioral parent refactors (full matrix required).**
Ships: §3.2 `ItemTransfer.copyState`; §3.3 `EquipSource` + `WornResolver`/`FactPopulator`
rewrite.
Verified: `./gradlew build` **plus the full 13-target Paper+Folia matrix** (this touches the
combat-path `WornResolver`), plus a worn-state regression test that an **off-hand totem still
resolves**. Per `test-scope-calibration`, a hot-path change earns the heavy matrix; do not
co-merge it with 0a's lint.
**Phase 0 (0a + 0b together) is the only phase recommended unconditionally — it is a net
modularity win on the *modern* plugin and raises core-share quality regardless of whether
1.8 is ever built.**

**Phase 1 — `:compat-legacy` stand-up (the fork skeleton).**
Ships: `overlay/legacy/` source sets in engine/item/platform with the 4 seam impls; the
`:compat-legacy` Bukkit-edge module (feature fork, legacy bootstrap); the build pipeline
(Gate 1 + Gate 1b + JvmDowngrader + Gate 2 + Gate 3); the `-Pse.target=legacy` opt-in.
If Phase 0 shipped seam *interfaces*, this is where the second impl finally exists; if it
shipped concrete classes, extract the interfaces here.
Verified: Gates 1, 1b, 2, 3 green. **No runtime claim yet** — green here means "links +
lowers + resolves names," not "works."
**Precondition: the §11 go/no-go must be answered YES first.**

**Phase 2 — the live 1.8 integration gate (first shippable point).**
Ships: the `v1_8_R3` fake-player tester fork; the JDK-8 Spigot-1.8.8 CI lane (Gate 4); the
smoke suite (§6) incl. the named `LoreRenderer` 100×-render-read-blob assertion; the second
release stage + classifier.
Verified: live 1.8.9 boot + smoke suite green. First point the legacy jar is shippable.
Does NOT cover: full feature parity (only smoke-tested `EffectKind` families + main GUI);
pre-1.9 combat-mechanic fidelity.

**Phase 3 — hardening.**
Ships: per-category mapping-table tests (R4); the full SnakeYAML/Guava/Gson skew audit (R2);
the equip-poller behavioral validation; expand the smoke suite toward the modern matrix's
coverage; broaden the Gate-3 alias table.
Verified: broadened 1.8 suite. Residual: the permanent semantic-divergence tail (R4) is
mitigated, never eliminated.

---

## 10. Honest verdict

**What this achieves.** The cross-version core (~22.3k LOC) is **~88–90% shareable after the
§3 parent refactors**, with a thin ~2k-LOC overlay fork. Adding an effect/selector/condition/
set/crystal that emits through the interned `Sink` touches **zero** legacy code — grounded
in the verified fact that `Sink` carries no version-volatile referents across 54 methods. The
platform wall is **compile-enforced** by Gate 1 (overlay) and Gate 1b (shared main): a
1.8-absent symbol is a `javac` error against the real server jar, not a green-then-crash
surprise. The Phase-0 parent changes make the **modern** plugin strictly better and stand on
their own merits.

**The locality win, stated precisely (red-team).** "Adding a feature is local" holds for any
feature expressible as a **recombination of the existing 54 Sink intents**. A feature that
introduces a **new physical effect** (a Sink intent not already present) costs one interface
method + two overlay `DispatchSink` impls + one legacy `v1_8_R3` body — and it touches the
shared `BuiltinEffects`/`BuiltinSelectors`/`BuiltinTriggers` registration sites. Whether the
locality win is large in practice depends on how often shipped content recombines vs.
introduces a novel intent; that ratio is currently unmeasured and should be measured before
the locality claim is used as a headline.

**What it costs.** Whole-tree share is **~60–63% — a tie, not a win**. `feature/**` (7,329
LOC of GUIs/listeners/services), a new `tester` 1.8 fake-player, and `integrate` fork
regardless. A permanent second toolchain, a pinned JvmDowngrader, the ASM gate, a budgeted
legacy resolver/alias table (R3), and — the real recurring cost — a **second live integration
matrix on a JDK-8 Spigot server** that rots silently if unfunded. It is **multi-person-week**
work even after Phase 0.

**What stays impossible.** One universal jar covering 1.8 → 26.1 (the bytecode floor forces
two artifacts; reflection cannot rescue a class the Java-8 verifier won't load). Sharing
`feature/**` without a manual fork. Catching a semantically-wrong legacy mapping, the
`compile`-module config-name divergence beyond name conformance, or the equip-poller
divergence at compile time — only Gates 3 and 4 can. Per the standing
`starenchants-legacy-version-feasibility` memory, 1.8.9 is structurally a second product.

**Does the floor recommendation hold? Yes.** The floor stays **1.17.1**; the primary artifact
is unchanged. 1.8.9 is an **optional second jar** via `:compat-legacy`. **Execute Phase 0
now** (it pays for itself on the modern plugin and raises core-share quality by making the
seams real and CI-enforced). Treat Phases 1–3 as a spec on the shelf — the clean overlay +
four-gate design makes the 1.8 fork the **best-structured and safest it can be**, but it does
not make it free, it does not make it one jar, and it must not be started until the §11
precondition is answered YES.

---

## 11. Open decisions for the maintainer (human yes/no before Phase 1)

1. **Land the `ItemBlobStore`/`ItemFlagStore` + `EquipSource` + `SinkReadback` refactors now
   (Phase 0), independent of any 1.8 commitment?** Recommended **YES** — they are modern
   modularity/correctness wins and CI-enforce two invariants. This is the only
   unconditional recommendation in this document.

2. **Seam shape if Phase 1 is not yet committed:** ship the new seams as **concrete classes**
   (extract the interface in Phase 1 when the NBT impl exists, YAGNI), **or** ship the
   *interfaces* now with checked-in legacy stub impls under a **weekly-cron**
   `-Pse.target=legacy` lane? A seam interface with only a modern impl and no CI exercising
   the contract is the `compat-modern` rot pattern — pick one of the two, not "interface +
   no legacy lane."

3. **Confirm the build mechanism: source-set overlay + dual-compile gate (this design) over
   pure bytecode downgrade.** The design assumes overlay-direct-compile-against-1.8 because
   it converts the platform wall from a runtime crash into a `javac` error. Reject only if
   you accept the bytecode-downgrade baseline's green-then-crash failure mode.

4. **The Phase-1 go/no-go ownership gate.** Do **not** start Phase 1 unless a named owner
   commits to running the JDK-8 Spigot smoke lane (Gate 4) on **every release** for the
   lifetime of the legacy jar, **and** the legacy lane is a **blocking** (never
   continue-on-error) CI gate from commit one. An unfunded legacy lane's silent runtime rot
   is worse than no fork; this is the trap §10 warns against.
