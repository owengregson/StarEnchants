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
 * The heroic durability property (docs/v3-directives.md §F): an item carrying a heroic durability
 * chance has each item-damage event CANCELLED with that probability — heroic gear barely wears. Reads
 * the SPECIFIC damaged item's heroic stat (not the worn sum). Item-damage is not the combat hot path,
 * so a direct decode here is acceptable. Folia-correct: {@code PlayerItemDamageEvent} fires on the
 * player's own region thread, and only that player's own item is touched.
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
