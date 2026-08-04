package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@code POTION_AMP_REDUCE}'s arithmetic and its one-window-per-type claim. The arithmetic is the whole
 * fidelity risk of the primitive — an off-by-one against the 1-based/0-based split, or a "denied" that reads
 * as a real amplifier, is the difference between taking the authored few hearts and taking all of them.
 */
class ReducedPotionsTest {

    private final UUID entity = UUID.randomUUID();

    @AfterEach
    void clean() {
        ReducedPotions.clearAll();
    }

    @Test
    void aReductionSubtractsItsLevelsFromTheSourceAmplifier() {
        // Health Boost VI is amplifier 5; Mortal Coil I saps 2 LEVELS, leaving IV — amplifier 3, i.e. four of
        // the six tiers of hearts. Levels are 1-based and amplifiers 0-based, but a DIFFERENCE is unit-free,
        // so the authored amount subtracts unchanged.
        assertEquals(3, ReducedPotions.reduced(5, 2));
        assertEquals(0, ReducedPotions.reduced(1, 1), "the last tier left is level I — amplifier 0, not denied");
        assertEquals(5, ReducedPotions.reduced(5, 0), "a zero sap is the source itself");
    }

    @Test
    void aReductionThatLeavesNothingDeniesTheEffectOutright() {
        // "≤ 0 means denied entirely": level I less two levels is not level −1, it is gone. One sentinel
        // however far past the floor, so a caller cannot read a deeper strip as a deeper (negative) amplifier.
        assertEquals(ReducedPotions.DENIED, ReducedPotions.reduced(0, 2));
        assertEquals(ReducedPotions.DENIED, ReducedPotions.reduced(1, 2));
        assertEquals(ReducedPotions.DENIED, ReducedPotions.reduced(2, 9));
    }

    @Test
    void theRestoreGivesBackWhatWasLeftOfTheSourceLessTheWindowItSatOut() {
        assertEquals(140, ReducedPotions.restoreDuration(200, 60));
        assertEquals(0, ReducedPotions.restoreDuration(40, 60), "it would have lapsed anyway — restore nothing");
        assertEquals(0, ReducedPotions.restoreDuration(60, 60), "exactly consumed is still nothing left");
        assertEquals(-1, ReducedPotions.restoreDuration(-1, 60),
                "the infinite marker rides through — subtracting would invent a finite buff");
    }

    @Test
    void oneReductionPerTypeAtATimeSoTwoAttackersCannotCompoundTheDrain() {
        assertTrue(ReducedPotions.arm(entity, "HEALTH_BOOST", 60_000L));
        assertFalse(ReducedPotions.arm(entity, "HEALTH_BOOST", 60_000L), "the incumbent window holds");
        assertTrue(ReducedPotions.arm(entity, "SPEED", 60_000L), "a different type is its own window");
        assertTrue(ReducedPotions.arm(UUID.randomUUID(), "HEALTH_BOOST", 60_000L), "as is a different victim");

        ReducedPotions.release(entity, "HEALTH_BOOST");
        assertTrue(ReducedPotions.arm(entity, "HEALTH_BOOST", 60_000L), "the next proc claims it once released");
    }

    @Test
    void guardsRejectNoOpClaims() {
        assertFalse(ReducedPotions.arm(null, "HEALTH_BOOST", 60_000L));
        assertFalse(ReducedPotions.arm(entity, null, 60_000L));
        assertFalse(ReducedPotions.arm(entity, "HEALTH_BOOST", 0L));
        assertFalse(ReducedPotions.arm(entity, "HEALTH_BOOST", -5L));
        assertTrue(ReducedPotions.arm(entity, "HEALTH_BOOST", 60_000L), "none of those claimed the window");
    }
}
