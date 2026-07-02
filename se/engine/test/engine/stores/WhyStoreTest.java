package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

/** Per-player ring lifecycle + generation stamping for the flight recorder (ADR-0045). */
class WhyStoreTest {

    private final WhyStore store = new WhyStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void unknownPlayerHasNoAttempts() {
        assertTrue(store.attempts(UUID.randomUUID()).isEmpty());
    }

    @Test
    void recordCreatesTheRingLazilyAndReturnsIt() {
        store.record(p, 5L, 1, 42, 10, 7, 8);
        List<WhyRing.Attempt> out = store.attempts(p);
        assertEquals(1, out.size());
        WhyRing.Attempt a = out.get(0);
        assertEquals(42, a.defId());
        assertEquals(10, a.verdict());
        assertEquals(1, a.triggerId());
        assertEquals(7, a.pA());
        assertEquals(8, a.pB());
    }

    @Test
    void generationIsStampedAndAffectsOnlySubsequentRecords() {
        store.generation(7);
        store.record(p, 0L, 0, 1, 0, 0, 0);
        store.generation(9);
        store.record(p, 0L, 0, 2, 0, 0, 0);

        List<WhyRing.Attempt> out = store.attempts(p); // newest first
        assertEquals(9, out.get(0).gen());
        assertEquals(7, out.get(1).gen());
    }

    @Test
    void clearFreesOnePlayerAndClearAllFreesEveryone() {
        UUID q = UUID.randomUUID();
        store.record(p, 0L, 0, 1, 0, 0, 0);
        store.record(q, 0L, 0, 1, 0, 0, 0);

        store.clear(p);
        assertTrue(store.attempts(p).isEmpty());
        assertEquals(1, store.attempts(q).size());

        store.clearAll();
        assertTrue(store.attempts(q).isEmpty());
    }

    @Test
    void concurrentWritersClaimUniqueSlotsAndEveryRowIsConsistent() throws Exception {
        int threads = 8;
        int perThread = 64;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                await(start);
                for (int i = 0; i < perThread; i++) {
                    store.record(p, i, 1, i, 10, i, i);
                }
                done.countDown();
            });
            worker.start();
        }
        start.countDown();
        done.await();

        List<WhyRing.Attempt> out = store.attempts(p);
        assertEquals(Math.min(threads * perThread, WhyRing.SIZE), out.size());
        for (WhyRing.Attempt a : out) {
            assertTrue(a.verdict() == 10, "each recorded verdict survives packing intact");
        }
        // Claim uniqueness: no two surviving rows share a sequence number.
        long distinct = out.stream().map(WhyRing.Attempt::seq).distinct().count();
        assertEquals(out.size(), distinct);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
