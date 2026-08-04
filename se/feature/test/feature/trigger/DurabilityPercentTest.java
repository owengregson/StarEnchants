package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The one {@code %item.durabilitypercent%} formula both eras read (modern from {@code Damageable#getDamage},
 * 1.8 from {@code ItemStack#getDurability}), so the fact cannot mean two different things on the two lanes.
 */
class DurabilityPercentTest {

    @ParameterizedTest(name = "damage {0} of max {1} → {2}%")
    @CsvSource({
            "0,   1561, 100.0",  // untouched: full bar
            "780, 1560, 50.0",   // half worn
            "1560, 1561, 0.0640614990390775", // one point from breaking — near zero, never 100
            "-5,  100,  100.0",  // a negative damage value clamps rather than reading over 100
            "250, 100,  0.0",    // over-damaged (a shrunken max) clamps rather than reading negative
    })
    void remainingIsMeasuredAgainstTheEffectiveMax(int damage, int max, double expected) {
        assertEquals(expected, DurabilityPercent.of(damage, max), 1e-9);
    }

    @Test
    void anItemThatDoesNotWearIsAbsentRatherThanFull() {
        // NaN is the "no reading" sentinel the ActivationContext carries; 0 would say "spent" and 100 "pristine",
        // and a stone block is neither.
        assertTrue(Double.isNaN(DurabilityPercent.of(0, 0)));
        assertTrue(Double.isNaN(DurabilityPercent.of(3, -1)));
    }
}
