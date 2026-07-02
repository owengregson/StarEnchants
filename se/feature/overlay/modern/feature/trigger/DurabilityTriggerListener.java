package feature.trigger;

import engine.run.ActivationContext;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;

/**
 * Fires the ITEM_DAMAGE trigger on {@code PlayerItemDamageEvent} — the era-exclusive {@code overlay/modern}
 * source (ADR-0044). The event does not exist on 1.8.9 (docs/legacy-1.8.9-codeshare-design.md §4), where
 * ITEM_DAMAGE is fired instead by the {@code LegacyGearPoll} durability-rise subscriber (§6).
 */
public final class DurabilityTriggerListener implements Listener {

    private final TriggerDispatch dispatch;

    public DurabilityTriggerListener(TriggerDispatch dispatch) {
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        Player player = event.getPlayer();
        dispatch.fire(player, dispatch.itemDamage,
                new ActivationContext(player, null, null, player.getLocation()), event);
    }
}
