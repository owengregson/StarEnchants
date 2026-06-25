package engine.selector.kind;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Block-grid + facing math for the mining-shape selectors ({@link TrenchSelector}, {@link TunnelSelector}).
 * Pure computation, no world read — the consuming effect is what touches the world.
 */
final class BlockShapes {

    private BlockShapes() {
    }

    /** Block-grid-snapped {@code loc}, or {@code null}. */
    static Location block(Location loc) {
        return loc == null ? null : loc.getBlock().getLocation();
    }

    /**
     * Actor's dominant facing as a unit step on ONE axis ({@code {±1,0,0}}/{@code {0,±1,0}}/{@code {0,0,±1}}),
     * or {@code null} if the actor is absent — the "forward" a tunnel/trench grows along.
     */
    static int[] facing(Player actor) {
        if (actor == null) {
            return null;
        }
        Vector d = actor.getLocation().getDirection();
        double ax = Math.abs(d.getX());
        double ay = Math.abs(d.getY());
        double az = Math.abs(d.getZ());
        if (ax >= ay && ax >= az) {
            return new int[] {sign(d.getX()), 0, 0};
        }
        if (az >= ax && az >= ay) {
            return new int[] {0, 0, sign(d.getZ())};
        }
        return new int[] {0, sign(d.getY()), 0};
    }

    /** The two unit axes perpendicular to a forward axis (the plane a trench fills). */
    static int[][] perpendicular(int[] forward) {
        if (forward[0] != 0) {
            return new int[][] {{0, 1, 0}, {0, 0, 1}}; // forward = X → fill the Y,Z plane
        }
        if (forward[2] != 0) {
            return new int[][] {{1, 0, 0}, {0, 1, 0}}; // forward = Z → fill the X,Y plane
        }
        return new int[][] {{1, 0, 0}, {0, 0, 1}};     // forward = Y → fill the X,Z plane
    }

    private static int sign(double v) {
        return v > 0 ? 1 : (v < 0 ? -1 : 1); // a zero component on the dominant axis can't happen; bias +1
    }
}
