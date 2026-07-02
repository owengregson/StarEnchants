package bootstrap.compat;

import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Legacy (1.8.9) targeting (ADR-0044) — the era-exclusive {@code overlay/legacy} line-of-sight scan, extracted
 * from the bindings (its {@code targetEntity} loop is real logic, not delegation). 1.8 has no
 * {@code getTargetEntity}/{@code getTargetBlockExact} raytrace: the entity target is approximated by a
 * dot-product scan over nearby entities, and the block target uses the 1.8 {@code getTargetBlock(Set,int)}
 * overload (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class LegacyTargets {

    private LegacyTargets() {
    }

    public static Entity targetEntity(Player from, int maxDistance) {
        Location eye = from.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : from.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            // aim at roughly torso height, not the feet, so a level look-direction still hits
            Vector toCandidate = candidate.getLocation().add(0.0, 1.0, 0.0).toVector().subtract(eye.toVector());
            double distance = toCandidate.length();
            if (distance > maxDistance || distance < 1.0E-4) {
                continue;
            }
            if (toCandidate.normalize().dot(direction) > 0.99 && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    @SuppressWarnings("deprecation") // getTargetBlock(Set,int) is the 1.8 line-of-sight block lookup
    public static Block targetBlock(Player from, int maxDistance) {
        return from.getTargetBlock((Set<Material>) null, maxDistance);
    }
}
