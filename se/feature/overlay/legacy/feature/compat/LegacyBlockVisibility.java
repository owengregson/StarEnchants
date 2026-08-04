package feature.compat;

import engine.sink.BlockVisibility;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * The 1.8.9 {@link BlockVisibility}: no {@code BlockData} exists on this era, so both directions ride the
 * {@code (Material, byte)} form. The captured data byte is load-bearing here in a way it is not on the modern
 * lane — granite is {@code STONE:1} and podzol {@code DIRT:2}, so a material-only revert would repaint half
 * the ground it was undoing.
 */
public final class LegacyBlockVisibility implements BlockVisibility {

    @Override
    public void sendPhantom(Player viewer, Location at, Material material) {
        viewer.sendBlockChange(at, material, (byte) 0);
    }

    @Override
    @SuppressWarnings("deprecation") // Block#getData(): the 1.8 sub-state, with no successor on this era.
    public Appearance capture(Block block) {
        Material type = block.getType();
        byte data = block.getData();
        return (viewer, at) -> viewer.sendBlockChange(at, type, data);
    }
}
