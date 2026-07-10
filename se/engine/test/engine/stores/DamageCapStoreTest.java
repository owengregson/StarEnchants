package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The ADR-0049 Diminish cap: last-taken bookkeeping + a one-shot armed cap consumed exactly once. */
class DamageCapStoreTest {

    private final DamageCapStore store = new DamageCapStore();

    @Test
    void lastTakenRecordsAndReadsBack() {
        UUID player = UUID.randomUUID();
        assertEquals(0.0, store.lastTaken(player), "no history reads zero");
        store.recordLastTaken(player, 12.0);
        assertEquals(12.0, store.lastTaken(player));
        store.recordLastTaken(player, 7.0); // the latest hit overwrites
        assertEquals(7.0, store.lastTaken(player));
    }

    @Test
    void armedCapConsumesExactlyOnce() {
        UUID player = UUID.randomUUID();
        store.arm(player, 6.0, false, 0L, 100);
        DamageCapStore.Cap cap = store.consumeArmed(player, 50L);
        assertNotNull(cap);
        assertEquals(6.0, cap.value());
        assertFalse(cap.reflectOverflow());
        assertNull(store.consumeArmed(player, 51L), "a one-shot cap: gone after the first consume");
    }

    @Test
    void reflectOverflowFlagRoundTrips() {
        UUID player = UUID.randomUUID();
        store.arm(player, 6.0, true, 0L, 100);
        DamageCapStore.Cap cap = store.consumeArmed(player, 1L);
        assertNotNull(cap);
        assertTrue(cap.reflectOverflow());
    }

    @Test
    void elapsedCapIsNotConsumed() {
        UUID player = UUID.randomUUID();
        store.arm(player, 6.0, false, 0L, 40);
        assertNull(store.consumeArmed(player, 40L), "the expiry tick counts as elapsed (half-open)");
    }

    @Test
    void nonPositiveValueOrDurationArmsNothing() {
        UUID player = UUID.randomUUID();
        store.arm(player, 0.0, false, 0L, 100); // no last-taken history → nothing to cap
        store.arm(player, 6.0, false, 0L, 0);
        assertNull(store.consumeArmed(player, 0L));
    }

    @Test
    void clearForgetsBothMaps() {
        UUID player = UUID.randomUUID();
        store.recordLastTaken(player, 12.0);
        store.arm(player, 6.0, false, 0L, 100);
        store.clear(player);
        assertEquals(0.0, store.lastTaken(player));
        assertNull(store.consumeArmed(player, 0L));
    }
}
