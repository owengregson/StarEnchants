package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.sink.TrailWalker.Step;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The trail memory + 4-connected staircase core, hand-computed with no Bukkit. Pins the snake contract the
 * single-point stamp violated: consecutive samples join into an edge-connected path (no gaps, L-stepped
 * diagonals), a jump/teleport/relog restarts instead of drawing a line, and the ground snap climbs one-block
 * stairs but pauses over air.
 */
class TrailWalkerTest {

    private static final UUID W1 = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID W2 = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID WALKER = UUID.fromString("00000000-0000-0000-0000-0000000000b0");
    private static final int DEF = 7;
    private static final int Y = 64;

    /** Every consecutive pair (from → first, then along the list) differs by exactly one block in xz — 4-connected. */
    private static void assertEdgeConnected(Step from, List<Step> steps) {
        Step prev = from;
        for (Step s : steps) {
            int manhattanXz = Math.abs(s.x() - prev.x()) + Math.abs(s.z() - prev.z());
            assertEquals(1, manhattanXz, "consecutive cells " + prev + "→" + s + " are not edge-adjacent in xz");
            prev = s;
        }
    }

    // (a) A straight sprint gap of 3 emits all three skipped blocks, each edge-adjacent — no gaps at speed.
    @Test
    void straightSprintGapEmitsEveryBlock() {
        TrailWalker walker = new TrailWalker();
        Step start = new Step(0, Y, 0);
        assertEquals(List.of(start), walker.walk(DEF, WALKER, W1, 0, Y, 0, 0)); // first touch → restart, only current

        List<Step> steps = walker.walk(DEF, WALKER, W1, 3, Y, 0, 1);
        assertEquals(List.of(new Step(1, Y, 0), new Step(2, Y, 0), new Step(3, Y, 0)), steps);
        assertEdgeConnected(start, steps);
    }

    // (b) A pure diagonal of N renders as 2N cells in L-steps, every consecutive pair edge-adjacent (the ragged
    // corner-touching stamps the bug produced would be N cells, not 2N, and 8-connected).
    @Test
    void pureDiagonalRendersAsLStaircase() {
        TrailWalker walker = new TrailWalker();
        Step start = new Step(0, Y, 0);
        walker.walk(DEF, WALKER, W1, 0, Y, 0, 0);

        int n = 3;
        List<Step> steps = walker.walk(DEF, WALKER, W1, n, Y, n, 1);
        assertEquals(2 * n, steps.size(), "a diagonal of N must emit 2N L-stepped cells");
        assertEdgeConnected(start, steps);
        assertEquals(new Step(n, Y, n), steps.get(steps.size() - 1), "the walk ends at the current cell");
    }

    // (c) An arbitrary (dx,dz) knight-ish move is still edge-connected end to end, ending at the current cell.
    @Test
    void arbitraryMoveIsEdgeConnectedEndToEnd() {
        TrailWalker walker = new TrailWalker();
        Step start = new Step(0, Y, 0);
        walker.walk(DEF, WALKER, W1, 0, Y, 0, 0);

        List<Step> steps = walker.walk(DEF, WALKER, W1, 5, Y, 2, 1);
        assertEquals(5 + 2, steps.size(), "|dx|+|dz| orthogonal steps");
        assertEdgeConnected(start, steps);
        assertEquals(new Step(5, Y, 2), steps.get(steps.size() - 1));
    }

    // y is linearly interpolated last→current and rounded, so a sloped sprint stamps a smooth ramp of cells.
    @Test
    void yInterpolatesLinearlyAlongTheLine() {
        TrailWalker walker = new TrailWalker();
        walker.walk(DEF, WALKER, W1, 0, Y, 0, 0);

        List<Step> steps = walker.walk(DEF, WALKER, W1, 4, Y + 4, 0, 1);
        assertEquals(List.of(
                new Step(1, Y + 1, 0), new Step(2, Y + 2, 0),
                new Step(3, Y + 3, 0), new Step(4, Y + 4, 0)), steps);
    }

    // (d) The ground snap climbs a one-block stair: y not placeable but y+1 is → step up; y placeable → stay.
    @Test
    void groundSnapClimbsAStairUp() {
        assertEquals(Y + 1, TrailWalker.snapY(Y, false, true, false), "y air, y+1 solid → step up onto the stair");
        assertEquals(Y, TrailWalker.snapY(Y, true, true, true), "y placeable → stay (prefer the interpolated floor)");
        assertEquals(Y - 1, TrailWalker.snapY(Y, false, false, true), "y and y+1 air, y-1 solid → step down");
    }

    // (e) An air gap (no solid at y or y±1) skips that cell so a jump never scaffolds; solid ground still stamps.
    @Test
    void groundSnapSkipsAnAirGap() {
        assertEquals(TrailWalker.SKIP, TrailWalker.snapY(Y, false, false, false), "no solid at y or y±1 → skip");
        assertEquals(Y, TrailWalker.snapY(Y, true, false, false), "the neighbouring solid cell is still emitted");
    }

    // (f) A gap over the cap (a teleport/death) restarts at the current cell — never a line drawn across the map.
    @Test
    void oversizeGapRestartsAtCurrentCell() {
        TrailWalker walker = new TrailWalker();
        walker.walk(DEF, WALKER, W1, 0, Y, 0, 0);

        int far = TrailWalker.MAX_GAP_CELLS + 1;
        List<Step> steps = walker.walk(DEF, WALKER, W1, far, Y, 0, 1);
        assertEquals(List.of(new Step(far, Y, 0)), steps, "an oversize gap stamps only the current cell");
    }

    // (g) A stale mark (paused/relogged past the window) or a world change restarts — no line to the old cell.
    @Test
    void staleMarkOrWorldChangeRestarts() {
        TrailWalker stale = new TrailWalker();
        stale.walk(DEF, WALKER, W1, 0, Y, 0, 0);
        assertEquals(List.of(new Step(2, Y, 0)),
                stale.walk(DEF, WALKER, W1, 2, Y, 0, TrailWalker.STALE_TICKS + 1),
                "a stamp older than the stale window restarts");

        TrailWalker crossWorld = new TrailWalker();
        crossWorld.walk(DEF, WALKER, W1, 0, Y, 0, 0);
        assertEquals(List.of(new Step(2, Y, 0)), crossWorld.walk(DEF, WALKER, W2, 2, Y, 0, 1),
                "a world change restarts (no line across dimensions)");
    }

    // (h) Standing still (the sample lands on the last cell) emits nothing — no duplicate stamp of the same block.
    @Test
    void sameCellRepeatEmitsNothing() {
        TrailWalker walker = new TrailWalker();
        walker.walk(DEF, WALKER, W1, 0, Y, 0, 0);
        assertTrue(walker.walk(DEF, WALKER, W1, 0, Y, 0, 1).isEmpty(), "the current cell equals the last → no emission");
    }

    // Two walkers (distinct UUIDs) keep independent memory — one sprinting never bleeds into the other's trail.
    @Test
    void distinctWalkersHaveIndependentMemory() {
        TrailWalker walker = new TrailWalker();
        UUID other = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
        walker.walk(DEF, WALKER, W1, 0, Y, 0, 0);
        walker.walk(DEF, other, W1, 100, Y, 100, 0);

        List<Step> a = walker.walk(DEF, WALKER, W1, 1, Y, 0, 1);
        assertEquals(List.of(new Step(1, Y, 0)), a, "walker A steps from ITS own last cell, not the other's");
    }
}
