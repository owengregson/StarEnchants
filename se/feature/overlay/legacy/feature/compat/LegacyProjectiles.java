package feature.compat;

import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.entity.ProjectileHitEvent;
import platform.sched.Scheduling;

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
    public void landing(ProjectileHitEvent event, Consumer<Location> land) {
        // 1.8's hit event fires pre-branch and pre-move (see Projectiles#landing), so the answer is a tick away,
        // not absent. Arrows only: a thrown projectile or fireball is already dead when its event fires.
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        Scheduling.onEntityLater(arrow, 1L, () -> {
            // A lodged arrow is still alive and now sits at the impact; one that hit an entity was die()d, which
            // is exactly the discrimination the event could not make — BOW already dispatched that hit.
            if (!arrow.isValid() || !((CraftArrow) arrow).getHandle().isInGround()) {
                return;
            }
            if (event.getEntity().getShooter() instanceof Player shooter && shooter.isOnline()) {
                land.accept(arrow.getLocation());
            }
        });
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
