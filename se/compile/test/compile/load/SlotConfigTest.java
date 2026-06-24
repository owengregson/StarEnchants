package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The slot-item config (§H) — defaults and the constructor's clamping invariants. */
class SlotConfigTest {

    @Test
    void defaultsAreSane() {
        SlotConfig d = SlotConfig.defaults();
        assertTrue(d.orbAmount() >= 1, "an orb grants at least one slot");
        assertTrue(d.hardCap() >= 1, "the hard cap is positive");
        assertTrue(d.hardCap() >= d.orbAmount(), "the cap exceeds a single orb grant");
    }

    @Test
    void clampsNonPositiveAmounts() {
        SlotConfig c = new SlotConfig("M", "n", List.of(), 0, 0);
        assertEquals(1, c.orbAmount(), "orb amount floored to 1");
        assertEquals(1, c.hardCap(), "hard cap floored to 1");
    }
}
