package feature.heroic;

import item.codec.CombatCodec;
import java.util.Objects;
import java.util.Random;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Heroic durability (docs/v3-directives.md §F): cancel each item-damage event with the item's heroic
 * chance. Reads the SPECIFIC damaged item's stat, not the worn sum. Not the combat hot path, so a direct
 * decode is acceptable. Folia-correct: {@code PlayerItemDamageEvent} fires on the player's own region thread.
 */
public final class HeroicDurabilityListener implements Listener {

    private final CombatCodec codec;
    private final Random random;

    public HeroicDurabilityListener(CombatCodec codec, Random random) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.random = Objects.requireNonNull(random, "random");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        double chance = codec.read(item).heroic().durability(); // EMPTY → NONE → 0.0 (fast no-op)
        if (chance > 0.0 && random.nextDouble() < chance) {
            event.setCancelled(true);
        }
    }
}
