package item.worn;

import compile.model.FactMask;
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
 * @param heldEnchantByTrigger    per-trigger ability ids sourced specifically from the main-hand item's enchants;
 *                                used by mechanics such as Cosmic Enchant Reflect that inspect held lore only
 * @param triggerFactMask         per-trigger union of the referenced {@code FactBuffer} slots (ADR-0039),
 *                                so the populator computes only the facts this wearer's trigger abilities
 *                                read; {@code null} means "populate everything" (hand-built test states)
 */
public record WornState(
        int gen,
        BitSet activeSets,
        int[] activeCrystalAbilityIds,
        HeroicStat heroic,
        int[][] byTrigger,
        int[] combatAttack,
        int[] combatDefense,
        int[][] heldEnchantByTrigger,
        FactMask[] triggerFactMask) {

    private static final int[] NO_IDS = new int[0];

    /** No per-trigger masks — {@link #factMask} then reports {@link FactMask#ALL} (populate everything). */
    public WornState(int gen, BitSet activeSets, int[] activeCrystalAbilityIds, HeroicStat heroic,
                     int[][] byTrigger, int[] combatAttack, int[] combatDefense) {
        this(gen, activeSets, activeCrystalAbilityIds, heroic, byTrigger, combatAttack, combatDefense,
                new int[0][], null);
    }

    /** Compatibility constructor for callers that supplied the pre-provenance fact-mask shape. */
    public WornState(int gen, BitSet activeSets, int[] activeCrystalAbilityIds, HeroicStat heroic,
                     int[][] byTrigger, int[] combatAttack, int[] combatDefense, FactMask[] triggerFactMask) {
        this(gen, activeSets, activeCrystalAbilityIds, heroic, byTrigger, combatAttack, combatDefense,
                new int[0][], triggerFactMask);
    }

    public static WornState empty(int gen) {
        return new WornState(gen, new BitSet(), NO_IDS, HeroicStat.NONE, new int[0][], NO_IDS, NO_IDS,
                new int[0][], null);
    }

    /** Ability ids firing on the interned {@code triggerId}; empty array if none (never {@code null}). */
    public int[] byTrigger(int triggerId) {
        return triggerId >= 0 && triggerId < byTrigger.length ? byTrigger[triggerId] : NO_IDS;
    }

    /** Main-hand ENCHANT ability ids firing on this trigger; excludes armor, crystals, sets, pets and off-hand. */
    public int[] heldEnchantByTrigger(int triggerId) {
        return triggerId >= 0 && triggerId < heldEnchantByTrigger.length
                ? heldEnchantByTrigger[triggerId] : NO_IDS;
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
