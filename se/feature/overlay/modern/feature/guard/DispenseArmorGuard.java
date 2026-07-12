package feature.guard;

import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Denies a DISPENSER auto-equipping an SE cosmetic head (mask/pet) onto a nearby player's helmet slot — the
 * one equip route that is a block event rather than an inventory click, so it lives outside {@link HeadEquipGuard}.
 * Modern-only (an ADR-0044 overlay twin): {@code BlockDispenseArmorEvent} does not exist on the 1.8.9 floor
 * (added after 1.8), so the legacy era binds the inert {@code NoopListener} in its place — there is no such event
 * to cancel on that lane. Always-on, keyed off the head-item identity (the {@link HeadEquipGuard} rule).
 *
 * <p>On 1.21.2+ the stripped {@code equippable} component ({@code dispensable=false}) already blocks this at the
 * source; this guard carries the 1.17.1 → 1.21.1 window and is a harmless belt-and-braces above it.
 */
public final class DispenseArmorGuard implements Listener {

    private final Predicate<ItemStack> isHeadItem; // null/AIR-safe (wrapped at the composition root)

    public DispenseArmorGuard(Predicate<ItemStack> isHeadItem) {
        this.isHeadItem = Objects.requireNonNull(isHeadItem, "isHeadItem");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        if (isHeadItem.test(event.getItem())) {
            event.setCancelled(true); // a head only equips to the helmet slot — never let a dispenser wear it
        }
    }
}
