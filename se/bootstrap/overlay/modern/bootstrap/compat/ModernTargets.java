package bootstrap.compat;

import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Modern targeting (ADR-0044) — the era-exclusive {@code overlay/modern} acquisition rays, extracted
 * from the bindings (real logic, the {@code LegacyTargets} shape).
 *
 * <p>Not {@code getTargetEntity}: that ray inflates candidate boxes only by {@code getPickRadius()} —
 * 0.0 for players and mobs — so acquisition demands an exact server-side hitbox intersection at click
 * time, which whiffs most real attempts against a moving target under latency (the reforge actives then
 * burn their cooldown on silence). A 0.3-inflated ray is the forgiving-but-fair acquisition every
 * look-targeted active wants; the COLLIDER block pre-clip keeps walls shielding targets without letting
 * tall grass eat the ray. And not {@code getTargetBlockExact}: its OUTLINE clip collides with
 * no-collision decoration (tall grass, flowers, signs), which anchored Singularity wells on the tuft two
 * blocks ahead of the caster — COLLIDER semantics target what a player means by "that block".
 */
public final class ModernTargets {

    private static final double RAY_INFLATE = 0.3;

    private ModernTargets() {
    }

    public static Entity targetEntity(Player from, int maxDistance) {
        Location eye = from.getEyeLocation();
        Vector dir = eye.getDirection();
        double range = maxDistance;
        RayTraceResult wall = from.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (wall != null) {
            range = Math.max(0.0, wall.getHitPosition().distance(eye.toVector()));
        }
        RayTraceResult hit = from.getWorld().rayTraceEntities(eye, dir, range, RAY_INFLATE,
                e -> e != from && e instanceof LivingEntity && !(e instanceof ArmorStand)
                        && !(e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR));
        return hit == null ? null : hit.getHitEntity();
    }

    public static Block targetBlock(Player from, int maxDistance) {
        Location eye = from.getEyeLocation();
        RayTraceResult hit = from.getWorld().rayTraceBlocks(eye, eye.getDirection(), maxDistance,
                FluidCollisionMode.NEVER, true);
        return hit == null ? null : hit.getHitBlock();
    }
}
