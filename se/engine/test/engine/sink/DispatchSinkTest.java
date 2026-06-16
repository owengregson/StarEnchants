package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import compile.model.Affinity;
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
 * Mock-host tests for the {@link DispatchSink} routing policy (docs/architecture.md §3.6), with no
 * server: a {@link SyncSchedulerBackend} runs deferred batches inline and mocked entities record
 * the emitted mutations. These pin the version-agnostic policy — inline-vs-deferred, per-entity
 * batching + order, and the inline-feedback fold/cancel read-back. The handle-using intents (potion,
 * sound, spawn, …) and the genuine cross-region hop are pinned LIVE on the Paper+Folia matrix, where
 * a real {@link RuntimeHandles} resolves ids and a real scheduler crosses threads.
 */
class DispatchSinkTest {

    private RuntimeHandles handles;

    @BeforeEach
    void setUp() {
        // Resolution is never exercised here (handle-free intents only), so a resolver with no live
        // registry behind it is fine; construction touches no Bukkit API.
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
    }

    @Test
    void contributesToTheDamageFoldSynchronously() {
        DispatchSink sink = new DispatchSink(handles);
        sink.addOutgoingDamage(1.0);   // +100%
        sink.addFlatDamage(5.0);       // +5 flat, after the multiplier
        sink.addDamageReduction(0.5);  // −50% incoming
        sink.addFlatReduction(2.0);    // −2 flat, last

        // (10 × (1 + 1.0) + 5) × (1 − 0.5) − 2 = (25) × 0.5 − 2 = 10.5
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
    void contextLocalAppliesInlineWithoutFlush() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.affinity(Affinity.CONTEXT_LOCAL);
        sink.ignite(target, 60);

        // Zero-hop: a CONTEXT_LOCAL mutation runs immediately on the firing thread — before flush.
        verify(target).setFireTicks(60);
    }

    @Test
    void deferredIntentWaitsForFlush() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.affinity(Affinity.TARGET_ENTITY);
        sink.ignite(target, 60);
        verifyNoInteractions(target); // captured, not yet applied

        sink.flush();
        verify(target).setFireTicks(60);
    }

    @Test
    void batchesIntentsForTheSameEntityInEmissionOrder() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.affinity(Affinity.TARGET_ENTITY);
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
        sink.affinity(Affinity.AOE); // any wider-than-local affinity defers
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
        sink.affinity(Affinity.TARGET_ENTITY);
        sink.teleport(target, to);
        verifyNoInteractions(target);

        sink.flush();
        verify(target).teleportAsync(to);
    }

    @Test
    void flushIsIdempotent() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.affinity(Affinity.TARGET_ENTITY);
        sink.ignite(target, 40);

        sink.flush();
        sink.flush(); // a second flush must not re-apply the batch
        verify(target).setFireTicks(40);
    }
}
