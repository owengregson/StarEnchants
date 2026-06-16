package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;

/**
 * Mock-host tests for the {@link DispatchSink} (docs/architecture.md §3.6), with no server: a
 * {@link SyncSchedulerBackend} runs deferred batches inline and mocked entities record the emitted
 * mutations. They pin the policy — the damage-fold + cancel feedback is synchronous, every world
 * mutation is deferred to the flush and routed to its owning thread (NEVER inlined on the firing
 * thread, since the target may be a different entity/region), and the per-entity batch preserves
 * emission order. The handle-using intents + the genuine cross-region hop are pinned LIVE.
 */
class DispatchSinkTest {

    private RuntimeHandles handles;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
    }

    @Test
    void contributesToTheDamageFoldSynchronously() {
        DispatchSink sink = new DispatchSink(handles);
        sink.addOutgoingDamage(1.0);   // +100%
        sink.addFlatDamage(5.0);       // +5 flat, after the multiplier
        sink.addDamageReduction(0.5);  // -50% incoming
        sink.addFlatReduction(2.0);    // -2 flat, last

        // (10 x (1 + 1.0) + 5) x (1 - 0.5) - 2 = 25 x 0.5 - 2 = 10.5
        assertEquals(10.5, sink.fold().apply(10.0), 1e-9);
    }

    @Test
    void cancelEventSetsTheReadBackFlag() {
        DispatchSink sink = new DispatchSink(handles);
        assertFalse(sink.cancelled(), "a fresh sink must not be cancelled");
        sink.cancelEvent();
        assertTrue(sink.cancelled(), "cancelEvent must set the read-back flag");
    }

    @Test
    void worldMutationsAreDeferredUntilFlush() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.ignite(target, 60);
        verifyNoInteractions(target); // captured, never applied inline on the firing thread

        sink.flush();
        verify(target).setFireTicks(60);
    }

    @Test
    void batchesIntentsForTheSameEntityInEmissionOrder() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.ignite(target, 60);
        sink.extinguish(target);
        verifyNoInteractions(target);

        sink.flush();
        InOrder order = inOrder(target);
        order.verify(target).setFireTicks(60);
        order.verify(target).setFireTicks(0);
    }

    @Test
    void deferredDamageHopsToTheEntityOnFlush() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.damage(target, 7.5);
        verifyNoInteractions(target);

        sink.flush();
        verify(target).damage(7.5);
    }

    @Test
    void deferredTeleportRoutesThroughTheEntityScheduler() {
        Entity target = mock(Entity.class);
        Location to = mock(Location.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.teleport(target, to);
        verifyNoInteractions(target);

        sink.flush();
        verify(target).teleportAsync(to);
    }

    @Test
    void flushIsIdempotent() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.ignite(target, 40);

        sink.flush();
        sink.flush(); // a second flush must not re-apply the batch
        verify(target).setFireTicks(40);
    }
}
