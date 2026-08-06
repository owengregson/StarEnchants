package platform.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * {@link Numbers#grouped} as a format spec over test-owned values (R-QC65). The contract is the recorded
 * {@code DecimalFormat("#,###.##")} convention, which is why the cases below are the ones that pattern is
 * easy to get wrong: the group boundary itself, a value with no group at all, and a whole number that must
 * NOT grow a {@code .0}.
 */
class NumbersTest {

    @Test
    void thousandsAreGroupedFromTheDecimalPointOutward() {
        assertEquals("999", Numbers.grouped(999), "no separator below the first boundary");
        assertEquals("1,000", Numbers.grouped(1000), "the boundary itself");
        assertEquals("56,250", Numbers.grouped(56_250), "the longest pet curve in the pack");
        assertEquals("1,234,567", Numbers.grouped(1_234_567), "grouping repeats every three");
        assertEquals("0", Numbers.grouped(0));
    }

    @Test
    void aFractionKeepsAtMostTwoDecimalsAndTheGroupingIgnoresThem() {
        // The decimals must not be grouped and must not shift the integer part's boundaries.
        assertEquals("1,000.25", Numbers.grouped(1000.25));
        assertEquals("3", Numbers.grouped(3.0), "a trailing .0 is dropped, as the recorded pattern drops it");
        assertEquals("1.25", Numbers.grouped(1.25));
    }

    @Test
    void aNegativeGroupsItsDigitsAndNotItsSign() {
        assertEquals("-1,000", Numbers.grouped(-1000));
        assertEquals("-100", Numbers.grouped(-100), "a sign must not be mistaken for a fourth digit");
    }

    @Test
    void groupingAgreesWithTheUngroupedReadoutOnEveryDigitButTheSeparators() {
        // One rule, two renderings: the difference between the chat readout and the lore readout is commas and
        // nothing else, so a rounding change can never land in one and not the other.
        for (double value : new double[] {0, 7, 999.5, 1000.005, 123_456.789, -2.505}) {
            assertEquals(Numbers.chat(value), Numbers.grouped(value).replace(",", ""),
                    "grouped(" + String.format(Locale.ROOT, "%s", value) + ") must differ only by separators");
        }
    }
}
