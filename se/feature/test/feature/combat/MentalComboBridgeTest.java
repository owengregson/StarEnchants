package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import engine.sink.DotParkLedger;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/**
 * The optional/reflective registration edges of {@link MentalComboBridge} (ADR-0069), mirroring
 * {@link MentalKnockbackBridgeTest}: the disabled flag short-circuits before any reflection, and an absent
 * Mental (its event class is not on the test classpath) resolves to ABSENT and registers nothing. The BOUND
 * leg and everything downstream of the events are verified out-of-matrix on a Mental-installed server plus
 * the ledger/release/flush unit suites.
 */
class MentalComboBridgeTest {

    private static ComboDotRelease release() {
        return new ComboDotRelease(new DotParkLedger(), () -> 0L);
    }

    @Test
    void disabledFlagShortCircuitsToDISABLED() {
        Plugin plugin = mock(Plugin.class);
        // Prove no reflection/registration is attempted: any touch of the plugin would blow up here.
        when(plugin.getServer()).thenThrow(new AssertionError("disabled register must not touch the plugin"));

        assertEquals(MentalComboBridge.Path.DISABLED,
                MentalComboBridge.register(plugin, new DotParkLedger(), release(), () -> 0L, false));
        verifyNoInteractions(plugin);
    }

    @Test
    void absentMentalClassResolvesToABSENT() {
        Plugin plugin = mock(Plugin.class);
        // Mental is not on the test classpath, so Class.forName(START_EVENT) throws → ABSENT, nothing registered.
        assertEquals(MentalComboBridge.Path.ABSENT,
                MentalComboBridge.register(plugin, new DotParkLedger(), release(), () -> 0L, true));
        verifyNoInteractions(plugin);
    }
}
