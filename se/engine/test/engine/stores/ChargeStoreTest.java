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
        assertEquals(3, store.increment(p, 1, 3, 30L, 100));
        assertEquals(3, store.count(p, 1, 30L));
    }

    @Test
    void slidingTtlKeepsAnActivelyTriggeredChargeAlive() {
        store.increment(p, 1, 5, 100L, 40);
        store.increment(p, 1, 5, 130L, 40); // re-trigger before expiry slides the TTL to 170
        assertEquals(2, store.count(p, 1, 150L)); // would have lapsed at the original 140
    }

    @Test
    void countAtExpiryTickIsLapsedAndEvicted() {
        store.increment(p, 1, 5, 100L, 40); // expiry 140
        assertEquals(1, store.count(p, 1, 139L));
        assertEquals(0, store.count(p, 1, 140L));
        assertEquals(0, store.count(p, 1, 139L)); // the lapsed read evicted it, so even a pre-expiry tick is now empty
    }

    @Test
    void incrementOnALapsedChargeRestartsFromZero() {
        store.increment(p, 1, 5, 100L, 40);
        store.increment(p, 1, 5, 110L, 40);
        assertEquals(1, store.increment(p, 1, 5, 200L, 40)); // incrementing after the window lapsed restarts the count, not resumes it
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
        store.increment(p, 1, 5, 0L, 100);
        assertEquals(0, store.increment(p, 1, 0, 0L, 100));
        assertEquals(0, store.count(p, 1, 0L)); // a max < 1 also drops any charge accrued before the cap dropped
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
