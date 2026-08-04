package engine.selector.kind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import engine.selector.SelectorCtx;
import engine.selector.SelectorKind;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import schema.spec.Args;

/**
 * The block/location selectors and their facing/plane math. Pure (Here/Add/EyeHeight) and delegating
 * (Block/BlockInDistance) selectors resolve against a mock {@link SelectorCtx}; the mining-shape selectors
 * (Trench/Tunnel/Vein) read the world live, so only the SHAPE they compute is pinned here via {@link BlockShapes}.
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

    @Test
    void widthHeightPutsTheVerticalAxisSecond() {
        // half-height must always mean "up and down" — perpendicular() alone yields Y first for a +X face
        // and second for a +Z one, which would make a 1x3 bore turn on which way the miner happened to look.
        assertEquals(List.of(0, 0, 1), List.of(box(BlockShapes.widthHeight(new int[] {1, 0, 0})[0])));
        assertEquals(List.of(0, 1, 0), List.of(box(BlockShapes.widthHeight(new int[] {1, 0, 0})[1])));
        assertEquals(List.of(1, 0, 0), List.of(box(BlockShapes.widthHeight(new int[] {0, 0, 1})[0])));
        assertEquals(List.of(0, 1, 0), List.of(box(BlockShapes.widthHeight(new int[] {0, 0, 1})[1])));
        // Straight down: neither perpendicular axis is vertical, so the pair is left alone.
        assertEquals(List.of(1, 0, 0), List.of(box(BlockShapes.widthHeight(new int[] {0, -1, 0})[0])));
        assertEquals(List.of(0, 0, 1), List.of(box(BlockShapes.widthHeight(new int[] {0, -1, 0})[1])));
    }

    @Test
    void boreMarchesTheCrossSectionIntoTheFace() {
        // 3 wide x 1 tall, two layers deep, facing +X: 3*1*2 blocks, the first layer on the struck block's own
        // plane (so depth=1 is exactly @Trench) and the second one step along the face normal.
        List<Location> out = new BoreSelector().resolveLocations(shapeCtx(new Vector(1, 0, 0), true,
                Map.of("half-width", 1, "half-height", 0, "depth", 2,
                        "left", -1, "right", -1, "up", -1, "down", -1)));

        assertEquals(6, out.size());
        assertTrue(out.contains(new Location(null, 10, 64, 20)), () -> out.toString());  // layer 0, centre
        assertTrue(out.contains(new Location(null, 10, 64, 19)), () -> out.toString());  // layer 0, width -1
        assertTrue(out.contains(new Location(null, 11, 64, 21)), () -> out.toString());  // layer 1, width +1
        assertTrue(out.stream().noneMatch(l -> l.getBlockY() != 64), () -> out.toString()); // half-height 0 is flat
    }

    @Test
    void borePerSideExtentsReachTheEvenCrossSectionsTheHalfParamsCannot() {
        // 4 wide (left 1 + centre + right 2) x 1 tall, one layer, facing +X. No half-width value produces an
        // even span from a -half..+half loop, so this row is the whole reason the extents exist.
        List<Location> out = new BoreSelector().resolveLocations(shapeCtx(new Vector(1, 0, 0), true,
                Map.of("half-width", 1, "half-height", 0, "depth", 1,
                        "left", 1, "right", 2, "up", 0, "down", 0)));

        assertEquals(4, out.size(), () -> out.toString());
        assertTrue(out.contains(new Location(null, 10, 64, 19)), () -> out.toString()); // one block left
        assertTrue(out.contains(new Location(null, 10, 64, 22)), () -> out.toString()); // two blocks right
        assertTrue(out.stream().noneMatch(l -> l.getBlockZ() < 19 || l.getBlockZ() > 22), () -> out.toString());
    }

    @Test
    void aNegativeExtentFallsBackToItsAxisSymmetricHalf() {
        // The -1 sentinel is what keeps every already-authored @Bore identical: an unset side means "as before".
        List<Location> asymmetric = new BoreSelector().resolveLocations(shapeCtx(new Vector(1, 0, 0), true,
                Map.of("half-width", 2, "half-height", 0, "depth", 1,
                        "left", -1, "right", -1, "up", -1, "down", -1)));
        assertEquals(5, asymmetric.size(), () -> asymmetric.toString()); // 2 + centre + 2, the symmetric span
    }

    @Test
    void blockShapesConsultTheMaterialFilterPerBlock() {
        // The filter is what makes a break tool selective; a shape that never asked would break everything.
        assertTrue(new BoreSelector().resolveLocations(shapeCtx(new Vector(1, 0, 0), false,
                Map.of("half-width", 1, "half-height", 1, "depth", 2,
                        "left", -1, "right", -1, "up", -1, "down", -1))).isEmpty());
        assertTrue(new TrenchSelector().resolveLocations(shapeCtx(new Vector(1, 0, 0), false,
                Map.of("radius", 1))).isEmpty());
        assertTrue(new TunnelSelector().resolveLocations(shapeCtx(new Vector(1, 0, 0), false,
                Map.of("depth", 3))).isEmpty());
        // VEIN gates the SEED instead: the fill is same-material by construction, so a filtered-out struck
        // block must vein nothing rather than vein and be emptied afterwards.
        SelectorCtx vein = shapeCtx(new Vector(1, 0, 0), false, Map.of("limit", 8));
        assertTrue(new VeinSelector().resolveLocations(vein).isEmpty());
        verify(vein, never()).vein(any(), anyInt());
    }

    /**
     * Every shape must hand BOTH filter lists to the seam. A shape that read only {@code materials} would
     * compile an authored deny list and then quietly ignore it — the failure that turns an excavation enchant
     * into a bedrock eraser, and one no diagnostic can catch because the param resolved fine.
     */
    @Test
    void everyShapeForwardsBothMaterialListsToTheFilter() {
        Args lists = Args.empty()
                .with("materials", List.of(1, 2))
                .with("exclude-materials", List.of(7));

        for (SelectorKind shape : List.of(new BoreSelector(), new TrenchSelector(),
                new TunnelSelector(), new VeinSelector())) {
            SelectorCtx ctx = shapeCtx(new Vector(1, 0, 0), true,
                    Map.of("half-width", 1, "half-height", 1, "depth", 1, "radius", 1, "limit", 8));
            when(ctx.args()).thenReturn(lists);

            shape.resolveLocations(ctx);

            verify(ctx, org.mockito.Mockito.atLeastOnce())
                    .materialMatches(any(), org.mockito.ArgumentMatchers.eq(List.of(1, 2)),
                            org.mockito.ArgumentMatchers.eq(List.of(7)));
        }
    }

    /** A block-shape ctx anchored at 10/64/20, looking along {@code direction}, with the material filter's answer. */
    private static SelectorCtx shapeCtx(Vector direction, boolean materialMatches, Map<String, Integer> args) {
        Location snapped = new Location(null, 10, 64, 20);
        org.bukkit.block.Block block = mock(org.bukkit.block.Block.class);
        when(block.getLocation()).thenReturn(snapped);
        Location raw = mock(Location.class);
        when(raw.getBlock()).thenReturn(block);

        Location eye = new Location(null, 10, 64, 20);
        eye.setDirection(direction);
        Player actor = mock(Player.class);
        when(actor.getLocation()).thenReturn(eye);

        SelectorCtx ctx = mock(SelectorCtx.class);
        when(ctx.location()).thenReturn(raw);
        when(ctx.actor()).thenReturn(actor);
        when(ctx.args()).thenReturn(Args.empty());
        args.forEach((name, value) -> lenient().when(ctx.integer(name)).thenReturn(value));
        lenient().when(ctx.materialMatches(any(), any(), any())).thenReturn(materialMatches);
        return ctx;
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
