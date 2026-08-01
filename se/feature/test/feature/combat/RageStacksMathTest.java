package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Regression coverage for the exact source formula: 1 + priorHits*(0.05*level), capped at 2.5. */
class RageStacksMathTest {

    @Test
    void firstHitHasNoBonusBecauseTheCounterIncrementsAfterTheWalk() {
        assertEquals(1.0, RageStacksService.multiplier(0, 1));
        assertEquals(1.0, RageStacksService.multiplier(0, 6));
    }

    @Test
    void multiplierUsesTheUnboundedPriorHitCounterAndCapsAtTwoPointFive() {
        assertEquals(1.05, RageStacksService.multiplier(1, 1));
        assertEquals(1.5, RageStacksService.multiplier(5, 2));
        assertEquals(2.5, RageStacksService.multiplier(30, 1));
        assertEquals(2.5, RageStacksService.multiplier(100, 6));
    }
}
