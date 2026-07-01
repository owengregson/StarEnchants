# Heroic ↔ Mental combat-stat parity — display, damage & durability

- **Status:** Draft for review (pre-implementation spec)
- **Date:** 2026-06-30
- **Relates to:** ADR 0021 (heroic diamond-equivalence), ADR 0031 (heroic vanilla-stats),
  ADR 0026 (Mental knockback coordination), `docs/v3-directives.md` §F
- **Repos touched:** **StarEnchants** (primary) + **StrikeSync / Mental** (`me.vexmc.mental`)
- **Graduates to:** ADR 0032 on approval (StarEnchants) + a StrikeSync spec doc for the Mental changes
- **Chosen split:** *Mental renders the era tooltip; SE stays era-agnostic.* Display is driven by REAL
  attribute modifiers (vanilla renders them) — SE never hand-writes attribute lore, never sets
  `HIDE_ATTRIBUTES`, and holds no modern/1.8 logic. Mental owns the era-correct presentation on its own
  servers via its existing packet tooltip-rewriter.

## 1. Problem

A heroic piece is display-swapped from diamond to a gold twin but must **function and display as
diamond** everywhere: vanilla-modern servers, and Mental servers running 1.8-combat restoration.
v1.1.18 (ADR 0031) fixed armour *defence* but left gaps, now confirmed by reading Mental's source
(`Documents/StrikeSync`, not guessed):

| Surface | How it resolves the item today | Result on a heroic gold piece |
|---|---|---|
| **Armour defence** — `ArmourStrengthModule` → `Attributes.armor()` | summed **`GENERIC_ARMOR` attribute** (toughness ignored — 1.8 has none) | ✅ correct (our diamond modifier is read) |
| **Armour tooltip** | vanilla renders the attribute line; v1.1.18 set **`HIDE_ATTRIBUTES`** | ❌ the "+N Armor" line disappeared |
| **Weapon damage (Mental legacy path)** — `DamageCalculator.legacyAttackDamage(weapon.getType())` | the **material name** (GOLD); the attack attribute is **ignored** | ❌ deals **gold** damage, not diamond |
| **Weapon tooltip** | resolved `attack_damage` modifier (Mental's `WeaponAttributeTooltipHider` keeps it, strips only speed) | ❌ shows **gold** (we write no attack attribute) |
| **Weapon durability (Mental fast path)** — `WeaponDurability.applyOneHit` | `weapon.getType().getMaxDurability()` = **material max** (gold 32) | ❌ breaks at ~32 hits, ignoring our diamond `max_damage` component |
| **Armour durability** — `ArmourDurabilityModule` → `PlayerItemDamageEvent` | vanilla wear, **component-aware** | ✅ correct (breaks at the diamond component max) |

Three facts drive the design:

1. **Armour reads the attribute; weapons read the material.** A diamond `GENERIC_ARMOR` modifier fixes
   armour defence on every server. A diamond `GENERIC_ATTACK_DAMAGE` modifier fixes weapon damage on
   vanilla + Mental's *non*-legacy path, but on Mental's `legacy-tool-damage` path the material still
   wins — that path needs a Mental change.
2. **Weapon durability under Mental ignores the `max_damage` component** (`WeaponDurability` compares
   against the material max), so any custom-max item breaks early — a general Mental correctness bug.
3. **Mental already rewrites weapon tooltips at the packet layer** (`WeaponAttributeTooltipHider` strips
   `attack_speed`). It is the natural place to make the *era* tooltip correct — strip the meaningless
   toughness line and show the legacy attack number — because the era lives in Mental, live and
   config-driven, not baked into an item at forge time.

## 2. Goals / non-goals

**Goals**
- The heroic gold piece **deals, resists, lasts, and displays as diamond** on vanilla-modern, Mental
  non-legacy, and Mental 1.8-combat servers.
- Display is **vanilla-rendered from real attribute modifiers** — no SE-authored attribute lore, no
  `HIDE_ATTRIBUTES`. On a 1.8-combat server the shown values are the **1.8 values** (armour: no
  toughness; weapon: the legacy number) via Mental's tooltip layer, so display == reality, live.
- The plugin bridge is a **vendor-neutral, documented contract** any display-swapping plugin can stamp
  and any combat plugin can honour.
- SE holds **no era logic** and no compile-time dependency on Mental.

**Non-goals**
- Reconciling SE's own combat pipeline (custom-enchant deltas, heroic ±% fold) with Mental's packet fast
  path — Mental's `HitApplier` calls `damage(amount, attacker)`, which fires the Bukkit damage chain, so
  SE's `EntityDamageByEntity` listeners still run; deeper coordination is a separate track.
- Changing heroic gameplay balance (percent buffs unchanged).
- Era-perfect display on **non-Mental** 1.8 plugins (they'd need to honour the same contract). A
  non-Mental *modern* server shows modern diamond, which is correct for it.
- <1.20.5 weapon-durability-under-Mental parity is best-effort (§7.3): the `max_damage` component is
  1.20.5+.

## 3. Design overview — a neutral "effective material" contract

> **Core principle (the single rule everything follows).** SE makes the gold piece a **diamond** — it
> writes a real diamond's attribute values (the current *modern* form) + the marker, **unconditionally**,
> and never reads, detects, or reacts to any "era". **Mental alone decides whether values are legacy**,
> strictly gated on *its own* restore modules (`old-armour-strength`, `legacy-tool-damage`, …). Crucially,
> Mental applies that decision **uniformly to the heroic gold piece and a real diamond alike** — the
> marker makes the gold piece indistinguishable from a real diamond to Mental — so "gold = diamond of the
> era" falls out for free. With **no** Mental restore module on, the piece is exactly a modern diamond,
> everywhere. SE never hard-codes a legacy number; Mental computes it from its own tables.

SE stamps every heroic piece with a **vendor-neutral PDC marker** recording its true, pre-swap material,
and writes **real modern diamond attribute modifiers**. Responsibilities:

| Concern | Owner | Mechanism |
|---|---|---|
| Item is diamond on vanilla + Mental-non-legacy (defence, attack, base tooltip) | **StarEnchants** | real diamond `GENERIC_ARMOR`(+toughness) / `GENERIC_ATTACK_DAMAGE` modifiers — **no `HIDE_ATTRIBUTES`** |
| Item lasts like diamond (bar + break point) | **StarEnchants** | diamond `max_damage` **component** (1.20.5+) |
| Weapon *deals* diamond on Mental's legacy path | **Mental** | reads the marker → `legacyAttackDamage(trueMaterial)` |
| Weapon durability respects the component under Mental | **Mental** | component-max-aware `WeaponDurability` |
| **Era-correct tooltip** (strip toughness in 1.8; show legacy attack number) | **Mental** | extend the packet tooltip-rewriter, marker-aware |

**The contract key**
- Key: `combat:effective_material` — `NamespacedKey.fromString("combat:effective_material")`, a neutral
  namespace both plugins agree on (not `mental:` / `starenchants:`).
- Value: `PersistentDataType.STRING`, the Bukkit `Material` name of the *true* item (e.g. `DIAMOND_SWORD`).
- Semantics (documented both repos): "A combat plugin MAY treat this item as the named material for
  era/legacy stat computation and tooltip. Absent ⇒ use the item's own type." Read-only on the honour side.

## 4. Part A — StarEnchants changes (era-agnostic, minimal)

### A1. Stamp the effective-material marker
- Small codec `item.codec.EffectiveMaterialCodec`: `write(ItemStack, Material trueType)` /
  `Optional<Material> read(ItemStack)` over `combat:effective_material` STRING. Invalid name → empty.
- `HeroicService.applyTo` stamps it with the **pre-swap** gear type, only when a material swap actually
  happens (a no-swap heroic piece already IS its true material).

### A2. Write real diamond attribute modifiers; STOP hiding them
- `HeroicVanillaStats.apply` (modern overlay):
  - **Armour** (existing): `GENERIC_ARMOR` = diamond points (helmet 3 / chest 8 / legs 6 / boots 3) +
    `GENERIC_ARMOR_TOUGHNESS` = 2, on the piece's slot. Always modern (era is Mental's concern).
  - **Weapon** (new): `GENERIC_ATTACK_DAMAGE` `ADD_NUMBER` = `diamondAttack − 1.0` on `EquipmentSlot.HAND`
    (base attack 1.0; sword 7 ⇒ +6, axe 9 ⇒ +8). No attack-speed write.
  - **Remove the `HIDE_ATTRIBUTES` flag** entirely (both armour and weapon). Vanilla renders the block at
    the tooltip bottom — the owner's requested "When on Feet / Main Hand" format, natively.
  - Diamond `max_damage` component (existing, 1.20.5+) for both.
  - Returns which real attributes were written; `HeroicService` drops the plugin-maths `flatReduction`
    (armour) / `flatDamage` (weapon) for that piece — keep `percent*` buffs, never double-count.
- `HeroicDiamond` gains `diamondAttackDamage(Material)` (sword 7 / axe 9, total incl. base) — pure, tested.
- **No `LoreRenderer` attribute-lore work** (the earlier fake-lore plan is dropped). The decorative
  HEROIC line stays as-is.
- **No era detection in SE.** SE always writes modern diamond; Mental presents the era.

### A3. Durability
- Rely on the diamond `max_damage` component (1.20.5+) as the real, component-driven bar; Mental
  (post-patch B2) and vanilla both wear against it. Keep the existing `HeroicDurabilityListener`
  wear-cancel as the heroic `percent-durability` **buff** (collapses to base when the effective max is
  diamond). Note: the buff does not fire on Mental's weapon fast path (which bypasses
  `PlayerItemDamageEvent`); the component still guarantees diamond longevity there. Add a regression
  assertion that the component survives the full forge.
- <1.20.5: unchanged (wear-cancel scaling); the Mental-weapon gap is documented (§7.3).

### A4. Config
- `vanilla-stats` (existing) now also gates the weapon attribute write + the marker. `diamond-stats`
  unchanged. **No new keys, no `HIDE_ATTRIBUTES`.**

## 5. Part B — Mental (StrikeSync) changes

Three changes: two small functional fixes + one tooltip extension. Each is a clean no-op when the
marker/component/flag is absent.

### B1. Honour the marker in legacy weapon damage
- **File:** `core/.../module/hitreg/DamageCalculator.java` (+ small `EffectiveMaterial` helper).
- Resolve the effective material from `combat:effective_material` (STRING → `Material.valueOf`, guarded)
  and use it for the legacy table only:
  ```java
  Material effective = EffectiveMaterial.of(weapon); // marker, else weapon.getType()
  Double legacy = legacyToolDamage && weapon != null ? legacyAttackDamage(effective) : null;
  ```
- Effect: heroic gold sword → `legacyAttackDamage(DIAMOND_SWORD)` = 8 (axe 7) on the legacy path.
- **Strictly gated on Mental's existing `legacy-tool-damage` flag.** When it is off, this path never runs
  and the SE-written modern diamond attribute drives both the damage and the tooltip — the gold piece is
  a modern diamond. Mental owns the era decision; SE contributes nothing to it.

### B2. Respect the `max_damage` component in weapon durability
- **File:** `core/.../module/damage/WeaponDurability.java`.
- Replace `weapon.getType().getMaxDurability()` (break check ~line 69 and guard ~line 53) with an
  **effective max**: `Damageable.hasMaxDamage()`/`getMaxDamage()` (1.20.5+, reflected as Mental does
  elsewhere) when present, else the material max. Break when `newDamage >= effectiveMax`.
- Effect: heroic gold sword wears against 1561, not 32. General correctness fix for any custom-max item.
- Optional (<1.20.5 best-effort): also consult the marker's `Material.getMaxDurability()` — documented
  caveat that the bar still reflects the display material on <1.20.5. Decide at implementation.

### B3. Era-correct tooltip (extend `WeaponAttributeTooltipHider` → a general era rewriter)
Generalise the existing packet rewriter (SET_SLOT / WINDOW_ITEMS, NMS component path A + Bukkit-defaults
path B) into per-rule rewrites over the resolved attribute set, each gated on its own module/flag:

1. **Strip `attack_speed`** on weapons/tools when `attack-cooldown` on — *unchanged* (existing behaviour).
2. **Strip `armor_toughness`** on armour when `old-armour-strength` on — same removal mechanism as speed,
   extended to armour items. (1.8 has no toughness ⇒ the line is meaningless.)
3. **Rewrite `attack_damage`** on weapons when `legacy-tool-damage` on: replace the resolved
   `attack_damage` modifier's amount so the shown total = `legacyAttackDamage(EffectiveMaterial.of(item))`
   (marker-aware). Modifier amount = `legacyTotal − 1` (base 1.0). This makes tooltip == actual for
   *every* weapon on a legacy server (a general era-faithfulness win), and a heroic gold sword shows 8.
- Gating is per-rule (speed↔cooldown, toughness↔armour-strength, damage↔legacy-tool-damage); the listener
  activates if *any* rule is on. **When a rule's module is off, that surface renders the unchanged
  SE-written modern diamond attribute** — no era transform happens unless Mental's own module elects it.
  Display-only, on the packet copy — never the real stack. Graceful no-op on any version/reflection miss
  (the line simply stays, as today). The rewrite keys off `EffectiveMaterial.of(item)`, so it treats a
  marked gold piece exactly like the real diamond it names.

### B4. Contract doc
- `docs/…/effective-material-contract.md` in StrikeSync describing `combat:effective_material` as a
  stable, documented honour-side API for any plugin.

## 6. Values (single-sourced)

Attack totals **include** the base 1.0 (as the tooltip does). SE always writes the **modern attribute**;
Mental rewrites the *displayed* weapon number to the 1.8 value on a legacy server.

| Piece | SE-written attribute (modern) → vanilla tooltip | Mental 1.8 tooltip (legacy on) | Actual damage (Mental legacy) |
|---|---|---|---|
| Sword | +6 ⇒ **7** | **8** (`1 + 4 + 3`) | 8 (marker → legacy table) |
| Axe | +8 ⇒ **9** | **7** (`1 + 3 + 3`) | 7 |
| Helmet | +3 armor, **+2** toughness | +3 armor (toughness line stripped) | n/a (armour attribute-driven) |
| Chestplate | +8 armor, **+2** toughness | +8 armor (stripped) | n/a |
| Leggings | +6 armor, **+2** toughness | +6 armor (stripped) | n/a |
| Boots | +3 armor, **+2** toughness | +3 armor (stripped) | n/a |
| Durability (component max) | sword/axe 1561, helmet 363, chest 528, legs 495, boots 429 | same | breaks at component max (B2) |

**Note for review:** 1.8 swords hit *harder* (8) and axes *softer* (7) than modern — era-accurate; the
sword showing 8 (not 7) on a legacy server is intentional so display == damage.

## 7. Testing

### 7.1 Unit — StarEnchants
- `HeroicDiamond.diamondAttackDamage` (7/9) + per-slot armour/toughness (pure).
- `EffectiveMaterialCodec` round-trip + invalid-name → empty.
- `HeroicService`: writing a weapon attribute drops `flatDamage`; marker stamped on swap, absent on no-swap.

### 7.2 Unit — Mental
- `EffectiveMaterial.of` (marked / unmarked / invalid).
- `WeaponDurability` effective-max: breaks at component max, not material max.
- `DamageCalculator`: legacy damage uses the effective material.
- Tooltip rewriter (as far as pure-testable): the per-rule modifier-set transform (strip toughness /
  strip speed / rewrite attack amount) over a synthetic modifier set.

### 7.3 Live matrix
- StarEnchants `HeroicVanillaStatsSuite`: assert the weapon `GENERIC_ATTACK_DAMAGE` modifier (6/8),
  `GENERIC_ARMOR`/toughness, the marker PDC value, `HIDE_ATTRIBUTES` **absent**, and (1.20.5+) the
  component max survives the full forge.
- **Client-rendered tooltips + cross-plugin are not matrix-testable**: the end-to-end
  "deals/resists/lasts/looks diamond, era-correct on my Mental server" is verified on the owner's live
  server; each plugin's matrix verifies its own half.
- Document the <1.20.5 + Mental weapon-durability gap explicitly (no silent cap).

## 8. Rollout
- Two repos, two releases:
  1. **StrikeSync / Mental** (B1–B4) → Mental release. Ships first so the era display + legacy damage +
     durability are correct the moment SE updates.
  2. **StarEnchants** (A1–A4, ADR 0032) → release. Standalone-correct on vanilla + Mental-non-legacy even
     before the Mental release (armour/attack/durability all show/behave diamond-modern); the Mental
     release adds the era-correct tooltip + legacy-damage/durability on 1.8 servers.
- Graceful degradation if only one ships: SE-only ⇒ diamond-modern everywhere (weapon shows 7 not 8 on a
  legacy server, toughness line shows; damage/durability handled by Mental once it ships). Mental-only ⇒
  no heroic items to honour. No breakage either way.

## 9. Risks & open questions
- **Mental tooltip-rewriter scope:** rewriting the `attack_damage` *amount* (not just stripping a line)
  is the largest new Mental piece; feasible via the same resolved-modifier rebuild already used for
  attack_speed, on both the NMS-component and Bukkit-defaults paths. Graceful no-op on miss.
- **SE combat vs Mental fast path:** out of scope (non-goal), flagged.
- **Neutral namespace:** `combat:` is a convention documented in both repos, not a registered authority.
- **Durability buff on Mental weapon path:** the heroic `percent-durability` +% buff doesn't apply to
  weapons on Mental's fast path (bypasses `PlayerItemDamageEvent`); the component still gives diamond
  longevity. Acceptable; noted.
