package engine.sink;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * The sink's view of per-viewer BLOCK visibility ({@code PHANTOM_BLOCKS}) — the {@link PlayerVisibility} twin,
 * a seam because the modern lane sends a {@code BlockData} and 1.8.9 has no such type at all, only the
 * {@code (Material, byte)} pair. Client-only in both directions: nothing here ever writes the world.
 *
 * <p>{@link #capture} reads a block, so it runs on that block's OWNING region thread; {@link #sendPhantom} and
 * {@link Appearance#resend} touch only the viewer's connection and run on the VIEWER's own thread — the
 * {@link PlayerVisibility} rule, and the reason the revert carries a snapshot instead of re-reading the world.
 */
public interface BlockVisibility {

    /** One block's real appearance, snapshotted by the era impl so the revert re-sends truth with no world read. */
    @FunctionalInterface
    interface Appearance {

        /** Re-send this appearance to {@code viewer} at {@code at}, undoing a phantom. */
        void resend(Player viewer, Location at);
    }

    /** The inert default for non-root construction sites (tests, tester suites): no client ever sees a phantom. */
    BlockVisibility NONE = new BlockVisibility() {

        @Override
        public void sendPhantom(Player viewer, Location at, Material material) {
        }

        @Override
        public Appearance capture(Block block) {
            return (viewer, at) -> { };
        }
    };

    /** Show {@code viewer} {@code material} at {@code at} — their client only; the world is untouched. */
    void sendPhantom(Player viewer, Location at, Material material);

    /** Snapshot {@code block}'s real appearance for a later {@link Appearance#resend}. */
    Appearance capture(Block block);
}
