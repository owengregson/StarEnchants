package feature.soul;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Feeds the soul loop (docs/architecture.md §6.3, docs/v3-directives.md §D): a player kill deposits
 * souls into the killer's carried gem (on ANY kill — soul mode no longer gates acquisition), and a quit
 * clears their soul mode. The deposit's locate-and-credit is deferred to the killer's own thread by
 * {@link SoulService} (a death fires on the victim's region thread, but the gem is the killer's). MONITOR
 * priority + {@code ignoreCancelled} so only a real death counts; the victim's {@code EntityType} drives
 * the per-mob deposit amount.
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
            souls.onKill(killer, event.getEntityType()); // amount: per-mob override else the flat per-kill
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        souls.clear(event.getPlayer());
    }
}
