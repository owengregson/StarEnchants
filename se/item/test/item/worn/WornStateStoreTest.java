package item.worn;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the per-player worn-state store: refresh resolves + stores, get reads the stored
 * immutable snapshot, and remove/clear forget. The resolution function is injected, so this needs no
 * server — only a mocked entity for its UUID.
 */
class WornStateStoreTest {

    private static LivingEntity entityWithId(UUID id) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(id);
        return entity;
    }

    @Test
    void refreshStoresAndGetReadsTheSameInstance() {
        WornState worn = WornState.empty(5);
        WornStateStore store = new WornStateStore((entity, snapshot) -> worn);
        UUID id = UUID.randomUUID();

        assertNull(store.get(id));
        assertSame(worn, store.refresh(entityWithId(id), null));
        assertSame(worn, store.get(id));
    }

    @Test
    void removeAndClearForget() {
        WornStateStore store = new WornStateStore((entity, snapshot) -> WornState.empty(1));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.refresh(entityWithId(a), null);
        store.refresh(entityWithId(b), null);
        assertNotNull(store.get(a));
        assertNotNull(store.get(b));

        store.remove(a);
        assertNull(store.get(a));
        assertNotNull(store.get(b));

        store.clear();
        assertNull(store.get(b));
    }
}
