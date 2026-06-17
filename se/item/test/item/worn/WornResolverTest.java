package item.worn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import compile.model.StableKeyIndex;
import item.codec.CombatState;
import item.codec.HeroicStat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the worn-state resolution core (ADR-0014, §5.5): on-item enchant {@code (baseKey,
 * level)} composes the path-derived per-level stable key, crystals resolve directly, unknown keys are
 * skipped, and multiplicity is preserved across pieces. Exercises the package-private {@code resolveFrom}
 * with hand-built keys/abilities — no server. The full equipment-reading path is verified live.
 */
class WornResolverTest {

    private static final IntPredicate ATTACK = t -> t == 0;
    private static final IntPredicate DEFENSE = t -> t == 1;
    private static final int TRIGGERS = 2;

    // id 0 = enchants/lifesteal/3, id 1 = crystals/zap — both fire on attack trigger 0.
    private static final StableKeyIndex KEYS =
            new StableKeyIndex(List.of("enchants/lifesteal/3", "crystals/zap"));
    private static final Ability[] ABILITIES = {ability(0, 1 << 0), ability(1, 1 << 0)};

    private static Ability ability(int id, int triggerMask) {
        return new Ability(id, 0, SourceKind.ENCHANT, triggerMask, 1, 100.0, 0, 0, 0L, null,
                new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 0);
    }

    private static WornResolver resolver() {
        return new WornResolver(null, TRIGGERS, ATTACK, DEFENSE); // itemViews unused by resolveFrom
    }

    @Test
    void composesEnchantKeysAndResolvesCrystals() {
        CombatState combat = new CombatState(Map.of("enchants/lifesteal", 3), List.of("crystals/zap"));
        WornState worn = resolver().resolveFrom(List.of(combat), KEYS, ABILITIES, 7);

        assertEquals(7, worn.gen());
        assertArrayEquals(new int[] {0, 1}, sorted(worn.byTrigger(0)));      // both fire on trigger 0
        assertArrayEquals(new int[] {0, 1}, sorted(worn.combatAttack()));    // trigger 0 is attack-direction
        assertArrayEquals(new int[] {1}, worn.activeCrystalAbilityIds());    // zap tracked as a crystal
    }

    @Test
    void multiCrystalEntryResolvesBothComponents() {
        // A multi-crystal occupies ONE crystal-list entry "a+b" but contributes BOTH ability ids (§E);
        // the additive fold then sums overlapping effect magnitudes downstream.
        StableKeyIndex keys = new StableKeyIndex(List.of("crystals/zap", "crystals/frost"));
        Ability[] abilities = {ability(0, 1 << 0), ability(1, 1 << 0)};
        CombatState combat = new CombatState(Map.of(), List.of("crystals/zap+crystals/frost"));
        WornState worn = resolver().resolveFrom(List.of(combat), keys, abilities, 1);
        assertArrayEquals(new int[] {0, 1}, sorted(worn.activeCrystalAbilityIds()), "both components tracked");
        assertArrayEquals(new int[] {0, 1}, sorted(worn.byTrigger(0)), "both components fire");
    }

    @Test
    void unknownKeysAreSkippedNotCrashed() {
        CombatState combat = new CombatState(Map.of("enchants/ghost", 9), List.of("crystals/missing"));
        WornState worn = resolver().resolveFrom(List.of(combat), KEYS, ABILITIES, 1);
        assertEquals(0, worn.byTrigger(0).length);
        assertEquals(0, worn.activeCrystalAbilityIds().length);
    }

    @Test
    void multiplicityIsPreservedAcrossPieces() {
        CombatState a = new CombatState(Map.of("enchants/lifesteal", 3), List.of());
        CombatState b = new CombatState(Map.of("enchants/lifesteal", 3), List.of());
        WornState worn = resolver().resolveFrom(List.of(a, b), KEYS, ABILITIES, 1);
        assertEquals(2, worn.byTrigger(0).length); // lifesteal on two pieces → id 0 twice
    }

    // A SET bonus: id 0, fires on DEFENSE (trigger 1), completes at 3 worn pieces (setPieces=3).
    private static final StableKeyIndex SET_KEYS = new StableKeyIndex(List.of("sets/yeti"));
    private static final Ability[] SET_ABILITIES = {
        new Ability(0, 0, SourceKind.SET, 1 << 1, 0, 100.0, 0, 0, 0L, null,
                new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1, 3)
    };

    @Test
    void setBonusActivatesAtThresholdAndStaysOffBelowIt() {
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/yeti", false);

        WornState below = resolver().resolveFrom(List.of(piece, piece), SET_KEYS, SET_ABILITIES, 1);
        assertEquals(0, below.byTrigger(1).length, "2 of 3 pieces — bonus must not fire");
        assertEquals(false, below.isSetActive(0));

        WornState met = resolver().resolveFrom(List.of(piece, piece, piece), SET_KEYS, SET_ABILITIES, 1);
        assertArrayEquals(new int[] {0}, met.byTrigger(1), "3 pieces — bonus fires on DEFENSE");
        assertEquals(true, met.isSetActive(0));
        assertEquals(0, met.byTrigger(0).length, "bonus does not fire on ATTACK");
    }

    @Test
    void omniPieceCompletesAPartiallyWornSet() {
        CombatState piece = new CombatState(Map.of(), List.of(), "sets/yeti", false);
        CombatState omni = new CombatState(Map.of(), List.of(), null, true);

        // 2 real + 1 omni = 3 → the set completes (§6.6).
        WornState worn = resolver().resolveFrom(List.of(piece, piece, omni), SET_KEYS, SET_ABILITIES, 1);
        assertEquals(true, worn.isSetActive(0));
        assertArrayEquals(new int[] {0}, worn.byTrigger(1));
    }

    @Test
    void heroicStatsSumAcrossPieces() {
        CombatState a = new CombatState(Map.of(), List.of(), null, false, new HeroicStat(0.20, 0.10, 0.0));
        CombatState b = new CombatState(Map.of(), List.of(), null, false, new HeroicStat(0.30, 0.40, 0.50));
        WornState worn = resolver().resolveFrom(List.of(a, b), KEYS, ABILITIES, 1);
        assertEquals(0.50, worn.heroic().percentDamage(), 1e-9);
        assertEquals(0.50, worn.heroic().percentReduction(), 1e-9);
        assertEquals(0.50, worn.heroic().durability(), 1e-9);
    }

    @Test
    void omniAloneCannotConjureASet() {
        // Omni pieces with NO real piece of the set must not complete it (§6.6).
        CombatState omni = new CombatState(Map.of(), List.of(), null, true);
        WornState worn = resolver().resolveFrom(List.of(omni, omni, omni), SET_KEYS, SET_ABILITIES, 1);
        assertEquals(false, worn.isSetActive(0));
        assertEquals(0, worn.byTrigger(1).length);
    }

    private static int[] sorted(int[] values) {
        int[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }
}
