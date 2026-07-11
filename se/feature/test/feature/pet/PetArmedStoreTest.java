package feature.pet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The armed-window store (ADR-0052): tick-expiry + the generation guard against stale expiry tasks. */
class PetArmedStoreTest {

    private final PetArmedStore store = new PetArmedStore();
    private final UUID player = UUID.randomUUID();

    @Test
    void armedUntilExpiryTickThenNot() {
        store.arm(player, "shield", 100);
        assertTrue(store.isArmed(player, "shield", 99));
        assertFalse(store.isArmed(player, "shield", 100)); // expiry tick itself is closed
        assertFalse(store.isArmed(player, "eagle", 50));   // per-pet windows
    }

    @Test
    void staleExpiryNeverClearsAReArmedWindow() {
        long first = store.arm(player, "shield", 100);
        long second = store.arm(player, "shield", 300);    // re-armed before the first window lapsed

        assertFalse(store.clearIfGeneration(player, "shield", first)); // the stale task must no-op
        assertTrue(store.isArmed(player, "shield", 150));              // the new window survives

        assertTrue(store.clearIfGeneration(player, "shield", second)); // its own task clears it
        assertFalse(store.isArmed(player, "shield", 150));
        assertFalse(store.clearIfGeneration(player, "shield", second)); // idempotent
    }

    @Test
    void quitAndDisableTeardownDropEverything() {
        store.arm(player, "shield", 100);
        store.clear(player);
        assertFalse(store.isArmed(player, "shield", 1));

        store.arm(player, "shield", 100);
        store.clearAll();
        assertFalse(store.isArmed(player, "shield", 1));
    }
}
