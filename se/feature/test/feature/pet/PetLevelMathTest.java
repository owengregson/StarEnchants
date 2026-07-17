package feature.pet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.PetItemConfig;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The pet leveling math (ADR-0059) as pure statics with hand-computed rows: the exp roll's carry and cap
 * parking, the use-XP bounds, the exact fractional passive accrual, and the displayed-number render gate.
 */
class PetLevelMathTest {

    @Test
    void expRollCarriesRemaindersAndRollsMultipleLevels() {
        // 100/level: +250 exp from (level 1, exp 40) → two rolls and a 90-exp carry — distinct values so a
        // transposed level/exp fails loudly
        assertEquals(new PetService.LevelRoll(3, 90), PetService.rollExp(1, 40, 250, 100, 100));
    }

    @Test
    void expRollParksAtTheCapWithZeroExp() {
        // overshooting the cap parks at (max, 0) — never a part-filled bar at max
        assertEquals(new PetService.LevelRoll(100, 0), PetService.rollExp(99, 50, 5_000, 100, 100));
        // an already-capped roll is inert (gainExp guards earlier; the math must still be safe)
        assertEquals(new PetService.LevelRoll(100, 0), PetService.rollExp(100, 0, 10, 100, 100));
    }

    @Test
    void useExpRollStaysInSpecBoundsAndHitsBothEndpoints() {
        Random random = new Random(42);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2_000; i++) {
            int roll = PetService.useExpRoll(random, 100);
            assertTrue(roll >= 12 && roll <= 20, "uniform in [expPerLevel/8, expPerLevel/5], got " + roll);
            seen.add(roll);
        }
        // both inclusive endpoints must be reachable — catches an off-by-one exclusive bound
        assertTrue(seen.contains(12) && seen.contains(20), "endpoints reachable, saw " + seen);
    }

    @Test
    void useExpRollNeverDropsBelowOne() {
        Random random = new Random(7);
        for (int i = 0; i < 100; i++) {
            assertEquals(1, PetService.useExpRoll(random, 1)); // expPerLevel/8 == 0 → the min-1 floor
        }
    }

    @Test
    void passiveAccrualIsExactOverAnHour() {
        // 0.5 level/hour at 100 exp/level: exactly 50 exp after 60 online minutes, remainder exactly zero
        long units = 0;
        int whole = 0;
        for (int minute = 0; minute < 60; minute++) {
            units += PetService.accrueUnitsPerMinute(0.5, 100);
            whole += (int) (units / PetService.FRAC_UNITS_PER_EXP);
            units %= PetService.FRAC_UNITS_PER_EXP;
        }
        assertEquals(50, whole, "0.5 lvl/h × 100 exp/lvl = 50 exp per hour, no drift");
        assertEquals(0, units, "the carry lands exactly on the hour");
        // the PASSIVE-in-hotbar double rate is exactly one level's worth per hour
        assertEquals(100L * PetService.FRAC_UNITS_PER_EXP, 60L * PetService.accrueUnitsPerMinute(1.0, 100));
        // a zero/negative rate accrues nothing (the disable knob)
        assertEquals(0L, PetService.accrueUnitsPerMinute(0.0, 100));
    }

    @Test
    void renderGateFiresOnLevelOrBarTenthOnly() {
        assertTrue(PetService.displayedChanged(1, 2, 99, 0, 100), "a level change always renders");
        assertTrue(PetService.displayedChanged(1, 1, 9, 10, 100), "crossing a bar tenth renders");
        assertFalse(PetService.displayedChanged(1, 1, 10, 19, 100), "inside one tenth: a silent write");
    }

    @Test
    void defaultNameTemplateCarriesTheLevelToken() {
        // the ADR-0059 suffix must keep {LEVEL} in the universal name — without it the level never shows in
        // the item name on any level write (the exact styling is authored likeness, not pinned here)
        assertTrue(PetItemConfig.defaults().name().contains("{LEVEL}"));
    }
}
