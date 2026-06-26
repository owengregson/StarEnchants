package feature.compat;

import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Trident;

/**
 * Modern projectile-type predicates for combat trigger routing. Same-FQN counterpart to the
 * {@code overlay/legacy} impl; {@code Trident} (1.13) and {@code AbstractArrow} (1.14) do not exist on
 * 1.8.9 (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Projectiles {

    private Projectiles() {
    }

    public static boolean isTrident(Entity entity) {
        return entity instanceof Trident;
    }

    /** Any arrow-family projectile (tipped/spectral/normal) — the BOW-trigger family. */
    public static boolean isArrowLike(Entity entity) {
        return entity instanceof AbstractArrow;
    }
}
