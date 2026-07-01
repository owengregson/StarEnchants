# Heroic ↔ Mental combat-stat parity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make a heroic gold piece deal, resist, last, and display as diamond across vanilla-modern, Mental non-legacy, and Mental 1.8-combat servers — via a vendor-neutral `combat:effective_material` PDC contract, with Mental owning all era presentation.

**Architecture:** SE writes real *modern* diamond attribute modifiers (no `HIDE_ATTRIBUTES`, no authored lore) + a neutral PDC marker naming the true material. Mental honours the marker for legacy weapon damage, respects the `max_damage` component for weapon durability, and extends its packet tooltip-rewriter to present era-correct values. **Core rule:** SE always makes the gold a *modern* diamond; Mental alone decides (per its own restore modules) whether anything becomes legacy, applying that decision identically to the marked gold piece and a real diamond.

**Tech Stack:** Java, Paper/Folia (1.17.1→26.1.x) + Spigot 1.8.8 legacy; StrikeSync/Mental (Java, packetevents). JUnit 5, live integration matrices.

## Global Constraints

- Cross-version: resolve `Attribute` by registry key (`attack_damage`→`generic.attack_damage`, `armor`→`generic.armor`), never a constant. `max_damage` component + `Damageable.getMaxDamage/hasMaxDamage` are 1.20.5+ (reflected).
- Folia-correct: no raw `Bukkit.getScheduler()` for entity work.
- Neutral contract key: `NamespacedKey.fromString("combat:effective_material")`, `PersistentDataType.STRING`, value = a Bukkit `Material` name (e.g. `DIAMOND_SWORD`). Documented in both repos.
- No compile-time dependency from SE on Mental.
- Attack totals include the base 1.0 (as the tooltip does): diamond sword 7 (modifier +6), axe 9 (+8).
- Conventional Commits; end AI commits with `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- Two repos, two branches, two PRs. Mental (StrikeSync) ships first.

---

# PART 1 — Mental / StrikeSync (`~/Documents/StrikeSync`, branch `feat/effective-material-contract`)

Independently shippable: general correctness fixes + an era-faithful tooltip that no-op when no marker/module is present.

### Task 1.1: `EffectiveMaterial` resolver

**Files:**
- Create: `core/src/main/java/me/vexmc/mental/platform/EffectiveMaterial.java`
- Test: `core/src/test/java/me/vexmc/mental/platform/EffectiveMaterialTest.java`

**Interfaces:**
- Produces: `static Material EffectiveMaterial.of(ItemStack item)` — the `combat:effective_material` PDC material if present + valid, else `item.getType()`; null item → null.

- [ ] **Step 1: Failing test** — marked item → the marked material; unmarked → `getType()`; invalid name → `getType()`. Build items via `ItemStack` + `ItemMeta.getPersistentDataContainer().set(KEY, STRING, "DIAMOND_SWORD")`. (Uses MockBukkit or the existing Mental test harness — check `core/src/test` for the pattern.)
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.** `KEY = NamespacedKey.fromString("combat:effective_material")`; read STRING; `Material.matchMaterial`/`valueOf` guarded (invalid → fall through); absent meta → `item.getType()`.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `feat(compat): read the neutral combat:effective_material marker`.

### Task 1.2: Legacy weapon damage honours the marker

**Files:**
- Modify: `core/src/main/java/me/vexmc/mental/module/hitreg/DamageCalculator.java` (~line 98)
- Test: `core/src/test/java/me/vexmc/mental/module/hitreg/DamageCalculatorTest.java`

**Interfaces:**
- Consumes: `EffectiveMaterial.of` (1.1), `legacyAttackDamage(Material)` (existing).

- [ ] **Step 1: Failing test** — a gold sword marked `DIAMOND_SWORD` with `legacyToolDamage=true` yields `legacyAttackDamage(DIAMOND_SWORD)=8`, not gold's 5. (Extend `DamageCalculatorTest`; if `calculate` needs a live attacker, add a focused test on the extracted resolution instead.)
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.** Replace `legacyAttackDamage(weapon.getType())` with `legacyAttackDamage(EffectiveMaterial.of(weapon))`. Gate unchanged (`legacyToolDamage && weapon != null`). Add a javadoc line: honours the neutral marker; off ⇒ unchanged.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `feat(hitreg): legacy weapon damage honours combat:effective_material`.

### Task 1.3: Weapon durability respects the `max_damage` component

**Files:**
- Modify: `core/src/main/java/me/vexmc/mental/module/damage/WeaponDurability.java` (guard ~line 53, break check ~line 69)
- Test: `core/src/test/java/me/vexmc/mental/module/damage/WeaponDurabilityTest.java` (create if absent)

**Interfaces:**
- Produces: `static int WeaponDurability.effectiveMax(ItemStack, Damageable meta)` — `getMaxDamage()` when `hasMaxDamage()` (1.20.5+, reflected), else `getType().getMaxDurability()`.

- [ ] **Step 1: Failing test** (pure helper) — a Damageable reporting `hasMaxDamage=true, getMaxDamage=1561` → `effectiveMax=1561`; no custom max → material max. Reflect the same way Mental does elsewhere; inject a stub meta.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.** Extract `effectiveMax`; use it for the guard (`effectiveMax <= 0`) and the break check (`newDamage >= effectiveMax`). Reflect `Damageable.hasMaxDamage/getMaxDamage` once (static), mirroring existing Mental reflection idiom.
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `fix(durability): weapon wear respects the max_damage component`.

### Task 1.4: Era-correct tooltip — generalize the rewriter

**Files:**
- Modify/Rename: `core/src/main/java/me/vexmc/mental/module/rules/cooldown/WeaponAttributeTooltipHider.java` → `EraAttributeTooltipRewriter` (update the registration site — find via `new WeaponAttributeTooltipHider`)
- Test: `core/src/test/java/…/EraAttributeTooltipRewriterTest.java` (pure transform over a synthetic modifier set, as far as reflectively testable)

**Interfaces:**
- Consumes: `config.cooldown().enabled()`, `config.armourStrength().enabled()`, `config.hitReg().legacyToolDamage()`, `EffectiveMaterial.of`, `DamageCalculator.legacyAttackDamage`.

Three per-rule transforms over the resolved attribute-modifier set, each gated on its own module; the listener activates if *any* is on:
1. Strip `attack_speed` on weapons/tools when `cooldown` on — **existing behaviour, keep.**
2. Strip `armor_toughness` on armour when `old-armour-strength` on.
3. Rewrite `attack_damage` amount on weapons when `legacy-tool-damage` on so the shown total = `legacyAttackDamage(EffectiveMaterial.of(item))`; modifier amount = `legacyTotal − 1.0`.

- [ ] **Step 1: Failing test** — over a synthetic resolved-modifier set: rule 2 removes toughness for a chestplate when armour-strength on; rule 3 replaces the sword's attack_damage amount so total==8 (marker DIAMOND_SWORD) / ==legacy(getType) unmarked; all rules no-op when their module is off. Extract the pure list-transform (`List<Entry> → List<Entry>` for path A; `Multimap → Multimap` for path B) so it's unit-testable without a live packet.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.** Generalize both reflective paths (NMS component A, Bukkit-defaults B): pass the item's effective material + the three gates; apply strips + the attack_damage amount rewrite (rebuild the modifier/Entry with the new amount, same id/op/slot). Rename the class + gate the packet listener on `cooldown || armourStrength || legacyToolDamage`. Keep the graceful-no-op-on-miss contract.
- [ ] **Step 4: Run, verify pass** + `./gradlew build` (Mental).
- [ ] **Step 5: Commit** `feat(display): era-faithful weapon/armour tooltip (toughness + legacy attack)`.

### Task 1.5: Contract doc + Mental live/matrix verification

**Files:**
- Create: `docs/…/effective-material-contract.md` (StrikeSync) — the neutral key spec.
- Modify: any Mental live/tester suite covering durability/damage if one asserts material-max.

- [ ] **Step 1:** Write the contract doc (key, type, value, semantics, "absent ⇒ own type", both-directions example).
- [ ] **Step 2:** Run Mental's full gate (`./gradlew build` + its integration matrix per StrikeSync's `matrix-gate` equivalent). Verify fresh PASS.
- [ ] **Step 3: Commit** `docs(compat): document the combat:effective_material contract`.
- [ ] **Step 4:** Open PR into StrikeSync `main`; wait for its CI + live matrix green before merge.

---

# PART 2 — StarEnchants (`~/Documents/StarEnchants`, branch `feat/heroic-mental-combat-parity` — current)

Standalone-correct on vanilla + Mental-non-legacy even before Part 1 ships.

### Task 2.1: `HeroicDiamond` — diamond attack + diamond-material name

**Files:**
- Modify: `se/feature/src/feature/heroic/HeroicDiamond.java`
- Test: `se/feature/test/feature/heroic/HeroicDiamondTest.java`

**Interfaces:**
- Produces: `static double diamondAttackDamage(Material)` (sword 7 / axe 9 total incl. base; 0 non-weapon); `static String diamondMaterialName(Material)` — the `DIAMOND_<kind>` name for any sub-diamond gear (armour or sword/axe), else `null` (already ≥diamond / not gear).

- [ ] **Step 1: Failing test** — `diamondAttackDamage`: GOLDEN_SWORD→7, GOLDEN_AXE→9, DIAMOND_SWORD→7, GOLDEN_HELMET→0. `diamondMaterialName`: GOLDEN_SWORD→"DIAMOND_SWORD", IRON_CHESTPLATE→"DIAMOND_CHESTPLATE", DIAMOND_BOOTS→null, NETHERITE_HELMET→null, STICK→null. Assert against the returned constants (no external string source — these are our parity constants, hand-computed).
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.** `diamondAttackDamage`: `_SWORD`→7, `_AXE`→9, else 0. `diamondMaterialName`: map `_HELMET/_CHESTPLATE/_LEGGINGS/_BOOTS/_SWORD/_AXE` → `"DIAMOND_"+kind`, returning null when the name starts `DIAMOND_`/`NETHERITE_` or isn't gear.
- [ ] **Step 4: Run, verify pass** (`./gradlew :se-feature:test --tests '*HeroicDiamondTest'`).
- [ ] **Step 5: Commit** `feat(heroic): diamond attack-damage + diamond-material-name parity constants`.

### Task 2.2: `EffectiveMaterialCodec`

**Files:**
- Create: `se/item/src/item/codec/EffectiveMaterialCodec.java`
- Test: `se/item/test/item/codec/EffectiveMaterialCodecTest.java`

**Interfaces:**
- Produces: `void write(ItemStack, String materialName)` / `Optional<String> read(ItemStack)` over `NamespacedKey.fromString("combat:effective_material")` STRING; blank/absent/no-meta → empty. Pure item-state (no Bukkit Material resolution — SE only stores the name).

- [ ] **Step 1: Failing test** — round-trips a name; empty when unset; empty for an air/meta-less stack. Mirror the existing codec test pattern in `se/item/test/item/codec`.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.** Thin PDC read/write; the neutral key via `NamespacedKey.fromString` (NOT `ItemKeys`, which is SE-namespaced/versioned).
- [ ] **Step 4: Run, verify pass.**
- [ ] **Step 5: Commit** `feat(item): EffectiveMaterialCodec for the neutral combat marker`.

### Task 2.3: `HeroicVanillaStats` — write weapon attribute, stop hiding

**Files:**
- Modify: `se/feature/overlay/modern/feature/heroic/HeroicVanillaStats.java`
- Legacy no-op unchanged: `se/feature/overlay/legacy/feature/heroic/HeroicVanillaStats.java` (still returns false)

**Interfaces:**
- Consumes: `HeroicDiamond.diamondAttackDamage` (2.1).
- Produces: `static boolean apply(ItemStack, boolean weapon)` — now writes the weapon `GENERIC_ATTACK_DAMAGE` modifier for a sub-diamond weapon and **no longer sets `HIDE_ATTRIBUTES`** (armour or weapon). Returns whether real attrs (armour OR weapon) were written for this piece.

- [ ] **Step 1:** Add `ATTACK_DAMAGE = byKey("attack_damage", "generic.attack_damage")`. Add `writeWeapon(meta, type)`: `addModifier(ATTACK_DAMAGE, "attack_damage", EquipmentSlot.HAND, diamondAttackDamage(type) - 1.0)` when it's a sub-diamond `_SWORD`/`_AXE`; return true. No attack-speed write.
- [ ] **Step 2:** In `apply`, branch: weapon → `writeWeapon` (guarded by `ATTACK_DAMAGE != null` and sub-diamond); armour → existing `writeArmour`. **Remove** `meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES)` from `writeArmour`; do not add it for weapons. Keep `applyMaxDurability` for both.
- [ ] **Step 3:** Verify no other code depends on HIDE_ATTRIBUTES being set (grep `HIDE_ATTRIBUTES` in `se/`).
- [ ] **Step 4: Run** `./gradlew :se-feature:test` (unit-level; the writer is verified live in 2.6).
- [ ] **Step 5: Commit** `feat(heroic): write real diamond attack attribute; drop HIDE_ATTRIBUTES`.

### Task 2.4: `HeroicService` — stamp marker, drop the right flat delta

**Files:**
- Modify: `se/feature/src/feature/heroic/HeroicService.java` (~lines 129-137)
- Test: `se/feature/test/feature/heroic/HeroicServiceTest.java` (or the existing service test)

**Interfaces:**
- Consumes: `EffectiveMaterialCodec` (2.2), `HeroicDiamond.diamondMaterialName` (2.1), `HeroicVanillaStats.apply` (2.3).

- [ ] **Step 1: Failing test** — forging a sub-diamond weapon with vanilla-stats on: the marker PDC = `DIAMOND_SWORD` and the resulting `CombatState.heroic().flatDamage()==0` (real weapon attr ⇒ no plugin-maths double-count). Forging a diamond piece (no swap): no marker. Use the default-messages `HeroicService` ctor as the existing suites do.
- [ ] **Step 2: Run, verify fail.**
- [ ] **Step 3: Implement.**
  ```java
  boolean realStats = cfg.diamondStats() && cfg.vanillaStats() && HeroicVanillaStats.apply(upgraded, weapon);
  boolean realArmour = !weapon && realStats;
  boolean realWeapon = weapon && realStats;
  double flatDamage = cfg.diamondStats() && weapon && !realWeapon
          ? HeroicDiamond.weaponFlatDamage(upgraded.getType()) : 0.0;
  double flatReduction = cfg.diamondStats() && !weapon && !realArmour
          ? HeroicDiamond.armourFlatReduction(upgraded.getType()) : 0.0;
  // Neutral cross-plugin contract: name the diamond this gold piece stands in for (§ADR 0032).
  if (cfg.diamondStats()) {
      String trueMat = HeroicDiamond.diamondMaterialName(upgraded.getType());
      if (trueMat != null) {
          effectiveMaterial.write(upgraded, trueMat);
      }
  }
  ```
  (Inject an `EffectiveMaterialCodec effectiveMaterial` field, constructed in the same spots `combat`/`lore` are.)
- [ ] **Step 4: Run, verify pass** + fix the ctor call sites (grep `new HeroicService(`).
- [ ] **Step 5: Commit** `feat(heroic): stamp the effective-material marker; drop flat delta when real attrs written`.

### Task 2.5: heroic.yml + ADR 0032

**Files:**
- Modify: `se/bootstrap/packs-src/cosmic-pack/items/heroic.yml` (comment: `vanilla-stats` now also writes the weapon attribute + neutral marker; no more hidden attributes)
- Create: `docs/decisions/0032-heroic-cross-plugin-combat-parity.md`
- Regen if needed: `./gradlew regenDocs` (surface.json/catalog if heroic.yml comments feed docs)

- [ ] **Step 1:** Update heroic.yml comments (no new keys). Write ADR 0032 (Status Accepted; amends the display slice of ADR 0031 — HIDE_ATTRIBUTES removed, vanilla renders; adds the neutral contract + Mental era ownership).
- [ ] **Step 2:** `./gradlew regenDocs`; confirm the diff is only intended.
- [ ] **Step 3: Commit** `docs(heroic): ADR 0032 + heroic.yml notes for cross-plugin parity`.

### Task 2.6: Update the live suite

**Files:**
- Modify: `se/tester/src/tester/suite/HeroicVanillaStatsSuite.java`

- [ ] **Step 1:** Flip `heroic.vanilla.hiddenTooltip`: assert `HIDE_ATTRIBUTES` is **absent** (rename the check to `heroic.vanilla.visibleTooltip`). Add: forge a `DIAMOND_SWORD→GOLDEN_SWORD`, assert `getAttributeModifiers(HAND)` contains amount `6.0` (diamond sword modifier) and the `combat:effective_material` PDC == `DIAMOND_SWORD`. Keep the armour (8.0/2.0) + component-max assertions.
- [ ] **Step 2:** `./gradlew build`.
- [ ] **Step 3: Commit** `test(heroic): live-assert visible attrs + weapon modifier + marker`.

### Task 2.7: Gate + PR (StarEnchants)

- [ ] **Step 1:** `./gradlew build` (unit) — PASS.
- [ ] **Step 2:** Run the live Paper+Folia matrix subset covering heroic/combat/render (per `matrix-gate`); verify FRESH PASS (both Paper AND Folia). This touches item attributes + PDC across the mapping flip → the matrix is warranted, not just `build`.
- [ ] **Step 3:** Open PR into `main`. **WAIT for the live matrix (non-required check) green before merge** (per the release-matrix memory — auto-merge once shipped a matrix-caught bug).
- [ ] **Step 4:** After merge, bump `build.gradle.kts` version (release ADR 0025) → release; report the download link + the "verify on your Mental server" note.

---

## Self-review

- **Spec coverage:** A1 marker→2.2+2.4; A2 attributes/no-hide→2.3; A3 durability→2.3 (component kept) + 2.6 assert; A4 config→2.5. B1→1.2; B2→1.3; B3→1.4; B4→1.5. Values (§6)→2.1 constants + 1.4 rewrite. Rollout (§8)→1.5/2.7. ✅
- **Placeholders:** none — each code step shows the change; test steps state the exact assertions.
- **Type consistency:** `EffectiveMaterial.of` (Mental) vs `EffectiveMaterialCodec.read/write` (SE) — distinct by repo, both over the same STRING key. `apply(ItemStack, boolean)` return reused by 2.4. `diamondMaterialName`/`diamondAttackDamage` names consistent 2.1→2.3/2.4.
- **Open impl choices (flagged, not blocking):** the <1.20.5 marker-max fallback in 1.3 (default off); rename vs keep of the tooltip class in 1.4 (rename recommended).
