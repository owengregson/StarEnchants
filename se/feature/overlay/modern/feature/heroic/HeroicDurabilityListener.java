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
 * Heroic durability (docs/v3-directives.md §F): cancel each item-damage event with the SPECIFIC damaged
 * item's heroic chance, not the worn sum. Folia-correct: {@code PlayerItemDamageEvent} fires on the
 * player's own region thread.
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
        double base = codec.read(item).heroic().durability(); // EMPTY → NONE → 0.0 (fast no-op)
        if (base <= 0.0) {
            return;
        }
        // §F: scale the wear-cancel so a sub-diamond-max heroic piece (e.g. a gold display piece) lasts like
        // diamond — its real durability bar is the ledger, depleting at the diamond rate. With vanilla-stats on
        // (ADR-0031) the piece carries a REAL diamond max durability, so the effective max already equals diamond
        // and the scaling collapses to the base heroic buff (no double protection) — read the effective max, not
        // the material max, so the two paths compose.
        double chance = HeroicDiamond.scaledWearCancel(
                HeroicVanillaStats.effectiveMaxDurability(item), item.getType(), base);
        if (random.nextDouble() < chance) {
            event.setCancelled(true);
        }
    }
}
