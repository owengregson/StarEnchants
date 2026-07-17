package engine.sink;

/** Ring placement + AI-speed damping math for SPAWN_SWARM (ADR-0060). Pure — unit-tested by hand. */
public final class SwarmRing {

    /** The Bat-AI lerp constant: {@code v' = v + (target - v) * K} per tick (Bat.customServerAiStep). */
    private static final double K = 0.1;

    private SwarmRing() {
    }

    /** Ring slot {@code i} of {@code n}, evenly spaced starting at yaw 0 (+Z), in degrees. */
    public static float yawDegrees(int i, int n) {
        return (float) (i * (360.0 / Math.max(1, n)));
    }

    /** X of the outward unit vector for {@code yawDegrees} scaled by {@code r} — MC yaw θ faces (−sin θ, cos θ). */
    public static double offsetX(float yawDegrees, double r) {
        return -Math.sin(Math.toRadians(yawDegrees)) * r;
    }

    /** Z of the outward unit vector for {@code yawDegrees} scaled by {@code r}. */
    public static double offsetZ(float yawDegrees, double r) {
        return Math.cos(Math.toRadians(yawDegrees)) * r;
    }

    /**
     * Per-tick velocity multiplier that settles a lerp-to-target AI at {@code fraction} of its vanilla
     * steady-state speed: s = q / (K + q(1 − K)). Exact when the damp runs after the AI step; tick
     * ordering varies by platform, so the realized fraction sits within ~±10% of q — stated honestly.
     * Out-of-range fractions (≤ 0, ≥ 1) mean "no damping".
     */
    public static double dampingFactor(double fraction) {
        if (fraction <= 0.0 || fraction >= 1.0) {
            return 1.0;
        }
        return fraction / (K + fraction * (1.0 - K));
    }
}
