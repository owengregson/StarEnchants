package item.worn;

import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import item.codec.CombatState;
import item.codec.HeroicStat;
import item.view.ItemView;
import item.view.ItemViewCache;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/**
 * Resolves a {@link LivingEntity}'s worn + held items into an immutable {@link WornState} on an
 * equipment change (§5.5; ADR-0014) — NEVER per hit. Each item is read once through the
 * {@link ItemViewCache}; each enchant's {@code (baseKey, level)} composes a per-level stable key
 * ({@code <base>/<level>}, ADR-0014) resolved (with each crystal key) to a dense ability id via the
 * snapshot's {@link StableKeyIndex}; {@link WornFlattener} flattens the union into the per-trigger +
 * per-direction arrays the hit walks. An unknown key (content no longer present) resolves to
 * {@code -1} and is skipped — never a crash. Multiplicity is preserved (an enchant on two pieces
 * contributes twice).
 *
 * <p><strong>Hand attribution (G01).</strong> An off-hand item never swings in vanilla, so it contributes
 * no attacker-direction procs, no summed heroic flat stats, no set-completion count, and no held
 * set-weapon bonus — only its DEFENSE / NEUTRAL / HELD / PASSIVE effects (e.g. an off-hand shield's
 * defensive enchant) stay live. The split is decided here at resolve time and baked into the flattened
 * arrays, so the combat hot path is unchanged and pays nothing. A bow/trident fired FROM the off-hand
 * therefore loses its SE attack-side procs (the firing hand is unknowable under source erasure at
 * arrow-hit time) — main-hand the weapon to keep them.
 *
 * <p>Sets resolve here too: each piece's {@code setKey}/{@code omni} (§6.6) feeds {@link SetResolver},
 * whose active-set {@code BitSet} joins {@link WornState#activeSets()} and contributes each completed
 * set's bonus ability id (a set's dense id is its set id; its threshold is that ability's
 * {@code setPieces}). Trigger metadata is injected to keep {@code se-item} free of an {@code se-engine}
 * dependency; the caller passes the live published {@link Snapshot}, so resolution is reload-correct.
 *
 * <p><strong>Generation invariant.</strong> The caller must pass the {@link Snapshot} whose generation
 * matches the injected {@link ItemViewCache} — both advance together on reload. Resolution is correct
 * either way (keys are version-independent; an absent key misses), but {@link WornState#gen()} is
 * stamped from the snapshot, so a mismatched cache makes a stale-equip check read as fresh.
 */
public final class WornResolver {

    private final EquipSource equipSource;
    private final ItemViewCache itemViews;
    private final int triggerCount;
    private final IntPredicate attackTrigger;
    private final IntPredicate defenseTrigger;
    private final java.util.function.Supplier<Features> features; // §L per-feature master toggles (live)
    // §ADR-0035 the base keys of crystals declared NON-stackable, read live so a reload re-tunes it. A crystal in
    // this set contributes its abilities at most once per wearer, even if it sits on several worn pieces.
    private final java.util.function.Supplier<java.util.Set<String>> nonStackableCrystals;
    // ADR-0052: the hotbar-pet contribution, decided wholly by the pets feature (bracket + armed gate + toggle).
    private final PetSource petSource;

    /** Per-feature source toggles (config.yml {@code features:}) — which sources contribute to worn state. */
    public record Features(boolean enchants, boolean sets, boolean crystals, boolean heroic, boolean masks,
                           boolean reforges) {
        public static final Features ALL = new Features(true, true, true, true, true, true);
    }

    public WornResolver(EquipSource equipSource, ItemViewCache itemViews, int triggerCount,
                        IntPredicate attackTrigger, IntPredicate defenseTrigger) {
        this(equipSource, itemViews, triggerCount, attackTrigger, defenseTrigger, () -> Features.ALL);
    }

    public WornResolver(EquipSource equipSource, ItemViewCache itemViews, int triggerCount,
                        IntPredicate attackTrigger, IntPredicate defenseTrigger,
                        java.util.function.Supplier<Features> features) {
        this(equipSource, itemViews, triggerCount, attackTrigger, defenseTrigger, features, java.util.Set::of,
                PetSource.NONE);
    }

    public WornResolver(EquipSource equipSource, ItemViewCache itemViews, int triggerCount,
                        IntPredicate attackTrigger, IntPredicate defenseTrigger,
                        java.util.function.Supplier<Features> features,
                        java.util.function.Supplier<java.util.Set<String>> nonStackableCrystals) {
        this(equipSource, itemViews, triggerCount, attackTrigger, defenseTrigger, features, nonStackableCrystals,
                PetSource.NONE);
    }

    /**
     * Canonical form: {@code features} and {@code nonStackableCrystals} are read live per resolve, so a
     * {@code /se reload} re-tunes which sources contribute and which crystals dedup per wearer (§ADR-0035);
     * {@code petSource} supplies the hotbar-pet stable keys (ADR-0052 — the pets feature owns the decision).
     */
    public WornResolver(EquipSource equipSource, ItemViewCache itemViews, int triggerCount,
                        IntPredicate attackTrigger, IntPredicate defenseTrigger,
                        java.util.function.Supplier<Features> features,
                        java.util.function.Supplier<java.util.Set<String>> nonStackableCrystals,
                        PetSource petSource) {
        this.equipSource = java.util.Objects.requireNonNull(equipSource, "equipSource");
        this.itemViews = itemViews;
        this.triggerCount = triggerCount;
        this.attackTrigger = attackTrigger;
        this.defenseTrigger = defenseTrigger;
        this.features = java.util.Objects.requireNonNull(features, "features");
        this.nonStackableCrystals = java.util.Objects.requireNonNull(nonStackableCrystals, "nonStackableCrystals");
        this.petSource = java.util.Objects.requireNonNull(petSource, "petSource");
    }

    public WornState resolve(LivingEntity entity, Snapshot snapshot) {
        // The version-specific equipment read (1.9+ off-hand vs 1.8 main-hand-only) lives behind the
        // EquipSource overlay seam (§3.3); this core stays version-agnostic over the returned array.
        ItemStack[] gear = equipSource.snapshot(entity);
        if (gear == null) {
            return WornState.empty(snapshot.generation());
        }
        List<CombatState> combats = new ArrayList<>();
        // Index into `combats` where off-hand-sourced states begin (G01): everything at/after it came from the
        // off-hand slot, which never swings, so its attacker-direction procs are dropped downstream. Stays at the
        // full size on 1.8 (no off-hand slot) → no off-hand region → today's behaviour bit-for-bit.
        int offhandFrom = -1;
        for (int slot = 0; slot < gear.length; slot++) { // 0-3 armour, 4 main hand, 5 off-hand (EquipSource contract)
            if (slot == ARMOR_SLOTS + 1 && offhandFrom < 0) {
                offhandFrom = combats.size(); // the off-hand slot begins here, whether or not it holds anything
            }
            ItemStack piece = gear[slot];
            // A held armour piece is NOT equipped, so none of its bonuses (passive effects, combat enchants,
            // set membership) apply — only armour worn in its slot counts. Non-armour held items (weapons,
            // tools, shields, the set weapon) keep working while held.
            if (slot >= ARMOR_SLOTS && isArmorMaterial(piece)) {
                continue;
            }
            addCombat(piece, combats);
        }
        if (offhandFrom < 0) {
            offhandFrom = combats.size(); // no off-hand slot in the equipment array (1.8)
        }
        // ADR-0052: the hotbar-pet keys, decided wholly by the pets feature (bracket + armed gate + toggle).
        List<String> petKeys = petSource.liveKeys(entity);
        return resolveFrom(combats, offhandFrom, petKeys, snapshot.stableKeys(), snapshot.abilities(),
                snapshot.generation());
    }

    /** Equipment-array index where the hand slots begin; indices below this are the four armour slots. */
    private static final int ARMOR_SLOTS = 4;

    /** Whether {@code stack} is a wearable armour piece, by material NAME (cross-version) — a held one is ignored. */
    private static boolean isArmorMaterial(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        String name = stack.getType().name();
        return name.endsWith("_HELMET")     // leather/chain/iron/gold/diamond/netherite/turtle helmets
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || name.equals("ELYTRA");
    }

    private void addCombat(ItemStack stack, List<CombatState> out) {
        if (stack == null) {
            return;
        }
        ItemView view = itemViews.of(stack);
        if (!view.isEmpty()) {
            out.add(view.combat());
        }
    }

    /** Pure resolution + flatten over already-decoded combat states (version-agnostic core); no off-hand region. */
    WornState resolveFrom(List<CombatState> combats, StableKeyIndex keys, Ability[] abilities, int generation) {
        return resolveFrom(combats, combats.size(), List.of(), keys, abilities, generation);
    }

    /** The pre-ADR-0052 shape (tests): no pet keys. */
    WornState resolveFrom(List<CombatState> combats, int offhandFrom, StableKeyIndex keys, Ability[] abilities,
                          int generation) {
        return resolveFrom(combats, offhandFrom, List.of(), keys, abilities, generation);
    }

    /**
     * Pure resolution + flatten over already-decoded combat states (version-agnostic core). States at index
     * {@code >= offhandFrom} come from the off-hand slot (G01): they never contribute attacker-direction procs,
     * heroic flat stats, set membership, or a held set-weapon bonus — an off-hand item never swings.
     * {@code petKeys} are the ADR-0052 hotbar-pet stable keys: they fire on triggers like any main-hand source
     * (a pet's armed ATTACK rider must swing) but never join sets/heroic/crystal accounting.
     */
    WornState resolveFrom(List<CombatState> combats, int offhandFrom, List<String> petKeys, StableKeyIndex keys,
                          Ability[] abilities, int generation) {
        List<Integer> mergedIds = new ArrayList<>();   // armour + main-hand sourced ids
        List<Integer> offhandIds = new ArrayList<>();  // off-hand sourced ids (attack-direction dropped by flatten)
        List<Integer> crystalIds = new ArrayList<>();
        List<Integer> wornSetIds = new ArrayList<>();
        List<String> heldWeaponSetKeys = new ArrayList<>(); // sets whose WEAPON this entity holds (§6.6)
        int omniCount = 0;
        HeroicStat heroic = HeroicStat.NONE;
        Features f = features.get(); // §L master toggles: a disabled feature's source is skipped
        java.util.Set<String> nonStackable = nonStackableCrystals.get(); // §ADR-0035 crystals that dedup per wearer
        java.util.Set<String> seenNonStackable = new java.util.HashSet<>(); // non-stackable keys already contributed
        for (int i = 0; i < combats.size(); i++) {
            CombatState combat = combats.get(i);
            boolean offhand = i >= offhandFrom; // off-hand pieces are processed last (armour, then main, then off)
            List<Integer> firing = offhand ? offhandIds : mergedIds; // route the off-hand's firing ids apart
            if (f.heroic() && !offhand) {
                heroic = heroic.plus(combat.heroic()); // heroic flat stats sum across WORN + main-hand pieces (§6)
            }
            if (f.enchants()) {
                for (Map.Entry<String, Integer> enchant : combat.enchants().entrySet()) {
                    int id = keys.idOf(enchant.getKey() + "/" + enchant.getValue());
                    if (id >= 0) {
                        firing.add(id);
                    }
                }
            }
            if (f.crystals()) {
                for (String crystalEntry : combat.crystals()) {
                    // One slot may carry many component keys (a merged multi-crystal, "a+b+c", §E); each resolves
                    // and fires independently, the additive fold summing overlaps (ADR-0012).
                    for (String crystalKey : item.codec.CrystalItemData.componentsOf(crystalEntry)) {
                        int id = keys.idOf(crystalKey);
                        if (id < 0) {
                            continue;
                        }
                        // §ADR-0035: a NON-stackable crystal contributes once per wearer — skip a repeat on another
                        // piece/slot (and, via this continue, its /aN chain too). The seen-set spans both hands, so
                        // an armour copy (processed first) wins over an off-hand copy. Enchants and stackable
                        // crystals keep full multiplicity (an enchant on two pieces still fires twice).
                        if (nonStackable.contains(crystalKey) && !seenNonStackable.add(crystalKey)) {
                            continue;
                        }
                        firing.add(id);      // fires on triggers like any source (off-hand: attack dir dropped)...
                        crystalIds.add(id);  // ...and tracked as the dedicated crystal source (§5.5)
                        // A multi-ability crystal keys its further bonuses <key>/a1, /a2, … (dense, no gaps),
                        // exactly like a set's extra armour bonuses (ADR-0034). Walk them so every bonus fires.
                        for (int n = 1; ; n++) {
                            int extra = keys.idOf(crystalKey + "/a" + n);
                            if (extra < 0) {
                                break;
                            }
                            firing.add(extra);
                            crystalIds.add(extra);
                        }
                    }
                }
            }
            // ADR-0053: a mask applied onto this HELMET fires while worn like any source, but joins `firing`
            // ONLY — never crystalIds/set/heroic accounting (a mask is its own source kind). Helmets are armour,
            // never off-hand, so `!offhand` always holds here; the guard keeps that intent explicit.
            if (f.masks() && combat.maskKey() != null && !offhand) {
                int id = keys.idOf(combat.maskKey());
                if (id >= 0) {
                    firing.add(id);
                    // A multi-ability mask keys its further bonuses <key>/a1, /a2, … (dense, no gaps), exactly
                    // like a crystal/set (ADR-0034/0035). Walk them so every bonus fires.
                    for (int n = 1; ; n++) {
                        int extra = keys.idOf(combat.maskKey() + "/a" + n);
                        if (extra < 0) {
                            break;
                        }
                        firing.add(extra);
                    }
                }
            }
            // ADR-0070: a reforge applied onto this WEAPON fires while the weapon is HELD in the MAIN hand —
            // it rides this combat entry like a weapon enchant, but joins `firing` ONLY (never crystal/set/
            // heroic accounting; a reforge is its own source kind). `!offhand` is the held-gate: an off-hand
            // item never swings, and the reforge active is a main-hand gesture (the set-weapon `on:weapon`
            // rule). The /aN walk mirrors the mask's; USE-trigger ids landing in byTrigger[USE] are inert
            // (every USE path passes explicit candidates — the pets precedent).
            if (f.reforges() && combat.reforgeKey() != null && !offhand) {
                int id = keys.idOf(combat.reforgeKey());
                if (id >= 0) {
                    firing.add(id);
                    for (int n = 1; ; n++) {
                        int extra = keys.idOf(combat.reforgeKey() + "/a" + n);
                        if (extra < 0) {
                            break;
                        }
                        firing.add(extra);
                    }
                }
            }
            // §6.6: omni piece = wildcard toward any partially-worn set; ARMOUR piece contributes its
            // set id. A WEAPON member never counts toward completion — held separately, it grants the
            // set's additional weapon bonus only once the armour set is complete. An off-hand item is neither
            // worn armour nor the swinging weapon, so it contributes to neither (G01).
            if (f.sets() && !offhand) {
                if (combat.omni()) {
                    omniCount++;
                } else if (combat.setKey() != null) {
                    wornSetIds.add(keys.idOf(combat.setKey())); // -1 for unknown content → ignored by SetResolver
                }
                if (combat.setWeaponKey() != null) {
                    heldWeaponSetKeys.add(combat.setWeaponKey());
                }
            }
        }
        // A set's COMPLETION ability id is its set id; its threshold is that ability's setPieces.
        BitSet activeSets = SetResolver.activeSets(toIntArray(wornSetIds), omniCount,
                setId -> setId >= 0 && setId < abilities.length ? abilities[setId].setPieces() : 0);
        for (int setId = activeSets.nextSetBit(0); setId >= 0; setId = activeSets.nextSetBit(setId + 1)) {
            mergedIds.add(setId); // the set's primary on:armor bonus fires on triggers like any source
            // Further on:armor bonuses (<key>/a1, /a2, … — dense, no gaps) fire while the set is complete (§6.6).
            String setKey = keys.keyOf(setId);
            if (setKey != null) {
                for (int n = 1; ; n++) {
                    int extra = keys.idOf(setKey + "/a" + n);
                    if (extra < 0) {
                        break;
                    }
                    mergedIds.add(extra);
                }
            }
        }
        // on:weapon bonuses (<key>/w1, /w2, …), gated on BOTH the set being active AND its weapon held.
        for (String weaponSetKey : heldWeaponSetKeys) {
            int parentSetId = keys.idOf(weaponSetKey);
            if (parentSetId >= 0 && parentSetId < abilities.length && activeSets.get(parentSetId)) {
                for (int n = 1; ; n++) {
                    int weaponAbilityId = keys.idOf(weaponSetKey + "/w" + n);
                    if (weaponAbilityId < 0) {
                        break;
                    }
                    mergedIds.add(weaponAbilityId);
                }
            }
        }
        // ADR-0052 pets: resolve the feature-decided stable keys like any main-hand source. An unknown key
        // (content no longer present, a stale bracket) resolves -1 and is skipped — never a crash.
        for (String petKey : petKeys) {
            int petAbilityId = keys.idOf(petKey);
            if (petAbilityId >= 0) {
                mergedIds.add(petAbilityId);
            }
        }
        return WornFlattener.flatten(generation, toIntArray(mergedIds), toIntArray(offhandIds), abilities,
                triggerCount, activeSets, toIntArray(crystalIds), heroic, attackTrigger, defenseTrigger);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
