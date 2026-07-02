package feature.compat;

import org.bukkit.event.block.BlockBreakEvent;

/**
 * Modern impl of {@link DropControl} — the era-exclusive {@code overlay/modern} drop suppression (ADR-0044; §4).
 * {@code BlockBreakEvent.setDropItems} is 1.16+ (absent on 1.8.9), so the vanilla drop is suppressed in place.
 */
public final class ModernDropControl implements DropControl {

    @Override
    public void suppressVanillaDrops(BlockBreakEvent event) {
        event.setDropItems(false);
    }
}
