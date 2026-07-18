package engine.sink;

/**
 * Ring placement + AI-speed damping math for SPAWN_SWARM (ADR-0060) + attacker-cloud orbit math
 * (ADR-0068). Pure — unit-tested by hand.
 */
public final class SwarmRing {

    /** The Bat-AI lerp constant: {@code v' = v + (target - v) * K} per tick (Bat.customServerAiStep). */
    private static final double K = 0.1;

    /** The pillar column sits this many blocks in front of the attacker's feet (ADR-0068). */
    public static final double CLOUD_PILLAR_FORWARD = 1.0;
    /** Tight orbit that keeps bodies crossing a 1x1 column's sightline. */
    public static final double CLOUD_ORBIT_RADIUS = 0.9;
    /** rad/tick — one lap ≈ 18 ticks ≈ 0.9 s: reads as a cloud, not a carousel. */
    public static final double CLOUD_OMEGA = 0.35;
    /** Vertical shimmer amplitude, blocks. */
    public static final double CLOUD_BOB_AMPLITUDE = 0.15;
    /** rad/tick — slow, phase-desynced bob. */
    public static final double CLOUD_BOB_OMEGA = 0.12;
    /** blocks/tick velocity clamp; MUST exceed tangential Ω·R + a sprint ≈ 0.28 (unit-pinned). */
    public static final double CLOUD_CHASE_CAP = 0.9;
    /** Height bands span the 2-block pillar foot→head. */
    public static final int CLOUD_HEIGHT_BANDS = 5;
    /** Lowest/highest band — keeps bats inside the 1x2x1 visual column. */
    public static final double CLOUD_BAND_MIN = 0.2;
    public static final double CLOUD_BAND_MAX = 1.8;
    /** The owner-specified ±0.6 spawn-Y scatter (ADR-0068). */
    public static final double SWARM_Y_JITTER = 0.6;

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

    /** Ring slot i of n → its fixed orbit phase offset (radians), spreading the swarm around the lap. */
    public static double orbitPhase(int i, int n) {
        return (Math.PI * 2.0 * i) / Math.max(1, n);
    }

    /** Slot i's height band above the pillar's feet — i%5 → {0.2, 0.6, 1.0, 1.4, 1.8}: adjacent phases sit
     *  in different bands, so the whole 1x2x1 column is clouded, not one ring. */
    public static double bandHeight(int i) {
        return CLOUD_BAND_MIN + (CLOUD_BAND_MAX - CLOUD_BAND_MIN)
                * (i % CLOUD_HEIGHT_BANDS) / (CLOUD_HEIGHT_BANDS - 1);
    }

    /** X of slot {@code phase}'s orbit point around pillar-center {@code centerX} at local tick t. */
    public static double orbitX(double centerX, double phase, long t) {
        return centerX + CLOUD_ORBIT_RADIUS * Math.cos(phase + CLOUD_OMEGA * t);
    }

    /** Z twin of {@link #orbitX}. */
    public static double orbitZ(double centerZ, double phase, long t) {
        return centerZ + CLOUD_ORBIT_RADIUS * Math.sin(phase + CLOUD_OMEGA * t);
    }

    /** Y: pillar feet + the slot's band + a phase-desynced bob. */
    public static double orbitY(double pillarFeetY, double band, double phase, long t) {
        return pillarFeetY + band + CLOUD_BOB_AMPLITUDE * Math.sin(phase + CLOUD_BOB_OMEGA * t);
    }

    /** Uniform velocity clamp: 1 when |d| ≤ CLOUD_CHASE_CAP, else CAP/|d| (a zero vector scales to zero). */
    public static double chaseScale(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return len <= CLOUD_CHASE_CAP ? 1.0 : CLOUD_CHASE_CAP / len;
    }

    /** Map a unit roll [0,1) to the spawn-Y scatter [−SWARM_Y_JITTER, +SWARM_Y_JITTER). */
    public static double yJitter(double roll) {
        return (roll - 0.5) * 2.0 * SWARM_Y_JITTER;
    }
}
