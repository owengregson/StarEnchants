package engine.selector.kind;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Shared block-grid + facing math for the mining-shape selectors ({@link TrenchSelector},
 * {@link TunnelSelector}, docs/v3-directives.md §A). These are PURE computations over the actor's facing
 * and a base block — no world read — so the shapes are unit-testable; the effect that consumes the
 * locations ({@code BREAK_BLOCK}/{@code SET_BLOCK}) is what actually touches the world.
 */
final class BlockShapes {

    private BlockShapes() {
    }

    /** The block-grid-snapped location of {@code loc}, or {@code null}. */
    static Location block(Location loc) {
        return loc == null ? null : loc.getBlock().getLocation();
    }

    /**
     * The actor's dominant facing as a unit step on ONE axis ({@code {±1,0,0}}/{@code {0,±1,0}}/
     * {@code {0,0,±1}}), or {@code null} if the actor is absent — the "forward" a tunnel/trench grows along.
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
