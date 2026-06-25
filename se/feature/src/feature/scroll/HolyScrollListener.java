package feature.scroll;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Holy/death scroll death hook (docs/v3-directives.md §I); scan/roll/consume lives in
 * {@link HolyScrollService}. Respects an existing keepInventory gamerule (the scroll is then neither
 * needed nor spent). Folia-correct: the event fires on the dying player's own region thread.
 */
public final class HolyScrollListener implements Listener {

    private final HolyScrollService service;

    public HolyScrollListener(HolyScrollService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return; // the world already keeps inventory — the scroll is not needed and must not be spent
        }
        Player player = event.getEntity();
        String saved = service.trySave(player);
        if (saved == null) {
            return; // no scroll, or the save roll failed — the death proceeds normally
        }
        event.setKeepInventory(true);
        event.getDrops().clear();   // keepInventory + cleared drops: the inventory is retained, not duplicated
        event.setKeepLevel(true);
        event.setDroppedExp(0);
        player.sendMessage(saved);
    }
}
