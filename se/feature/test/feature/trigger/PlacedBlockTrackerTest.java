package feature.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.junit.jupiter.api.Test;

/**
 * Unit-pins {@link PlacedBlockTracker}: a placed block is remembered until broken, per-world; a piston push
 * moves the mark with the block so it cannot be laundered; the packing key separates nearby and negative coords.
 */
class PlacedBlockTrackerTest {

    private final World world = mock(World.class);
    private final UUID worldId = UUID.randomUUID();

    PlacedBlockTrackerTest() {
        when(world.getUID()).thenReturn(worldId);
    }

    private Block blockAt(World w, int x, int y, int z) {
        Block b = mock(Block.class);
        when(b.getWorld()).thenReturn(w);
        when(b.getX()).thenReturn(x);
        when(b.getY()).thenReturn(y);
        when(b.getZ()).thenReturn(z);
        return b;
    }

    @Test
    void packSeparatesNearbyAndNegativeCoords() {
        long a = PlacedBlockTracker.pack(10, 64, 20);
        assertEquals(a, PlacedBlockTracker.pack(10, 64, 20), "packing is deterministic");
        assertNotEquals(a, PlacedBlockTracker.pack(11, 64, 20));
        assertNotEquals(a, PlacedBlockTracker.pack(10, 65, 20));
        assertNotEquals(a, PlacedBlockTracker.pack(10, 64, 21));
        // a negative coord must not collide with the positive of the same magnitude
        assertNotEquals(PlacedBlockTracker.pack(-5, -3, -7), PlacedBlockTracker.pack(5, 3, 7));
    }

    @Test
    void placedBlockIsRememberedUntilBroken() {
        PlacedBlockTracker tracker = new PlacedBlockTracker();
        Block b = blockAt(world, 1, 70, 2);

        assertFalse(tracker.isPlaced(b), "an untouched block is natural");

        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getBlockPlaced()).thenReturn(b);
        tracker.onPlace(place);
        assertTrue(tracker.isPlaced(b), "a placed block is remembered");

        BlockBreakEvent breakEvent = mock(BlockBreakEvent.class);
        when(breakEvent.getBlock()).thenReturn(b);
        tracker.onBreak(breakEvent);
        assertFalse(tracker.isPlaced(b), "breaking clears the mark");
    }

    @Test
    void marksAreIsolatedPerWorld() {
        PlacedBlockTracker tracker = new PlacedBlockTracker();
        Block b = blockAt(world, 3, 60, 4);
        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getBlockPlaced()).thenReturn(b);
        tracker.onPlace(place);

        World other = mock(World.class);
        when(other.getUID()).thenReturn(UUID.randomUUID());
        Block sameCoordsOtherWorld = blockAt(other, 3, 60, 4);
        assertFalse(tracker.isPlaced(sameCoordsOtherWorld), "the same coords in another world are unmarked");
    }

    @Test
    void multiPlaceMarksEveryFilledCell() {
        PlacedBlockTracker tracker = new PlacedBlockTracker();
        Block foot = blockAt(world, 5, 64, 5);
        Block head = blockAt(world, 5, 64, 6);
        BlockState footState = mock(BlockState.class);
        BlockState headState = mock(BlockState.class);
        when(footState.getBlock()).thenReturn(foot);
        when(headState.getBlock()).thenReturn(head);
        BlockMultiPlaceEvent multi = mock(BlockMultiPlaceEvent.class);
        when(multi.getReplacedBlockStates()).thenReturn(List.of(footState, headState));

        tracker.onMultiPlace(multi);

        assertTrue(tracker.isPlaced(foot));
        assertTrue(tracker.isPlaced(head));
    }

    @Test
    void pistonPushMovesTheMarkWithTheBlock() {
        PlacedBlockTracker tracker = new PlacedBlockTracker();
        Block origin = blockAt(world, 8, 64, 8);
        Block destination = blockAt(world, 9, 64, 8);
        when(origin.getRelative(BlockFace.EAST)).thenReturn(destination);

        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getBlockPlaced()).thenReturn(origin);
        tracker.onPlace(place);

        BlockPistonExtendEvent extend = mock(BlockPistonExtendEvent.class);
        when(extend.getBlocks()).thenReturn(List.of(origin));
        when(extend.getDirection()).thenReturn(BlockFace.EAST);
        tracker.onPistonExtend(extend);

        assertFalse(tracker.isPlaced(origin), "the mark left the origin");
        assertTrue(tracker.isPlaced(destination), "the mark rode the block one cell over — no laundering");
    }
}
