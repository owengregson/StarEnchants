package feature.compat;

import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Legacy (1.8.9) impl of {@link DropControl} — the era-exclusive {@code overlay/legacy} drop suppression (ADR-0044;
 * §4). 1.8 has no {@code BlockBreakEvent.setDropItems}, so the vanilla break+drop is cancelled and the block
 * cleared; the caller (MineDrops) then places the effective drops itself. A slight 1.8 divergence (no vanilla
 * break XP/durability on the suppressed break) — docs/legacy-1.8.9-codeshare-design.md §6.
 */
public final class LegacyDropControl implements DropControl {

    @Override
    public void suppressVanillaDrops(BlockBreakEvent event) {
        event.setCancelled(true);
        event.getBlock().setType(Material.AIR);
    }
}
