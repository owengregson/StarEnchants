package feature.compat;

import org.bukkit.event.block.BlockBreakEvent;

/**
 * The version edge for suppressing a block's vanilla drop so the caller can place its own effective (smelted /
 * teleported) drops (ADR-0044; docs/legacy-1.8.9-codeshare-design.md §4/§6): {@code BlockBreakEvent.setDropItems}
 * is 1.16+ and absent on 1.8.9. Two era-exclusive impls back it — {@code ModernDropControl} ({@code overlay/modern})
 * and {@code LegacyDropControl} ({@code overlay/legacy}, cancel + clear the block) — injected into {@code MineDrops}.
 */
public interface DropControl {

    /** Suppress the vanilla drop so the caller places the effective drops itself. */
    void suppressVanillaDrops(BlockBreakEvent event);
}
