package engine.interact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The per-player cross-gem soul authority ({@link SoulPool}): the spend invariant {@code available == physical
 * − pending}, all-or-nothing spends, and no over-spend under concurrency (the no-double-spend contract
 * re-homed from the removed {@code SoulLedgerTest}).
 */
class SoulPoolTest {

    @Test
    void notActiveUntilEnabledAndDroppedOnDisable() {
        SoulPool pool = new SoulPool();
        UUID p = UUID.randomUUID();
        assertFalse(pool.isActive(p));
        assertFalse(pool.trySpend(p, 5), "a not-in-soul-mode player can never spend");
        assertEquals(0, pool.total(p));

        pool.enable(p, 100);
        assertTrue(pool.isActive(p));
        assertEquals(100, pool.total(p));

        pool.disable(p);
        assertFalse(pool.isActive(p));
        assertEquals(0, pool.total(p));
    }

    @Test
    void trySpendDebitsAvailableAndRaisesPendingWhenAffordable() {
        SoulPool pool = new SoulPool();
        UUID p = UUID.randomUUID();
        pool.enable(p, 10);

        assertTrue(pool.trySpend(p, 3));
        assertEquals(7, pool.total(p), "available drops by the spend");
        assertEquals(3, pool.takePending(p), "the spend is owed as pending until drained");
        assertEquals(0, pool.takePending(p), "takePending resets to zero");
    }

    @Test
    void trySpendIsAllOrNothingWhenUnaffordable() {
        SoulPool pool = new SoulPool();
        UUID p = UUID.randomUUID();
        pool.enable(p, 2);
        assertFalse(pool.trySpend(p, 3), "cannot spend more than available");
        assertEquals(2, pool.total(p), "a failed spend changes nothing");
        assertEquals(0, pool.takePending(p));
    }

    @Test
    void resyncReestablishesAvailableFromPhysicalMinusPending() {
        SoulPool pool = new SoulPool();
        UUID p = UUID.randomUUID();
        pool.enable(p, 10);
        pool.trySpend(p, 4); // available 6, pending 4 (physical still 10 — drain not yet flushed)

        // a tick before the drain lands: physical 10, pending 4 → available must be 6 (not clobbered back to 10)
        pool.resync(p, 10);
        assertEquals(6, pool.total(p));

        // the drain lands (4 souls leave the gems) and pending is taken → physical 6, pending 0 → available 6
        pool.takePending(p);
        pool.resync(p, 6);
        assertEquals(6, pool.total(p));
    }

    @Test
    void resyncCorrectsForExternalPickupAndDrop() {
        SoulPool pool = new SoulPool();
        UUID p = UUID.randomUUID();
        pool.enable(p, 100);

        pool.resync(p, 250); // picked up a 150-soul gem → spendable rises
        assertEquals(250, pool.total(p));

        pool.resync(p, 40); // dropped gems → spendable falls (never manufactured)
        assertEquals(40, pool.total(p));
    }

    @Test
    void noOverSpendUnderConcurrency() throws InterruptedException {
        SoulPool pool = new SoulPool();
        UUID p = UUID.randomUUID();
        pool.enable(p, 100);

        int threads = 16;
        int attemptsEach = 50;
        AtomicInteger spent = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < attemptsEach; i++) {
                        if (pool.trySpend(p, 1)) {
                            spent.incrementAndGet();
                        }
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();

        assertEquals(100, spent.get(), "exactly the 100 available souls may be spent — never more");
        assertEquals(0, pool.total(p));
        assertEquals(100, pool.takePending(p), "every spent soul is owed exactly once as pending");
    }
}
