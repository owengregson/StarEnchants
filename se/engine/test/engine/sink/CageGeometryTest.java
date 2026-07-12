package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

/**
 * The pure CAGE geometry (ADR-0052): the base point and the volume-clear verdict shared by the pets
 * pre-check and {@link Sink#cage}. A drift in either — the midpoint/rise math or the structure extent —
 * surfaces here as a changed coordinate or cell count.
 */
class CageGeometryTest {

    @Test
    void originIsTheFlooredMidpointRiseAboveTheLowerFeet() {
        Location origin = CageGeometry.origin(
                new Location(null, 10, 64, 20), new Location(null, 14, 66, 28), 2);
        assertEquals(12.0, origin.getX());
        assertEquals(66.0, origin.getY()); // min(64, 66) feet + rise 2
        assertEquals(24.0, origin.getZ());
    }

    @Test
    void originFloorsNegativeMidpointsRatherThanTruncating() {
        // (-3 + -6) / 2 = -4.5 must FLOOR to -5, not truncate toward zero (-4).
        Location origin = CageGeometry.origin(
                new Location(null, -3.0, 70.0, -3.0), new Location(null, -6.0, 70.0, -6.0), 0);
        assertEquals(-5.0, origin.getX());
        assertEquals(70.0, origin.getY());
        assertEquals(-5.0, origin.getZ());
    }

    @Test
    void volumeClearTrueChecksTheWholeStructureExtent() {
        AtomicInteger checked = new AtomicInteger();
        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        Predicate<Block> clear = b -> {
            checked.incrementAndGet();
            return true;
        };
        assertTrue(CageGeometry.volumeClear(world, new Location(world, 0, 10, 0), 3, 4, 3, clear));
        // Interior + a one-cell margin each horizontal side, a floor plate below and a roof above:
        // (width + 2) x (depth + 2) x (height + 2).
        assertEquals((3 + 2) * (3 + 2) * (4 + 2), checked.get());
    }

    @Test
    void volumeClearFalseWhenOneStructureCellIsObstructed() {
        World world = mock(World.class);
        Block air = mock(Block.class);
        when(air.getType()).thenReturn(Material.AIR);
        Block stone = mock(Block.class);
        when(stone.getType()).thenReturn(Material.STONE);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.getBlockAt(0, 14, 0)).thenReturn(stone); // the roof-centre cell (dx=0, dy=height, dz=0)
        Predicate<Block> clear = b -> b.getType() == Material.AIR;
        assertFalse(CageGeometry.volumeClear(world, new Location(world, 0, 10, 0), 3, 4, 3, clear));
    }
}
