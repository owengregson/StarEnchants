package engine.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class SuppressionStoreTest {

    private final SuppressionStore store = new SuppressionStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void unsuppressedIdIsNotSuppressed() {
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void suppressedIdIsSuppressedUntilExpiry() {
        store.suppress(p, 1, 100L, 40);
        assertTrue(store.isSuppressed(p, 1, 100L));
        assertTrue(store.isSuppressed(p, 1, 139L));
        assertFalse(store.isSuppressed(p, 1, 140L)); // expiry tick is no longer suppressed
    }

    @Test
    void idsAreIndependent() {
        store.suppress(p, 7, 0L, 100);
        assertTrue(store.isSuppressed(p, 7, 0L));
        assertFalse(store.isSuppressed(p, 8, 0L));
    }

    @Test
    void playersAreIndependent() {
        UUID q = UUID.randomUUID();
        store.suppress(p, 1, 0L, 100);
        assertTrue(store.isSuppressed(p, 1, 0L));
        assertFalse(store.isSuppressed(q, 1, 0L));
    }

    @Test
    void zeroOrNegativeDurationSuppressesNothing() {
        store.suppress(p, 1, 0L, 0);
        store.suppress(p, 1, 0L, -5);
        assertFalse(store.isSuppressed(p, 1, 0L));
    }

    @Test
    void reSuppressingExtendsButNeverShortens() {
        store.suppress(p, 1, 0L, 100); // expires at 100
        store.suppress(p, 1, 10L, 20); // would expire at 30 — must not shorten
        assertTrue(store.isSuppressed(p, 1, 50L)); // still covered by the longer one
        assertFalse(store.isSuppressed(p, 1, 100L)); // original expiry holds

        store.suppress(p, 1, 0L, 100); // re-arm to expire at 100
        store.suppress(p, 1, 0L, 200); // longer — must extend to 200
        assertTrue(store.isSuppressed(p, 1, 150L));
        assertFalse(store.isSuppressed(p, 1, 200L));
    }

    @Test
    void elapsedSuppressionIsEvictedLazily() {
        store.suppress(p, 1, 0L, 10);
        assertFalse(store.isSuppressed(p, 1, 10L)); // elapsed: evicted on access
        // After eviction the id reads clean and a fresh suppression still applies.
        assertFalse(store.isSuppressed(p, 1, 100L));
        store.suppress(p, 1, 100L, 5);
        assertTrue(store.isSuppressed(p, 1, 100L));
    }

    @Test
    void clearForgetsOnePlayerAndClearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.suppress(p, 1, 0L, 100);
        store.suppress(q, 1, 0L, 100);
        store.clear(p);
        assertFalse(store.isSuppressed(p, 1, 0L));
        assertTrue(store.isSuppressed(q, 1, 0L));
        store.clearAll();
        assertFalse(store.isSuppressed(q, 1, 0L));
    }
}
