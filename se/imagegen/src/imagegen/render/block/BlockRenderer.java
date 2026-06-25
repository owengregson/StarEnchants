package imagegen.render.block;

import imagegen.Argb;
import imagegen.assets.BlockModel;
import java.awt.image.BufferedImage;

/**
 * Renders a full-cube {@link BlockModel} as the 16x16 isometric icon Minecraft shows for a block item in an
 * inventory slot — a flat-top diamond (the top face) with the two visible side faces below it.
 *
 * <p>Minecraft's {@code gui} item transform rotates the block (x≈30°, y≈225°) so three faces show: top + two
 * sides. Rather than rotate a 3D mesh we reproduce that look directly with a fixed dimetric (2:1) projection:
 * each face is mapped through an affine transform into its on-screen parallelogram. We rasterise by inverse
 * mapping — for every destination pixel inside a face we solve back to source texel (u,v) and sample NEAREST,
 * so the 16px texture stays pixel-crisp (no resampling, no anti-aliasing) and the output is deterministic.
 *
 * <p>Per-face shading matches vanilla's directional lighting: top at full brightness, the two sides darkened so
 * the cube reads as 3D from a flat icon.
 */
public final class BlockRenderer {

    private static final int SIZE = 16;

    // Vanilla-style directional shading: top lit fully, the left-facing side dimmer, the right-facing side darkest.
    private static final double TOP_SHADE = 1.00;
    private static final double LEFT_SHADE = 0.80;
    private static final double RIGHT_SHADE = 0.62;

    // Diamond (top-face) corners for a 2:1 iso filling the 16px slot: top-face height (8px) ≈ half its width.
    // The cube spans x∈[1,15], y∈[0,15]; the side faces hang 7px below the diamond's lower edges to the baseline.
    private static final double TOP_X = 8, TOP_Y = 0;     // back/top apex
    private static final double RIGHT_X = 15, RIGHT_Y = 4; // right apex
    private static final double FRONT_X = 8, FRONT_Y = 8;  // near/bottom apex (top of the front vertical edge)
    private static final double LEFT_X = 1, LEFT_Y = 4;    // left apex
    private static final double BOTTOM_Y = 15;             // baseline of the two side faces

    public BlockRenderer() {
    }

    /**
     * Render {@code model} as a 16x16 ARGB isometric inventory icon on a transparent background. Returns a fully
     * transparent 16x16 image when the model has no usable faces.
     */
    public BufferedImage render(BlockModel model) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        if (model == null) {
            return out;
        }

        BufferedImage top = model.up();
        if (top == null) {
            // Top is the dominant face; fall back to any side so a model missing only `up` still renders a diamond.
            top = firstNonNull(model.leftSide(), model.rightSide(),
                    model.down(), model.north(), model.south(), model.east(), model.west());
        }
        BufferedImage left = model.leftSide();
        BufferedImage right = model.rightSide();

        // Sides first, then the top over them: the diamond's lower edges are the sides' shared upper edges, so the
        // top must win those seam pixels.
        if (left != null) {
            // Source u runs along the top edge (LEFT→FRONT apex); v runs down the vertical drop to the baseline.
            fillFace(out, left, LEFT_SHADE,
                    LEFT_X, LEFT_Y,                        // origin = left apex
                    FRONT_X - LEFT_X, FRONT_Y - LEFT_Y,    // +u edge → front apex
                    0, BOTTOM_Y - LEFT_Y);                 // +v edge → straight down to baseline
        }
        if (right != null) {
            // Source u runs along the top edge (FRONT→RIGHT apex); v runs straight down to the baseline.
            fillFace(out, right, RIGHT_SHADE,
                    FRONT_X, FRONT_Y,                      // origin = front apex
                    RIGHT_X - FRONT_X, RIGHT_Y - FRONT_Y,  // +u edge → right apex
                    0, BOTTOM_Y - FRONT_Y);                // +v edge → straight down to baseline
        }
        if (top != null) {
            // The rhombus: u edge LEFT→TOP apex, v edge LEFT→FRONT apex (the two sheared texture axes).
            fillFace(out, top, TOP_SHADE,
                    LEFT_X, LEFT_Y,                        // origin = left apex
                    TOP_X - LEFT_X, TOP_Y - LEFT_Y,        // +u edge → top apex
                    FRONT_X - LEFT_X, FRONT_Y - LEFT_Y);   // +v edge → front apex
        }
        return out;
    }

    /**
     * Map a 16x16 source texture into the parallelogram {@code origin + s*edgeU + t*edgeV} (s,t ∈ [0,1]) by inverse
     * sampling. We solve the 2x2 edge system per destination pixel back to texture coords, sample NEAREST, shade,
     * and alpha-over — skipping fully transparent texels so glass/cutouts show through.
     */
    private static void fillFace(BufferedImage out, BufferedImage tex, double shade,
                                 double ox, double oy, double ux, double uy, double vx, double vy) {
        int tw = tex.getWidth();
        int th = tex.getHeight();

        // Inverse of the [edgeU edgeV] matrix; this projection's edges are never collinear so det != 0.
        double det = ux * vy - uy * vx;
        if (det == 0) {
            return;
        }
        double inv = 1.0 / det;

        // Tight pixel bounds for the face's bounding box (the whole sprite is only 16px, so this stays cheap).
        int minX = (int) Math.floor(Math.min(Math.min(ox, ox + ux), Math.min(ox + vx, ox + ux + vx)));
        int maxX = (int) Math.ceil(Math.max(Math.max(ox, ox + ux), Math.max(ox + vx, ox + ux + vx)));
        int minY = (int) Math.floor(Math.min(Math.min(oy, oy + uy), Math.min(oy + vy, oy + uy + vy)));
        int maxY = (int) Math.ceil(Math.max(Math.max(oy, oy + uy), Math.max(oy + vy, oy + uy + vy)));
        minX = Math.max(0, minX);
        minY = Math.max(0, minY);
        maxX = Math.min(SIZE - 1, maxX);
        maxY = Math.min(SIZE - 1, maxY);

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                // Sample at the pixel centre so edge pixels land on the correct side of the seam.
                double px = x + 0.5 - ox;
                double py = y + 0.5 - oy;
                double s = (px * vy - py * vx) * inv; // fraction along edgeU
                double t = (ux * py - uy * px) * inv; // fraction along edgeV
                if (s < 0 || s >= 1 || t < 0 || t >= 1) {
                    continue;
                }
                int u = clampTexel((int) (s * tw), tw);
                int v = clampTexel((int) (t * th), th);
                int texel = tex.getRGB(u, v);
                if (Argb.a(texel) == 0) {
                    continue; // transparent texel — leave the slot background showing through
                }
                int shaded = Argb.shade(texel, shade);
                out.setRGB(x, y, Argb.over(shaded, out.getRGB(x, y)));
            }
        }
    }

    private static int clampTexel(int c, int n) {
        return c < 0 ? 0 : Math.min(c, n - 1);
    }

    private static BufferedImage firstNonNull(BufferedImage... imgs) {
        for (BufferedImage img : imgs) {
            if (img != null) {
                return img;
            }
        }
        return null;
    }
}
