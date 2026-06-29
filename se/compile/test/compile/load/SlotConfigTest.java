package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The slot-item config (§H) — defaults and the constructor's clamping/copy invariants. */
class SlotConfigTest {

    @Test
    void defaultsAreSane() {
        SlotConfig d = SlotConfig.defaults();
        assertTrue(d.orbAmount() >= 1, "an orb grants at least one slot");
        assertTrue(d.hardCap() >= 1, "the hard cap is positive");
        assertTrue(d.hardCap() >= d.orbAmount(), "the cap exceeds a single orb grant");
        assertEquals(List.of("ARMOR", "WEAPON", "TOOL"), d.appliesTo(), "the orb applies to armor, weapons, and tools");
    }

    @Test
    void clampsNonPositiveAmounts() {
        SlotConfig c = new SlotConfig("M", "n", List.of(), 0, 0, 100, 100, List.of("TOOL"));
        assertEquals(1, c.orbAmount(), "orb amount floored to 1");
        assertEquals(1, c.hardCap(), "hard cap floored to 1");
    }

    @Test
    void appliesToIsADefensiveCopy() {
        List<String> mutable = new ArrayList<>(List.of("TOOL"));
        SlotConfig c = new SlotConfig("M", "n", List.of(), 1, 1, 100, 100, mutable);
        mutable.add("WEAPON");
        assertEquals(List.of("TOOL"), c.appliesTo(), "the record copies applies-to, so later source mutation can't leak in");
    }
}
