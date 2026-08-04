package engine.sink;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The two field profiles' PURE decisions — the layer/density/offset draws and the payload arithmetic that the
 * sink would otherwise only exercise against a booted server. Every draw here is scripted rather than seeded, so
 * each expectation is hand-computed from the profile's own rule and a scripted value outside the bound the
 * profile computed fails loudly (which pins the bound too).
 */
class FieldProfileTest {

    @Nested
    class BlockField {

        /** radius 1, height 4, one layer, no step, full density — the grid every pre-profile FALLING_BLOCK rained. */
        private static final BlockFieldProfile PLAIN = new BlockFieldProfile(1, 4, 1, 1, 0, 0, 100, 0, 0, -1);

        @Test
        void anUnprofiledFieldIsOneFullLayerAtHeightAndDrawsNothingAtAll() {
            // The back-compat contract: shipped FALLING_BLOCK content must rain exactly what it always did, and
            // must not even perturb the shared RNG stream doing it.
            assertArrayEquals(new int[] {4}, PLAIN.layerYOffsets(new NeverDrawn()));
            assertTrue(PLAIN.spawns(new NeverDrawn()));
        }

        @Test
        void eachLayerRisesByItsOwnDrawTimesItsIndex() {
            BlockFieldProfile storm = new BlockFieldProfile(4, 10, 3, 4, 12, 19, 50, 4, 200, -1);
            // layers: 3 + draw(bound 2) = 3. steps: 12 + draw(bound 8) = 12, 19, 15 — one draw per layer.
            ScriptedRandom rnd = new ScriptedRandom(new int[] {0, 0, 7, 3});

            // layer 0 is always the bare height; the rest rise by their OWN step times their index.
            assertArrayEquals(new int[] {10, 10 + 19, 10 + 15 * 2}, storm.layerYOffsets(rnd));
        }

        @Test
        void theTopLayerReachesTheProfilesFullVerticalSpan() {
            // The reason `height`'s range had to widen: the tallest authorable layer of this profile sits far
            // above the origin, and a range that cannot express it silently truncates the storm.
            BlockFieldProfile storm = new BlockFieldProfile(4, 10, 3, 4, 12, 19, 50, 4, 200, -1);
            int[] offsets = storm.layerYOffsets(new ScriptedRandom(new int[] {1, 7, 7, 7, 7}));

            assertEquals(4, offsets.length);
            assertEquals(10 + 19 * 3, offsets[3]);
        }

        @Test
        void densityIsAHalfOpenPerPositionDrawAndZeroRainsNothing() {
            BlockFieldProfile half = new BlockFieldProfile(4, 10, 1, 1, 0, 0, 50, 0, 0, -1);
            ScriptedRandom rnd = new ScriptedRandom(new int[0], new double[] {0.49, 0.50, 0.51});

            assertTrue(half.spawns(rnd));
            assertFalse(half.spawns(rnd), "the boundary draw is a miss — half-open, so 50% is really 50%");
            assertFalse(half.spawns(rnd));

            BlockFieldProfile none = new BlockFieldProfile(4, 10, 1, 1, 0, 0, 0, 0, 0, -1);
            assertFalse(none.spawns(new NeverDrawn()), "a zero density short-circuits rather than drawing");
        }
    }

    @Nested
    class StrikeField {

        /** 16 points, ±2..9, 1s delay, radius 3, 16 half-hearts floored at 1 — a test-owned fixture. */
        private static final StrikeFieldProfile FIELD =
                new StrikeFieldProfile(16, 2, 9, 20, 3.0, 32.0, "ENEMIES", 16.0, 1.0);

        @Test
        void eachAxisIsAnIndependentSignedDraw() {
            // magnitude 2 + draw(bound 8), then a sign per axis — so a point can land in any quadrant, and the
            // two axes never mirror each other.
            ScriptedRandom rnd = new ScriptedRandom(new int[] {0, 7}, new double[0], new boolean[] {true, false});

            assertArrayEquals(new int[] {2, -9}, FIELD.drawOffset(rnd));
        }

        @Test
        void theHitTestIsSquaredAndInclusiveAtTheRadius() {
            assertTrue(FIELD.hits(0.0));
            assertTrue(FIELD.hits(9.0), "a body exactly on the radius is inside the strike");
            assertFalse(FIELD.hits(9.001));
        }

        @Test
        void theFloorKeepsAStrikeFromKillingHoweverManyPointsOverlap() {
            assertEquals(4.0, FIELD.struckHealth(20.0));
            assertEquals(1.0, FIELD.struckHealth(17.0), "at damage+floor the strike lands exactly on the floor");
            assertEquals(1.5, FIELD.struckHealth(17.5));
            assertEquals(1.0, FIELD.struckHealth(10.0), "below the threshold it can only ever reach the floor");
            // Points are never de-duplicated, so the floor is what has to hold — not a single-hit budget.
            assertEquals(1.0, FIELD.struckHealth(FIELD.struckHealth(FIELD.struckHealth(20.0))));
        }
    }

    /** A {@link Random} with a scripted draw sequence; a draw outside the bound the profile computed is a failure. */
    private static final class ScriptedRandom extends Random {

        private final int[] ints;
        private final double[] doubles;
        private final boolean[] booleans;
        private int i;
        private int d;
        private int b;

        ScriptedRandom(int[] ints) {
            this(ints, new double[0], new boolean[0]);
        }

        ScriptedRandom(int[] ints, double[] doubles) {
            this(ints, doubles, new boolean[0]);
        }

        ScriptedRandom(int[] ints, double[] doubles, boolean[] booleans) {
            super(0);
            this.ints = ints;
            this.doubles = doubles;
            this.booleans = booleans;
        }

        @Override
        public int nextInt(int bound) {
            int value = ints[i++];
            if (value >= bound) {
                throw new AssertionError("scripted draw " + value + " is outside the bound " + bound);
            }
            return value;
        }

        @Override
        public double nextDouble() {
            return doubles[d++];
        }

        @Override
        public boolean nextBoolean() {
            return booleans[b++];
        }
    }

    /** A {@link Random} that fails if it is drawn from at all — the instrument for "this path costs no draw". */
    private static final class NeverDrawn extends Random {

        NeverDrawn() {
            super(0);
        }

        @Override
        public int nextInt(int bound) {
            throw new AssertionError("drew nextInt on a path that must consume no randomness");
        }

        @Override
        public double nextDouble() {
            throw new AssertionError("drew nextDouble on a path that must consume no randomness");
        }

        @Override
        public boolean nextBoolean() {
            throw new AssertionError("drew nextBoolean on a path that must consume no randomness");
        }
    }
}
