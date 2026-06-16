package engine.condition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FactBufferTest {

    @Test
    void numberSlotsRoundTrip() {
        FactBuffer f = new FactBuffer(2, 0, 0);
        f.setNumber(0, 3.5);
        f.setNumber(1, -2.0);
        assertEquals(3.5, f.number(0));
        assertEquals(-2.0, f.number(1));
    }

    @Test
    void flagSlotsAreIndependentBits() {
        FactBuffer f = new FactBuffer(0, 3, 0);
        f.setFlag(0, true);
        f.setFlag(2, true);
        assertTrue(f.flag(0));
        assertFalse(f.flag(1));
        assertTrue(f.flag(2));
        f.setFlag(0, false); // clearing one bit leaves the others
        assertFalse(f.flag(0));
        assertTrue(f.flag(2));
    }

    @Test
    void stringSlotsRoundTrip() {
        FactBuffer f = new FactBuffer(0, 0, 1);
        f.setString(0, "nether");
        assertEquals("nether", f.string(0));
    }

    @Test
    void clearResetsEverythingIncludingPapi() {
        FactBuffer f = new FactBuffer(1, 1, 1);
        f.setNumber(0, 9);
        f.setFlag(0, true);
        f.setString(0, "x");
        f.papiResolver(t -> "v");
        f.clear();
        assertEquals(0.0, f.number(0));
        assertFalse(f.flag(0));
        assertNull(f.string(0));
        assertNull(f.resolvePapi("anything"));
    }

    @Test
    void papiResolverIsInvoked() {
        FactBuffer f = new FactBuffer(0, 0, 0);
        f.papiResolver(t -> "player_" + t);
        assertEquals("player_level", f.resolvePapi("level"));
    }

    @Test
    void tooManyFlagSlotsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FactBuffer(0, FactBuffer.MAX_FLAGS + 1, 0));
    }
}
