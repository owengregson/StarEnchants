package engine.sink;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The path memory + staircase geometry behind the radius-0 {@code FOOTPRINT} snake (the devil netherrack trail).
 * A single sampled point per {@code REPEATING} activation skips blocks when the wearer sprints (&gt;1 block per
 * 5-tick sample) and reads ragged on diagonals (corner-touching stamps); this joins consecutive samples into a
 * 4-connected trail so every consecutive block shares an edge in the xz-plane. One shared instance rides the
 * per-boot {@link SinkEnv} — the memory must survive across the SEPARATE activations a repeating trigger fires,
 * which a per-event sink (freshly allocated each activation) could not.
 *
 * <p><strong>Consistency model.</strong> Each {@code (defId, walker)} key is touched by a SINGLE writer: the
 * {@code REPEATING} trigger for one wearer fires on that wearer's own region thread, and {@link #walk} runs
 * there at activation time (the current cell's {@code regionOp}). So the per-key get-then-put is confined to
 * that one thread and races with nothing; the {@link ConcurrentHashMap} guards only cross-key concurrent
 * access, when different walkers' region threads touch distinct keys at the same time. The per-cell placement
 * then hops to each cell's own region — a trail line may straddle a Folia region boundary — but that lives in
 * the sink, not here.
 *
 * <p>Pure and server-free (hand-computed unit tests, no Bukkit): {@link #walk} yields the block cells to stamp
 * and {@link #snapY} decides the ground snap from three replaceable-probe booleans the sink reads live off each
 * cell's own region. The y here is the interpolated target; the sink snaps and places it through the shared
 * {@link TempBlockLedger}.
 */
public final class TrailWalker {

    /** Ticks since the last stamp beyond which the trail restarts — a pause / relog / respawn, not a walk. */
    static final long STALE_TICKS = 100;

    /** Staircase length past which a walk is treated as a teleport/death and restarts at the current cell (no line across the map). */
    static final int MAX_GAP_CELLS = 16;

    /** {@link #snapY} sentinel: no solid ground at y or y±1, so the cell is skipped — a jump pauses the snake, never scaffolds air. */
    static final int SKIP = Integer.MIN_VALUE;

    /** Sweep stale entries once the map crosses this size, so a walker who logged off never strands a mark forever. */
    private static final int SWEEP_THRESHOLD = 1024;

    /** One emitted trail cell — block coordinates in the walker's current world (the caller supplies the world). */
    record Step(int x, int y, int z) {
    }

    private record Key(int defId, UUID walker) {
    }

    /** The last stamped cell for a key: its world + block coords + the tick it was stamped (for the stale check). */
    private record Mark(UUID world, int x, int y, int z, long tick) {
    }

    private final ConcurrentHashMap<Key, Mark> memory = new ConcurrentHashMap<>();
    private final AtomicInteger touches = new AtomicInteger();

    /**
     * The cells to stamp for one activation of walker {@code (defId, walker)} now standing at block
     * {@code (cx, cy, cz)} in {@code world}. A restart — first touch, a world change, a stamp older than
     * {@link #STALE_TICKS}, or a straight-line gap over {@link #MAX_GAP_CELLS} — yields only the current cell.
     * Otherwise a 4-connected staircase from the last cell (exclusive; already stamped) to the current cell
     * (inclusive), each consecutive pair edge-adjacent in xz, with y linearly interpolated last→current. The
     * mark advances to the current cell either way.
     */
    List<Step> walk(int defId, UUID walker, UUID world, int cx, int cy, int cz, long now) {
        if (touches.incrementAndGet() % SWEEP_THRESHOLD == 0) {
            memory.values().removeIf(m -> now - m.tick() > STALE_TICKS);
        }
        Key key = new Key(defId, walker);
        Mark last = memory.put(key, new Mark(world, cx, cy, cz, now));
        boolean restart = last == null
                || !last.world().equals(world)
                || now - last.tick() > STALE_TICKS
                || Math.abs(cx - last.x()) + Math.abs(cz - last.z()) > MAX_GAP_CELLS;
        if (restart) {
            return List.of(new Step(cx, cy, cz));
        }
        return staircase(last.x(), last.y(), last.z(), cx, cy, cz);
    }

    /**
     * The chosen y for a cell whose interpolated ground is {@code y}: place at {@code y} when it is replaceable
     * ground, else step up ({@code y+1}) or down ({@code y-1}) so the snake climbs a one-block stair, else
     * {@link #SKIP} — a jump over air pauses the snake rather than scaffolding it. Priority y → y+1 → y-1
     * keeps the trail hugging the floor. The replaceability is mode-3 solid-only, read live per cell by the sink.
     */
    static int snapY(int y, boolean replaceableAtY, boolean replaceableAbove, boolean replaceableBelow) {
        if (replaceableAtY) {
            return y;
        }
        if (replaceableAbove) {
            return y + 1;
        }
        if (replaceableBelow) {
            return y - 1;
        }
        return SKIP;
    }

    /**
     * A 4-connected line from {@code last} (exclusive) to {@code current} (inclusive): each iteration takes ONE
     * orthogonal step, choosing the axis whose next step stays closest to the ideal line, so a diagonal renders
     * as an L-staircase and every emitted cell is edge-adjacent to the previous. y is interpolated by progress.
     */
    private static List<Step> staircase(int lx, int ly, int lz, int cx, int cy, int cz) {
        int adx = Math.abs(cx - lx);
        int adz = Math.abs(cz - lz);
        int total = adx + adz;
        List<Step> out = new ArrayList<>(total);
        int sx = Integer.signum(cx - lx);
        int sz = Integer.signum(cz - lz);
        int x = lx;
        int z = lz;
        int ix = 0;
        int iz = 0;
        for (int k = 1; k <= total; k++) {
            boolean stepX;
            if (ix >= adx) {
                stepX = false; // x exhausted → the rest is pure z
            } else if (iz >= adz) {
                stepX = true;  // z exhausted → the rest is pure x
            } else {
                stepX = (1L + 2 * ix) * adz < (1L + 2 * iz) * adx; // whichever advance hugs the diagonal
            }
            if (stepX) {
                x += sx;
                ix++;
            } else {
                z += sz;
                iz++;
            }
            int y = ly + (int) Math.round((double) (cy - ly) * k / total);
            out.add(new Step(x, y, z));
        }
        return out;
    }
}
