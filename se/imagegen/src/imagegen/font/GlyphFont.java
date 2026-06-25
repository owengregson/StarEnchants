package imagegen.font;

import imagegen.Canvas;

/**
 * A pixel-grid font: measures and draws plain text at unscaled (1:1) size. The tooltip/GUI renderers split a line
 * into styled runs and add colour, the down-right shadow, underline/strike and the final NEAREST upscale on top —
 * so a {@code GlyphFont} only has to know glyph shapes and advances. Implementations rasterise the Minecraft
 * typeface (variable-width: most glyphs advance 6px incl. the 1px gap; {@code i}/{@code l}/{@code .} are narrower).
 */
public interface GlyphFont {

    /** Line height in unscaled px — the vertical step between successive lore lines (Minecraft uses 10). */
    int lineHeight();

    /** Pixels from the top of a line to the glyph baseline, so callers can place underline/strike rules. */
    int ascent();

    /** The advance width of {@code text} in unscaled px ({@code bold} adds 1px per visible glyph). */
    int width(String text, boolean bold);

    /**
     * Draw {@code text} with its top-left at {@code (x,y)} in solid {@code rgb} (no shadow — the caller draws that
     * by calling again, offset by 1px, in the shadow colour). Returns the advance width actually drawn.
     */
    int draw(Canvas canvas, String text, int x, int y, int rgb, boolean bold, boolean italic);
}
