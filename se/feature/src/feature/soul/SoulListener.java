package feature.soul;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds the soul loop (§6.3, §D): a kill deposits souls into the killer's carried gem (on ANY kill); a quit
 * clears their soul mode. {@link SoulService} defers the locate-and-credit to the killer's own thread (death
 * fires on the victim's region, but the gem is the killer's). MONITOR + {@code ignoreCancelled} so only a
 * real death counts.
 */
public final class SoulListener implements Listener {

    private final SoulService souls;

    public SoulListener(SoulService souls) {
        this.souls = Objects.requireNonNull(souls, "souls");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            souls.onKill(killer, event.getEntityType());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        souls.clear(event.getPlayer());
    }
}
