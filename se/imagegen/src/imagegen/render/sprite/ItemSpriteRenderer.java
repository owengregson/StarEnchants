package imagegen.render.sprite;

import imagegen.Argb;
import imagegen.assets.FlatLayer;
import imagegen.assets.ResolvedModel;
import java.awt.image.BufferedImage;

/**
 * Composites a flat ({@code item/generated}) item model into a single 16×16 sprite — Minecraft stacks the model's
 * {@code layer0,layer1,…} bottom-to-top. Animated textures (a vertical strip of square frames, e.g. clock/compass)
 * render their first frame, which is what a still inventory icon shows. Tinting is out of scope (the few tinted
 * items — leather/potions — render in their base colour). Returns {@code null} when the model isn't flat, so the
 * GUI renderer can fall back to the isometric block path or a placeholder.
 */
public final class ItemSpriteRenderer {

    private static final int SIZE = 16;

    public BufferedImage render(ResolvedModel model) {
        if (model == null || model.kind != ResolvedModel.Kind.FLAT || model.layers.isEmpty()) {
            return null;
        }
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        boolean drew = false;
        for (FlatLayer layer : model.layers) {
            BufferedImage tex = layer.texture();
            if (tex == null) {
                continue;
            }
            int fw = tex.getWidth();
            // First animation frame: a strip taller than wide whose height is a multiple of the width.
            int fh = tex.getHeight() > fw && tex.getHeight() % fw == 0 ? fw : tex.getHeight();
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    int sx = x * fw / SIZE;
                    int sy = y * fh / SIZE;
                    int src = tex.getRGB(sx, sy);
                    if (Argb.a(src) == 0) {
                        continue;
                    }
                    out.setRGB(x, y, Argb.over(src, out.getRGB(x, y)));
                    drew = true;
                }
            }
        }
        return drew ? out : null;
    }
}
