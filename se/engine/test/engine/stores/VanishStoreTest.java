package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The generation contract that lets a {@code VANISH} window end EARLY without racing its own expiry timer, and
 * the exactly-once restore that keeps a hidden body from being stranded on somebody's client. Nothing else
 * covers either: {@code VIEWER_HIDE} has no store at all.
 */
class VanishStoreTest {

    private static final UUID SUBJECT = UUID.randomUUID();

    @Test
    void theLastLandedHitClosesTheWindowAndTheStaleTimerThenNoOps() {
        VanishStore store = new VanishStore();
        AtomicInteger restores = new AtomicInteger();
        long seq = store.open(SUBJECT, 0L, 60, 2, restores::incrementAndGet);

        assertNull(store.spendHit(SUBJECT, 1L), "the first of two absorbed hits leaves the window standing");
        assertTrue(store.vanished(SUBJECT, 1L));

        VanishStore.Window exhausted = store.spendHit(SUBJECT, 2L);
        assertNotNull(exhausted, "the second hit is the one that breaks it");
        exhausted.restore().run();
        assertEquals(1, restores.get());
        assertFalse(store.vanished(SUBJECT, 2L));

        // The timer armed at open() still fires; presenting its now-stale seq must change nothing.
        assertNull(store.close(SUBJECT, seq));
        assertEquals(1, restores.get(), "the restore runs exactly once, whichever path got there first");
    }

    @Test
    void aReProcReplacesTheWindowSoTheOldTimerCannotEndTheNewOne() {
        VanishStore store = new VanishStore();
        AtomicInteger firstRestores = new AtomicInteger();
        AtomicInteger secondRestores = new AtomicInteger();
        long first = store.open(SUBJECT, 0L, 60, 1, firstRestores::incrementAndGet);

        // Re-proc while the first window is still live: fresh duration, fresh allowance, and the FIRST window's
        // restore runs now (its hide is subsumed) rather than being silently dropped.
        long second = store.open(SUBJECT, 10L, 60, 1, secondRestores::incrementAndGet);
        assertEquals(1, firstRestores.get(), "the replaced window is restored, not forgotten");

        assertNull(store.close(SUBJECT, first), "the superseded timer refuses to end the live window");
        assertTrue(store.vanished(SUBJECT, 11L));
        assertEquals(0, secondRestores.get());

        assertNotNull(store.close(SUBJECT, second), "the current generation's timer does end it");
    }

    @Test
    void breakHitsZeroMeansOnlyTheTimerEndsIt() {
        VanishStore store = new VanishStore();
        long seq = store.open(SUBJECT, 0L, 60, 0, () -> { });
        assertNull(store.spendHit(SUBJECT, 1L), "with no allowance there is nothing for a hit to spend");
        assertTrue(store.vanished(SUBJECT, 59L));
        assertNotNull(store.close(SUBJECT, seq));
    }

    @Test
    void aLapsedWindowFoundByAReaderIsRestoredRatherThanJustDropped() {
        // A timer that never fired (an unloaded region, a dropped task) would otherwise leave every other
        // client hiding a body standing in front of them.
        VanishStore store = new VanishStore();
        AtomicInteger restores = new AtomicInteger();
        store.open(SUBJECT, 0L, 60, 1, restores::incrementAndGet);
        assertFalse(store.vanished(SUBJECT, 60L), "the window lapses exactly at its expiry tick");
        assertEquals(1, restores.get());
        assertNull(store.spendHit(SUBJECT, 61L));
        assertEquals(1, restores.get(), "the lapse restores once, not once per reader");
    }

    @Test
    void quitRestoresBecauseAHiddenSetLivesOnTheWatchersConnections() {
        VanishStore store = new VanishStore();
        AtomicInteger restores = new AtomicInteger();
        store.open(SUBJECT, 0L, 60, 1, restores::incrementAndGet);
        store.clear(SUBJECT);
        assertEquals(1, restores.get());
        assertFalse(store.vanished(SUBJECT, 1L));
        store.clear(SUBJECT);
        assertEquals(1, restores.get(), "clearing an absent window restores nothing");
    }
}
