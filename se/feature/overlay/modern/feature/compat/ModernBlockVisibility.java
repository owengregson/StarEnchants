package feature.compat;

import engine.sink.BlockVisibility;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * The modern (1.17.1 &rarr; 26.1.x) {@link BlockVisibility}: the {@code BlockData} form of
 * {@code sendBlockChange}, which is present unchanged across the whole range (verified by javap on 1.17.1 and
 * 26.1.2), so the lane needs no version gate of its own. The bulk {@code sendMultiBlockChange} is deliberately
 * unused — it is absent on the 1.17.1 floor.
 */
public final class ModernBlockVisibility implements BlockVisibility {

    @Override
    public void sendPhantom(Player viewer, Location at, Material material) {
        viewer.sendBlockChange(at, material.createBlockData());
    }

    @Override
    public Appearance capture(Block block) {
        BlockData real = block.getBlockData(); // a detached copy, so the resend needs no world access
        return (viewer, at) -> viewer.sendBlockChange(at, real);
    }
}
