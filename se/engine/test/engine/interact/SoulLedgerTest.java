package engine.interact;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SoulLedgerTest {

    /** A simple in-memory balance; all access happens under the ledger's per-gem lock. */
    private static final class IntBalance implements SoulLedger.Balance {
        private int souls;

        IntBalance(int souls) {
            this.souls = souls;
        }

        @Override
        public int souls() {
            return souls;
        }

        @Override
        public void setSouls(int souls) {
            this.souls = souls;
        }
    }

    @Test
    void peekDoesNotSeedAndReportsAbsenceVersusZero() {
        SoulLedger ledger = new SoulLedger();
        UUID gem = UUID.randomUUID();
        // Untouched → empty (NOT zero), and peeking must not seed the authority.
        assertTrue(ledger.peek(gem).isEmpty());
        // After a touch, peek reports the live authority without re-seeding.
        ledger.deposit(gem, new IntBalance(0), 7);
        assertEquals(7, ledger.peek(gem).getAsInt());
        // A zero balance is distinguishable from absence.
        ledger.tryConsume(gem, new IntBalance(7), 7);
        assertEquals(0, ledger.peek(gem).getAsInt());
        ledger.forget(gem);
        assertTrue(ledger.peek(gem).isEmpty());
    }

    @Test
    void consumeDebitsWhenAffordable() {
        SoulLedger ledger = new SoulLedger();
        IntBalance gem = new IntBalance(10);
        assertTrue(ledger.tryConsume(UUID.randomUUID(), gem, 4));
        assertEquals(6, gem.souls());
    }

    @Test
    void consumeFailsAndLeavesBalanceUntouchedWhenTooPoor() {
        SoulLedger ledger = new SoulLedger();
        IntBalance gem = new IntBalance(3);
        assertFalse(ledger.tryConsume(UUID.randomUUID(), gem, 4));
        assertEquals(3, gem.souls()); // untouched
    }

    @Test
    void exactBalanceIsAffordable() {
        SoulLedger ledger = new SoulLedger();
        IntBalance gem = new IntBalance(5);
        assertTrue(ledger.tryConsume(UUID.randomUUID(), gem, 5));
        assertEquals(0, gem.souls());
    }

    @Test
    void nonPositiveCostIsFreeAndDebitsNothing() {
        SoulLedger ledger = new SoulLedger();
        IntBalance gem = new IntBalance(5);
        assertTrue(ledger.tryConsume(UUID.randomUUID(), gem, 0));
        assertTrue(ledger.tryConsume(UUID.randomUUID(), gem, -3));
        assertEquals(5, gem.souls());
    }

    @Test
    void depositCredits() {
        SoulLedger ledger = new SoulLedger();
        IntBalance gem = new IntBalance(5);
        ledger.deposit(UUID.randomUUID(), gem, 7);
        assertEquals(12, gem.souls());
    }

    @Test
    void inMemoryAuthorityPreventsDoubleSpendAcrossSeparateBackingSnapshots() {
        // Simulates two Folia region threads each holding their own per-thread PDC copy
        // ("snapshot") of the SAME gem: both read 1 soul. The ledger's in-memory authority
        // must make the second consume see the first's debit, so only one succeeds.
        SoulLedger ledger = new SoulLedger();
        UUID gemId = UUID.randomUUID();
        IntBalance snapshotA = new IntBalance(1);
        IntBalance snapshotB = new IntBalance(1);

        assertTrue(ledger.tryConsume(gemId, snapshotA, 1));  // loads 1, debits to 0
        assertFalse(ledger.tryConsume(gemId, snapshotB, 1)); // authority says 0, stale snapshot ignored
        assertEquals(0, snapshotA.souls());
        assertEquals(1, snapshotB.souls()); // failed consume left snapshot B untouched
    }

    @Test
    void forgetDropsTheAuthoritySoItReloadsFromBacking() {
        SoulLedger ledger = new SoulLedger();
        UUID gemId = UUID.randomUUID();
        IntBalance gem = new IntBalance(5);
        ledger.tryConsume(gemId, gem, 5); // authority now 0, backing 0
        ledger.forget(gemId);
        IntBalance refreshed = new IntBalance(8); // gem re-fed offline, fresh backing
        assertEquals(8, ledger.balance(gemId, refreshed)); // reloads from backing after forget
    }

    @Test
    void distinctGemsDoNotContend() {
        SoulLedger ledger = new SoulLedger();
        IntBalance a = new IntBalance(5);
        IntBalance b = new IntBalance(5);
        assertTrue(ledger.tryConsume(UUID.randomUUID(), a, 5));
        assertTrue(ledger.tryConsume(UUID.randomUUID(), b, 5));
        assertEquals(0, a.souls());
        assertEquals(0, b.souls());
    }

    @Test
    void noDoubleSpendUnderConcurrency() throws InterruptedException {
        SoulLedger ledger = new SoulLedger();
        UUID gemId = UUID.randomUUID();
        IntBalance gem = new IntBalance(100);
        int threads = 16;
        int attemptsPerThread = 50; // 800 attempts for 100 souls

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < attemptsPerThread; i++) {
                    if (ledger.tryConsume(gemId, gem, 1)) {
                        successes.incrementAndGet();
                    }
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

        // Exactly the available souls were spent — never more (no double-spend), never fewer.
        assertEquals(100, successes.get());
        assertEquals(0, gem.souls());
    }
}
