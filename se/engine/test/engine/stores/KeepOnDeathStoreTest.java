package engine.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins {@link KeepOnDeathStore}: a flag is armed within its TTL (lazy-evicted on read), re-arming
 * extends the window, and clear/clearAll forget it — the §C KEEP_ON_DEATH "while worn" flag re-armed each
 * tick by a REPEATING ability and lapsing after unequip.
 */
class KeepOnDeathStoreTest {

    private final KeepOnDeathStore store = new KeepOnDeathStore();

    @Test
    void unarmedReportsFalse() {
        assertFalse(store.shouldKeep(UUID.randomUUID(), 0L));
    }

    @Test
    void armedWithinTtlThenEvicts() {
        UUID player = UUID.randomUUID();
        store.keep(player, 100L, 200); // expires at 300

        assertTrue(store.shouldKeep(player, 100L));
        assertTrue(store.shouldKeep(player, 299L));
        assertFalse(store.shouldKeep(player, 300L), "the expiry tick itself counts as elapsed");
        assertFalse(store.shouldKeep(player, 301L), "evicted lazily on the first elapsed read");
    }

    @Test
    void reArmExtendsTheWindow() {
        UUID player = UUID.randomUUID();
        store.keep(player, 0L, 100);   // expires at 100
        store.keep(player, 50L, 100);  // re-armed: expires at 150 (the later expiry wins)
        assertTrue(store.shouldKeep(player, 120L), "a later re-arm extends past the original expiry");
        store.keep(player, 60L, 10);   // a shorter fresh arm (expiry 70) must NOT cut the window short
        assertTrue(store.shouldKeep(player, 120L));
    }

    @Test
    void nonPositiveTtlIsNoOp() {
        UUID player = UUID.randomUUID();
        store.keep(player, 0L, 0);
        store.keep(player, 0L, -5);
        assertFalse(store.shouldKeep(player, 0L));
    }

    @Test
    void clearAndClearAllForget() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.keep(a, 0L, 100);
        store.keep(b, 0L, 100);

        store.clear(a);
        assertFalse(store.shouldKeep(a, 0L));
        assertTrue(store.shouldKeep(b, 0L));

        store.clearAll();
        assertFalse(store.shouldKeep(b, 0L));
    }
}
