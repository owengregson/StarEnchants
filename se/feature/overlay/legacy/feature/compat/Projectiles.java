package feature.compat;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;

/**
 * Legacy (1.8.9) projectile-type predicates — same-FQN counterpart to the {@code overlay/modern} impl. 1.8
 * has no {@code Trident} (1.13) or {@code AbstractArrow} (1.14); the only arrow type is {@code Arrow}
 * (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Projectiles {

    private Projectiles() {
    }

    public static boolean isTrident(Entity entity) {
        return false; // no tridents on 1.8
    }

    public static boolean isArrowLike(Entity entity) {
        return entity instanceof Arrow; // the only arrow type on 1.8
    }
}
