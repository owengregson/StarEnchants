package feature.compat;

import org.bukkit.event.block.BlockBreakEvent;

/**
 * Modern block-break drop control. Same-FQN counterpart to the {@code overlay/legacy} impl;
 * {@code BlockBreakEvent.setDropItems} is 1.16+ (absent on 1.8.9 — docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Blocks {

    private Blocks() {
    }

    /** Suppress the vanilla drop so the caller can place its own (smelted / teleported) drops. */
    public static void suppressVanillaDrops(BlockBreakEvent event) {
        event.setDropItems(false);
    }
}
