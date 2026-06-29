package feature.combat;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import compile.load.ContentHolder;
import feature.trigger.LifecycleDriver;
import feature.trigger.PassiveEffectDriver;
import feature.trigger.RepeatingDriver;
import feature.trigger.SetMessageDriver;
import item.worn.WornState;
import item.worn.WornStateStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import platform.sched.Scheduling;

/**
 * Keeps each player's {@link item.worn.WornState} fresh in the {@link WornStateStore}, resolved on an
 * equipment change — NOT per hit (§5.5) — on the player's own region thread. Each refresh also drives
 * the §B equipment-lifecycle mechanisms ({@link RepeatingDriver}, {@link LifecycleDriver}) and reconciles
 * maintained passive potion buffs ({@link PassiveEffectDriver}).
 */
public final class EquipListener implements Listener {

    private final WornStateStore worn;
    private final ContentHolder content;
    private final RepeatingDriver repeating;
    private final LifecycleDriver lifecycle;
    private final PassiveEffectDriver passiveEffects;
    private final SetMessageDriver setMessages;

    public EquipListener(WornStateStore worn, ContentHolder content, RepeatingDriver repeating,
                         LifecycleDriver lifecycle, PassiveEffectDriver passiveEffects, SetMessageDriver setMessages) {
        this.worn = worn;
        this.content = content;
        this.repeating = repeating;
        this.lifecycle = lifecycle;
        this.passiveEffects = passiveEffects;
        this.setMessages = setMessages;
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
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Death clears all potion effects; re-derive once the player is back and their armour is restored, so a
        // permanent passive buff returns immediately rather than on the next equip change or periodic sweep.
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        worn.remove(event.getPlayer().getUniqueId());
        repeating.disarm(event.getPlayer().getUniqueId());
        lifecycle.clear(event.getPlayer().getUniqueId());
        passiveEffects.clear(event.getPlayer().getUniqueId());
        setMessages.clear(event.getPlayer().getUniqueId());
    }

    private void refresh(Player player) {
        WornState state = worn.refresh(player, content.snapshot());
        repeating.arm(player, state);       // (re)arm REPEATING abilities (§B)
        lifecycle.refresh(player, state);   // START/STOP newly-(un)worn HELD/PASSIVE buffs (§B)
        setMessages.refresh(player, state); // §6.6 announce a set becoming complete / dropping below threshold
        passiveEffects.refresh(player);     // reconcile maintained passive potion buffs LAST — it is the authority
    }
}
