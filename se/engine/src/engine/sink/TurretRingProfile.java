package engine.sink;

import java.util.Random;

/**
 * The authored shape of a TURRET_RING: where its emplacements stand, how long they live, what they may shoot
 * at, and how the refire jitter is drawn. An immutable carrier, so a volley scheduled many ticks out can never
 * alias a mutable argument (§3.6).
 *
 * <p>Every decision here is PURE — the sink owns the ground scan, the protection query and the spawn, but the
 * arithmetic that decides WHERE the ring sits, WHEN each turret fires next and WHICH of the bodies it can see
 * it shoots is arithmetic, and lives where it can be hand-checked without a server.
 */
public record TurretRingProfile(int turretTypeId, int count, double ringRadius, int ttlTicks,
                                double acquireRange, int initialDelayTicks, int periodMinTicks,
                                int periodMaxTicks, int projectileTypeId, double projectileSpeed,
                                String filter) {

    /**
     * Emplacement {@code index}'s horizontal offset from the ring's centre as {@code {dx, dz}} — the
     * {@code count} sites are evenly spaced, sharing SPAWN_SWARM's ring math so one ring formula serves both.
     */
    public double[] siteOffset(int index) {
        float yaw = SwarmRing.yawDegrees(index, count);
        return new double[] {SwarmRing.offsetX(yaw, ringRadius), SwarmRing.offsetZ(yaw, ringRadius)};
    }

    /**
     * Ticks until a turret's NEXT volley: a fresh draw in {@code [periodMin, periodMax]} inclusive, so a ring
     * never fires as one salvo. A degenerate range ({@code min >= max}) costs no draw at all.
     */
    public int drawPeriod(Random rnd) {
        int lo = Math.min(periodMinTicks, periodMaxTicks);
        int hi = Math.max(periodMinTicks, periodMaxTicks);
        return lo >= hi ? lo : lo + rnd.nextInt(hi - lo + 1);
    }

    /** Whether a body at this squared distance from a turret is inside its acquisition range. */
    public boolean inAcquireRange(double distanceSquared) {
        return distanceSquared <= acquireRange * acquireRange;
    }

    /**
     * The index of the NEAREST candidate still inside {@link #inAcquireRange}, or {@code -1} when none is.
     * Candidates arrive already judged for eligibility (side, sight, liveness) — this owns only "nearest, and
     * in range", and ties keep the FIRST candidate so an acquisition is stable across a stationary standoff.
     */
    public int nearest(double[] distancesSquared) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < distancesSquared.length; i++) {
            double distance = distancesSquared[i];
            if (inAcquireRange(distance) && distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }
}
