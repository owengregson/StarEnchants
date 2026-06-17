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
        assertTrue(d.black().successChance() >= 0 && d.black().successChance() <= 100);
        assertTrue(d.randomizer().minPercent() <= d.randomizer().maxPercent());
    }

    @Test
    void blackSuccessIsClamped() {
        ScrollsConfig.Black b = new ScrollsConfig.Black("M", "n", List.of(), 150, "a", "b", "c");
        assertEquals(100, b.successChance(), "success chance clamped to 100");
    }

    @Test
    void randomizerOrdersAndClampsRange() {
        // Reversed + out-of-range bounds are clamped to [0,100] and ordered low..high.
        ScrollsConfig.Randomizer r = new ScrollsConfig.Randomizer("M", "n", List.of(), 120, -5, "a", "b");
        assertEquals(0, r.minPercent());
        assertEquals(100, r.maxPercent());
    }
}
