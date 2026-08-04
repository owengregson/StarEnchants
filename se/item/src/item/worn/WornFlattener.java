package item.worn;

import compile.model.Ability;
import compile.model.FactMask;
import item.codec.HeroicStat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Pre-flattens already-resolved active ability ids into the immutable {@link WornState} arrays (§5.5)
 * so source unification costs nothing per hit: this pure step organises the merged ids into the
 * per-trigger index and the two combat-direction arrays the systems walk.
 *
 * <p>Multiplicity is preserved — a stacked crystal contributes its id once per stack, running (and
 * folding) once per stack (§6.5). Order follows the resolver's merge order (main-hand/armour ids first,
 * off-hand ids appended).
 *
 * <p><strong>Off-hand attribution (G01).</strong> An off-hand item never swings in vanilla, so its
 * attacker-direction procs must not ride a main-hand hit. The off-hand ids are flattened separately with
 * every attacker-direction trigger EXCLUDED — they never join the {@code attack} array nor any
 * attack-trigger index — while their DEFENSE / NEUTRAL / HELD triggers keep working (an off-hand shield's
 * defensive/passive enchants stay live). This is a resolve-time decision, so the hit path pays nothing.
 */
public final class WornFlattener {

    private static final int[] NO_IDS = new int[0];

    private WornFlattener() {
    }

    /** Convenience overload with no off-hand ids (all ids treated as main-hand/armour sourced). */
    public static WornState flatten(int gen, int[] activeAbilityIds, Ability[] abilities,
                                    int triggerCount, BitSet activeSets, int[] crystalAbilityIds,
                                    HeroicStat heroic, IntPredicate attackTrigger,
                                    IntPredicate defenseTrigger) {
        return flatten(gen, activeAbilityIds, NO_IDS, abilities, triggerCount, activeSets,
                crystalAbilityIds, heroic, attackTrigger, defenseTrigger);
    }

    /**
     * @param gen              the snapshot generation
     * @param mainIds          the merged active ability ids from armour + main-hand, in run order
     * @param offhandIds       the active ability ids from the off-hand item, in run order — appended with
     *                         attacker-direction triggers excluded (an off-hand item never swings, G01)
     * @param abilities        the snapshot's ability array (indexed by id, for trigger masks)
     * @param triggerCount     the number of interned triggers (sizes the per-trigger index)
     * @param activeSets       the resolved active armor sets (see {@link SetResolver})
     * @param crystalAbilityIds the crystal ability ids, kept as a dedicated list view
     * @param heroic           the summed heroic stats
     * @param attackTrigger    whether an interned trigger id is an attacker-side combat trigger
     * @param defenseTrigger   whether an interned trigger id is a defender-side combat trigger
     */
    public static WornState flatten(int gen, int[] mainIds, int[] offhandIds, Ability[] abilities,
                                    int triggerCount, BitSet activeSets, int[] crystalAbilityIds,
                                    HeroicStat heroic, IntPredicate attackTrigger,
                                    IntPredicate defenseTrigger) {
        return flatten(gen, mainIds, offhandIds, abilities, triggerCount, activeSets, crystalAbilityIds,
                heroic, attackTrigger, defenseTrigger, Map.of());
    }

    /** As above, carrying the resolver's flattened {@code enchantLevels} (stem &rarr; highest level worn). */
    public static WornState flatten(int gen, int[] mainIds, int[] offhandIds, Ability[] abilities,
                                    int triggerCount, BitSet activeSets, int[] crystalAbilityIds,
                                    HeroicStat heroic, IntPredicate attackTrigger,
                                    IntPredicate defenseTrigger, Map<String, Integer> enchantLevels) {
        return flatten(gen, mainIds, offhandIds, abilities, triggerCount, activeSets, crystalAbilityIds,
                heroic, attackTrigger, defenseTrigger, enchantLevels, 0);
    }

    /** As above, carrying the resolver's count of worn armour pieces that hold a heroic upgrade. */
    public static WornState flatten(int gen, int[] mainIds, int[] offhandIds, Ability[] abilities,
                                    int triggerCount, BitSet activeSets, int[] crystalAbilityIds,
                                    HeroicStat heroic, IntPredicate attackTrigger,
                                    IntPredicate defenseTrigger, Map<String, Integer> enchantLevels,
                                    int heroicPieces) {
        return flatten(gen, mainIds, offhandIds, abilities, triggerCount, activeSets, crystalAbilityIds,
                heroic, attackTrigger, defenseTrigger, enchantLevels, heroicPieces, false);
    }

    /** As above, carrying whether the main hand holds the weapon of a set this wearer has completed. */
    public static WornState flatten(int gen, int[] mainIds, int[] offhandIds, Ability[] abilities,
                                    int triggerCount, BitSet activeSets, int[] crystalAbilityIds,
                                    HeroicStat heroic, IntPredicate attackTrigger,
                                    IntPredicate defenseTrigger, Map<String, Integer> enchantLevels,
                                    int heroicPieces, boolean holdsSetWeapon) {
        List<List<Integer>> perTrigger = new ArrayList<>(triggerCount);
        FactMask[] triggerMask = new FactMask[triggerCount];
        for (int t = 0; t < triggerCount; t++) {
            perTrigger.add(new ArrayList<>());
            triggerMask[t] = FactMask.NONE;
        }
        List<Integer> attack = new ArrayList<>();
        List<Integer> defense = new ArrayList<>();

        for (int id : mainIds) {
            Ability ability = abilities[id];
            boolean isAttack = false;
            boolean isDefense = false;
            for (int t = 0; t < triggerCount; t++) {
                if (ability.firesOn(t)) {
                    perTrigger.get(t).add(id);
                    // Union the ability's referenced facts into the trigger's mask (ADR-0039): the populator
                    // then computes only what SOME candidate on this trigger actually reads.
                    triggerMask[t] = triggerMask[t].union(ability.factMask());
                    if (attackTrigger.test(t)) {
                        isAttack = true;
                    }
                    if (defenseTrigger.test(t)) {
                        isDefense = true;
                    }
                }
            }
            if (isAttack) {
                attack.add(id);
            }
            if (isDefense) {
                defense.add(id);
            }
        }

        // Off-hand ids: skip every attacker-direction trigger (a stowed off-hand item never swings, G01);
        // defensive/neutral/held triggers still index and still land in the defense array.
        for (int id : offhandIds) {
            Ability ability = abilities[id];
            boolean isDefense = false;
            for (int t = 0; t < triggerCount; t++) {
                if (!ability.firesOn(t) || attackTrigger.test(t)) {
                    continue;
                }
                perTrigger.get(t).add(id);
                triggerMask[t] = triggerMask[t].union(ability.factMask());
                if (defenseTrigger.test(t)) {
                    isDefense = true;
                }
            }
            if (isDefense) {
                defense.add(id);
            }
        }

        int[][] byTrigger = new int[triggerCount][];
        for (int t = 0; t < triggerCount; t++) {
            byTrigger[t] = toIntArray(perTrigger.get(t));
        }
        return new WornState(gen, activeSets, crystalAbilityIds.clone(), heroic, byTrigger,
                toIntArray(attack), toIntArray(defense), triggerMask, Map.copyOf(enchantLevels), heroicPieces,
                holdsSetWeapon);
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
