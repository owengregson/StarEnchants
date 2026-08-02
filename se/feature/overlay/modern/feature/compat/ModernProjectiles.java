package feature.compat;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;

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
