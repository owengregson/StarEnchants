package engine.stores;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pins the aggregate's structural cleanup guarantee: every component is a swept {@link PlayerScoped} store. */
class EngineStoresTest {

    @Test
    void everyComponentIsPlayerScopedAndSwept() throws Exception {
        EngineStores fresh = EngineStores.fresh();
        for (RecordComponent c : EngineStores.class.getRecordComponents()) {
            assertTrue(PlayerScoped.class.isAssignableFrom(c.getType()),
                    c.getName() + " must implement PlayerScoped");
            Object value = c.getAccessor().invoke(fresh);
            assertTrue(fresh.all().stream().anyMatch(store -> store == value),
                    c.getName() + " must be present in all()");
        }
        assertEquals(EngineStores.class.getRecordComponents().length, fresh.all().size());
    }

    @Test
    void clearAllFreesEveryStore() {
        UUID id = UUID.randomUUID();
        EngineStores s = EngineStores.fresh();

        s.vars().set(id, "x", "1", 0L, 100);
        s.suppression().suppress(id, 1L, 0L, 100);
        s.knockback().control(id, 0.5, 0L, 100);
        s.keepOnDeath().keep(id, 0L, 100);
        s.teleblock().block(id, 0L, 100);
        s.immune().immune(id, ImmuneStore.Type.of(0), 0L, 100);
        s.cooldowns().arm(id, CooldownStore.key(0, 1), 0L, 100);
        s.combo().hit(id, 0L);
        s.why().record(id, 0L, 0, 7, 10, 0, 0);

        assertEquals("1", s.vars().get(id, "x", 0L));
        assertTrue(s.suppression().isSuppressed(id, 1L, 0L));
        assertFalse(Double.isNaN(s.knockback().multiplier(id, 0L)));
        assertTrue(s.keepOnDeath().shouldKeep(id, 0L));
        assertTrue(s.teleblock().isBlocked(id, 0L));
        assertTrue(s.immune().isImmune(id, ImmuneStore.Type.of(0), 0L));
        assertFalse(s.cooldowns().ready(id, CooldownStore.key(0, 1), 0L));
        assertEquals(1, s.combo().current(id, 0L));
        assertEquals(1, s.why().attempts(id).size());

        s.clearAll(id);

        assertNull(s.vars().get(id, "x", 0L));
        assertFalse(s.suppression().isSuppressed(id, 1L, 0L));
        assertTrue(Double.isNaN(s.knockback().multiplier(id, 0L)));
        assertFalse(s.keepOnDeath().shouldKeep(id, 0L));
        assertFalse(s.teleblock().isBlocked(id, 0L));
        assertFalse(s.immune().isImmune(id, ImmuneStore.Type.of(0), 0L));
        assertTrue(s.cooldowns().ready(id, CooldownStore.key(0, 1), 0L));
        assertEquals(0, s.combo().current(id, 0L));
        assertTrue(s.why().attempts(id).isEmpty());
    }
}
