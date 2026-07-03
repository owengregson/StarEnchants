package engine.sink;

import engine.sink.TempBlockLedger.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/**
 * The Bukkit-backed {@link TempBlockLedger.BlockOps} — the era-neutral platform edge for the temp-block
 * ledger. Material identity is {@code Material.ordinal()} (a stable within-run bijection with
 * {@code Material.values()[id]}, an enum on both 1.8 and modern), used only for equality + re-set; the
 * original is a full {@link BlockState} restored via {@code update(true, false)}, the same 1.8-safe technique
 * the pre-ledger {@code tempPlatform} used. Runs on the tile's own region thread (the ledger's consistency
 * model), so the {@code Bukkit.getWorld}/{@code getBlockAt} reads are region-correct.
 */
final class BukkitBlockOps implements TempBlockLedger.BlockOps<BlockState> {

    /** A fresh per-boot ledger over the Bukkit world — the one instance shared across every per-event sink. */
    static TempBlockLedger<BlockState> ledger() {
        return new TempBlockLedger<>(new BukkitBlockOps());
    }

    @Override
    public int readTypeId(Key key) {
        Block block = block(key);
        return block == null ? -1 : block.getType().ordinal();
    }

    @Override
    public void setTypeId(Key key, int typeId) {
        Block block = block(key);
        Material[] all = Material.values();
        if (block != null && typeId >= 0 && typeId < all.length) {
            block.setType(all[typeId], false);
        }
    }

    @Override
    public BlockState captureOriginal(Key key) {
        Block block = block(key);
        return block == null ? null : block.getState();
    }

    @Override
    public void restoreOriginal(Key key, BlockState original) {
        if (original != null) {
            original.update(true, false); // force the captured state back, no physics
        }
    }

    private static Block block(Key key) {
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : world.getBlockAt(key.x(), key.y(), key.z());
    }
}
