package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import compile.load.PetCurve;
import org.junit.jupiter.api.Test;

/**
 * {@code ITEM_XP_TRACK}'s grant math (owner ruling 2026-08-01) and the per-pet curve, hand-computed. The point
 * of this file is the COEXISTENCE: the two grant paths answer the same input DIFFERENTLY on purpose, so each
 * contrast is asserted against the shipped {@link PetService#rollExp} rather than assumed.
 */
class PetTrackedExpMathTest {

    @Test
    void oneGrantRollsAtMostOneLevelWhereTheShippedRollRollsAsManyAsItCanPayFor() {
        // 100/level, +250 from (level 1, exp 40) = 290 exp on the table.
        // Shipped: two levels and a 90 carry. Tracked: ONE level, 190 banked toward the next.
        assertEquals(new PetService.LevelRoll(3, 90), PetService.rollExp(1, 40, 250, 100, 100));
        assertEquals(new PetService.LevelRoll(2, 190), PetService.bankExp(1, 40, 250, 100, 100));
        assertNotEquals(PetService.rollExp(1, 40, 250, 100, 100), PetService.bankExp(1, 40, 250, 100, 100),
                "the two paths are meant to disagree — that is the whole ruling");
    }

    @Test
    void theBankIsUnboundedAtTheCapWhereTheShippedRollParksAtZero() {
        // At the cap the shipped roll zeroes exp (a clean landmark); the tracked one keeps accumulating, which
        // is what makes a capped pet's counter keep meaning something.
        assertEquals(new PetService.LevelRoll(10, 0), PetService.rollExp(10, 0, 5_000, 10, 100));
        assertEquals(new PetService.LevelRoll(10, 7_500), PetService.bankExp(10, 2_500, 5_000, 10, 100));
        assertEquals(new PetService.LevelRoll(10, 12_500), PetService.bankExp(10, 7_500, 5_000, 10, 100));
    }

    @Test
    void aGrantShortOfTheThresholdJustBanks() {
        assertEquals(new PetService.LevelRoll(1, 90), PetService.bankExp(1, 40, 50, 10, 100));
        // Exactly on the threshold levels: the ladder cost is the price of ARRIVING, not of exceeding.
        assertEquals(new PetService.LevelRoll(2, 0), PetService.bankExp(1, 40, 60, 10, 100));
    }

    @Test
    void theRecordedCurveShapesReproduceTheirRecordedTotals() {
        // Each threshold N is the cost of REACHING level N, so neededFrom(level) reads the curve at level+1.
        PetCurve lava = new PetCurve(0, 1_000);      // level x 1000
        PetCurve blackscroll = new PetCurve(250, 1_000); // 250 + 1000 x level
        PetCurve banner = new PetCurve(1_000, 0);    // flat 1000

        assertEquals(2_000, lava.neededFrom(1), "L1 -> L2 costs the L2 threshold");
        assertEquals(10_000, lava.neededFrom(9));
        assertEquals(2_250, blackscroll.neededFrom(1));
        assertEquals(10_250, blackscroll.neededFrom(9));
        assertEquals(1_000, banner.neededFrom(1));
        assertEquals(1_000, banner.neededFrom(9));

        assertEquals(54_000, totalTo10(lava), "Lava's recorded lifetime total");
        assertEquals(56_250, totalTo10(blackscroll), "Blackscroll/Enchanter's recorded lifetime total");
        assertEquals(9_000, totalTo10(banner), "Banner's recorded lifetime total");
    }

    @Test
    void aCurvedPetRollsEachLevelAtItsOwnPriceUnlikeTheFlatRoll() {
        // 500/level flat vs `level x 500`: from level 1, 3000 exp buys 6 flat levels but only levels 2-3 on
        // the curve (1000 + 1500 = 2500, with 500 left over). A curve folded into the flat roll would be a
        // silent 2x level inflation on every cosmic pet.
        PetCurve water = new PetCurve(0, 500);
        assertEquals(new PetService.LevelRoll(7, 0), PetService.rollExp(1, 0, 3_000, 10, 500));
        assertEquals(new PetService.LevelRoll(3, 500), PetService.rollCurve(1, 0, 3_000, 10, water));
    }

    private static int totalTo10(PetCurve curve) {
        int total = 0;
        for (int level = 1; level < 10; level++) {
            total += curve.neededFrom(level);
        }
        return total;
    }
}
