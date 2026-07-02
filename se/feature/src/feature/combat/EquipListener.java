package feature.combat;

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
 * The shared {@link WornStateStore} refresher — keeps each player's {@link WornState} fresh, resolved on an
 * equipment change (NOT per hit, §5.5) on the player's own region thread, and drives the §B equipment-lifecycle
 * mechanisms + maintained passive potion buffs on each refresh (ADR-0036/0044). The join / held-item / respawn /
 * quit lifecycle and the {@link #refresh} pipeline live here (era-neutral); the era-specific armour-change SOURCE
 * is a separate feeder that calls {@link #refresh}: modern hooks Paper's {@code PlayerArmorChangeEvent}
 * ({@code ModernArmourChangeListener}); 1.8 (which lacks it) drives refresh from the per-tick {@code LegacyGearPoll}
 * armour-signature delta plus an {@code InventoryCloseEvent} backup (docs/legacy-1.8.9-codeshare-design.md §6).
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
    public void onHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // The new slot is current only after this event returns — refresh next tick on the player's thread.
        Scheduling.onEntityLater(player, 1L, () -> refresh(player));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Death clears all potion effects; re-derive once respawned so a permanent passive buff returns at once.
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

    /** Re-resolve {@code player}'s worn state and drive every equipment-lifecycle mechanism from it. Called by
     *  the shared lifecycle handlers and by the era armour-change feeders (modern event / legacy poll + close). */
    public void refresh(Player player) {
        WornState state = worn.refresh(player, content.snapshot());
        repeating.arm(player, state);       // (re)arm REPEATING abilities (§B)
        lifecycle.refresh(player, state);   // START/STOP newly-(un)worn HELD/PASSIVE buffs (§B)
        setMessages.refresh(player, state); // §6.6 announce a set becoming complete / dropping below threshold
        passiveEffects.refresh(player);     // reconcile maintained passive potion buffs LAST — it is the authority
    }
}
