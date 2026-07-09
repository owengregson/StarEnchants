package feature.trak;

import feature.compat.Hands;
import feature.compat.Projectiles;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Remembers the launcher item behind each in-flight player projectile, so a trak kill can credit the weapon
 * that actually fired the fatal arrow/trident rather than whatever is held when the victim dies (§I F19). The
 * bow/trident is cloned at launch and keyed by the projectile's UUID; {@link #claim} removes-and-returns it on
 * the kill. Purely in-memory (no entity PDC/metadata era seam); entries older than {@link #TTL_NANOS} are
 * lazily evicted on insert, bounding the map by shots-per-minute. Folia-safe: a launch fires on the shooter's
 * own region thread, so the concurrent map needs no other locking.
 */
public final class ShotWeapons implements Listener {

    private static final long TTL_NANOS = 60_000_000_000L; // ~60s — a projectile that hasn't landed by then is gone

    private final ConcurrentHashMap<UUID, Shot> shots = new ConcurrentHashMap<>();
    private final Projectiles projectiles;
    private final Hands hands;

    private record Shot(ItemStack launcher, long stampNanos) {
    }

    public ShotWeapons(Projectiles projectiles, Hands hands) {
        this.projectiles = Objects.requireNonNull(projectiles, "projectiles");
        this.hands = Objects.requireNonNull(hands, "hands");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && event.getBow() != null && event.getProjectile() != null) {
            record(event.getProjectile().getUniqueId(), event.getBow());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        // A thrown trident carries no bow: the launcher is the shooter's main hand at throw time (era-safe
        // trident predicate — the Trident class is 1.13+). Arrows already arrive via EntityShootBowEvent.
        if (projectiles.isTrident(projectile) && projectile.getShooter() instanceof Player shooter) {
            ItemStack main = hands.mainHand(shooter);
            if (main != null) {
                record(projectile.getUniqueId(), main);
            }
        }
    }

    /** Remove and return the launcher cloned at this projectile's launch, or {@code null} if unknown/evicted. */
    public ItemStack claim(UUID projectileId) {
        Shot shot = shots.remove(projectileId);
        return shot == null ? null : shot.launcher();
    }

    private void record(UUID projectileId, ItemStack launcher) {
        long now = System.nanoTime();
        shots.values().removeIf(shot -> now - shot.stampNanos() > TTL_NANOS);
        shots.put(projectileId, new Shot(launcher.clone(), now));
    }
}
