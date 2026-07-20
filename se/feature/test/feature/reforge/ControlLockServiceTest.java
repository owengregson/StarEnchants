package feature.reforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import compile.resolve.PlatformResolvers;
import feature.trigger.TriggerDispatch;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import platform.caps.Capabilities;
import platform.caps.Regions;
import platform.sched.Scheduling;
import testfx.RecordingSchedulerBackend;

/**
 * The extracted combat CONTROL-LOCK (ADR-0071): a victim who loses control WITHOUT being frozen — driven down a
 * tracked ballistic arc (a per-tick integrated velocity re-asserted every tick, so a camera-snap teleport can't
 * stall the fall), the view snapped back only on drift, walk+jump neutralised. Under {@link PlatformResolvers}
 * {@code none()} the Slowness/Jump ids resolve to {@code -1} and are skipped, so the tests focus on the arc +
 * camera behaviour; the {@code *Later} arm hop is replayed by delay through {@link RecordingSchedulerBackend}.
 */
class ControlLockServiceTest {

    private RecordingSchedulerBackend backend;
    private final World world = mock(World.class);
    private final TriggerDispatch dispatch = mock(TriggerDispatch.class);

    @BeforeEach
    void setUp() {
        backend = new RecordingSchedulerBackend();
        Scheduling.install(backend);
        Regions.install(false);
        ControlLockService.clearAll();
    }

    @AfterEach
    void tearDown() {
        ControlLockService.clearAll();
        Regions.install(Capabilities.foliaPresent());
    }

    private ControlLockService service() {
        return new ControlLockService(dispatch, PlatformResolvers.none());
    }

    private LivingEntity victim(Location loc, Vector velocity) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getUniqueId()).thenReturn(UUID.randomUUID());
        when(e.isValid()).thenReturn(true);
        when(e.getLocation()).thenReturn(loc);
        when(e.getVelocity()).thenReturn(velocity);
        return e;
    }

    /** Run every recorded delayed hop whose delay is {@code delay} (fires the armed lock). */
    private void runDue(long delay) {
        List<RecordingSchedulerBackend.Delayed> due = new ArrayList<>();
        for (RecordingSchedulerBackend.Delayed d : backend.delayed) {
            if (d.delayTicks() == delay) {
                due.add(d);
            }
        }
        backend.delayed.removeAll(due);
        for (RecordingSchedulerBackend.Delayed d : due) {
            d.task().run();
        }
    }

    @Test
    void drivesASmoothArcAndSnapsLookOnDrift() {
        Location loc = new Location(world, 4, 65, 0.5, 33f, 7f);
        LivingEntity victim = victim(loc, new Vector(0.4, 0.2, 0.0)); // the live knock velocity — seed of the arc

        service().arm(victim, 5, 20); // arm after a 5-tick delay, lock for 20
        runDue(5); // arm fires: seeds the tracked arc, starts the 1-tick task

        RecordingSchedulerBackend.Repeat lock = backend.repeating.get(0);
        List<Vector> applied = new ArrayList<>();
        doAnswer(inv -> {
            applied.add(((Vector) inv.getArgument(0)).clone());
            return null;
        }).when(victim).setVelocity(any(Vector.class));

        // Un-drifted ticks: no snap, but the tracked arc is driven onto the victim EVERY tick.
        for (int i = 0; i < 3; i++) {
            lock.task.run();
        }
        verify(dispatch, never()).teleportEntity(any(), any());
        assertEquals(3, applied.size());
        assertTrue(applied.get(0).getX() > applied.get(1).getX(), "horizontal decays each tick");
        assertTrue(applied.get(1).getY() < applied.get(0).getY(), "vertical accelerates downward");
        assertTrue(applied.get(2).getY() < applied.get(1).getY(), "…smoothly, tick over tick");

        // Spam the camera past the leash: every tick snaps the view — but the arc keeps advancing smoothly
        // (setVelocity(arc) fires after each teleport, so a reset can never stall the fall).
        when(victim.getLocation()).thenReturn(new Location(world, 4, 60, 0.5, 120f, 40f));
        int before = applied.size();
        for (int i = 0; i < 3; i++) {
            lock.task.run();
        }
        verify(dispatch, times(3)).teleportEntity(eq(victim), any());
        for (int i = before; i < applied.size(); i++) {
            assertTrue(applied.get(i).getY() < applied.get(i - 1).getY(), "the arc stays smooth through camera spam");
        }
        verify(victim, never()).setVelocity(new Vector(0, 0, 0)); // never a freeze write

        // Run out the lock → release.
        for (int i = 0; i < 14; i++) {
            lock.task.run(); // ticks 7..20
        }
        lock.task.run(); // the 21st tick passes the deadline → release
        assertEquals(0, ControlLockService.lockCount());
        assertTrue(lock.isCancelled());
    }

    @Test
    void snapTeleportsToTheLivePositionWithTheHeldLook() {
        Location loc = new Location(world, 4, 65, 0.5, 33f, 7f);
        LivingEntity victim = victim(loc, new Vector(0, 0, 0));
        service().arm(victim, 5, 20);
        runDue(5);
        RecordingSchedulerBackend.Repeat lock = backend.repeating.get(0);

        // A real mouse-turn at a fallen position: snap the view back to (33,7) at the LIVE position.
        when(victim.getLocation()).thenReturn(new Location(world, 4, 58, 0.5, 120f, 40f));
        lock.task.run();
        ArgumentCaptor<Location> snap = ArgumentCaptor.forClass(Location.class);
        verify(dispatch).teleportEntity(eq(victim), snap.capture());
        assertEquals(4, snap.getValue().getX(), 1.0e-9);       // the LIVE position, not a captured pin point
        assertEquals(58, snap.getValue().getY(), 1.0e-9);      // still falling — position never pinned
        assertEquals(33.0, snap.getValue().getYaw(), 1.0e-4);  // look snapped back to the held facing
        assertEquals(7.0, snap.getValue().getPitch(), 1.0e-4);
    }

    @Test
    void refreshNotStackReplacesTheEarlierLock() {
        LivingEntity victim = victim(new Location(world, 4, 65, 0.5, 0f, 0f), new Vector(0, 0, 0));
        ControlLockService svc = service();
        svc.arm(victim, 5, 20);
        runDue(5); // lock #1 arms
        RecordingSchedulerBackend.Repeat first = backend.repeating.get(0);

        svc.arm(victim, 5, 20); // a second lock on the same victim
        runDue(5); // lock #2 arms → must cancel #1

        assertTrue(first.isCancelled(), "the earlier lock is cancelled, not stacked");
        assertEquals(1, ControlLockService.lockCount());
    }

    @Test
    void clearAllReleasesEveryLock() {
        LivingEntity victim = victim(new Location(world, 4, 65, 0.5, 0f, 0f), new Vector(0, 0, 0));
        service().arm(victim, 5, 20);
        runDue(5);
        assertEquals(1, ControlLockService.lockCount());

        ControlLockService.clearAll();

        assertEquals(0, ControlLockService.lockCount());
        assertTrue(backend.repeating.get(0).isCancelled()); // the lock task is released, never stranded
    }
}
