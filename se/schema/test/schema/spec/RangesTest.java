package schema.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link Ranges} — the [0,100] clamp and the clamp-then-order pair rule. */
final class RangesTest {

    @Test
    void clampPercentInt() {
        assertEquals(0, Ranges.clampPercent(-5));
        assertEquals(0, Ranges.clampPercent(0));
        assertEquals(42, Ranges.clampPercent(42));
        assertEquals(100, Ranges.clampPercent(100));
        assertEquals(100, Ranges.clampPercent(150));
    }

    @Test
    void clampPercentDouble() {
        assertEquals(0.0, Ranges.clampPercent(-0.1));
        assertEquals(100.0, Ranges.clampPercent(100.5));
        assertEquals(55.5, Ranges.clampPercent(55.5));
    }

    @Test
    void percentRangeCanonicalises() {
        assertEquals(new Ranges.IntRange(25, 75), Ranges.percentRange(25, 75));
        assertEquals(new Ranges.IntRange(25, 75), Ranges.percentRange(75, 25));
        assertEquals(new Ranges.IntRange(0, 100), Ranges.percentRange(-10, 150));
        assertEquals(new Ranges.IntRange(0, 100), Ranges.percentRange(150, -10));
        assertEquals(new Ranges.IntRange(40, 40), Ranges.percentRange(40, 40));
    }
}
