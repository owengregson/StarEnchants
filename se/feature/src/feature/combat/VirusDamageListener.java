package feature.combat;

import engine.sink.VirusMarks;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Cosmic Virus consumes its timed mark on every Poison/Wither damage event without clearing it. */
public final class VirusDamageListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)
                || (event.getCause() != EntityDamageEvent.DamageCause.WITHER
                    && event.getCause() != EntityDamageEvent.DamageCause.POISON)) {
            return;
        }
        double multiplier = VirusMarks.multiplier(event.getEntity().getUniqueId());
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        VirusMarks.clear(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        VirusMarks.clear(event.getPlayer().getUniqueId());
    }
}
