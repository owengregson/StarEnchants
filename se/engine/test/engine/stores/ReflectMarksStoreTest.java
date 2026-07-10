package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The ADR-0049 Hex reflect mark: expiry, and the monotone "stronger fraction / later expiry" re-mark merge. */
class ReflectMarksStoreTest {

    private final ReflectMarksStore store = new ReflectMarksStore();

    @Test
    void activeWithinWindowThenElapses() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 25.0, 0L, 80);
        assertEquals(25.0, store.active(afflicted, 79L));
        assertEquals(0.0, store.active(afflicted, 80L), "the expiry tick counts as elapsed (half-open)");
    }

    @Test
    void reMarkKeepsStrongerFractionAndLaterExpiryComponentWise() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 25.0, 0L, 80);  // strong, expires at 80
        store.mark(afflicted, 10.0, 0L, 160); // weaker but longer — extends the window, keeps the strong fraction
        assertEquals(25.0, store.active(afflicted, 120L), "the stronger fraction survives");
        assertEquals(25.0, store.active(afflicted, 159L), "and the later expiry survives");
        assertEquals(0.0, store.active(afflicted, 160L));
    }

    @Test
    void nonPositiveFractionOrDurationIsNoOp() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 0.0, 0L, 80);
        store.mark(afflicted, 25.0, 0L, 0);
        assertEquals(0.0, store.active(afflicted, 0L));
    }

    @Test
    void evictElapsedDropsOnlyExpired() {
        UUID live = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        store.mark(live, 25.0, 0L, 200);
        store.mark(stale, 25.0, 0L, 40);
        store.evictElapsed(100L);
        assertEquals(25.0, store.active(live, 100L));
        assertEquals(0.0, store.active(stale, 100L));
    }

    @Test
    void clearForgets() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 25.0, 0L, 80);
        store.clear(afflicted);
        assertEquals(0.0, store.active(afflicted, 0L));
    }
}
