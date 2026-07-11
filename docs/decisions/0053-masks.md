# ADR 0053: Masks — a helmet-only applicable head-likeness family on the shared engine

- **Status:** Accepted
- **Date:** 2026-07-11
- **Deciders:** project owner + agent
- **Extends:** ADR-0014 (content compiler), ADR-0039 (source-erased `Ability`), ADR-0041
  (apply gestures), ADR-0047 (feature-module wiring), ADR-0052 (pets — textured heads)
- **Relates to:** ADR-0034/0035 (crystals — the applied-to-gear precedent), ADR-0040
  (render-from-state), ADR-0044 (era seams), ADR-0012/0050 (the additive damage fold)

## Context

A mask is a textured player-head item that drag-applies onto a HELMET (exclusively), grants
passive abilities while the helmet is worn, and — the novel part — overrides the helmet's
WORN appearance for the wearer and every observer with the mask's player-head likeness,
without ever touching the inventory item. One mask per helmet; right-clicking the masked
helmet pops the mask back off intact. Crystals already prove the apply-onto-gear shape;
pets already prove textured heads and the content-family recipe. Two things have no
precedent: a client-only worn-equipment repaint, and four ability semantics the engine
cannot yet express (mob-target calm, /invsee//near shielding, splash-heal boost, fish-hook
knockback immunity, per-effect-kind suppression, heroic negation).

## Decision

1. **A new content family `content/masks/*.yml`** — the 6th `LibraryLoader` loop, directly
   after pets. Filename stem = the mask key; stable keys `masks/<stem>` (+ `/aN` for
   multi-ability, the crystal reader shape — no levels, no brackets). A def declares
   `display`, `color`, `description`, `head` (base64), optional `material` fallback, and
   `abilities:` in the crystal dual form. `MaskDefReader` mirrors `CrystalDefReader`;
   malformed input is `E_LOAD_MASK`, never a throw. `SourceKind.MASK` is the 7th source
   kind. Applies-to is NOT authored: masks are helmets-only by construction — the service
   validates against the existing `ItemGroups` HELMET group.

2. **On-helmet state is Path A (the `setKey` shape), not a `PetSource`.** `CombatState`
   gains a nullable `String maskKey` (blob label `m` in `CombatCodec`; unknown labels are
   already ignored by old readers). `WornResolver.resolveFrom` gains one branch after the
   crystal block — `keys.idOf(maskKey)` + the `/aN` chain feed `firing` like any source;
   masks never join set/heroic/crystal accounting. Re-resolution rides the existing armour
   feeders (`PlayerArmorChangeEvent` / legacy gear poll) — a helmet mask needs no new
   refresh listener and no sweep for state. `FeaturesSection` gains `masks` (LIVE toggle).

3. **One mask per helmet; gestures per ADR-0041.** The physical mask item carries its def
   key under a new `ItemKeys` logical key `maskitem` (`MaskCodec`, the `PetCodec` shape).
   Apply = a `MaskListener` leaf of `ApplyGestureListener`: cursor claims mask items,
   validation = helmet group + amount 1 + no existing mask (`mask.already`) + def
   exists/compiled; commit = `withMask(key)` + codec write + ADR-0040 recompose + consume
   cursor. Remove = right-click the masked helmet with an EMPTY cursor in the own inventory
   — a third documented non-template gesture (`MaskRemoveListener`, joining the soul-gem
   merge and unopened-book exemptions): cancel the vanilla pickup, clear `maskKey`,
   recompose, and hand the popped mask back via `Inventories.giveOrDrop`. Both cues flow
   through `items/mask.yml` sounds and `mask.*` lang keys.

4. **The worn repaint is an ADR-0044 era seam `item.head.EquipmentRepaint`** —
   `boolean helmet(Player recipient, Player wearer, int wearerEntityId, ItemStack shown)`,
   inert `NONE` default.
   - **Modern (1.18.2 → 26.1.x):** `ModernEquipmentRepaint` resolves
     `Player.sendEquipmentChange(LivingEntity, EquipmentSlot, ItemStack)` once by name via
     `MethodHandle` (Bukkit API — mapping-flip immune; the method is absent from the
     1.17.1 compile floor, so it cannot be a direct call), gated
     `Capabilities.atLeast(1, 18, 2)`.
   - **1.17.1 exactly:** inert (`NONE`) — the mask still grants abilities; the visual
     override is a recorded degrade (the `VanillaStats.NONE` asymmetric-seam convention).
     A reflective spigot-mapped packet route was rejected as disproportionate.
   - **Legacy 1.8.9:** `LegacyEquipmentRepaint` sends
     `PacketPlayOutEntityEquipment(entityId, 4, CraftItemStack.asNMSCopy(shown))` with
     direct `v1_8_R3` types (the `LegacyDispatchSink.sendPacket` precedent; helmet slot
     index 4 in the 1.8 wire order held/boots/legs/chest/helmet).
   The shown head comes from the existing `TexturedHeads` seam, built once per mask key
   and cached; `TexturedHeads` returning null (untexturable server) degrades to no repaint.

5. **Illusion maintenance is snapshot-out, recipient-thread-in.** A
   `MaskIllusionStore` (concurrent, UUID-keyed) holds each wearer's pre-built shown head +
   entity id, maintained on the wearer's own thread whenever their worn state refreshes
   (equip listener seam) and cleared on unequip/quit (unequip also broadcasts a one-shot
   true-equipment restore captured on the wearer's thread). Because vanilla re-broadcasts
   true equipment whenever an observer starts tracking the wearer (and 1.8 does so on its
   own tracker cadence), a repeating global sweep (40t) fans out per the pets pattern —
   `Scheduling.onEntity(recipient, …)` per online player — re-asserting every masked
   wearer's override to same-world players; packets about untracked entity ids are
   client-ignored, and the wearer is included for the F5 self-view. `PlayerTrackEntityEvent`
   (1.19.4+ only) was rejected this round: the sweep already bounds the flash window to 2s
   across ALL versions including 1.8, at negligible cost (masked wearers only).

6. **Universal likeness + one gear line.** `items/mask.yml` (`type: mask`) is the one
   likeness for every mask (the pet.yml precedent): `name`/`lore` templates with
   `{COLOR}`/`{NAME}`/`{DESCRIPTION}` (line-expanding)/`{APPLIES}` (the `ItemGroups`
   kinds label for HELMET), plus `lore-while-on-item` — the on-gear line rendered by a
   new `LoreComposer` section **directly below the crystal line(s)**, `{NAME}` expanding
   to the mask's colour-styled display. `LoreRenderer.Config` gains the `maskLine`
   supplier + a mask-display lookup. Apply/remove sounds live on the same file.

7. **New engine surface** (all fingerprint-drifting; goldens regenerated):
   - **`WARD`** (new kind): arms a typed, TTL'd per-player flag in a new `WardStore`
     (the `TeleblockStore` shape) — `type: mob-target | invsee | near | splash-heal`,
     `duration`, optional `amount`. Feature guards consult it:
     `MobTargetGuard` (cancels `EntityTargetLivingEntityEvent` at a warded player unless
     the wearer provoked that mob — provocation recorded from a MONITOR damage listener
     into a TTL'd per-wearer store), `InvseeGuard` (cancels another player opening a
     warded player's own `PlayerInventory`), `NearGuard` (see 8), `SplashHealGuard`
     (scales `PotionSplashEvent` intensity for warded targets by `amount`% when the
     potion carries a healing effect — instant-health/regeneration resolved through the
     boot-time resolver, never a bare constant). Content re-arms wards on `REPEATING`
     (the Wither-TELEBLOCK idiom), so flags self-heal and never outlive the helmet.
   - **`IMMUNE` gains type `fishhook`**: `ImmuneListener` additionally cancels
     fish-hook damager events and the `PlayerFishEvent` CAUGHT_ENTITY reel when the
     caught entity is flagged — killing rod knockback at the source.
   - **`SUPPRESS` gains scope `KIND`** (`ScopeKinds.KIND = 3`): suppression keyed by
     effect head (e.g. `MODIFY_FOOD`), matched at gate 5 against the ability's compiled
     effect kind ids (checked only when the victim has any KIND window — zero cost
     otherwise), mirrored in `PassiveEffectDriver` via the shared `suppressesAny` seam.
     The key cross-validates against the effect registry at compile (new `CrossRule`,
     fuzz generator taught). Chef therefore disables *any* hunger-restoring ability,
     present or future, without enumerating enchants.
   - **`IGNORE_HEROIC`** (new kind, no params): sets a per-hit sink flag; `DamageFold`
     now accumulates the victim's heroic reduction/flat-reduction in a heroic-tagged
     bucket that the commit skips when flagged. Attacker-side heroic (weapon damage) is
     untouched — the spec negates enemy heroic *armor* bonuses only.
   - No new triggers, no new facts, no new selectors.

8. **`/near` immunity is an owned interception, config-gated.** No plugin can filter
   another plugin's proximity scan, so `NearGuard` listens to
   `PlayerCommandPreprocessEvent` for the master-config command list
   (`masks.near-commands`, default `[near]`), cancels, and answers with its own listing
   (radius `masks.near-radius`, default 200) that excludes `near`-warded players —
   locations of cross-region players are `Regions`-guarded reads on Folia (unreadable =
   omitted). Setting `near-commands: []` disables the interception (the divergence knob,
   per the modernize-freely-but-opt-in invariant). `/invsee` needs no such knob — the
   `InventoryOpenEvent` cancel is plugin-agnostic.

9. **Ability mapping for the cosmic pack** (14 masks, pure YAML given 7):
   Agent/Angel/Santa = maintained `POTION` (invisibility / regeneration /
   health-boost) on PASSIVE; Monopoly/Knight = percent `DAMAGE_MOD` (armor-side content
   stays percent, ADR-0050 R3); Blaze = `CANCEL` on the FIRE trigger; Wither =
   `TELEBLOCK @Aoe{filter=ENEMIES}` on REPEATING; Shaman/Ghost/Hacker/Medic = `WARD`
   on REPEATING; Fisherman = `IMMUNE type:fishhook` on REPEATING; Chef =
   `SUPPRESS scope:KIND key:MODIFY_FOOD` riders on ATTACK+DEFENSE; Midas =
   `IGNORE_HEROIC` on ATTACK.

10. **One `MasksModule`** (ADR-0047): LIVE toggle `features.masks`; listeners (apply
    leaf, remove gesture, illusion refresh hooks, the four ward guards); the 40t illusion
    sweep boot; `WardStore` + provocation + illusion stores cleared on quit/disable;
    `Mints.mask` (give + tiles); `mask.*` lang roots; plugin-item guard for mask items.
    A masks browser menu is deliberately deferred (mint tiles suffice this round).

## Consequences

- Adding a mask is PURE YAML, gated by `CatalogValidationTest`/`CosmicPackValidationTest`
  (default-catalog exemplars ship under `resources/content/masks/`).
- New kinds/params/scope drift the ADR-0046 fingerprint + docs goldens (`./gradlew
  regenDocs`), the wiring goldens (`RegistryWiringTest`, module count G2-b ≥ 20), and the
  items index (hand-edited).
- The repaint is best-effort by design: 1.17.1 shows the real helmet (recorded degrade);
  new observers may see up to ~2s of true equipment before the sweep re-asserts; the
  first-person wearer never sees their own head anyway. Nothing ever rewrites the real
  ItemStack, so there is no desync to recover from.
- `DamageFold` gains one bucket + one flag on the hot path (two adds and a branch —
  within the ADR-0039 budget); gate 5's KIND check costs one empty-set test per
  activation when unused.
- The `/near` interception replaces the external plugin's output format while any
  `near`-command is configured — a documented, opt-out divergence.
- Suppressing by KIND suppresses passives of that kind too (the driver mirror) — correct
  and intended (a suppressed `MODIFY_FOOD` passive must also stop).
