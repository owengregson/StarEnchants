package engine.sink;

import java.util.Random;

/**
 * The authored shape of a DELAYED strike field: where its points land, how wide each one hits, and what the
 * hit does. An immutable carrier, so the deferred phase-2 batch can never alias a mutable argument (§3.6).
 *
 * <p>The payload is a RAW health subtraction with a floor rather than a damage application, and the floor is
 * what makes the ability survivable: {@link #struckHealth} can never return less than {@code healthFloor}, so
 * however many overlapping points cover one body, the field alone never kills.
 */
public record StrikeFieldProfile(int points, int offsetMin, int offsetMax, int delayTicks,
                                 double hitRadius, double targetRange, String filter,
                                 double damage, double healthFloor) {

    /** One point's horizontal offset from the origin: an INDEPENDENT ±[offsetMin, offsetMax] draw per axis. */
    public int[] drawOffset(Random rnd) {
        return new int[] {axisOffset(rnd), axisOffset(rnd)};
    }

    /** Whether a body at this squared distance from a stored point is inside the strike. */
    public boolean hits(double distanceSquared) {
        return distanceSquared <= hitRadius * hitRadius;
    }

    /**
     * The health a struck body is left at — a raw subtraction floored at {@code healthFloor}. Equivalent to the
     * authored pair "above the floor + damage, take damage; at or below it, set the floor", with no threshold to
     * keep in sync.
     */
    public double struckHealth(double currentHealth) {
        return Math.max(healthFloor, currentHealth - damage);
    }

    private int axisOffset(Random rnd) {
        int lo = Math.min(offsetMin, offsetMax);
        int hi = Math.max(offsetMin, offsetMax);
        int magnitude = lo >= hi ? lo : lo + rnd.nextInt(hi - lo + 1);
        return rnd.nextBoolean() ? magnitude : -magnitude;
    }
}
