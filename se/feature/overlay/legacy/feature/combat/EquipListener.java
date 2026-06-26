package feature.combat;

import compile.load.ContentHolder;
import feature.trigger.LifecycleDriver;
import feature.trigger.RepeatingDriver;
import item.worn.WornState;
import item.worn.WornStateStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import platform.sched.Scheduling;

/**
 * Legacy (1.8.9) {@link WornStateStore} refresher — same-FQN counterpart to the {@code overlay/modern}
 * listener. 1.8 has no Paper {@code PlayerArmorChangeEvent}, so armour changes are caught via
 * {@link InventoryCloseEvent} (the player closes their inventory after equipping) plus join / held-item
 * change. This is a slight degrade from the modern instant armour-change refresh
 * (docs/legacy-1.8.9-codeshare-design.md §6) — a tick poll could tighten it in a later phase.
 */
public final class EquipListener implements Listener {

    private final WornStateStore worn;
    private final ContentHolder content;
    private final RepeatingDriver repeating;
    private final LifecycleDriver lifecycle;

    public EquipListener(WornStateStore worn, ContentHolder content, RepeatingDriver repeating,
                         LifecycleDriver lifecycle) {
        this.worn = worn;
        this.content = content;
        this.repeating = repeating;
        this.lifecycle = lifecycle;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // armour may have changed while the inventory was open; refresh on the player's thread
            Scheduling.onEntityLater(player, 1L, () -> refresh(player));
        }
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        worn.remove(event.getPlayer().getUniqueId());
        repeating.disarm(event.getPlayer().getUniqueId());
        lifecycle.clear(event.getPlayer().getUniqueId());
    }

    private void refresh(Player player) {
        WornState state = worn.refresh(player, content.snapshot());
        repeating.arm(player, state);
        lifecycle.refresh(player, state);
    }
}
