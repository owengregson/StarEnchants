package feature.combat;

import engine.sink.TempBlockLedger;
import java.util.Objects;
import java.util.function.LongSupplier;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import platform.sched.Scheduling;

/**
 * Enforces the "temp blocks are cosmetic, unbreakable, and immovable" contract over the shared
 * {@link TempBlockLedger}, closing three abuses the coordinate-keyed ledger otherwise left open (F25/F26/F29).
 * Every handler first checks {@link TempBlockLedger#isEmpty()} so world-event overhead is ~zero while no temp
 * effect is live; the only allocation on the live path is one small key per {@link TempBlockLedger#guarded}
 * lookup.
 *
 * <p><strong>F25 — no free drop, no deleted original.</strong> Breaking a placed temp block would yield its
 * vanilla drop AND turn the tile to air, which sends the scheduled revert down its world-diverged drop branch —
 * silently discarding the captured original. {@link #onBreak} at {@link EventPriority#LOWEST} cancels the break
 * (so the server never computes drops) and {@link TempBlockLedger#reclaim} restores the true original instantly.
 * LOWEST also runs before the MINE-trigger handler (HIGH, {@code ignoreCancelled}), so a temp tile can never pay
 * mining rewards either. {@link #onEntityChangeBlock} covers the enderman-pickup path (excluding FallingBlock,
 * which {@link FallingBlockListener} already owns at LOWEST).
 *
 * <p><strong>F26 — immovable.</strong> The ledger keys tiles by fixed coordinates, so any displacement moves the
 * visible block off its key (untracked-permanent) and orphans the captured original. Rather than track moves
 * (zero new state, deterministic), a guarded tile simply refuses to move: pistons touching one are cancelled;
 * explosions excise it from the block list so it survives the blast and reverts on its own timer; liquid flow,
 * fade (ice melt), and burn onto/of a guarded tile are cancelled for the tile's short (<=5s) life.
 *
 * <p><strong>F29 — Folia unload self-heal.</strong> A region task scheduled for a revert can be dropped when the
 * chunk unloads, saving the temp material to disk. {@link #onChunkLoad} re-arms a revert for every layer of every
 * in-chunk entry; {@link TempBlockLedger#revert} is idempotent, so a duplicate delivery no-ops. The complementary
 * loaded-chunk sweep is armed in the controls module.
 *
 * <p>All handlers fire on the block's owning region thread. {@link TempBlockLedger#guarded} is the one cross-region
 * read a piston straddling a Folia boundary may make; it never mutates an entry, so a stale read at worst cancels
 * one already-doomed piston stroke. Era-neutral — every event here exists on the 1.8.8 API. Accepted residual: a
 * server crash/stop mid-window still strands temp tiles (the ledger and its captured states are in-memory only);
 * persisting them to chunk PDC is out of proportion for cosmetic blocks, matching {@code PlacedBlockTracker}'s
 * boot-lifetime memory note.
 */
public final class TempBlockGuardListener implements Listener {

    private final TempBlockLedger<BlockState> ledger;
    private final LongSupplier nowTicks;

    public TempBlockGuardListener(TempBlockLedger<BlockState> ledger, LongSupplier nowTicks) {
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.nowTicks = Objects.requireNonNull(nowTicks, "nowTicks");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent event) {
        if (ledger.isEmpty()) {
            return;
        }
        Block b = event.getBlock();
        if (ledger.reclaim(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ())) {
            event.setCancelled(true); // no vanilla drop; reclaim already restored the captured original
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (ledger.isEmpty() || event.getEntity() instanceof FallingBlock) {
            return; // FallingBlockListener owns the falling-block path at LOWEST
        }
        Block b = event.getBlock();
        if (ledger.guarded(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ())) {
            event.setCancelled(true); // e.g. an enderman lifting a guarded netherrack tile
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyGuarded(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyGuarded(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!ledger.isEmpty()) {
            event.blockList().removeIf(this::guarded); // the temp tile survives; its timer reverts it normally
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!ledger.isEmpty()) {
            event.blockList().removeIf(this::guarded);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (ledger.isEmpty()) {
            return;
        }
        Block to = event.getToBlock();
        if (ledger.guarded(to.getWorld().getUID(), to.getX(), to.getY(), to.getZ())) {
            event.setCancelled(true); // liquid washing over a non-solid temp tile (e.g. fantasy cobweb)
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (ledger.isEmpty()) {
            return;
        }
        if (guarded(event.getBlock())) {
            event.setCancelled(true); // yeti/permafrost ice must not melt out from under its captured original
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (ledger.isEmpty()) {
            return;
        }
        if (guarded(event.getBlock())) {
            event.setCancelled(true); // a flammable temp tile (cobweb) must not burn away
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (ledger.isEmpty()) {
            return;
        }
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        ledger.rearmChunk(world.getUID(), chunk.getX(), chunk.getZ(), nowTicks.getAsLong(),
                (x, y, z, id, seq, delay) -> Scheduling.onRegionLater(new Location(world, x, y, z), delay,
                        () -> ledger.revertAt(world.getUID(), x, y, z, id, seq, nowTicks.getAsLong())));
    }

    private boolean anyGuarded(java.util.List<Block> blocks) {
        if (ledger.isEmpty()) {
            return false;
        }
        for (Block b : blocks) {
            if (guarded(b)) {
                return true;
            }
        }
        return false;
    }

    private boolean guarded(Block b) {
        return ledger.guarded(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
    }
}
