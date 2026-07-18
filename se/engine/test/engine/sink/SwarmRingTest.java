package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Hand-computed pins of the SPAWN_SWARM ring/damping (ADR-0060) + cloud-orbit (ADR-0068) math. */
class SwarmRingTest {

    @Test
    void slotsAreEvenlySpaced() {
        assertEquals(0.0f, SwarmRing.yawDegrees(0, 4));
        assertEquals(90.0f, SwarmRing.yawDegrees(1, 4));
        assertEquals(270.0f, SwarmRing.yawDegrees(3, 4));
        assertEquals(36.0f, SwarmRing.yawDegrees(1, 10));
    }

    @Test
    void offsetsPointOutwardAlongTheFacing() {
        // MC yaw 0 faces +Z; 90 faces −X; 180 faces −Z; 270 faces +X — position offset == facing × r.
        double r = 0.5;
        assertEquals(0.0, SwarmRing.offsetX(0.0f, r), 1e-9);
        assertEquals(r, SwarmRing.offsetZ(0.0f, r), 1e-9);
        assertEquals(-r, SwarmRing.offsetX(90.0f, r), 1e-9);
        assertEquals(0.0, SwarmRing.offsetZ(90.0f, r), 1e-9);
        assertEquals(0.0, SwarmRing.offsetX(180.0f, r), 1e-9);
        assertEquals(-r, SwarmRing.offsetZ(180.0f, r), 1e-9);
        assertEquals(r, SwarmRing.offsetX(270.0f, r), 1e-9);
        assertEquals(0.0, SwarmRing.offsetZ(270.0f, r), 1e-9);
    }

    @Test
    void dampingSettlesTheLerpAiAtTheFraction() {
        // Simulate the Bat AI (v' = v + (T − v)·0.1) with the post-step damp: must converge to 0.5·T.
        double s = SwarmRing.dampingFactor(0.5);
        assertEquals(0.5 / (0.1 + 0.5 * 0.9), s, 1e-12);
        double v = 0.0;
        double target = 0.5;
        for (int tick = 0; tick < 400; tick++) {
            v = (v + (target - v) * 0.1) * s;
        }
        assertEquals(0.5 * target, v, 1e-6);
    }

    @Test
    void outOfRangeFractionsMeanNoDamping() {
        assertEquals(1.0, SwarmRing.dampingFactor(1.0));
        assertEquals(1.0, SwarmRing.dampingFactor(0.0));
        assertEquals(1.0, SwarmRing.dampingFactor(-3.0));
        assertEquals(1.0, SwarmRing.dampingFactor(2.0));
    }

    @Test
    void orbitPhasesSpreadEvenly() {
        assertEquals(0.0, SwarmRing.orbitPhase(0, 8));
        assertEquals(Math.PI / 2, SwarmRing.orbitPhase(2, 8), 1e-9);
        assertEquals(7 * Math.PI / 4, SwarmRing.orbitPhase(7, 8), 1e-9);
        assertEquals(0.0, SwarmRing.orbitPhase(0, 1));
        assertEquals(0.0, SwarmRing.orbitPhase(0, 0), "n=0 guard");
    }

    @Test
    void bandHeightsCoverThePillarAndWrap() {
        assertEquals(0.2, SwarmRing.bandHeight(0), 1e-9);
        assertEquals(0.6, SwarmRing.bandHeight(1), 1e-9);
        assertEquals(1.0, SwarmRing.bandHeight(2), 1e-9);
        assertEquals(1.4, SwarmRing.bandHeight(3), 1e-9);
        assertEquals(1.8, SwarmRing.bandHeight(4), 1e-9);
        assertEquals(0.2, SwarmRing.bandHeight(5), 1e-9, "band 5 wraps to the foot");
        assertEquals(1.0, SwarmRing.bandHeight(12), 1e-9, "12 % 5 = 2 — catches a transposed band table");
    }

    @Test
    void orbitPointsSitOnTheCircleAndAdvanceByOmega() {
        double cx = 10.0;
        double cz = -4.0;
        assertEquals(10.9, SwarmRing.orbitX(cx, 0.0, 0), 1e-9);
        assertEquals(-4.0, SwarmRing.orbitZ(cz, 0.0, 0), 1e-9);
        for (long t : new long[] {0, 7, 31}) {
            double dx = SwarmRing.orbitX(cx, 0.0, t) - cx;
            double dz = SwarmRing.orbitZ(cz, 0.0, t) - cz;
            assertEquals(SwarmRing.CLOUD_ORBIT_RADIUS, Math.hypot(dx, dz), 1e-9);
            double angleNow = Math.atan2(dz, dx);
            double angleNext = Math.atan2(SwarmRing.orbitZ(cz, 0.0, t + 1) - cz,
                    SwarmRing.orbitX(cx, 0.0, t + 1) - cx);
            double delta = angleNext - angleNow;
            if (delta <= -Math.PI) {
                delta += Math.PI * 2; // atan2 wrap at ±π
            }
            assertEquals(SwarmRing.CLOUD_OMEGA, delta, 1e-9);
        }
    }

    @Test
    void orbitYStaysInsideTheBobBandAroundItsBand() {
        assertEquals(64 + 1.0 + 0.15 * Math.sin(0.3), SwarmRing.orbitY(64.0, 1.0, 0.3, 0));
        for (long t = 0; t <= 200; t++) {
            double y = SwarmRing.orbitY(64.0, 1.0, 0.3, t);
            assertTrue(y >= 65.0 - 0.15 - 1e-9 && y <= 65.0 + 0.15 + 1e-9, "t=" + t + " y=" + y);
        }
    }

    @Test
    void chaseScaleClampsToTheCap() {
        assertEquals(1.0, SwarmRing.chaseScale(0.3, 0.0, 0.0));
        assertEquals(0.2, SwarmRing.chaseScale(0.0, 0.0, 4.5), 1e-12); // 0.9 / 4.5
        assertEquals(1.0, SwarmRing.chaseScale(0.9, 0.0, 0.0), "the cap itself is unscaled (inclusive)");
        assertEquals(1.0, SwarmRing.chaseScale(0.0, 0.0, 0.0), "a zero vector scales to zero either way");
    }

    @Test
    void chaseCapOutrunsTheOrbitPlusASprintingTarget() {
        // The bug this catches: retuning Ω/R/cap into a cloud that can never hold its orbit.
        assertTrue(SwarmRing.CLOUD_CHASE_CAP > SwarmRing.CLOUD_OMEGA * SwarmRing.CLOUD_ORBIT_RADIUS + 0.3);
    }

    @Test
    void steeringConvergesOnAMovingPillar() {
        // The per-tick consumer step (seek the moving orbit point, clamped) against a sprinting pillar.
        double px = 5.0;
        double py = 0.0;
        double pz = 0.0;
        double band = 1.0;
        double pillarX = 0.0;
        for (long t = 1; t <= 100; t++) {
            pillarX += 0.2; // sprint-ish
            double dx = SwarmRing.orbitX(pillarX, 0.0, t) - px;
            double dy = SwarmRing.orbitY(0.0, band, 0.0, t) - py;
            double dz = SwarmRing.orbitZ(0.0, 0.0, t) - pz;
            double clamp = SwarmRing.chaseScale(dx, dy, dz);
            px += dx * clamp;
            py += dy * clamp;
            pz += dz * clamp;
            if (t >= 60) {
                assertTrue(Math.hypot(px - pillarX, pz) <= SwarmRing.CLOUD_ORBIT_RADIUS + 0.2,
                        "t=" + t + " horizontal drift");
                assertTrue(Math.abs(py - band) <= SwarmRing.CLOUD_BOB_AMPLITUDE + 0.05,
                        "t=" + t + " vertical drift");
            }
        }
    }

    @Test
    void yJitterMapsTheUnitRollToTheScatterRange() {
        assertEquals(-0.6, SwarmRing.yJitter(0.0), 1e-12);
        assertEquals(0.0, SwarmRing.yJitter(0.5), 1e-12);
        assertEquals(-0.3, SwarmRing.yJitter(0.25), 1e-12);
        double top = SwarmRing.yJitter(0.9999);
        assertTrue(top > 0.59 && top < 0.6, "the top of the half-open range");
    }
}
