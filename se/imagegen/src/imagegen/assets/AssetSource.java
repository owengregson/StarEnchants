package imagegen.assets;

import java.awt.image.BufferedImage;

/**
 * Supplies the vanilla textures the GUI/sprite renderers composite — item models, the chest-GUI background, raw
 * block textures. Implementations resolve these from real Minecraft assets (a local {@code MC_ASSETS_DIR} or a
 * pinned client jar fetched at run time); when assets are absent {@link #available()} is {@code false} and the
 * lookups return {@code null}/{@link ResolvedModel.Kind#UNKNOWN} so the generator degrades to placeholders rather
 * than failing. Material names are the plugin's config spelling (e.g. {@code DIAMOND_SWORD}); the implementation
 * normalises to the lowercase resource id.
 */
public interface AssetSource {

    /** True if a real asset set is loaded; false means every lookup returns empty and callers draw placeholders. */
    boolean available();

    /** How {@code materialName} renders in a slot (flat layers, a full cube, or unknown). Never {@code null}. */
    ResolvedModel resolve(String materialName);

    /** A GUI texture by path under {@code textures/gui/}, e.g. {@code "container/generic_54"}; {@code null} if absent. */
    BufferedImage gui(String path);

    /** A raw block texture by name under {@code textures/block/}, e.g. {@code "stone"}; {@code null} if absent. */
    BufferedImage blockTexture(String name);

    /** Any texture by path under {@code textures/}, e.g. {@code "font/ascii"} (the glyph atlas); {@code null} if absent. */
    BufferedImage texture(String path);

    /** An {@link AssetSource} that has nothing — every render falls back to placeholders. */
    AssetSource NONE = new AssetSource() {
        @Override public boolean available() {
            return false;
        }

        @Override public ResolvedModel resolve(String materialName) {
            return ResolvedModel.unknown();
        }

        @Override public BufferedImage gui(String path) {
            return null;
        }

        @Override public BufferedImage blockTexture(String name) {
            return null;
        }

        @Override public BufferedImage texture(String path) {
            return null;
        }
    };
}
