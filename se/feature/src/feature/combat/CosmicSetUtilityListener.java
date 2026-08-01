package feature.combat;

import engine.effect.kind.ActiveSets;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/** Exact event-coupled utility branches for Cosmic armor sets. */
public final class CosmicSetUtilityListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLoss(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && event.getFoodLevel() < player.getFoodLevel()
                && ActiveSets.has(player, "sets/supreme")) {
            event.setCancelled(true);
        }
    }
}
