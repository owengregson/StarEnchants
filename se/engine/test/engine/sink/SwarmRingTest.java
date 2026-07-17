package engine.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Hand-computed pins of the SPAWN_SWARM ring/damping math (ADR-0060). */
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
}
