package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;

/**
 * WAIT-tier tests for the {@link DispatchSink} (docs/architecture.md §3.6). A
 * {@link RecordingSchedulerBackend} runs immediate hops inline but CAPTURES every delayed hop with its
 * tick count, so these pin the policy: a delay-0 intent dispatches on flush; a delay&gt;0 intent is held
 * for exactly its tick count and runs only when that timer fires; distinct {@code WAIT} tiers schedule
 * independently; and inline feedback (the damage fold) ignores the delay because it cannot defer onto a
 * spent event. The Folia-correctness of the per-owner hop itself is the same matrix-verified path the
 * immediate tests use — these add only the deferral.
 */
class DispatchSinkWaitTest {

    private RuntimeHandles handles;
    private RecordingSchedulerBackend backend;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
    }

    @Test
    void delayZeroIntentsDispatchImmediatelyOnFlush() {
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.delay(0);
        sink.ignite(target, 60);
        sink.flush();

        verify(target).setFireTicks(60);
        assertTrue(backend.delayed.isEmpty(), "a delay-0 intent must not schedule a delayed task");
    }

    @Test
    void delayedIntentsAreHeldUntilTheirTickThenRun() {
        LivingEntity immediate = mock(LivingEntity.class);
        LivingEntity later = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.delay(0);
        sink.ignite(immediate, 60);
        sink.delay(40);
        sink.ignite(later, 80);
        sink.flush();

        verify(immediate).setFireTicks(60);  // the tier-0 effect ran on flush
        verifyNoInteractions(later);          // the tier-40 effect is captured, not yet applied
        assertEquals(1, backend.delayed.size(), "exactly one delayed batch scheduled");
        assertEquals(40L, backend.delayed.get(0).delayTicks(), "scheduled for the accumulated WAIT");

        backend.runDelayed();
        verify(later).setFireTicks(80);       // fires only when its timer lands
    }

    @Test
    void distinctDelayTiersScheduleIndependentlyInOrder() {
        LivingEntity a = mock(LivingEntity.class);
        LivingEntity b = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.delay(20);
        sink.ignite(a, 10);
        sink.delay(60);
        sink.ignite(b, 10);
        sink.flush();

        verifyNoInteractions(a, b);
        assertEquals(2, backend.delayed.size(), "two WAIT tiers ⇒ two delayed batches");
        assertEquals(20L, backend.delayed.get(0).delayTicks());
        assertEquals(60L, backend.delayed.get(1).delayTicks());

        backend.runDelayed();
        verify(a).setFireTicks(10);
        verify(b).setFireTicks(10);
    }

    @Test
    void changingTheDelayBackToZeroResumesImmediateDispatch() {
        LivingEntity delayed = mock(LivingEntity.class);
        LivingEntity instant = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles);
        sink.delay(40);
        sink.ignite(delayed, 10);
        sink.delay(0); // a later effect with no WAIT accumulated before it
        sink.ignite(instant, 20);
        sink.flush();

        verify(instant).setFireTicks(20);     // dispatched immediately
        verifyNoInteractions(delayed);
        assertEquals(1, backend.delayed.size());

        backend.runDelayed();
        verify(delayed).setFireTicks(10);
    }

    @Test
    void anIntentWithNoPriorDelayCallDispatchesImmediately() {
        // Locks the default-tier invariant: the executor sets delay() before every effect, but a sink whose
        // delay() was never called (e.g. a future direct-emit path) must default to immediate, not a stale tier.
        LivingEntity target = mock(LivingEntity.class);

        DispatchSink sink = new DispatchSink(handles); // no sink.delay(...) call at all
        sink.ignite(target, 60);
        sink.flush();

        verify(target).setFireTicks(60);
        assertTrue(backend.delayed.isEmpty(), "the default delay must be 0 (immediate), never a stale tier");
    }

    @Test
    void damageFoldIgnoresTheDelayAndAppliesToTheFiringEvent() {
        DispatchSink sink = new DispatchSink(handles);
        sink.delay(40);                 // a WAIT preceding a damage-arbiter contribution
        sink.addOutgoingDamage(1.0);    // +100% must still fold onto the original hit, not defer

        assertEquals(20.0, sink.fold().apply(10.0), 1e-9);
        assertTrue(backend.delayed.isEmpty(), "fold contributions never schedule a delayed task");
    }
}
