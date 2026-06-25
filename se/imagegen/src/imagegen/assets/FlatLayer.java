package imagegen.assets;

import java.awt.image.BufferedImage;

/**
 * One texture layer of a flat ({@code item/generated}) item model. Minecraft stacks {@code layer0,layer1,…} bottom
 * to top; a layer with a {@code tintIndex >= 0} is multiplied by a per-item colour at render time (leather armour,
 * potions, spawn eggs). {@code tintIndex == -1} means draw the texture as-is.
 */
public record FlatLayer(BufferedImage texture, int tintIndex) {
}
