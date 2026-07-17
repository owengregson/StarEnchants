package feature.pet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The home-window visuals geometry (ADR-0061 amendment): line stepping + cap, ring math, colour fallback. */
class PetHomeVisualsTest {

    @Test
    void linePointsStepAtDensityAndKeepBothEndpoints() {
        // distinct values per axis so a transposed coordinate fails loudly
        double[][] points = PetHomeVisuals.linePoints(1.0, 64.0, -2.0, 6.0, 66.0, -2.0, 2.0, 100);
        // dist = sqrt(25 + 4) ≈ 5.385 → round(10.77) = 11 steps → 12 inclusive points
        assertEquals(12, points.length);
        assertArrayEquals(new double[]{1.0, 64.0, -2.0}, points[0]);
        assertArrayEquals(new double[]{6.0, 66.0, -2.0}, points[points.length - 1], 1e-9);
    }

    @Test
    void lineIsBoundedByMaxSteps() {
        double[][] points = PetHomeVisuals.linePoints(0, 0, 0, 500.0, 0, 0, 2.0, 100);
        assertEquals(101, points.length); // capped, not 1001 — the past-range wander bound
        assertArrayEquals(new double[]{500.0, 0, 0}, points[100], 1e-9); // the far endpoint is still reached
    }

    @Test
    void zeroLengthLineStillEmitsTheAnchors() {
        assertEquals(2, PetHomeVisuals.linePoints(3, 3, 3, 3, 3, 3, 2.0, 100).length);
    }

    @Test
    void ringPointsAreEvenlySpacedAtTheGivenRadiusAndHeight() {
        double[][] points = PetHomeVisuals.ringPoints(10.0, 64.5, -4.0, 0.7, 4);
        assertEquals(4, points.length);
        assertArrayEquals(new double[]{10.7, 64.5, -4.0}, points[0], 1e-9);
        assertArrayEquals(new double[]{10.0, 64.5, -3.3}, points[1], 1e-9);
        assertArrayEquals(new double[]{9.3, 64.5, -4.0}, points[2], 1e-9);
        assertArrayEquals(new double[]{10.0, 64.5, -4.7}, points[3], 1e-9);
    }

    @Test
    void colourResolvesHexAndFallsBackToWhite() {
        assertArrayEquals(new int[]{16, 32, 48}, PetHomeVisuals.rgbOrWhite("{#102030}")); // test-owned token
        assertArrayEquals(new int[]{255, 255, 255}, PetHomeVisuals.rgbOrWhite(""));
        assertArrayEquals(new int[]{255, 255, 255}, PetHomeVisuals.rgbOrWhite(null));
    }
}
