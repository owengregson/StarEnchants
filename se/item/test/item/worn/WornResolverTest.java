package item.worn;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.SourceKind;
import compile.model.StableKeyIndex;
import item.codec.CombatState;
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
                new CompiledEffect[0], 0, Affinity.CONTEXT_LOCAL, -1, -1, -1, -1);
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

    private static int[] sorted(int[] values) {
        int[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }
}
