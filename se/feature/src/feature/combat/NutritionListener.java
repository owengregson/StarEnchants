package feature.combat;

import engine.effect.kind.EnchantLevels;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/** Cosmic Nutrition's event-coupled food gain multiplier and Alien Implants' hunger lock. */
public final class NutritionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAlienImplantsFoodLoss(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player
                && CosmicTierGate.tierSixPlusEnabled(player)
                && event.getFoodLevel() < player.getFoodLevel()
                && EnchantLevels.worn(player, "enchants/alien-implants") > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getFoodLevel() <= player.getFoodLevel()) {
            return;
        }
        int level = EnchantLevels.worn(player, "enchants/nutrition");
        if (level <= 0) {
            return;
        }
        double multiplier = 1.1 + level * 0.3;
        event.setFoodLevel((int) Math.round(event.getFoodLevel() * multiplier));
    }
}
