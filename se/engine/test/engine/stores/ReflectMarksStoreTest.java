package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The ADR-0049 Hex reflect mark: expiry, and the monotone "stronger fraction / later expiry" re-mark merge. */
class ReflectMarksStoreTest {

    private final ReflectMarksStore store = new ReflectMarksStore();

    /** The percent of a live window, or {@code 0} when none — the shape every expiry assertion below reads. */
    private double percent(UUID afflicted, long nowTicks) {
        ReflectMarksStore.Mark mark = store.active(afflicted, nowTicks);
        return mark == null ? 0.0 : mark.fractionPercent();
    }

    @Test
    void activeWithinWindowThenElapses() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 25.0, 0, "", 0L, 80);
        assertEquals(25.0, percent(afflicted, 79L));
        assertNull(store.active(afflicted, 80L), "the expiry tick counts as elapsed (half-open)");
    }

    @Test
    void reMarkKeepsStrongerFractionAndLaterExpiryComponentWise() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 25.0, 0, "", 0L, 80);  // strong, expires at 80
        store.mark(afflicted, 10.0, 0, "", 0L, 160); // weaker but longer — extends the window, keeps the strong fraction
        assertEquals(25.0, percent(afflicted, 120L), "the stronger fraction survives");
        assertEquals(25.0, percent(afflicted, 159L), "and the later expiry survives");
        assertEquals(0.0, percent(afflicted, 160L));
    }

    @Test
    void reMarkKeepsTheLooserCapAndTheSpeakingFeedback() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 25.0, 5.0, "hexed for {damage}", 0L, 80);
        store.mark(afflicted, 25.0, 9.0, "", 0L, 80); // a silent re-mark must not mute the armed line
        ReflectMarksStore.Mark merged = store.active(afflicted, 0L);
        assertNotNull(merged);
        assertEquals(9.0, merged.cap(), "the larger ceiling is the stronger window");
        assertEquals("hexed for {damage}", merged.feedback());

        store.mark(afflicted, 25.0, 0, "", 0L, 80); // uncapped: the loosest ceiling of all, not the tightest
        assertEquals(0.0, store.active(afflicted, 0L).cap());
    }

    @Test
    void nonPositiveFractionOrDurationIsNoOp() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 0.0, 0, "", 0L, 80);
        store.mark(afflicted, 25.0, 0, "", 0L, 0);
        assertEquals(0.0, percent(afflicted, 0L));
    }

    @Test
    void evictElapsedDropsOnlyExpired() {
        UUID live = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        store.mark(live, 25.0, 0, "", 0L, 200);
        store.mark(stale, 25.0, 0, "", 0L, 40);
        store.evictElapsed(100L);
        assertEquals(25.0, percent(live, 100L));
        assertEquals(0.0, percent(stale, 100L));
    }

    @Test
    void clearForgets() {
        UUID afflicted = UUID.randomUUID();
        store.mark(afflicted, 25.0, 0, "", 0L, 80);
        store.clear(afflicted);
        assertEquals(0.0, percent(afflicted, 0L));
    }
}
