package item.worn;

import item.codec.HeroicStat;
import java.util.BitSet;

/**
 * A player's resolved equipment state — immutable, multi-set, pre-flattened (§5.5). Resolved once per
 * equipment change, never per hit: source unification happens at equip time, so the hit walks one
 * pre-merged ordered array per direction and pays nothing for set/omni/crystal resolution.
 *
 * <p>Immutable, so it is the safe cross-region victim read on Folia: an attacker's thread reads the
 * victim's {@link #combatDefense} with no lock and no wrong-thread access (§3.6).
 *
 * @param gen                     the snapshot generation it was built against (§5.2)
 * @param activeSets              the SET of active armor sets — never a single id (§5.5 #1)
 * @param activeCrystalAbilityIds crystal ability ids merged from worn pieces (a list source)
 * @param heroic                  heroic flat stats as a source
 * @param byTrigger               per-trigger dense ability ids from ALL sources, ordered
 * @param combatAttack            attacker-direction ability ids, pre-merged
 * @param combatDefense           defender-direction ability ids, pre-merged
 */
public record WornState(
        int gen,
        BitSet activeSets,
        int[] activeCrystalAbilityIds,
        HeroicStat heroic,
        int[][] byTrigger,
        int[] combatAttack,
        int[] combatDefense) {

    private static final int[] NO_IDS = new int[0];

    /** Worn state for a player with nothing relevant equipped. */
    public static WornState empty(int gen) {
        return new WornState(gen, new BitSet(), NO_IDS, HeroicStat.NONE, new int[0][], NO_IDS, NO_IDS);
    }

    /** Ability ids firing on the interned {@code triggerId}; empty array if none (never {@code null}). */
    public int[] byTrigger(int triggerId) {
        return triggerId >= 0 && triggerId < byTrigger.length ? byTrigger[triggerId] : NO_IDS;
    }

    public boolean isSetActive(int setId) {
        return setId >= 0 && activeSets.get(setId);
    }
}
