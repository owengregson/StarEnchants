package imagegen.assets;

import java.awt.image.BufferedImage;

/**
 * A simplified full-cube block model: the six face textures (any may be {@code null}). This is the high-coverage
 * subset of the vanilla model format — enough to render the isometric inventory icon of a normal cube block
 * (stone, wool, glass, ores, …), which is how Minecraft shows block items in a slot. Non-cube blocks (stairs,
 * fences, torches) are out of scope here; many of those carry a flat item sprite anyway and resolve as
 * {@link ResolvedModel.Kind#FLAT}.
 *
 * <p>The inventory {@code gui} transform (rotate x≈30°, y≈225°) shows three faces — the top and two sides — so the
 * isometric renderer reads {@link #up()} plus the two visible sides via {@link #leftSide()}/{@link #rightSide()}.
 */
public record BlockModel(BufferedImage up, BufferedImage down,
                         BufferedImage north, BufferedImage south,
                         BufferedImage east, BufferedImage west) {

    /** The left visible side in the inventory transform (falls back across equivalent faces of a uniform cube). */
    public BufferedImage leftSide() {
        return north != null ? north : (west != null ? west : south);
    }

    /** The right visible side in the inventory transform. */
    public BufferedImage rightSide() {
        return east != null ? east : (south != null ? south : north);
    }
}
