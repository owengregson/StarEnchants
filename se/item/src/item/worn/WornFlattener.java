package item.worn;

import compile.model.Ability;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Pre-flattens a player's already-resolved active abilities into the immutable
 * {@link WornState} arrays (docs/architecture.md §5.5) — the "killer move" that makes
 * source unification cost nothing per hit. The server-bound resolver collects the
 * active ability ids (enchants + active-set bonuses + crystals + heroic + weapon) from
 * the worn items; this pure step organises them into the per-trigger index and the two
 * combat-direction arrays the systems walk.
 *
 * <p>Multiplicity is preserved: a stacked crystal contributes its ability id more than
 * once, so it runs (and contributes to the fold) once per stack — the list semantics of
 * §6.5. Ordering follows {@code activeAbilityIds}, the merge order the resolver chose.
 */
public final class WornFlattener {

    private WornFlattener() {
    }

    /**
     * @param gen              the snapshot generation
     * @param activeAbilityIds the complete merged list of active ability ids, in run order
     * @param abilities        the snapshot's ability array (indexed by id, for trigger masks)
     * @param triggerCount     the number of interned triggers (sizes the per-trigger index)
     * @param activeSets       the resolved active armor sets (see {@link SetResolver})
     * @param crystalAbilityIds the crystal ability ids, kept as a dedicated list view
     * @param heroic           the summed heroic stats
     * @param attackTrigger    whether an interned trigger id is an attacker-side combat trigger
     * @param defenseTrigger   whether an interned trigger id is a defender-side combat trigger
     */
    public static WornState flatten(int gen, int[] activeAbilityIds, Ability[] abilities,
                                    int triggerCount, BitSet activeSets, int[] crystalAbilityIds,
                                    HeroicStat heroic, IntPredicate attackTrigger,
                                    IntPredicate defenseTrigger) {
        List<List<Integer>> perTrigger = new ArrayList<>(triggerCount);
        for (int t = 0; t < triggerCount; t++) {
            perTrigger.add(new ArrayList<>());
        }
        List<Integer> attack = new ArrayList<>();
        List<Integer> defense = new ArrayList<>();

        for (int id : activeAbilityIds) {
            Ability ability = abilities[id];
            boolean isAttack = false;
            boolean isDefense = false;
            for (int t = 0; t < triggerCount; t++) {
                if (ability.firesOn(t)) {
                    perTrigger.get(t).add(id);
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

        int[][] byTrigger = new int[triggerCount][];
        for (int t = 0; t < triggerCount; t++) {
            byTrigger[t] = toIntArray(perTrigger.get(t));
        }
        return new WornState(gen, activeSets, crystalAbilityIds.clone(), heroic,
                byTrigger, toIntArray(attack), toIntArray(defense));
    }

    private static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }
}
