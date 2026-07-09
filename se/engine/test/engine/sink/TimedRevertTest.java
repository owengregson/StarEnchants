package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * The {@link TimedRevert} registry contract (F07/F08): a token runs its revert at most once, the quit drain runs
 * every outstanding revert, and the two are mutually exclusive so a timer firing after a quit is a no-op.
 */
class TimedRevertTest {

    private final UUID player = UUID.randomUUID();

    @Test
    void runOnceRunsTheRevertExactlyOnce() {
        TimedRevert reverts = new TimedRevert();
        AtomicInteger runs = new AtomicInteger();
        long token = reverts.begin(player, runs::incrementAndGet);

        reverts.runOnce(player, token);
        reverts.runOnce(player, token); // second claim finds nothing — no double-run

        assertEquals(1, runs.get(), "the revert must run exactly once across repeated runOnce");
        assertTrue(reverts.isEmpty(), "claiming the last token must empty the registry");
    }

    @Test
    void revertAllRunsEveryOutstandingRevertAndEmptiesThePlayer() {
        TimedRevert reverts = new TimedRevert();
        AtomicInteger runs = new AtomicInteger();
        reverts.begin(player, runs::incrementAndGet);
        reverts.begin(player, runs::incrementAndGet);

        reverts.revertAll(player);

        assertEquals(2, runs.get(), "the quit drain must run every registered revert");
        assertTrue(reverts.isEmpty(), "the quit drain must empty the player");
    }

    @Test
    void runOnceAfterRevertAllIsANoOp() {
        TimedRevert reverts = new TimedRevert();
        AtomicInteger runs = new AtomicInteger();
        long token = reverts.begin(player, runs::incrementAndGet);

        reverts.revertAll(player); // the quit drain claims it first
        reverts.runOnce(player, token); // the late timer must not re-run it (the stale-offline-player case)

        assertEquals(1, runs.get(), "a timer firing after the quit drain must not run the revert again");
    }

    @Test
    void revertAllAfterRunOnceIsANoOp() {
        TimedRevert reverts = new TimedRevert();
        AtomicInteger runs = new AtomicInteger();
        long token = reverts.begin(player, runs::incrementAndGet);

        reverts.runOnce(player, token); // normal expiry
        reverts.revertAll(player); // a later quit finds nothing outstanding

        assertEquals(1, runs.get(), "the quit drain must not re-run an already-expired revert");
    }

    @Test
    void revertsAreIsolatedPerPlayer() {
        TimedRevert reverts = new TimedRevert();
        UUID other = UUID.randomUUID();
        AtomicInteger mine = new AtomicInteger();
        AtomicInteger theirs = new AtomicInteger();
        reverts.begin(player, mine::incrementAndGet);
        reverts.begin(other, theirs::incrementAndGet);

        reverts.revertAll(player);

        assertEquals(1, mine.get(), "the drained player's revert runs");
        assertEquals(0, theirs.get(), "another player's revert is untouched");
        assertFalse(reverts.isEmpty(), "the other player's revert is still outstanding");
    }

    @Test
    void clearDropsRevertsWithoutRunningThem() {
        TimedRevert reverts = new TimedRevert();
        AtomicInteger runs = new AtomicInteger();
        reverts.begin(player, runs::incrementAndGet);

        reverts.clear(player);

        assertEquals(0, runs.get(), "clear must not run the revert");
        assertTrue(reverts.isEmpty(), "clear must empty the player");
    }
}
