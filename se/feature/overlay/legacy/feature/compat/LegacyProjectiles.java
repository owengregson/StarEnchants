package feature.compat;

import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.ProjectileHitEvent;

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

    @Override
    public Location landingOf(ProjectileHitEvent event) {
        // 1.8.8 cannot answer this — see Projectiles#landingOf for the javap/bytecode evidence. PROJECTILE_LAND
        // is therefore inert on 1.8 rather than double-dispatching every entity hit BOW already handled.
        return null;
    }

    @Override
    public String kindOf(Entity entity) {
        if (entity instanceof Arrow) {
            return ARROW;
        }
        if (entity instanceof Fireball) {
            return FIREBALL;
        }
        // 1.8 has no ThrowableProjectile marker, so the throwables are listed by type.
        if (entity instanceof Snowball || entity instanceof Egg || entity instanceof EnderPearl
                || entity instanceof ThrownPotion || entity instanceof ThrownExpBottle) {
            return THROWN;
        }
        return OTHER;
    }
}
