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

    /** Every store must classify itself for the quit sweep — a new one CANNOT default into either partition. */
    @Test
    void everyComponentIsInExactlyOneQuitPartition() throws Exception {
        EngineStores fresh = EngineStores.fresh();
        for (RecordComponent c : EngineStores.class.getRecordComponents()) {
            Object value = c.getAccessor().invoke(fresh);
            boolean volatileMember = fresh.quitVolatile().stream().anyMatch(store -> store == value);
            boolean retainedMember = fresh.quitRetained().stream().anyMatch(store -> store == value);
            assertTrue(volatileMember ^ retainedMember,
                    c.getName() + " must be in EXACTLY ONE of quitVolatile / quitRetained");
        }
        // the two partitions together cover every store (nothing silently dropped from the sweep)
        assertEquals(fresh.all().size(), fresh.quitVolatile().size() + fresh.quitRetained().size());
        // the retained partition is exactly the RetainedStore-typed stores (the structural marker)
        assertTrue(fresh.quitRetained().stream().allMatch(store -> store instanceof RetainedStore));
    }

    @Test
    void quitSweepClearsVolatileButRetainsLiveCombatWindows() {
        UUID id = UUID.randomUUID();
        EngineStores s = EngineStores.fresh();

        // volatile state
        s.vars().set(id, "x", "1", 0L, 100);
        s.combo().hit(id, UUID.randomUUID(), 0L);
        // retained combat windows, all still LIVE at the sweep tick
        s.cooldowns().arm(id, CooldownStore.key(0, 1), 0L, 400);
        s.teleblock().block(id, 0L, 400);
        s.suppression().suppress(id, 5L, 0L, 400);
        // one already-ELAPSED cooldown that the sweep should shed
        s.cooldowns().arm(id, CooldownStore.key(0, 2), 0L, 40);

        s.quitSweep(id, 100L);

        assertNull(s.vars().get(id, "x", 100L));            // volatile cleared
        assertEquals(0, s.combo().current(id, 100L));       // volatile cleared
        // retained windows survive the relog, read at a LATER tick against the same monotonic clock
        assertFalse(s.cooldowns().ready(id, CooldownStore.key(0, 1), 200L));
        assertTrue(s.teleblock().isBlocked(id, 200L));
        assertTrue(s.suppression().isSuppressed(id, 5L, 200L));
        // the elapsed cooldown was dropped
        assertTrue(s.cooldowns().ready(id, CooldownStore.key(0, 2), 100L));
    }

    @Test
    void quitSweepClearsSuppressionImmunity() {
        UUID id = UUID.randomUUID();
        EngineStores s = EngineStores.fresh();
        s.suppression().setImmune(id, 100); // self-state re-derived from worn gear on rejoin
        assertTrue(s.suppression().isImmune(id));

        s.quitSweep(id, 100L);

        assertFalse(s.suppression().isImmune(id), "the quit sweep drops self-derived suppression immunity");
    }

    @Test
    void evictElapsedSweepsRetainedStoresAcrossPlayers() {
        UUID id = UUID.randomUUID();
        EngineStores s = EngineStores.fresh();
        s.cooldowns().arm(id, CooldownStore.key(0, 1), 0L, 40);  // elapses at 40
        s.teleblock().block(id, 0L, 400);                        // live to 400
        s.suppression().suppress(id, 5L, 0L, 40);                // elapses at 40

        s.evictElapsed(100L);

        assertTrue(s.cooldowns().ready(id, CooldownStore.key(0, 1), 100L));
        assertFalse(s.suppression().isSuppressed(id, 5L, 100L));
        assertTrue(s.teleblock().isBlocked(id, 100L)); // still live
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
        s.combo().hit(id, UUID.randomUUID(), 0L);
        s.why().record(id, 0L, 0, 7, 10, 0, 0);
        s.dotAmplify().amplify(id, 3.0, DotAmplifyStore.CAUSE_DOT, 0L, 100);
        s.headTrophies().arm(id, "n", "l", 0L);
        s.foodWindows().arm(id, FoodWindowStore.Type.SCALE_GAIN, 0L, 100, 2.0);
        s.foodWindows().arm(id, FoodWindowStore.Type.CANCEL_DRAIN, 0L, 100, 0.0);
        s.messageThrottle().tryEmit(id, 0L, 300);
        s.soulEscalation().step(id, CooldownStore.key(0, 1), 0L, 0);
        s.dotSuppression().suppress(id, DotSuppressionStore.CAUSE_WITHER, 0L, 100);
        s.rebounds().arm(id, 1, 4, 5.0, 0, 5);

        assertEquals("1", s.vars().get(id, "x", 0L));
        assertTrue(s.suppression().isSuppressed(id, 1L, 0L));
        assertFalse(Double.isNaN(s.knockback().multiplier(id, 0L)));
        assertTrue(s.keepOnDeath().shouldKeep(id, 0L));
        assertTrue(s.teleblock().isBlocked(id, 0L));
        assertTrue(s.immune().isImmune(id, ImmuneStore.Type.of(0), 0L));
        assertFalse(s.cooldowns().ready(id, CooldownStore.key(0, 1), 0L));
        assertEquals(1, s.combo().current(id, 0L));
        assertEquals(1, s.why().attempts(id).size());
        assertEquals(3.0, s.dotAmplify().factor(id, 0L, DotAmplifyStore.CAUSE_WITHER));
        assertEquals(2.0, s.foodWindows().gainFactor(id, 0L));
        assertTrue(s.foodWindows().cancelsDrain(id, 0L));
        assertFalse(s.messageThrottle().tryEmit(id, 0L, 300)); // armed by the emit above
        assertEquals(1, s.soulEscalation().steps(id, CooldownStore.key(0, 1), 0L, 0));
        assertTrue(s.dotSuppression().suppressed(id, 0L, DotSuppressionStore.CAUSE_WITHER));
        assertTrue(s.rebounds().armed(id));

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
        assertEquals(1.0, s.dotAmplify().factor(id, 0L, DotAmplifyStore.CAUSE_WITHER));
        // An unexpiring store has no other way to be freed, so a missed clear here would leak forever.
        assertNull(s.headTrophies().consume(id));
        assertEquals(1.0, s.foodWindows().gainFactor(id, 0L)); // 1 = unarmed, the neutral multiplier
        assertFalse(s.foodWindows().cancelsDrain(id, 0L));
        assertTrue(s.messageThrottle().tryEmit(id, 0L, 300));
        assertEquals(0, s.soulEscalation().steps(id, CooldownStore.key(0, 1), 0L, 0)); // back to the base price
        assertFalse(s.dotSuppression().suppressed(id, 0L, DotSuppressionStore.CAUSE_WITHER));
        // A worn marker with no expiry: a missed clear would outlive the armour that granted it.
        assertFalse(s.rebounds().armed(id));
    }
}
