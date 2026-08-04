package engine.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DotSuppressionStoreTest {

    private final DotSuppressionStore store = new DotSuppressionStore();
    private final UUID player = UUID.randomUUID();

    @Test
    void onlyTheArmedCausesAreSuppressedAndOnlyForTheWindow() {
        store.suppress(player, DotSuppressionStore.CAUSE_WITHER, 0L, 100);

        assertTrue(store.suppressed(player, 99L, DotSuppressionStore.CAUSE_WITHER));
        assertFalse(store.suppressed(player, 99L, DotSuppressionStore.CAUSE_POISON),
                "a cause the burn never named still ticks");
        assertFalse(store.suppressed(player, 100L, DotSuppressionStore.CAUSE_WITHER), "the window closes");
        assertFalse(store.suppressed(UUID.randomUUID(), 0L, DotSuppressionStore.CAUSE_WITHER));
    }

    @Test
    void aSecondBurnWidensTheCausesAndKeepsTheLaterExpiry() {
        // The merge rule: union + max expiry. A replace-whole rule (DotAmplifyStore's) would let the FIRST
        // burn's replaced DoT resume ticking mid-window the moment a shorter second burn landed.
        store.suppress(player, DotSuppressionStore.CAUSE_WITHER, 0L, 200);
        store.suppress(player, DotSuppressionStore.CAUSE_POISON, 50L, 40);

        assertTrue(store.suppressed(player, 60L, DotSuppressionStore.CAUSE_WITHER));
        assertTrue(store.suppressed(player, 60L, DotSuppressionStore.CAUSE_POISON));
        assertTrue(store.suppressed(player, 150L, DotSuppressionStore.CAUSE_WITHER),
                "the longer window is never shortened by a later, shorter one");
    }

    @Test
    void anArmAfterTheWindowLapsedReplacesItRatherThanUnioningTheStaleMask() {
        store.suppress(player, DotSuppressionStore.CAUSE_WITHER, 0L, 100);
        store.suppress(player, DotSuppressionStore.CAUSE_POISON, 100L, 100);

        assertFalse(store.suppressed(player, 120L, DotSuppressionStore.CAUSE_WITHER),
                "a lapsed mask must not be resurrected by the next arm");
        assertTrue(store.suppressed(player, 120L, DotSuppressionStore.CAUSE_POISON));
    }

    @Test
    void anEmptyMaskOrNonPositiveWindowArmsNothing() {
        store.suppress(player, 0, 0L, 100);                                  // no cause named
        store.suppress(player, DotSuppressionStore.CAUSE_WITHER, 0L, 0);     // no window

        assertFalse(store.suppressed(player, 0L, DotSuppressionStore.CAUSE_WITHER));
    }

    @Test
    void clearForgetsTheWindow() {
        store.suppress(player, DotSuppressionStore.CAUSE_WITHER, 0L, 200);

        store.clear(player);

        assertFalse(store.suppressed(player, 0L, DotSuppressionStore.CAUSE_WITHER));
    }
}
