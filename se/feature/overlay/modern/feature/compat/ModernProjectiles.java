package feature.compat;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * Modern impl of {@link Projectiles} — the era-exclusive {@code overlay/modern} projectile typing (ADR-0044; §4).
 * {@code Trident} (1.13) and {@code AbstractArrow} (1.14) do not exist on 1.8.9.
 */
public final class ModernProjectiles implements Projectiles {

    @Override
    public boolean isTrident(Entity entity) {
        return entity instanceof Trident;
    }

    @Override
    public boolean isArrowLike(Entity entity) {
        return entity instanceof AbstractArrow;
    }

    @Override
    public Location landingOf(ProjectileHitEvent event) {
        if (event.getHitEntity() != null) {
            return null; // BOW/ATTACK owns entity hits — PROJECTILE_LAND must not double-dispatch them
        }
        Block block = event.getHitBlock();
        // Block centre, not the corner: an @Aoe anchored on the corner reaches a block less on one side.
        return block != null ? block.getLocation().add(0.5, 0.5, 0.5) : event.getEntity().getLocation();
    }

    @Override
    public String kindOf(Entity entity) {
        if (entity instanceof AbstractArrow) {
            return ARROW; // tipped / spectral / trident all bucket as ARROW — the shot, not the ammo
        }
        if (entity instanceof Fireball) {
            return FIREBALL; // incl. small/large/dragon fireballs and wither skulls
        }
        if (entity instanceof ThrowableProjectile) {
            return THROWN; // snowball, egg, ender pearl, splash potion, xp bottle
        }
        return OTHER;
    }
}
