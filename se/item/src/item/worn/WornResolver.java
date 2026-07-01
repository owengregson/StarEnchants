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

    private final ItemViewCache itemViews;
    private final int triggerCount;
    private final IntPredicate attackTrigger;
    private final IntPredicate defenseTrigger;
    private final java.util.function.Supplier<Features> features; // §L per-feature master toggles (live)

    /** Per-feature source toggles (config.yml {@code features:}) — which sources contribute to worn state. */
    public record Features(boolean enchants, boolean sets, boolean crystals, boolean heroic) {
        public static final Features ALL = new Features(true, true, true, true);
    }

    public WornResolver(ItemViewCache itemViews, int triggerCount,
                        IntPredicate attackTrigger, IntPredicate defenseTrigger) {
        this(itemViews, triggerCount, attackTrigger, defenseTrigger, () -> Features.ALL);
    }

    /** Canonical form: {@code features} is read live per resolve, so a {@code /se reload} re-tunes which sources contribute. */
    public WornResolver(ItemViewCache itemViews, int triggerCount,
                        IntPredicate attackTrigger, IntPredicate defenseTrigger,
                        java.util.function.Supplier<Features> features) {
        this.itemViews = itemViews;
        this.triggerCount = triggerCount;
        this.attackTrigger = attackTrigger;
        this.defenseTrigger = defenseTrigger;
        this.features = java.util.Objects.requireNonNull(features, "features");
    }

    public WornState resolve(LivingEntity entity, Snapshot snapshot) {
        // The version-specific equipment read (1.9+ off-hand vs 1.8 main-hand-only) lives behind the
        // EquipSource overlay seam (§3.3); this core stays version-agnostic over the returned array.
        ItemStack[] gear = EquipSource.snapshot(entity);
        if (gear == null) {
            return WornState.empty(snapshot.generation());
        }
        List<CombatState> combats = new ArrayList<>();
        for (int slot = 0; slot < gear.length; slot++) { // 0-3 armour, 4+ hands (EquipSource contract)
            ItemStack piece = gear[slot];
            // A held armour piece is NOT equipped, so none of its bonuses (passive effects, combat enchants,
            // set membership) apply — only armour worn in its slot counts. Non-armour held items (weapons,
            // tools, shields, the set weapon) keep working while held.
            if (slot >= ARMOR_SLOTS && isArmorMaterial(piece)) {
                continue;
            }
            addCombat(piece, combats);
        }
        return resolveFrom(combats, snapshot.stableKeys(), snapshot.abilities(), snapshot.generation());
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

    /** Pure resolution + flatten over already-decoded combat states (version-agnostic core). */
    WornState resolveFrom(List<CombatState> combats, StableKeyIndex keys, Ability[] abilities, int generation) {
        List<Integer> mergedIds = new ArrayList<>();
        List<Integer> crystalIds = new ArrayList<>();
        List<Integer> wornSetIds = new ArrayList<>();
        List<String> heldWeaponSetKeys = new ArrayList<>(); // sets whose WEAPON this entity holds (§6.6)
        int omniCount = 0;
        HeroicStat heroic = HeroicStat.NONE;
        Features f = features.get(); // §L master toggles: a disabled feature's source is skipped
        for (CombatState combat : combats) {
            if (f.heroic()) {
                heroic = heroic.plus(combat.heroic()); // heroic flat stats sum across worn pieces (§6)
            }
            if (f.enchants()) {
                for (Map.Entry<String, Integer> enchant : combat.enchants().entrySet()) {
                    int id = keys.idOf(enchant.getKey() + "/" + enchant.getValue());
                    if (id >= 0) {
                        mergedIds.add(id);
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
                        mergedIds.add(id);   // fires on triggers like any source...
                        crystalIds.add(id);  // ...and tracked as the dedicated crystal source (§5.5)
                        // A multi-ability crystal keys its further bonuses <key>/a1, /a2, … (dense, no gaps),
                        // exactly like a set's extra armour bonuses (ADR-0034). Walk them so every bonus fires.
                        for (int n = 1; ; n++) {
                            int extra = keys.idOf(crystalKey + "/a" + n);
                            if (extra < 0) {
                                break;
                            }
                            mergedIds.add(extra);
                            crystalIds.add(extra);
                        }
                    }
                }
            }
            // §6.6: omni piece = wildcard toward any partially-worn set; ARMOUR piece contributes its
            // set id. A WEAPON member never counts toward completion — held separately, it grants the
            // set's additional weapon bonus only once the armour set is complete.
            if (f.sets()) {
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
        return WornFlattener.flatten(generation, toIntArray(mergedIds), abilities, triggerCount,
                activeSets, toIntArray(crystalIds), heroic, attackTrigger, defenseTrigger);
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = values.get(i);
        }
        return out;
    }
}
