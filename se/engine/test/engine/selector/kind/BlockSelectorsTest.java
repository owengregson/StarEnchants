package engine.selector.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import engine.selector.SelectorCtx;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the §A block/location selectors and their shared facing/plane math. The pure selectors
 * (Here/Add/EyeHeight) and the delegating ones (Block/BlockInDistance) resolve against a mock
 * {@link SelectorCtx}; the dominant-axis + perpendicular-plane math is pinned directly. The mining-shape
 * selectors (Trench/Tunnel/Vein) snap to the block grid + read the world, so their end-to-end behaviour is
 * exercised live; the SHAPE they compute is pinned here through {@link BlockShapes}.
 */
class BlockSelectorsTest {

    @Test
    void hereResolvesToTheActivationLocation() {
        Location loc = new Location(null, 1, 2, 3);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(loc);
        assertEquals(List.of(loc), new HereSelector().resolveLocations(ctx));
    }

    @Test
    void addOffsetsTheActivationLocation() {
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(new Location(null, 10, 20, 30));
        when(ctx.dbl("x")).thenReturn(1.0);
        when(ctx.dbl("y")).thenReturn(2.0);
        when(ctx.dbl("z")).thenReturn(3.0);
        List<Location> out = new AddSelector().resolveLocations(ctx);
        assertEquals(1, out.size());
        assertEquals(new Location(null, 11, 22, 33), out.get(0));
    }

    @Test
    void eyeHeightResolvesToTheActorEyeLocation() {
        Location eye = new Location(null, 0, 64.62, 0);
        Player actor = mock(Player.class);
        when(actor.getEyeLocation()).thenReturn(eye);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.actor()).thenReturn(actor);
        assertEquals(List.of(eye), new EyeHeightSelector().resolveLocations(ctx));
    }

    @Test
    void blockSelectorsDelegateToTheRaytrace() {
        Location hit = new Location(null, 5, 5, 5);
        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.dbl("distance")).thenReturn(5.0);
        when(ctx.targetBlock(5.0)).thenReturn(hit);
        assertEquals(List.of(hit), new BlockSelector().resolveLocations(ctx));

        SelectorCtx far = mock(SelectorCtx.class);
        when(far.dbl("distance")).thenReturn(50.0);
        when(far.targetBlock(50.0)).thenReturn(hit);
        assertEquals(List.of(hit), new BlockInDistanceSelector().resolveLocations(far));

        // No block in sight → empty (never null).
        SelectorCtx empty = mock(SelectorCtx.class);
        when(empty.dbl("distance")).thenReturn(5.0);
        when(empty.targetBlock(5.0)).thenReturn(null);
        assertTrue(new BlockSelector().resolveLocations(empty).isEmpty());
    }

    @Test
    void facingPicksTheDominantAxis() {
        assertEquals(List.of(1, 0, 0), axis(new Vector(1, 0.1, 0.2)));   // mostly +X
        assertEquals(List.of(0, 0, -1), axis(new Vector(0.1, 0.2, -1))); // mostly -Z
        assertEquals(List.of(0, 1, 0), axis(new Vector(0.1, 1, 0.2)));   // mostly +Y (straight up)
    }

    @Test
    void perpendicularSpansThePlaneAcrossTheForwardAxis() {
        // forward X → the Y,Z plane; forward Z → the X,Y plane; forward Y → the X,Z plane.
        assertEquals(List.of(0, 1, 0), List.of(box(BlockShapes.perpendicular(new int[] {1, 0, 0})[0])));
        assertEquals(List.of(0, 0, 1), List.of(box(BlockShapes.perpendicular(new int[] {1, 0, 0})[1])));
        assertEquals(List.of(1, 0, 0), List.of(box(BlockShapes.perpendicular(new int[] {0, 0, 1})[0])));
        assertEquals(List.of(1, 0, 0), List.of(box(BlockShapes.perpendicular(new int[] {0, 1, 0})[0])));
        assertEquals(List.of(0, 0, 1), List.of(box(BlockShapes.perpendicular(new int[] {0, 1, 0})[1])));
    }

    private static List<Integer> axis(Vector direction) {
        Location loc = new Location(null, 0, 0, 0);
        loc.setDirection(direction);
        Player actor = mock(Player.class);
        when(actor.getLocation()).thenReturn(loc);
        int[] f = BlockShapes.facing(actor);
        return List.of(f[0], f[1], f[2]);
    }

    private static Integer[] box(int[] a) {
        return new Integer[] {a[0], a[1], a[2]};
    }
}
