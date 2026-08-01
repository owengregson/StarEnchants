package feature.combat;

import engine.selector.kind.Allies;
import engine.sink.GuardianCasts;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/** Friendly-fire, no-drop, and registry cleanup rules shared by GuardianCasts-owned summons. */
public final class GuardianProtectionListener implements Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID victimOwner = GuardianCasts.owner(event.getEntity().getUniqueId());
        if (victimOwner != null) {
            Player owner = Bukkit.getPlayer(victimOwner);
            Entity source = event.getDamager();
            if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                source = shooter;
            }
            if (source instanceof Player player && friendly(owner, victimOwner, player)) {
                event.setDamage(0.0);
                event.setCancelled(true);
                return;
            }
        }

        UUID damagerOwner = GuardianCasts.owner(event.getDamager().getUniqueId());
        if (damagerOwner != null && event.getEntity() instanceof Player player) {
            Player owner = Bukkit.getPlayer(damagerOwner);
            if (friendly(owner, damagerOwner, player)) {
                event.setDamage(0.0);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        UUID id = event.getEntity().getUniqueId();
        if (GuardianCasts.owner(id) != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            GuardianCasts.forget(id);
        }
    }

    private static boolean friendly(Player owner, UUID ownerId, Player other) {
        return ownerId.equals(other.getUniqueId()) || (owner != null && Allies.allied(owner, other));
    }
}
