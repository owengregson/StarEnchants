package feature.compat;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;

/**
 * Legacy (1.8.9) impl of {@link Projectiles} — the era-exclusive {@code overlay/legacy} projectile typing
 * (ADR-0044; §4). 1.8 has no {@code Trident} (1.13) or {@code AbstractArrow} (1.14); the only arrow type is
 * {@code Arrow}.
 */
public final class LegacyProjectiles implements Projectiles {

    @Override
    public boolean isTrident(Entity entity) {
        return false; // no tridents on 1.8
    }

    @Override
    public boolean isArrowLike(Entity entity) {
        return entity instanceof Arrow; // the only arrow type on 1.8
    }
}
