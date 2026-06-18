package feature.combat;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import compile.load.ContentHolder;
import feature.trigger.LifecycleDriver;
import feature.trigger.RepeatingDriver;
import item.worn.WornState;
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
 *
 * <p>Each refresh also drives the two §B equipment-lifecycle mechanisms off the fresh worn state: the
 * {@link RepeatingDriver} (re)arms timer-driven {@code REPEATING} abilities, and the {@link LifecycleDriver}
 * STARTs/STOPs maintained {@code HELD}/{@code PASSIVE} buffs by diffing what is now worn against what was.
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
        repeating.disarm(event.getPlayer().getUniqueId()); // cancel the player's repeating tasks (§B)
        lifecycle.clear(event.getPlayer().getUniqueId()); // forget started HELD/PASSIVE buffs (no teardown — gone)
    }

    private void refresh(Player player) {
        WornState state = worn.refresh(player, content.snapshot());
        repeating.arm(player, state);     // (re)arm REPEATING abilities against the fresh worn state (§B)
        lifecycle.refresh(player, state); // START/STOP newly-(un)worn HELD/PASSIVE buffs (§B equip lifecycle)
    }
}
