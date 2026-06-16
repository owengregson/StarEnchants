package feature.combat;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import compile.load.ContentHolder;
import item.worn.WornStateStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import platform.sched.Scheduling;

/**
 * Keeps each player's {@link item.worn.WornState} fresh in the {@link WornStateStore}, resolved on an
 * equipment change — NOT per hit (§5.5). Resolution runs on the player's own region thread (these are
 * player events), reading the player's own equipment, never cross-region. Cleared on quit.
 *
 * <p>The held-item change ({@link PlayerItemHeldEvent}) fires <em>before</em> the slot updates, so its
 * refresh is deferred one tick (on the player's scheduler) to read the new main-hand item.
 */
public final class EquipListener implements Listener {

    private final WornStateStore worn;
    private final ContentHolder content;

    public EquipListener(WornStateStore worn, ContentHolder content) {
        this.worn = worn;
        this.content = content;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // The new slot is current only after this event returns — refresh next tick on the player's thread.
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        worn.remove(event.getPlayer().getUniqueId());
    }

    private void refresh(Player player) {
        worn.refresh(player, content.snapshot());
    }
}
