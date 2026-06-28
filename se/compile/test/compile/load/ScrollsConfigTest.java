package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The scroll-family config (§I) — defaults and the constructor's clamping/ordering invariants. */
class ScrollsConfigTest {

    @Test
    void defaultsArePresent() {
        ScrollsConfig d = ScrollsConfig.defaults();
        assertTrue(d.black().minConvert() >= 0 && d.black().maxConvert() <= 100);
        assertTrue(d.black().minConvert() <= d.black().maxConvert());
        assertTrue(d.randomizer().minPercent() <= d.randomizer().maxPercent());
        assertTrue(d.holy().minSuccess() >= 0 && d.holy().maxSuccess() <= 100);
        assertTrue(d.holy().minSuccess() <= d.holy().maxSuccess());
        assertTrue(d.transmog().nameSuffix() != null);
        assertTrue(d.nametag().blacklist() != null);
    }

    @Test
    void holySuccessRangeOrdersAndClamps() {
        ScrollsConfig.Holy h = new ScrollsConfig.Holy("M", "n", List.of(), 250, -5);
        assertEquals(0, h.minSuccess(), "min clamped to 0");
        assertEquals(100, h.maxSuccess(), "max clamped to 100");
    }

    @Test
    void blackConvertRangeOrdersAndClamps() {
        // reversed, out-of-range bounds clamp to [0,100] and reorder low..high
        ScrollsConfig.Black b = new ScrollsConfig.Black("M", "n", List.of(), 150, -5);
        assertEquals(0, b.minConvert(), "min clamped to 0");
        assertEquals(100, b.maxConvert(), "max clamped to 100");
    }

    @Test
    void randomizerOrdersAndClampsRange() {
        // reversed, out-of-range bounds clamp to [0,100] and reorder low..high
        ScrollsConfig.Randomizer r = new ScrollsConfig.Randomizer("M", "n", List.of(), 120, -5);
        assertEquals(0, r.minPercent());
        assertEquals(100, r.maxPercent());
    }
}
