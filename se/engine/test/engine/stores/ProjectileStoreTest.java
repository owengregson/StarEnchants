package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectileStoreTest {

    private final ProjectileStore<String> store = new ProjectileStore<>();
    private final UUID a = UUID.randomUUID();

    @Test
    void putThenGetRoundTrips() {
        store.put(a, "payload", 100L, 40);
        assertTrue(store.get(a, 100L).isPresent());
        assertEquals("payload", store.get(a, 100L).get());
    }

    @Test
    void absentProjectileIsEmpty() {
        assertTrue(store.get(a, 0L).isEmpty());
        assertTrue(store.remove(a).isEmpty());
    }

    @Test
    void entryExpiresExactlyAtExpiryTick() {
        store.put(a, "payload", 100L, 40);
        assertTrue(store.get(a, 139L).isPresent());
        assertTrue(store.get(a, 140L).isEmpty());
    }

    @Test
    void elapsedEntryIsEvictedLazilyOnGet() {
        store.put(a, "payload", 100L, 40);
        store.get(a, 140L); // an elapsed read evicts the entry as a side effect (its result is unused)
        assertTrue(store.remove(a).isEmpty());
    }

    @Test
    void nonPositiveTtlStoresWithNoExpiry() {
        store.put(a, "payload", 0L, 0);
        assertTrue(store.get(a, Long.MAX_VALUE - 1).isPresent());
        UUID b = UUID.randomUUID();
        store.put(b, "payload", 0L, -5);
        assertTrue(store.get(b, Long.MAX_VALUE - 1).isPresent());
    }

    @Test
    void projectilesAreIndependent() {
        UUID b = UUID.randomUUID();
        store.put(a, "arrow-a", 0L, 100);
        store.put(b, "arrow-b", 0L, 100);
        assertEquals("arrow-a", store.get(a, 0L).get());
        assertEquals("arrow-b", store.get(b, 0L).get());
        store.remove(a);
        assertTrue(store.get(a, 0L).isEmpty());
        assertTrue(store.get(b, 0L).isPresent());
    }

    @Test
    void putOverwritesExistingEntry() {
        store.put(a, "first", 0L, 100);
        store.put(a, "second", 0L, 100);
        assertEquals("second", store.get(a, 0L).get());
    }

    @Test
    void removeReturnsDataAndDeletesEntry() {
        store.put(a, "payload", 0L, 100);
        assertEquals("payload", store.remove(a).get());
        assertTrue(store.get(a, 0L).isEmpty());
        assertTrue(store.remove(a).isEmpty()); // second removal is a no-op
    }

    @Test
    void removeReturnsDataEvenWhenElapsed() {
        store.put(a, "payload", 100L, 40);
        assertEquals("payload", store.remove(a).get()); // resolving consumes its own entry
    }

    @Test
    void clearAllForgetsEveryProjectile() {
        UUID b = UUID.randomUUID();
        store.put(a, "arrow-a", 0L, 100);
        store.put(b, "arrow-b", 0L, 100);
        store.clearAll();
        assertTrue(store.get(a, 0L).isEmpty());
        assertTrue(store.get(b, 0L).isEmpty());
    }
}
