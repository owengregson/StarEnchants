package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The armed PROC_REBOUND grades: the arm/lift lifecycle and the exclusive grade-precedence chain. */
class ReboundStoreTest {

    private static final int NORMAL = 1;
    private static final int HEROIC = 2;
    private static final int MASTERY = 3;

    private final ReboundStore store = new ReboundStore();
    private final UUID player = UUID.randomUUID();

    /** The matrix's three grades on one wearer: normal ≤5, heroic 6–7, mastery ==8. */
    private void armAllThreeGrades() {
        store.arm(player, NORMAL, 4, 5.0, 0, 5);
        store.arm(player, HEROIC, 6, 4.0, 6, 7);
        store.arm(player, MASTERY, 8, 3.0, 8, 8);
    }

    @Test
    void armingMakesTheWearerArmedAndLiftingTheLastGradeUnmakesIt() {
        assertFalse(store.armed(player));
        store.arm(player, NORMAL, 4, 5.0, 0, 5);
        assertTrue(store.armed(player));

        store.disarm(player, NORMAL);

        assertFalse(store.armed(player), "the last grade lifted forgets the player entirely");
        assertNull(store.strongestFor(player, 3));
    }

    @Test
    void oneRulePerDefIdSoReArmingReplacesRatherThanAccumulates() {
        store.arm(player, NORMAL, 4, 5.0, 0, 5);
        store.arm(player, NORMAL, 7, 9.0, 0, 5); // the same piece re-equipped at a higher level
        assertEquals(new ReboundStore.Rule(NORMAL, 7, 9.0, 0, 5), store.strongestFor(player, 3));
    }

    @Test
    void liftingOneGradeLeavesTheOthersArmed() {
        armAllThreeGrades();

        store.disarm(player, HEROIC);

        assertTrue(store.armed(player));
        assertNull(store.strongestFor(player, 7), "no band still reaches tier 7");
        assertEquals(MASTERY, store.strongestFor(player, 8).defId());
        assertEquals(NORMAL, store.strongestFor(player, 3).defId());
    }

    @Test
    void gradePrecedenceIsExclusivePerIncomingTier() {
        armAllThreeGrades();

        assertEquals(NORMAL, store.strongestFor(player, 5).defId());
        assertEquals(HEROIC, store.strongestFor(player, 6).defId());
        assertEquals(HEROIC, store.strongestFor(player, 7).defId());
        assertEquals(MASTERY, store.strongestFor(player, 8).defId());
        assertNull(store.strongestFor(player, 9), "no grade answers above the authored bands");
    }

    @Test
    void overlappingBandsResolveToTheNarrowestGradeThatReachesTheTier() {
        // A pack authoring cumulative bands rather than exclusive ones must still use ONE branch per tier.
        store.arm(player, NORMAL, 9, 5.0, 0, 8);   // reaches everything, at a high level
        store.arm(player, MASTERY, 1, 3.0, 8, 8);  // mastery-only, at a low level

        assertEquals(NORMAL, store.strongestFor(player, 4).defId());
        assertEquals(MASTERY, store.strongestFor(player, 8).defId(),
                "the greatest tier-min wins even when a lower grade sits at a higher level");
    }

    @Test
    void aNonPositiveChanceOrInvertedBandArmsNothing() {
        store.arm(player, NORMAL, 4, 0.0, 0, 5);
        store.arm(player, HEROIC, 4, 5.0, 7, 6);
        assertFalse(store.armed(player));
    }

    @Test
    void clearForgetsEveryGrade() {
        armAllThreeGrades();

        store.clear(player);

        assertFalse(store.armed(player));
        assertNull(store.strongestFor(player, 8));
    }
}
