package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CooldownStoreTest {

    private final CooldownStore store = new CooldownStore();
    private final UUID p = UUID.randomUUID();

    @Test
    void unarmedScopeIsReady() {
        assertTrue(store.ready(p, 1L, 0L));
        assertEquals(0L, store.remainingTicks(p, 1L, 0L));
    }

    @Test
    void armedScopeIsNotReadyUntilExpiry() {
        store.arm(p, 1L, 100L, 40);
        assertFalse(store.ready(p, 1L, 100L));
        assertFalse(store.ready(p, 1L, 139L));
        assertEquals(1L, store.remainingTicks(p, 1L, 139L));
        assertTrue(store.ready(p, 1L, 140L)); // expiry tick is ready
    }

    @Test
    void scopesAreIndependent() {
        store.arm(p, CooldownStore.key(1, 7), 0L, 100);
        assertFalse(store.ready(p, CooldownStore.key(1, 7), 0L));
        assertTrue(store.ready(p, CooldownStore.key(2, 7), 0L)); // different kind, same id
        assertTrue(store.ready(p, CooldownStore.key(1, 8), 0L)); // same kind, different id
    }

    @Test
    void zeroOrNegativeDurationArmsNothing() {
        store.arm(p, 1L, 0L, 0);
        store.arm(p, 1L, 0L, -5);
        assertTrue(store.ready(p, 1L, 0L));
    }

    @Test
    void keyPackingIsCollisionFreeAcrossKindAndId() {
        assertEquals(CooldownStore.key(1, 7), CooldownStore.key(1, 7));
        org.junit.jupiter.api.Assertions.assertNotEquals(CooldownStore.key(1, 7), CooldownStore.key(2, 7));
        org.junit.jupiter.api.Assertions.assertNotEquals(CooldownStore.key(1, 7), CooldownStore.key(1, 8));
    }

    @Test
    void clearForgetsOnePlayerAndClearAllForgetsEveryone() {
        UUID q = UUID.randomUUID();
        store.arm(p, 1L, 0L, 100);
        store.arm(q, 1L, 0L, 100);
        store.clear(p);
        assertTrue(store.ready(p, 1L, 0L));
        assertFalse(store.ready(q, 1L, 0L));
        store.clearAll();
        assertTrue(store.ready(q, 1L, 0L));
    }
}
