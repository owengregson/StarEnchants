package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChargeStoreTest {

    private final ChargeStore store = new ChargeStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void absentChargeCountsZero() {
        assertEquals(0, store.count(p, 1, 0L));
    }

    @Test
    void incrementRampsTowardMaxAndReportsNewCount() {
        assertEquals(1, store.increment(p, 1, 3, 0L, 100));
        assertEquals(2, store.increment(p, 1, 3, 10L, 100));
        assertEquals(3, store.increment(p, 1, 3, 20L, 100));
        assertEquals(3, store.increment(p, 1, 3, 30L, 100)); // clamped at max
        assertEquals(3, store.count(p, 1, 30L));
    }

    @Test
    void slidingTtlKeepsAnActivelyTriggeredChargeAlive() {
        store.increment(p, 1, 5, 100L, 40); // expiry 140
        store.increment(p, 1, 5, 130L, 40); // still alive at 130, expiry refreshed to 170
        assertEquals(2, store.count(p, 1, 150L)); // would have lapsed at the old 140
    }

    @Test
    void countAtExpiryTickIsLapsedAndEvicted() {
        store.increment(p, 1, 5, 100L, 40); // expiry 140
        assertEquals(1, store.count(p, 1, 139L));
        assertEquals(0, store.count(p, 1, 140L)); // expiry tick is lapsed
        assertEquals(0, store.count(p, 1, 139L)); // and the entry is now gone
    }

    @Test
    void incrementOnALapsedChargeRestartsFromZero() {
        store.increment(p, 1, 5, 100L, 40); // count 1, expiry 140
        store.increment(p, 1, 5, 110L, 40); // count 2, expiry 150
        assertEquals(1, store.increment(p, 1, 5, 200L, 40)); // long after expiry: restart at 1
    }

    @Test
    void abilitiesAreIndependentPerPlayer() {
        store.increment(p, 7, 5, 0L, 100);
        store.increment(p, 7, 5, 0L, 100);
        store.increment(p, 8, 5, 0L, 100);
        assertEquals(2, store.count(p, 7, 0L));
        assertEquals(1, store.count(p, 8, 0L)); // same player, different ability
        UUID q = UUID.randomUUID();
        assertEquals(0, store.count(q, 7, 0L)); // different player, same ability
    }

    @Test
    void maxBelowOneHoldsNoChargeAndDropsAnyPrior() {
        store.increment(p, 1, 5, 0L, 100); // count 1
        assertEquals(0, store.increment(p, 1, 0, 0L, 100)); // max 0 clamps to zero
        assertEquals(0, store.count(p, 1, 0L)); // and the prior charge is gone
    }

    @Test
    void nonPositiveTtlHoldsNoCharge() {
        assertEquals(0, store.increment(p, 1, 5, 0L, 0));
        assertEquals(0, store.increment(p, 1, 5, 0L, -10));
        assertEquals(0, store.count(p, 1, 0L));
    }

    @Test
    void resetDropsOneAbilityLeavingOthers() {
        store.increment(p, 1, 5, 0L, 100);
        store.increment(p, 2, 5, 0L, 100);
        store.reset(p, 1);
        assertEquals(0, store.count(p, 1, 0L));
        assertEquals(1, store.count(p, 2, 0L));
    }

    @Test
    void resetOfAbsentAbilityIsANoOp() {
        store.reset(p, 99); // no player map yet
        store.increment(p, 1, 5, 0L, 100);
        store.reset(p, 99); // map exists, ability does not
        assertEquals(1, store.count(p, 1, 0L));
    }

    @Test
    void clearForgetsOnePlayerAndClearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.increment(p, 1, 5, 0L, 100);
        store.increment(q, 1, 5, 0L, 100);
        store.clear(p);
        assertEquals(0, store.count(p, 1, 0L));
        assertEquals(1, store.count(q, 1, 0L));
        store.clearAll();
        assertEquals(0, store.count(q, 1, 0L));
    }
}
