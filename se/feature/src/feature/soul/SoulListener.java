package feature.soul;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds the soul loop (docs/architecture.md §6.3): a player kill deposits souls into the killer's
 * active gem, and a quit clears their soul mode. The deposit's durable write is deferred to the
 * killer's own thread by {@link SoulService} (a death fires on the victim's region thread, but the
 * gem is the killer's). MONITOR priority + {@code ignoreCancelled} so only a real death counts.
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
            souls.onKill(killer); // amount comes from the soul-gem config
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        souls.clear(event.getPlayer());
    }
}
