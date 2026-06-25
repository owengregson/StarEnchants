package imagegen.render.tooltip;

import imagegen.Canvas;
import imagegen.font.GlyphFont;
import imagegen.text.LegacyText;
import imagegen.text.McColors;
import imagegen.text.StyledRun;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the vanilla item hover-tooltip — the dark panel with the purple gradient border — for an item's name plus
 * lore, both legacy ({@code §}/{@code &}) coded. The output is a 1:1 ARGB {@link Canvas} with a small transparent
 * margin; the caller NEAREST-upscales it. Geometry and colours mirror Minecraft's {@code TooltipRenderUtil}: a dark
 * body that overhangs the text box by 3px (4px top/bottom), a 1px inset border solid at top/bottom and a
 * top→bottom gradient down the sides, and the classic down-right text shadow under every glyph.
 */
public final class TooltipRenderer {

    // Vanilla TooltipRenderUtil constants (ARGB; the border alpha is 0x50).
    private static final int BG = 0xF0100010;
    private static final int BORDER_TOP = 0x505000FF;
    private static final int BORDER_BOTTOM = 0x5028007F;

    private static final int LINE_STEP = 10; // Minecraft spaces tooltip lines 10px apart.
    private static final int MARGIN = 4; // transparent gutter so the upscaled PNG isn't flush to the edge.
    private static final int TITLE_RGB = 0xFFFFFF; // name and lore both default to white; codes override.

    private final GlyphFont font;

    public TooltipRenderer(GlyphFont font) {
        this.font = font;
    }

    /** Render the vanilla tooltip box for {@code title} (the item name) plus {@code lore} lines, all §/&-coded. */
    public Canvas render(String title, List<String> lore) {
        // The title is line 0; lore follows directly, one entry per 10px row (vanilla puts both in one list).
        List<List<StyledRun>> lines = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            lines.add(LegacyText.parse(title, TITLE_RGB));
        }
        if (lore != null) {
            for (String l : lore) {
                lines.add(LegacyText.parse(l == null ? "" : l, TITLE_RGB));
            }
        }
        if (lines.isEmpty()) {
            lines.add(new ArrayList<>()); // an empty panel still has one row's worth of body.
        }

        int w = 0;
        for (List<StyledRun> line : lines) {
            w = Math.max(w, lineWidth(line));
        }
        int contentW = Math.max(1, w);
        // No trailing gap after the last line: H spans the glyph rows minus the inter-line gap (clamped sensibly).
        int contentH = Math.max(font.lineHeight() - 2, lines.size() * LINE_STEP - 2);

        // The panel overhangs the (X,Y,W,H) text box: 3px left/right, 4px top + 3px bottom (the +1 border rows).
        // Leftmost panel pixel is X-4, topmost Y-4; place that at the MARGIN gutter.
        int x = MARGIN + 4;
        int y = MARGIN + 4;
        int panelW = contentW + 8; // (X+W+3) - (X-4) + 1
        int panelH = contentH + 8; // (Y+H+3) - (Y-4) + 1
        Canvas c = new Canvas(panelW + 2 * MARGIN, panelH + 2 * MARGIN);

        drawPanel(c, x, y, contentW, contentH);
        drawText(c, x, y, lines);
        return c;
    }

    /** The frame: dark body overhanging the text box, then the 1px purple border inset within it. */
    private void drawPanel(Canvas c, int x, int y, int wContent, int hContent) {
        int w = wContent;
        int h = hContent;
        c.fillRect(x - 3, y - 4, w + 6, 1, BG);       // top outer margin
        c.fillRect(x - 3, y + h + 3, w + 6, 1, BG);    // bottom outer margin
        c.fillRect(x - 3, y - 3, w + 6, h + 6, BG);    // main body
        c.fillRect(x - 4, y - 3, 1, h + 6, BG);        // left outer margin
        c.fillRect(x + w + 3, y - 3, 1, h + 6, BG);    // right outer margin
        c.verticalGradient(x - 3, y - 3 + 1, 1, h + 6 - 2, BORDER_TOP, BORDER_BOTTOM); // left border
        c.verticalGradient(x + w + 2, y - 3 + 1, 1, h + 6 - 2, BORDER_TOP, BORDER_BOTTOM); // right border
        c.fillRect(x - 3, y - 3, w + 6, 1, BORDER_TOP);    // top border
        c.fillRect(x - 3, y + h + 2, w + 6, 1, BORDER_BOTTOM); // bottom border
    }

    private void drawText(Canvas c, int x, int y, List<List<StyledRun>> lines) {
        for (int i = 0; i < lines.size(); i++) {
            drawLine(c, x, y + i * LINE_STEP, lines.get(i));
        }
    }

    /** Lay a line's runs left-to-right: shadow first, then glyphs, plus underline/strike rules per run. */
    private void drawLine(Canvas c, int x, int y, List<StyledRun> runs) {
        int penX = x;
        for (StyledRun run : runs) {
            String text = display(run);
            int rgb = run.rgb();
            int shadow = McColors.shadow(rgb);
            int advance = font.width(text, run.bold());

            font.draw(c, text, penX + 1, y + 1, shadow, run.bold(), run.italic());
            font.draw(c, text, penX, y, rgb, run.bold(), run.italic());

            if (run.underline()) {
                drawRule(c, penX, y + font.ascent() + 1, advance, rgb, shadow);
            }
            if (run.strikethrough()) {
                drawRule(c, penX, y + font.ascent() / 2, advance, rgb, shadow);
            }
            penX += advance;
        }
    }

    /** A 1px horizontal rule in the run colour, with the same down-right shadow Minecraft draws under decorations. */
    private void drawRule(Canvas c, int x, int y, int width, int rgb, int shadow) {
        int srgb = 0xFF000000 | shadow;
        int crgb = 0xFF000000 | rgb;
        for (int i = 0; i < width; i++) {
            c.blend(x + i + 1, y + 1, srgb);
            c.blend(x + i, y, crgb);
        }
    }

    private int lineWidth(List<StyledRun> runs) {
        int w = 0;
        for (StyledRun run : runs) {
            w += font.width(display(run), run.bold());
        }
        return w;
    }

    /**
     * Obfuscated text is rendered deterministically (the image must be byte-reproducible): each non-space glyph is
     * replaced by a constant stand-in, preserving spacing/width rather than scrambling per-frame like the live client.
     */
    private static String display(StyledRun run) {
        if (!run.obfuscated()) {
            return run.text();
        }
        char[] in = run.text().toCharArray();
        char[] out = new char[in.length];
        for (int i = 0; i < in.length; i++) {
            out[i] = in[i] == ' ' ? ' ' : '#';
        }
        return new String(out);
    }
}
