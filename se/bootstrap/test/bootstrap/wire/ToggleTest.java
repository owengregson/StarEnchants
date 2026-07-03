package bootstrap.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The declared toggle-depth semantics (ADR-0047) — the axis that makes boot-vs-live an explicit property. */
final class ToggleTest {

    @Test
    void alwaysIsDepthNoneNeverGatesAndReadsTrue() {
        assertEquals(Toggle.Depth.NONE, Toggle.ALWAYS.depth());
        assertFalse(Toggle.ALWAYS.gatesBoot());
        assertTrue(Toggle.ALWAYS.enabled().getAsBoolean());
        assertEquals("", Toggle.ALWAYS.disabledLog());
    }

    @Test
    void bootGatesWireTimeAndCarriesItsDisabledLog() {
        Toggle t = Toggle.boot("features.souls", () -> false, "souls disabled");
        assertEquals(Toggle.Depth.BOOT, t.depth());
        assertTrue(t.gatesBoot());
        assertFalse(t.enabled().getAsBoolean());
        assertEquals("souls disabled", t.disabledLog());
    }

    @Test
    void liveNeverGatesBootAndHasNoDisabledLog() {
        Toggle t = Toggle.live("features.enchants", () -> true);
        assertEquals(Toggle.Depth.LIVE, t.depth());
        assertFalse(t.gatesBoot());
        assertTrue(t.enabled().getAsBoolean());
        assertEquals("", t.disabledLog());
    }

    @Test
    void everyComponentIsNullChecked() {
        assertThrows(NullPointerException.class, () -> new Toggle(null, Toggle.Depth.NONE, () -> true, ""));
        assertThrows(NullPointerException.class, () -> new Toggle("k", null, () -> true, ""));
        assertThrows(NullPointerException.class, () -> new Toggle("k", Toggle.Depth.NONE, null, ""));
        assertThrows(NullPointerException.class, () -> new Toggle("k", Toggle.Depth.NONE, () -> true, null));
    }
}
