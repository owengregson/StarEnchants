package integrate.protect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the gate decision of {@link TownyProvider}. The static Towny side (resolving the player + the cache
 * query) is verified on a Towny server out-of-matrix (docs/decisions/0027).
 */
class TownyProviderTest {

    @Test
    void nonTownyWorldAllowsEverything() {
        assertTrue(TownyProvider.decide(false, false), "outside a Towny world nothing is gated");
        assertTrue(TownyProvider.decide(false, true));
    }

    @Test
    void townyWorldDefersToBuildCache() {
        assertTrue(TownyProvider.decide(true, true), "Towny allows the build → allow");
        assertFalse(TownyProvider.decide(true, false), "Towny denies the build → deny");
    }
}
