package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Models the §C KNOCKBACK_CONTROL bridge from a hit to the separate knockback event the same tick: a
 * short-lived per-victim multiplier (clamped at zero, latest write wins).
 */
class KnockbackControlStoreTest {

    private final KnockbackControlStore store = new KnockbackControlStore();

    @Test
    void noControlReportsNone() {
        assertTrue(Double.isNaN(store.multiplier(UUID.randomUUID(), 0L)));
    }

    @Test
    void controlIsLiveWithinTtlThenEvicts() {
        UUID victim = UUID.randomUUID();
        store.control(victim, 0.0, 100L, 2); // expires at tick 102

        assertEquals(0.0, store.multiplier(victim, 100L));
        assertEquals(0.0, store.multiplier(victim, 101L));
        assertTrue(Double.isNaN(store.multiplier(victim, 102L)), "the expiry tick itself counts as elapsed");
        assertTrue(Double.isNaN(store.multiplier(victim, 103L)), "evicted lazily on the first elapsed read");
    }

    @Test
    void multiplierIsClampedAtZero() {
        UUID victim = UUID.randomUUID();
        store.control(victim, -3.0, 0L, 5);
        assertEquals(0.0, store.multiplier(victim, 0L), "a negative multiplier clamps to a full cancel");
    }

    @Test
    void latestWriteWins() {
        UUID victim = UUID.randomUUID();
        store.control(victim, 0.5, 0L, 10);
        store.control(victim, 2.0, 1L, 10); // a later proc this hit overrides
        assertEquals(2.0, store.multiplier(victim, 1L));
    }

    @Test
    void nonPositiveTtlIsNoOp() {
        UUID victim = UUID.randomUUID();
        store.control(victim, 0.0, 0L, 0);
        store.control(victim, 0.0, 0L, -5);
        assertTrue(Double.isNaN(store.multiplier(victim, 0L)));
    }

    @Test
    void clearAndClearAllForget() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.control(a, 0.0, 0L, 100);
        store.control(b, 0.0, 0L, 100);

        store.clear(a);
        assertTrue(Double.isNaN(store.multiplier(a, 0L)));
        assertEquals(0.0, store.multiplier(b, 0L));

        store.clearAll();
        assertTrue(Double.isNaN(store.multiplier(b, 0L)));
    }
}
