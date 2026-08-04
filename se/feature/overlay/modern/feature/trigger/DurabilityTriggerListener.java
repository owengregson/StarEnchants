package feature.trigger;

import engine.run.ActivationContext;
import feature.heroic.ModernVanillaStats;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

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
        // ADR-0049: expose the durability points as %damage% and whether the worn armor (vs the held item) took the
        // loss as %itemdamage.armor% — armor is now walked too (the trigger is neutral). getDamage() is int points.
        ItemStack item = event.getItem();
        boolean armor = wornArmor(player, item);
        ActivationContext context = ActivationContext.itemDamage(
                player, event.getDamage(), armor, remainingPercent(item));
        dispatch.fire(player, dispatch.itemDamage, context, event);
    }

    /**
     * {@code %item.durabilitypercent%} for the stack this event names — the only place it is in hand. Read
     * against the EFFECTIVE max (a vanilla-stats heroic piece carries a real diamond one, ADR-0031), which is
     * the bar the player sees, and BEFORE the wear lands: the event has not applied it yet, matching the 1.8
     * poll's prior-damage reading so the two lanes agree.
     */
    private static double remainingPercent(ItemStack item) {
        if (item == null || !(item.getItemMeta() instanceof Damageable damage)) {
            return Double.NaN; // no durability bar → no reading (never 0, which means "spent")
        }
        return DurabilityPercent.of(damage.getDamage(), ModernVanillaStats.effectiveMaxDurability(item));
    }

    /** Whether {@code item} is one of {@code player}'s worn armor pieces (by reference/equals against the armor slots). */
    private static boolean wornArmor(Player player, ItemStack item) {
        if (item == null) {
            return false;
        }
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == item || item.equals(piece)) {
                return true;
            }
        }
        return false;
    }
}
