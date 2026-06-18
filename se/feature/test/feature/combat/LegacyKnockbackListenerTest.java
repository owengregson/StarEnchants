package feature.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import engine.stores.KnockbackControlStore;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins the legacy (destroystokyo) KNOCKBACK_CONTROL applier: a {@code multiplier <= 0} cancels the
 * event, {@code 0 < m} scales the acceleration vector in place, and no active flag leaves the event
 * untouched. The destroystokyo event is on the floor compile classpath, so it mocks directly.
 */
class LegacyKnockbackListenerTest {

    private static final LongSupplier NOW = () -> 10L;

    private EntityKnockbackByEntityEvent eventFor(UUID victimId, Vector acceleration) {
        EntityKnockbackByEntityEvent event = mock(EntityKnockbackByEntityEvent.class, RETURNS_DEEP_STUBS);
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getUniqueId()).thenReturn(victimId);
        when(event.getEntity()).thenReturn(victim);
        when(event.getAcceleration()).thenReturn(acceleration);
        return event;
    }

    @Test
    void cancelsOnZeroMultiplier() {
        KnockbackControlStore store = new KnockbackControlStore();
        UUID victim = UUID.randomUUID();
        store.control(victim, 0.0, 0L, 100);

        EntityKnockbackByEntityEvent event = eventFor(victim, new Vector(1, 1, 1));
        new LegacyKnockbackListener(store, NOW).onKnockback(event);

        verify(event).setCancelled(true);
    }

    @Test
    void scalesAccelerationOnPartialMultiplier() {
        KnockbackControlStore store = new KnockbackControlStore();
        UUID victim = UUID.randomUUID();
        store.control(victim, 0.5, 0L, 100);

        Vector acceleration = new Vector(2.0, 2.0, 2.0);
        EntityKnockbackByEntityEvent event = eventFor(victim, acceleration);
        new LegacyKnockbackListener(store, NOW).onKnockback(event);

        assertEquals(1.0, acceleration.getX(), 1.0e-9, "0.5× halves the launch in place");
        verify(event, never()).setCancelled(anyBoolean());
    }

    @Test
    void leavesEventUntouchedWithNoActiveControl() {
        KnockbackControlStore store = new KnockbackControlStore(); // nothing armed
        Vector acceleration = new Vector(3.0, 0.0, 0.0);
        EntityKnockbackByEntityEvent event = eventFor(UUID.randomUUID(), acceleration);

        new LegacyKnockbackListener(store, NOW).onKnockback(event);

        assertEquals(3.0, acceleration.getX(), 1.0e-9, "unscaled");
        verify(event, never()).setCancelled(anyBoolean());
    }
}
