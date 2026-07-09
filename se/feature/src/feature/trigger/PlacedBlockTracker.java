package feature.trigger;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Remembers which block positions a player has placed since the last server start, so the MINE trigger can
 * refuse to reward breaking your own placement (a place/break loop, ADR-0033 F33). Positions are packed to a
 * long per world; the sets are {@link ConcurrentHashMap#newKeySet()} — block events fire on the owning region
 * thread, so no other locking is needed (Folia-safe). Memory is boot-lifetime only (no persistence): a restart
 * forgets prior placements, so pre-restart placed blocks become rewardable again (accepted — persistence would
 * need chunk PDC + a legacy fallback, out of proportion).
 */
public final class PlacedBlockTracker implements Listener {

    private final ConcurrentHashMap<UUID, Set<Long>> byWorld = new ConcurrentHashMap<>();

    /** Whether {@code block}'s position is a remembered player placement. */
    public boolean isPlaced(Block block) {
        Set<Long> set = byWorld.get(block.getWorld().getUID());
        return set != null && set.contains(pack(block.getX(), block.getY(), block.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        mark(event.getBlockPlaced());
    }

    // A multi-place (e.g. a bed/door filling two cells) is a BlockPlaceEvent subclass; mark every filled cell.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMultiPlace(BlockMultiPlaceEvent event) {
        for (BlockState placed : event.getReplacedBlockStates()) {
            mark(placed.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        unmark(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        shift(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        shift(event.getBlocks(), event.getDirection());
    }

    /** Move each marked block in {@code moved} one cell along {@code direction}, so a push cannot launder a mark. */
    private void shift(List<Block> moved, org.bukkit.block.BlockFace direction) {
        for (Block block : moved) {
            if (isPlaced(block)) {
                unmark(block);
                mark(block.getRelative(direction));
            }
        }
    }

    private void mark(Block block) {
        byWorld.computeIfAbsent(block.getWorld().getUID(), w -> ConcurrentHashMap.newKeySet())
                .add(pack(block.getX(), block.getY(), block.getZ()));
    }

    private void unmark(Block block) {
        Set<Long> set = byWorld.get(block.getWorld().getUID());
        if (set != null) {
            set.remove(pack(block.getX(), block.getY(), block.getZ()));
        }
    }

    /** Pack a block position into one long (26-bit x, 26-bit z, 12-bit y) — a set key, not a decodable coord. */
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
