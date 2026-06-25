package feature.combat;

import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import platform.sched.Scheduling;
import platform.sched.TaskHandle;

/**
 * The {@code SEEK} (Cosmic Enchants-style {@code AUTO_LOCK}) homing engine: pick the best line-of-sight target for a freshly
 * fired projectile and steer it toward that target each tick until it lands, dies, loses the target, or
 * flies out of range. Started by the bow dispatcher after a {@code SEEK} proc; the steering runs on the
 * projectile's OWN entity scheduler ({@link Scheduling#repeatingEntity}) so it is Folia-region-correct for
 * the arrow.
 *
 * <p>Folia caveat: reading the target's location each tick is region-correct only while the target shares
 * the arrow's region (the common case for short-range homing). A cross-region read throws on Folia, so each
 * steering tick is wrapped — a failure simply cancels the homing (the arrow continues ballistically) rather
 * than aborting. On Paper every read is on the one thread, so homing is exact. A faithful port of a Cosmic Enchants-style
 * {@code AutoLockTask} (angle-limited velocity steering, slowed near a blocking target).
 */
public final class ProjectileHoming {

    private static final double MAX_DISTANCE = 60.0;   // give up past this range (Cosmic Enchants-style parity)
    private static final double SEARCH_RANGE = 64.0;   // initial target search box (Cosmic Enchants-style parity)
    private static final double MAX_CONE = 18.283185307179586; // a Cosmic Enchants-style initial best-angle sentinel
    private static final double MAX_ROTATION = 0.12;   // per-tick steering cone (radians, Cosmic Enchants-style parity)

    private ProjectileHoming() {
    }

    /**
     * Find the best target in {@code shooter}'s line of sight and, if any, start homing {@code projectile}
     * onto it. Runs on the firing (shoot-event) thread; the target search reads the shooter's region (owned
     * here). A no-op if there is no valid target. The returned task auto-cancels itself when it completes.
     */
    public static void start(Player shooter, Projectile projectile) {
        if (shooter == null || projectile == null) {
            return;
        }
        LivingEntity target = bestTarget(shooter, projectile);
        if (target == null) {
            return;
        }
        AtomicReference<TaskHandle> self = new AtomicReference<>();
        TaskHandle handle = Scheduling.repeatingEntity(projectile, 1L, 1L, () -> step(projectile, target, self));
        self.set(handle);
    }

    /** The nearest living entity within the projectile's forward cone and line of sight, excluding the shooter. */
    private static LivingEntity bestTarget(Player shooter, Projectile projectile) {
        Vector velocity = projectile.getVelocity();
        double best = MAX_CONE;
        LivingEntity chosen = null;
        for (Entity nearby : shooter.getNearbyEntities(SEARCH_RANGE, SEARCH_RANGE, SEARCH_RANGE)) {
            if (!(nearby instanceof LivingEntity living) || living.isDead() || living.equals(shooter)
                    || !shooter.hasLineOfSight(living)) {
                continue;
            }
            Vector toTarget = living.getLocation().toVector().subtract(shooter.getLocation().toVector());
            if (toTarget.lengthSquared() < 1.0e-6) {
                continue;
            }
            double angle = velocity.angle(toTarget);
            if (angle < best) {
                best = angle;
                chosen = living;
            }
        }
        return chosen;
    }

    /** One steering tick: re-aim the projectile's velocity toward the target, or cancel when the chase ends. */
    private static void step(Projectile projectile, LivingEntity target, AtomicReference<TaskHandle> self) {
        try {
            if (projectile.isDead() || projectile.isOnGround() || target.isDead()
                    || projectile.getLocation().distance(target.getLocation()) >= MAX_DISTANCE) {
                cancel(self);
                return;
            }
            double speed = projectile.getVelocity().length();
            Vector toTarget = target.getLocation().clone().add(0.0, 0.5, 0.0).toVector()
                    .subtract(projectile.getLocation().toVector());
            Vector heading = projectile.getVelocity().clone().normalize();
            Vector desired = toTarget.clone().normalize();
            double angle = heading.angle(desired);
            double newSpeed = 0.9 * speed + 0.14;
            if (target instanceof Player player && player.isBlocking()
                    && projectile.getLocation().distance(target.getLocation()) < 8.0) {
                newSpeed = speed * 0.6; // a blocking target deflects the homing, Cosmic Enchants-style parity
            }
            Vector steered = angle < MAX_ROTATION
                    ? heading.clone().multiply(newSpeed)
                    : heading.clone().multiply((angle - MAX_ROTATION) / angle)
                            .add(desired.clone().multiply(MAX_ROTATION / angle)).normalize().multiply(newSpeed);
            projectile.setVelocity(steered.add(new Vector(0.0, 0.03, 0.0)));
        } catch (RuntimeException crossRegionOrGone) {
            // Folia cross-region target read, or the entity vanished mid-flight — stop homing, fly ballistic.
            cancel(self);
        }
    }

    private static void cancel(AtomicReference<TaskHandle> self) {
        TaskHandle handle = self.get();
        if (handle != null) {
            handle.cancel();
        }
    }
}
