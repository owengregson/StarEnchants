package platform.text;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** {@link TimeFormat} — zero-unit omission, always-at-least-seconds, and tick→second ceiling. */
final class TimeFormatTest {

    @Test
    void hmsRendersEachUnitBand() {
        assertEquals("0s", TimeFormat.hms(0));
        assertEquals("30s", TimeFormat.hms(30));
        assertEquals("59s", TimeFormat.hms(59));
        assertEquals("1m", TimeFormat.hms(60)); // whole minute → seconds omitted
        assertEquals("1m 1s", TimeFormat.hms(61));
        assertEquals("2m 5s", TimeFormat.hms(125));
        assertEquals("59m 59s", TimeFormat.hms(3599));
        assertEquals("1h", TimeFormat.hms(3600)); // whole hour → minutes+seconds omitted
        assertEquals("1h 1s", TimeFormat.hms(3601)); // gap minute stays omitted, not zero-padded
        assertEquals("1h 1m 1s", TimeFormat.hms(3661));
        assertEquals("2h 3m 4s", TimeFormat.hms(2 * 3600 + 3 * 60 + 4));
    }

    @Test
    void hmsClampsNonPositiveToZeroSeconds() {
        assertEquals("0s", TimeFormat.hms(-1));
        assertEquals("0s", TimeFormat.hms(-3600));
    }

    @Test
    void hmsFromTicksCeilsPartialSecond() {
        assertEquals("0s", TimeFormat.hmsFromTicks(0));
        assertEquals("1s", TimeFormat.hmsFromTicks(1)); // any partial second rounds up
        assertEquals("1s", TimeFormat.hmsFromTicks(20));
        assertEquals("2s", TimeFormat.hmsFromTicks(21));
        assertEquals("1m", TimeFormat.hmsFromTicks(1200));
        assertEquals("1m 1s", TimeFormat.hmsFromTicks(1201)); // 60.05s → ceil 61s
        assertEquals("0s", TimeFormat.hmsFromTicks(-5)); // negative ceils to 0, then clamps
    }
}
