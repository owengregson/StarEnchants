package engine.stores;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeleblockStoreTest {

    private final TeleblockStore store = new TeleblockStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void blockedUntilExpiryThenReleased() {
        store.block(p, 100L, 40);
        assertTrue(store.isBlocked(p, 100L));
        assertTrue(store.isBlocked(p, 139L));
        assertFalse(store.isBlocked(p, 140L)); // half-open: the expiry tick is free
    }

    @Test
    void reBlockExtendsNeverShortens() {
        store.block(p, 0L, 100);
        store.block(p, 10L, 20); // would expire at 30 — must not shorten
        assertTrue(store.isBlocked(p, 50L));
        assertFalse(store.isBlocked(p, 100L));
    }

    @Test
    void evictElapsedKeepsALiveBlockAndDropsAnElapsedOne() {
        store.block(p, 0L, 200); // live to 200
        store.evictElapsed(p, 100L);
        assertTrue(store.isBlocked(p, 100L)); // retained — a relog can't shed a landed teleblock
        store.evictElapsed(p, 300L);
        assertFalse(store.isBlocked(p, 300L)); // elapsed → dropped
    }

    @Test
    void evictElapsedAllPlayersSweepsElapsedButKeepsLive() {
        UUID q = UUID.randomUUID();
        store.block(p, 0L, 40);   // expires at 40
        store.block(q, 0L, 200);  // expires at 200
        store.evictElapsed(100L);
        assertFalse(store.isBlocked(p, 100L));
        assertTrue(store.isBlocked(q, 100L));
    }

    @Test
    void clearForgetsALiveBlock() {
        store.block(p, 0L, 200);
        store.clear(p);
        assertFalse(store.isBlocked(p, 0L));
    }
}
