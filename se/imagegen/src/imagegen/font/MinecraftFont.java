package imagegen.font;

import imagegen.Argb;
import imagegen.Canvas;
import imagegen.assets.AssetSource;
import java.awt.image.BufferedImage;

/**
 * Minecraft's real {@code default} font, rendered from the game's own {@code font/ascii.png} glyph atlas (supplied
 * by the vanilla {@link AssetSource}). Both the glyph shapes and the per-character advances are taken straight from
 * the atlas using Minecraft's own width rule — advance = (rightmost lit column) + 2 px, i.e. the glyph's pixel width
 * plus the 1px inter-glyph gap; {@code space} is 4 — so on-screen spacing matches the game exactly rather than a
 * hand-tuned approximation.
 *
 * <p>The atlas is a 16×16 grid of glyph cells indexed by code point (cell {@code (code%16, code/16)}); vanilla cells
 * are 8×8 and any other size is normalised to the 8px grid so output stays on Minecraft's native pixel scale. The
 * atlas is fetched at generation time and never committed — only the composite PNGs are — the same licensing posture
 * as the item textures.
 */
public final class MinecraftFont implements GlyphFont {

    private static final int COLS = 16;     // atlas grid is 16×16 cells
    private static final int GRID = 8;      // normalised cell size (vanilla glyph cells are 8×8)
    private static final int ASCENT = 7;    // baseline row; underline/strike hang off this
    private static final int LINE_HEIGHT = 10; // vanilla lore line step
    private static final int SPACE_ADVANCE = 4;

    // Per code point (0..255): the normalised 8×8 lit-pixel mask (null when the cell is blank) and the advance width.
    private final boolean[][] glyph = new boolean[256][];
    private final int[] advance = new int[256];

    public MinecraftFont(AssetSource assets) {
        BufferedImage atlas = assets == null ? null : assets.texture("font/ascii");
        if (atlas == null) {
            throw new IllegalStateException("vanilla font atlas textures/font/ascii.png is unavailable — set "
                    + "MC_ASSETS_DIR or allow the client-jar download so text can be rendered in the real font");
        }
        load(atlas);
    }

    private void load(BufferedImage atlas) {
        int cellW = atlas.getWidth() / COLS;
        int cellH = atlas.getHeight() / COLS;
        for (int code = 0; code < 256; code++) {
            int ox = (code % COLS) * cellW;
            int oy = (code / COLS) * cellH;
            boolean[] mask = new boolean[GRID * GRID];
            int rightmost = -1;
            boolean any = false;
            for (int gy = 0; gy < GRID; gy++) {
                for (int gx = 0; gx < GRID; gx++) {
                    // Sample the (possibly HD) cell at the 8-grid pixel centre so the glyph keeps MC's native scale.
                    int sx = ox + gx * cellW / GRID;
                    int sy = oy + gy * cellH / GRID;
                    boolean lit = (atlas.getRGB(sx, sy) >>> 24) != 0; // glyphs are opaque-white on transparent
                    mask[gy * GRID + gx] = lit;
                    if (lit) {
                        any = true;
                        rightmost = Math.max(rightmost, gx);
                    }
                }
            }
            glyph[code] = any ? mask : null;
            // Minecraft's rule: pixel width = rightmost+1, advance adds the 1px gap → rightmost+2. Space is fixed at 4.
            advance[code] = code == ' ' ? SPACE_ADVANCE : (any ? rightmost + 2 : 0);
        }
    }

    @Override
    public int lineHeight() {
        return LINE_HEIGHT;
    }

    @Override
    public int ascent() {
        return ASCENT;
    }

    @Override
    public int width(String text, boolean bold) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            int code = codeOf(text.charAt(i));
            w += advance[code] + (bold && glyph[code] != null ? 1 : 0); // bold widens each inked glyph by 1px
        }
        return w;
    }

    @Override
    public int draw(Canvas canvas, String text, int x, int y, int rgb, boolean bold, boolean italic) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int color = Argb.opaque(rgb);
        int pen = x;
        for (int i = 0; i < text.length(); i++) {
            int code = codeOf(text.charAt(i));
            boolean[] mask = glyph[code];
            if (mask != null) {
                paint(canvas, mask, pen, y, color, bold, italic);
            }
            pen += advance[code] + (bold && mask != null ? 1 : 0);
        }
        return pen - x;
    }

    private void paint(Canvas canvas, boolean[] mask, int x, int y, int color, boolean bold, boolean italic) {
        for (int gy = 0; gy < GRID; gy++) {
            int shear = italic ? shear(gy) : 0;
            for (int gx = 0; gx < GRID; gx++) {
                if (mask[gy * GRID + gx]) {
                    canvas.put(x + gx + shear, y + gy, color);
                    if (bold) {
                        canvas.put(x + gx + 1 + shear, y + gy, color); // doubled draw = the bold weight
                    }
                }
            }
        }
    }

    // Minecraft's italic leans the glyph right, more toward the top of the cell. ~2px of lean over the 8px height.
    private static int shear(int row) {
        return (int) Math.round((GRID - 1 - row) * 0.25);
    }

    private static int codeOf(char c) {
        // The atlas covers code points 0..255 (ASCII printables map directly); anything else advances as a space.
        return c < 256 ? c : ' ';
    }
}
