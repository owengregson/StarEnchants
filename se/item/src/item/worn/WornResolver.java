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
 * {@code -1} and is skipped — never a crash.
 *
 * <p><strong>Stacking (R-QC63).</strong> An enchant on two pieces is ONE contribution by default: the highest
 * worn level fires, once, so an event rolls its chance once, arms one cooldown and plays one cue. An enchant
 * authored {@code stacks: true} keeps full multiplicity and fires once per piece with that piece's own level —
 * the per-piece folds (Tank, Valor, Armored, Heavy …) are exactly this set. The decision is made HERE, at
 * equip, so the hit path pays nothing either way. Ties break on source order (armour, then main hand, then
 * off-hand), which means a wearer whose highest copy sits in the OFF-HAND keeps that copy and loses its
 * attacker-direction procs to G01 — level beats slot, one rule.
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
        // %victim.heroicpieces% counts WORN ARMOUR only, so it is tallied HERE — `combats` keeps no slot
        // provenance, and a heroic sword in hand must not read as a worn armour piece.
        int heroicPieces = 0;
        // %scope.crystals.<key>% counts WORN ARMOUR only, for the same reason: `combats` keeps no slot
        // provenance, and a socketed sword in hand must not read as a worn armour piece.
        Map<String, Integer> crystalCounts = new java.util.HashMap<>();
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
            int before = combats.size();
            addCombat(piece, combats);
            if (slot < ARMOR_SLOTS && combats.size() > before) {
                CombatState armour = combats.get(before);
                if (!armour.heroic().isZero()) {
                    heroicPieces++;
                }
                // One piece counts ONCE per crystal however many times it names it: the fact answers
                // "how many pieces are socketed with X", which is the per-piece scaling authors reach for.
                java.util.Set<String> onThisPiece = new java.util.HashSet<>();
                for (String entry : armour.crystals()) {
                    for (String crystalKey : item.codec.CrystalItemData.componentsOf(entry)) {
                        if (onThisPiece.add(stemOf(crystalKey))) {
                            crystalCounts.merge(stemOf(crystalKey), 1, Integer::sum);
                        }
                    }
                }
            }
        }
        if (offhandFrom < 0) {
            offhandFrom = combats.size(); // no off-hand slot in the equipment array (1.8)
        }
        // ADR-0052: the hotbar-pet keys, decided wholly by the pets feature (bracket + armed gate + toggle).
        List<String> petKeys = petSource.liveKeys(entity);
        return resolveFrom(combats, offhandFrom, petKeys, snapshot.stableKeys(), snapshot.abilities(),
                snapshot.generation(), heroicPieces, crystalCounts);
    }

    /**
     * R-QC63: base key &rarr; the single level each NON-stacking enchant contributes at — the HIGHEST level worn.
     * An enchant authored {@code stacks: true} is absent from the map and keeps full multiplicity, which is what
     * lets Tank/Valor/Armored still fold per piece (D-02-10, D-02-14).
     *
     * <p>A level whose content is gone ({@code id < 0}) can never win, so pulling the top rung of an enchant out
     * of a pack falls back to the highest rung that still compiles instead of silently dropping the enchant off
     * every item that carried it.
     *
     * <p>Resolved once per equip, never per hit — the caller then drops the losing copies and the hot path just
     * walks a shorter array.
     */
    private static Map<String, Integer> soleContributingLevels(List<CombatState> combats, StableKeyIndex keys,
                                                               Ability[] abilities) {
        Map<String, Integer> out = new java.util.HashMap<>();
        for (CombatState combat : combats) {
            for (Map.Entry<String, Integer> enchant : combat.enchants().entrySet()) {
                int id = keys.idOf(enchant.getKey() + "/" + enchant.getValue());
                if (id < 0 || abilities[id].stacks()) {
                    continue;
                }
                out.merge(enchant.getKey(), enchant.getValue(), Math::max);
            }
        }
        return out;
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
        return resolveFrom(combats, offhandFrom, petKeys, keys, abilities, generation, 0);
    }

    /** As above, with the caller's count of WORN ARMOUR pieces carrying a heroic upgrade (slot provenance). */
    WornState resolveFrom(List<CombatState> combats, int offhandFrom, List<String> petKeys, StableKeyIndex keys,
                          Ability[] abilities, int generation, int heroicPieces) {
        return resolveFrom(combats, offhandFrom, petKeys, keys, abilities, generation, heroicPieces, Map.of());
    }

    /** As above, with the caller's per-armour-piece crystal tally (slot provenance, same as heroicPieces). */
    WornState resolveFrom(List<CombatState> combats, int offhandFrom, List<String> petKeys, StableKeyIndex keys,
                          Ability[] abilities, int generation, int heroicPieces,
                          Map<String, Integer> crystalCounts) {
        List<Integer> mergedIds = new ArrayList<>();   // armour + main-hand sourced ids
        List<Integer> offhandIds = new ArrayList<>();  // off-hand sourced ids (attack-direction dropped by flatten)
        List<Integer> crystalIds = new ArrayList<>();
        List<Integer> wornSetIds = new ArrayList<>();
        List<String> heldWeaponSetKeys = new ArrayList<>(); // sets whose WEAPON this entity holds (§6.6)
        // %scope.enchlevel.<key>%: flattened HERE, once per equip, so the hit-path read is a lookup and never
        // a gear scan. Keyed by the lower-cased stem — the canonical form ConditionCompiler lowers a key to.
        Map<String, Integer> enchantLevels = new java.util.HashMap<>();
        int omniCount = 0;
        HeroicStat heroic = HeroicStat.NONE;
        Features f = features.get(); // §L master toggles: a disabled feature's source is skipped
        java.util.Set<String> nonStackable = nonStackableCrystals.get(); // §ADR-0035 crystals that dedup per wearer
        java.util.Set<String> seenNonStackable = new java.util.HashSet<>(); // non-stackable keys already contributed
        // R-QC63: base key → the ONE level a non-stacking enchant contributes at, and the claim set that keeps
        // it to one source. Both empty for a single-source wearer, who has nothing to dedup.
        Map<String, Integer> soleLevel = f.enchants() && combats.size() > 1
                ? soleContributingLevels(combats, keys, abilities) : Map.of();
        java.util.Set<String> soleClaimed = soleLevel.isEmpty() ? java.util.Set.of() : new java.util.HashSet<>();
        for (int i = 0; i < combats.size(); i++) {
            CombatState combat = combats.get(i);
            boolean offhand = i >= offhandFrom; // off-hand pieces are processed last (armour, then main, then off)
            List<Integer> firing = offhand ? offhandIds : mergedIds; // route the off-hand's firing ids apart
            if (f.heroic() && !offhand) {
                heroic = heroic.plus(combat.heroic()); // heroic flat stats sum across WORN + main-hand pieces (§6)
            }
            if (f.enchants()) {
                for (Map.Entry<String, Integer> enchant : combat.enchants().entrySet()) {
                    // The item stores base key + level; the ability key is <base>/<level>, which is also the
                    // FIRST block of a multi-ability level — so an item written before multi-ability existed
                    // resolves byte-identically.
                    String levelKey = enchant.getKey() + "/" + enchant.getValue();
                    int id = keys.idOf(levelKey);
                    if (id >= 0) {
                        // Highest level wins when the same enchant sits on two pieces at different levels.
                        enchantLevels.merge(stemOf(enchant.getKey()), enchant.getValue(), Math::max);
                        // R-QC63: a non-stacking enchant contributes ONCE per wearer — the highest worn level,
                        // and the first source carrying it. Dropping the copy here (rather than folding it
                        // downstream) is what makes it one activation: one chance roll, one cooldown arm, one
                        // cue, however many pieces spell the enchant.
                        Integer sole = soleLevel.get(enchant.getKey());
                        if (sole != null
                                && (enchant.getValue().intValue() != sole || !soleClaimed.add(enchant.getKey()))) {
                            continue;
                        }
                        firing.add(id);
                        // A multi-ability level keys its further blocks <base>/<level>/a1, /a2, … (dense, no
                        // gaps), exactly like a crystal/mask/reforge/set. Walk them so every block fires;
                        // without this they would compile, take dense ids, and never activate.
                        for (int n = 1; ; n++) {
                            int extra = keys.idOf(levelKey + "/a" + n);
                            if (extra < 0) {
                                break;
                            }
                            firing.add(extra);
                        }
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
                // ADR-0074: one helmet may carry a COMPOSITE — several child masks folded into one entry
                // ("a+b", the multi-crystal packing). Every child resolves and fires as if IT were the worn
                // mask, which is the whole contract; the additive fold sums any overlap (ADR-0012).
                java.util.Set<String> seenChildren = new java.util.HashSet<>();
                for (String maskKey : item.codec.MaskItemData.componentsOf(combat.maskKey())) {
                    int id = keys.idOf(maskKey);
                    if (id < 0) {
                        continue; // a child whose content went away on reload — the siblings still fire
                    }
                    // A repeated child is ONE identity, not two sources: firing it twice would run the same
                    // ability twice per trigger — two chance rolls, two cues. The fold gesture already refuses
                    // duplicates; this is the belt for an entry that arrived some other way (an admin-set blob,
                    // an item from before the refusal), and it is why the mask branch dedupes where the enchant
                    // one deliberately does not — an enchant on two PIECES is genuinely two sources.
                    if (!seenChildren.add(maskKey)) {
                        continue;
                    }
                    firing.add(id);
                    // A multi-ability mask keys its further bonuses <key>/a1, /a2, … (dense, no gaps), exactly
                    // like a crystal/set (ADR-0034/0035). Walk them so every bonus fires.
                    for (int n = 1; ; n++) {
                        int extra = keys.idOf(maskKey + "/a" + n);
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
        // The same condition is the %actor.setweapon% fact: an author reading it and an author writing
        // `on: weapon` are asking the same question, so they are answered from one place.
        boolean holdsSetWeapon = false;
        for (String weaponSetKey : heldWeaponSetKeys) {
            int parentSetId = keys.idOf(weaponSetKey);
            if (parentSetId >= 0 && parentSetId < abilities.length && activeSets.get(parentSetId)) {
                holdsSetWeapon = true;
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
                triggerCount, activeSets, toIntArray(crystalIds), heroic, attackTrigger, defenseTrigger,
                enchantLevels, f.heroic() ? heroicPieces : 0, // one toggle gates the stat and the count alike
                f.sets() && holdsSetWeapon, // and the sets toggle gates the fact with the bonuses it mirrors
                f.crystals() ? crystalCounts : Map.of()); // ...and crystals gates its own count
    }

    /** The {@code <stem>} of a {@code <source>/<stem>} base key, lower-cased — the enchlevel lookup's key. */
    private static String stemOf(String baseKey) {
        int slash = baseKey.lastIndexOf('/');
        return (slash < 0 ? baseKey : baseKey.substring(slash + 1)).toLowerCase(java.util.Locale.ROOT);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
