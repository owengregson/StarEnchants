package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.sched.SchedulerBackend;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.SyncSchedulerBackend;

/**
 * The BLINK sink walk (ADR-0071): from the actor's own thread, sample the ray in 0.5-block steps deduped by
 * block cell, land on the LAST standable cell before the FIRST wall (never scanning past it), and puff dust at
 * departure and arrival. A point-blank wall blinks zero and still spends. Origin at x=0.5 facing +X so each
 * whole block boundary is a clean, hand-checkable cell.
 */
class BlinkForwardTest {

    private SchedulerBackend previous;

    @BeforeEach
    void setUp() {
        previous = Scheduling.isInitialized() ? Scheduling.backend() : null;
        Scheduling.install(new SyncSchedulerBackend()); // the whole entityOp body runs inline at flush()
    }

    @AfterEach
    void tearDown() {
        if (previous != null) {
            Scheduling.install(previous);
        }
    }

    private static RecordingSink sink() {
        return new RecordingSink(Envs.sink().build());
    }

    private static void blink(RecordingSink sink, double maxDistance) {
        Player actor = mock(Player.class);
        Location origin = new Location(null, 0.5, 64, 0.5, 0f, 0f);
        sink.blinkForward(actor, origin, new Vector(1, 0, 0), maxDistance, 1, 10, 20, 30, 1f, 5);
        sink.flush();
    }

    @Test
    void landsOnFarthestOpenCell() {
        RecordingSink sink = sink();
        sink.safe = cell -> cell.getBlockX() <= 3; // blocks 1..3 open, block 4 is the wall
        blink(sink, 5);
        assertEquals(1, sink.teleports.size());
        assertEquals(3, sink.teleports.get(0).getBlockX()); // the last open cell, not block 4
        assertEquals(2, sink.dust.size());                  // departure + arrival puffs
    }

    @Test
    void pointBlankWallDoesNotMove() {
        RecordingSink sink = sink();
        sink.safe = cell -> false; // the very first cell is a wall
        blink(sink, 5);
        assertTrue(sink.teleports.isEmpty()); // zero blink, attempt spent
        assertTrue(sink.dust.isEmpty());       // no departure/arrival puff
    }

    @Test
    void neverPhasesThroughAGap() {
        RecordingSink sink = sink();
        sink.safe = cell -> cell.getBlockX() == 1 || cell.getBlockX() == 3; // wall at 2, open again at 3
        blink(sink, 5);
        assertEquals(1, sink.teleports.size());
        assertEquals(1, sink.teleports.get(0).getBlockX()); // stops at 1, never reaches the far gap at 3
    }

    @Test
    void stepsUpASingleRiseLikeWalking() {
        RecordingSink sink = sink();
        // A 1-block rise at x=2 (open above it): walking would step onto it, so the blink does too —
        // the walk lifts to y=65 and carries on. Only a 2-high face reads as a wall.
        sink.safe = cell -> !(cell.getBlockX() == 2 && cell.getBlockY() == 64);
        blink(sink, 4);
        assertEquals(1, sink.teleports.size());
        assertEquals(4, sink.teleports.get(0).getBlockX()); // full distance, not stranded at the rise
        assertEquals(65, sink.teleports.get(0).getBlockY()); // landed ON the step, not inside it
    }

    @Test
    void twoHighFaceStillStopsTheWalk() {
        RecordingSink sink = sink();
        sink.safe = cell -> cell.getBlockX() < 2; // x=2 blocked at EVERY height: a wall, not a step
        blink(sink, 4);
        assertEquals(1, sink.teleports.size());
        assertEquals(1, sink.teleports.get(0).getBlockX()); // stopped short — the authored downside holds
        assertEquals(64, sink.teleports.get(0).getBlockY());
    }

    @Test
    void dedupesSamplesInsideOneCell() {
        RecordingSink sink = sink();
        sink.safe = cell -> true; // all open to the max
        blink(sink, 4);
        // 8 raw 0.5-block samples over 4 whole blocks collapse to 4 distinct-cell standability checks.
        assertEquals(4, sink.safeChecks);
        assertTrue(sink.safeChecks < 8);
    }
}
