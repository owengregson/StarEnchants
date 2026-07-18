package engine.sink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-structure grouping over the coordinate-keyed {@link TempBlockLedger} (ADR-0071 TRAP_BREAK): a
 * CONFINING placement (cage / entity-anchored box / in-cell web) opens a structure, appends every
 * tile it actually placed, and records who it confines; Turnkey early-restores exactly the
 * structures holding a given player. Floors, trails, platforms and pillars are never registered —
 * the confinement decision is made at the effect/sink call site, not inferred here.
 *
 * <p>Entries are bookkeeping only (the ledger owns the blocks): they lapse at their deadline and
 * are lazily purged, so a structure whose reverts already fired can never be "broken" again. Rides
 * {@link SinkEnv} as an instance (the DotParkLedger pattern — never a mutable static); structures
 * are opened on the placing activation's firing thread, tiles appended from each tile's owning
 * region thread, matched/closed on the breaking actor's thread — all collections concurrent, the
 * per-structure AABB maintained under the structure's own monitor (placement-rate, not hit-rate).
 */
public final class TrapStructures {

    /**
     * One confining structure: the world, the confined victims, the placed tiles, an AABB
     * accumulated from the tiles, and the placement's revert deadline (+ the ledger grace).
     */
    public static final class Structure {
        final long id;
        final UUID world;
        final Set<UUID> victims;                                        // fixed at open
        final List<int[]> tiles = Collections.synchronizedList(new ArrayList<>()); // appended per region thread
        volatile int minX;
        volatile int minY;
        volatile int minZ;
        volatile int maxX;
        volatile int maxY;
        volatile int maxZ;                                              // maintained under this
        final long deadlineTick;

        Structure(long id, UUID world, Set<UUID> victims, long deadlineTick) {
            this.id = id;
            this.world = world;
            this.victims = victims;
            this.deadlineTick = deadlineTick;
            // An empty AABB matches no point until the first tile grows it.
            this.minX = Integer.MAX_VALUE;
            this.minY = Integer.MAX_VALUE;
            this.minZ = Integer.MAX_VALUE;
            this.maxX = Integer.MIN_VALUE;
            this.maxY = Integer.MIN_VALUE;
            this.maxZ = Integer.MIN_VALUE;
        }

        public long id() {
            return id;
        }

        public UUID world() {
            return world;
        }

        public Set<UUID> victims() {
            return victims;
        }

        /** A snapshot of the placed tiles (each {@code int[]{x, y, z}}); never mutated after append. */
        public List<int[]> tilesSnapshot() {
            synchronized (tiles) {
                return new ArrayList<>(tiles);
            }
        }

        public long deadlineTick() {
            return deadlineTick;
        }

        boolean aabbContains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }

    private final ConcurrentHashMap<Long, Structure> live = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong();

    /**
     * Open a structure; {@code victims} may be 1 or 2 entries; deadline =
     * {@code nowTicks + durationTicks + }{@link TempBlockLedger#STALE_GRACE_TICKS}. Lazily purges
     * lapsed entries first. Returns the structure id for the per-tile appends.
     */
    public long open(UUID world, Set<UUID> victims, long nowTicks, int durationTicks) {
        purgeLapsed(nowTicks);
        long id = nextId.incrementAndGet();
        long deadline = nowTicks + durationTicks + TempBlockLedger.STALE_GRACE_TICKS;
        live.put(id, new Structure(id, world, Set.copyOf(victims), deadline));
        return id;
    }

    /**
     * Append one PLACED tile (only tiles the ledger actually placed — a skipped replaceMode tile is
     * never appended); grows the AABB. Safe from any region thread. A no-op for an unknown/lapsed id.
     */
    public void tile(long structureId, int x, int y, int z) {
        Structure s = live.get(structureId);
        if (s == null) {
            return;
        }
        s.tiles.add(new int[] {x, y, z});
        synchronized (s) {
            s.minX = Math.min(s.minX, x);
            s.minY = Math.min(s.minY, y);
            s.minZ = Math.min(s.minZ, z);
            s.maxX = Math.max(s.maxX, x);
            s.maxY = Math.max(s.maxY, y);
            s.maxZ = Math.max(s.maxZ, z);
        }
    }

    /**
     * Every live structure confining {@code player} at {@code (world, bx, by, bz)} now: victims
     * contains the player OR the AABB (inclusive) contains their block cell. Purges lapsed first.
     */
    public List<Structure> confining(UUID player, UUID world, int bx, int by, int bz, long nowTicks) {
        purgeLapsed(nowTicks);
        List<Structure> out = new ArrayList<>();
        for (Structure s : live.values()) {
            if (!s.world.equals(world)) {
                continue;
            }
            if (s.victims.contains(player) || s.aabbContains(bx, by, bz)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Drop a broken/lapsed structure. */
    public void remove(long structureId) {
        live.remove(structureId);
    }

    /** Live structure count (tests). */
    public int size() {
        return live.size();
    }

    /** Forget every structure (boot hygiene). */
    public void clearAll() {
        live.clear();
    }

    private void purgeLapsed(long nowTicks) {
        live.values().removeIf(s -> nowTicks >= s.deadlineTick);
    }
}
