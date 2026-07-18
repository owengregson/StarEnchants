package engine.sink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-0071 TRAP_BREAK grouping surface: tiles grow an AABB; confinement matches victims OR the box. */
class TrapStructuresTest {

    private final TrapStructures store = new TrapStructures();

    @Test
    void tilesGrowAabbAndSnapshot() {
        UUID p = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long id = store.open(world, Set.of(p), 0L, 100);
        store.tile(id, 1, 2, 3);
        store.tile(id, 4, 8, 3);
        store.tile(id, 2, 5, 9);
        // Retrieved via the victims term so the AABB fields can be inspected directly (same package).
        List<TrapStructures.Structure> found = store.confining(p, world, 0, 0, 0, 0L);
        assertEquals(1, found.size());
        TrapStructures.Structure s = found.get(0);
        assertEquals(1, s.minX);
        assertEquals(4, s.maxX);
        assertEquals(2, s.minY);
        assertEquals(8, s.maxY);
        assertEquals(3, s.minZ);
        assertEquals(9, s.maxZ);
        List<int[]> tiles = s.tilesSnapshot();
        assertEquals(3, tiles.size());
        assertArrayEquals(new int[] {1, 2, 3}, tiles.get(0));
        assertArrayEquals(new int[] {4, 8, 3}, tiles.get(1));
        assertArrayEquals(new int[] {2, 5, 9}, tiles.get(2));
    }

    @Test
    void confiningMatchesVictims() {
        UUID p = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long id = store.open(world, Set.of(p), 0L, 100);
        store.tile(id, 0, 0, 0);
        // A victim standing far outside the tiny AABB is still confined (a cage they were teleported into).
        assertEquals(1, store.confining(p, world, 100, 100, 100, 0L).size());
    }

    @Test
    void confiningMatchesAabb() {
        UUID p = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long id = store.open(world, Set.of(p), 0L, 100); // p is the victim, not foreign
        store.tile(id, 0, 0, 0);
        store.tile(id, 2, 2, 2); // AABB [0..2]^3
        assertEquals(1, store.confining(foreign, world, 1, 1, 1, 0L).size(),
                "anyone shoved into the box is confined by the AABB term");
        assertTrue(store.confining(foreign, world, 3, 1, 1, 0L).isEmpty(),
                "one block outside the box is not confined");
    }

    @Test
    void worldMismatchNeverMatches() {
        UUID p = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        UUID otherWorld = UUID.randomUUID();
        long id = store.open(world, Set.of(p), 0L, 100);
        store.tile(id, 0, 0, 0);
        assertTrue(store.confining(p, otherWorld, 0, 0, 0, 0L).isEmpty());
    }

    @Test
    void lapsedStructurePurged() {
        UUID p = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long id = store.open(world, Set.of(p), 0L, 100);
        store.tile(id, 0, 0, 0);
        long deadline = 100L + TempBlockLedger.STALE_GRACE_TICKS; // now(0) + duration(100) + grace
        assertEquals(1, store.confining(p, world, 0, 0, 0, deadline - 1).size(), "live one tick before the deadline");
        assertTrue(store.confining(p, world, 0, 0, 0, deadline).isEmpty(), "purged at the deadline (half-open)");
        assertEquals(0, store.size());
    }

    @Test
    void removeDrops() {
        UUID p = UUID.randomUUID();
        UUID world = UUID.randomUUID();
        long id = store.open(world, Set.of(p), 0L, 100);
        store.tile(id, 0, 0, 0);
        store.remove(id);
        assertTrue(store.confining(p, world, 0, 0, 0, 0L).isEmpty());
        assertEquals(0, store.size());
    }
}
