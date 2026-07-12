package engine.sink;

import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The pure geometry of the ADR-0052 CAGE — the base point and the volume-safety verdict, extracted so the
 * pets right-click PRE-check (which aborts before the cooldown arms) and {@link Sink#cage} placement can
 * never drift on WHERE the cage sits or WHICH cells must be clear. What counts as "clear" is the caller's
 * era-correct block predicate (the sink's replace-mode-0 air test; the feature's {@code ActorProbe} air
 * test), so this stays server-free apart from the block reads that predicate performs.
 */
public final class CageGeometry {

    private CageGeometry() {
    }

    /**
     * The cage base: the floored horizontal midpoint of {@code actor} and {@code victim}, {@code rise} blocks
     * above the lower of their two feet — the exact point {@code CageEffect} emits and {@link Sink#cage}
     * builds around. The result carries {@code victim}'s world (the caller guarantees both share it).
     */
    public static Location origin(Location actor, Location victim, int rise) {
        return new Location(victim.getWorld(),
                Math.floor((actor.getX() + victim.getX()) / 2.0),
                Math.floor(Math.min(actor.getY(), victim.getY())) + rise,
                Math.floor((actor.getZ() + victim.getZ()) / 2.0));
    }

    /**
     * Whether EVERY cell of the full cage structure volume (floor + roof plates, wall ring, air interior) at
     * {@code origin} is currently clear — the exact volume {@link Sink#cage} safety-checks before it places
     * anything. {@code clear} is the caller's era-correct block predicate (replace-mode-0 air). Block reads
     * run on whatever region {@code origin}'s cells belong to; the CALLER guards a cross-region fault.
     */
    public static boolean volumeClear(World world, Location origin, int width, int height, int depth,
                                      Predicate<Block> clear) {
        int cx = origin.getBlockX();
        int baseY = origin.getBlockY(); // interior floor level; the floor PLATE sits at baseY - 1
        int cz = origin.getBlockZ();
        int hx = (width - 1) / 2;
        int hz = (depth - 1) / 2;
        for (int dx = -hx - 1; dx < width - hx + 1; dx++) {
            for (int dz = -hz - 1; dz < depth - hz + 1; dz++) {
                for (int dy = -1; dy <= height; dy++) {
                    if (!clear.test(world.getBlockAt(cx + dx, baseY + dy, cz + dz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
