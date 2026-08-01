package feature.combat;

import engine.sink.BleedStacks;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Bug-free logout cleanup for the source's entity-metadata Bleed state and persisted walk-speed side effect. */
public final class BleedCleanupListener implements Listener {
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        BleedStacks.clear(event.getPlayer().getUniqueId());
        event.getPlayer().setWalkSpeed(0.2f);
    }
}
