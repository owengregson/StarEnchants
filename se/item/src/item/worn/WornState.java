package item.worn;

import compile.model.FactMask;
import item.codec.HeroicStat;
import java.util.BitSet;
import java.util.Map;

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
 * @param triggerFactMask         per-trigger union of the referenced {@code FactBuffer} slots (ADR-0039),
 *                                so the populator computes only the facts this wearer's trigger abilities
 *                                read; {@code null} means "populate everything" (hand-built test states)
 * @param enchantLevels           lower-cased enchant stem &rarr; highest level worn, flattened once here so
 *                                {@code %scope.enchlevel.<key>%} is a lookup and never a gear scan
 * @param heroicPieces            how many WORN ARMOUR pieces carry a heroic upgrade (0..4), counted once here
 *                                for {@code %victim.heroicpieces%}. A count, not the {@link #heroic} sum: two
 *                                pieces at 10 % and one at 20 % are the same stat and a different gate
 */
public record WornState(
        int gen,
        BitSet activeSets,
        int[] activeCrystalAbilityIds,
        HeroicStat heroic,
        int[][] byTrigger,
        int[] combatAttack,
        int[] combatDefense,
        FactMask[] triggerFactMask,
        Map<String, Integer> enchantLevels,
        int heroicPieces) {

    private static final int[] NO_IDS = new int[0];

    /** No heroic piece count — {@link #heroicPieces} then reports 0. */
    public WornState(int gen, BitSet activeSets, int[] activeCrystalAbilityIds, HeroicStat heroic,
                     int[][] byTrigger, int[] combatAttack, int[] combatDefense, FactMask[] triggerFactMask,
                     Map<String, Integer> enchantLevels) {
        this(gen, activeSets, activeCrystalAbilityIds, heroic, byTrigger, combatAttack, combatDefense,
                triggerFactMask, enchantLevels, 0);
    }

    /** No flattened enchant levels — {@link #enchantLevel} then reports 0 for every key. */
    public WornState(int gen, BitSet activeSets, int[] activeCrystalAbilityIds, HeroicStat heroic,
                     int[][] byTrigger, int[] combatAttack, int[] combatDefense, FactMask[] triggerFactMask) {
        this(gen, activeSets, activeCrystalAbilityIds, heroic, byTrigger, combatAttack, combatDefense,
                triggerFactMask, Map.of());
    }

    /** No per-trigger masks — {@link #factMask} then reports {@link FactMask#ALL} (populate everything). */
    public WornState(int gen, BitSet activeSets, int[] activeCrystalAbilityIds, HeroicStat heroic,
                     int[][] byTrigger, int[] combatAttack, int[] combatDefense) {
        this(gen, activeSets, activeCrystalAbilityIds, heroic, byTrigger, combatAttack, combatDefense, null);
    }

    public static WornState empty(int gen) {
        return new WornState(gen, new BitSet(), NO_IDS, HeroicStat.NONE, new int[0][], NO_IDS, NO_IDS);
    }

    /** The highest level of the enchant {@code key} (lower-cased stem) worn; {@code 0} when absent. */
    public int enchantLevel(String key) {
        Integer level = enchantLevels == null ? null : enchantLevels.get(key);
        return level == null ? 0 : level;
    }

    /** Ability ids firing on the interned {@code triggerId}; empty array if none (never {@code null}). */
    public int[] byTrigger(int triggerId) {
        return triggerId >= 0 && triggerId < byTrigger.length ? byTrigger[triggerId] : NO_IDS;
    }

    /**
     * The union of {@code FactBuffer} slots the {@code triggerId} abilities read (ADR-0039), or
     * {@link FactMask#ALL} when unknown — a safe superset, so a referenced fact is always populated.
     */
    public FactMask factMask(int triggerId) {
        if (triggerFactMask == null || triggerId < 0 || triggerId >= triggerFactMask.length) {
            return FactMask.ALL;
        }
        return triggerFactMask[triggerId];
    }

    public boolean isSetActive(int setId) {
        return setId >= 0 && activeSets.get(setId);
    }
}
