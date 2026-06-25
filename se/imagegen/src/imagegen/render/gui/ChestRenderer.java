package imagegen.render.gui;

import imagegen.Argb;
import imagegen.Canvas;
import imagegen.assets.AssetSource;
import imagegen.assets.ResolvedModel;
import imagegen.fixture.MenuFixture;
import imagegen.fixture.SlotFixture;
import imagegen.font.GlyphFont;
import imagegen.render.block.BlockRenderer;
import imagegen.render.sprite.ItemSpriteRenderer;
import imagegen.render.tooltip.TooltipRenderer;
import imagegen.text.LegacyText;
import imagegen.text.McColors;
import imagegen.text.StyledRun;
import java.awt.image.BufferedImage;

/**
 * Composites a chest GUI the way Minecraft draws it: the beveled gray container panel with recessed 18px slot
 * cells, the dark title (GUI text has no shadow), each slot's item sprite (flat icon or isometric block) with its
 * stack count, and — for the hovered slot — the item tooltip overlaid beside it. The panel chrome is drawn from
 * Minecraft's known inventory palette rather than slicing the {@code generic_54} texture, so a GUI still renders
 * exactly right even when no vanilla assets are present; only the item icons need real textures (and degrade to the
 * vanilla magenta/black missing-texture when they're absent).
 */
public final class ChestRenderer {

    // Vanilla inventory palette.
    private static final int PANEL = Argb.opaque(0xC6C6C6);
    private static final int BEVEL_LIGHT = Argb.opaque(0xFFFFFF);
    private static final int BEVEL_DARK = Argb.opaque(0x555555);
    private static final int SLOT_BG = Argb.opaque(0x8B8B8B);
    private static final int SLOT_TL = Argb.opaque(0x373737); // recessed top-left
    private static final int SLOT_BR = Argb.opaque(0xFFFFFF); // raised bottom-right
    private static final int TITLE_RGB = 0x404040;

    private static final int PANEL_W = 176;
    private static final int X0 = 8;   // first slot left
    private static final int Y0 = 18;  // first slot top (below the title bar)
    private static final int PITCH = 18;

    private final GlyphFont font;
    private final AssetSource assets;
    private final ItemSpriteRenderer sprites;
    private final BlockRenderer blocks;
    private final TooltipRenderer tooltips;

    public ChestRenderer(GlyphFont font, AssetSource assets, ItemSpriteRenderer sprites, BlockRenderer blocks,
            TooltipRenderer tooltips) {
        this.font = font;
        this.assets = assets;
        this.sprites = sprites;
        this.blocks = blocks;
        this.tooltips = tooltips;
    }

    /** Render {@code menu} to an UNSCALED canvas (the caller NEAREST-upscales). Includes the hovered tooltip, if any. */
    public Canvas render(MenuFixture menu) {
        int rows = menu.rows();
        int panelH = Y0 + rows * PITCH + 7;
        Canvas panel = new Canvas(PANEL_W, panelH);

        panel.fillRect(0, 0, PANEL_W, panelH, PANEL);
        // Raised-window bevel: light top/left, dark bottom/right.
        panel.fillRect(0, 0, PANEL_W, 1, BEVEL_LIGHT);
        panel.fillRect(0, 0, 1, panelH, BEVEL_LIGHT);
        panel.fillRect(0, panelH - 1, PANEL_W, 1, BEVEL_DARK);
        panel.fillRect(PANEL_W - 1, 0, 1, panelH, BEVEL_DARK);

        drawText(panel, menu.title(), X0, 6, TITLE_RGB, false);

        boolean hasFiller = menu.filler() != null && !menu.filler().isBlank();
        for (int idx = 0; idx < rows * 9; idx++) {
            int sx = X0 + (idx % 9) * PITCH;
            int sy = Y0 + (idx / 9) * PITCH;
            drawSlotWell(panel, sx, sy);
            SlotFixture slot = menu.slots().get(idx);
            if (slot == null && hasFiller) {
                slot = SlotFixture.of(menu.filler(), " ");
            }
            if (slot != null) {
                panel.blit(iconFor(slot.material()), sx, sy);
                if (slot.count() > 1) {
                    String count = Integer.toString(slot.count());
                    drawText(panel, count, sx + 17 - font.width(count, false), sy + 9, 0xFFFFFF, true);
                }
            }
        }

        return overlayTooltip(panel, menu);
    }

    private Canvas overlayTooltip(Canvas panel, MenuFixture menu) {
        int hovered = menu.hoveredSlot();
        SlotFixture slot = hovered >= 0 ? menu.slots().get(hovered) : null;
        if (slot == null || (blank(slot.name()) && slot.lore().isEmpty())) {
            return panel;
        }
        Canvas tip = tooltips.render(slot.name(), slot.lore());
        int hx = X0 + (hovered % 9) * PITCH;
        int hy = Y0 + (hovered / 9) * PITCH;
        int tipX = hx + 16; // just right of the slot, where the cursor would be
        int tipY = hy - 4;
        Canvas out = new Canvas(Math.max(panel.width, tipX + tip.width), Math.max(panel.height, tipY + tip.height));
        out.blit(panel, 0, 0);
        out.blit(tip, tipX, tipY);
        return out;
    }

    /** A recessed 16×16 slot well at {@code (sx,sy)} with the dark top-left / light bottom-right inset border. */
    private void drawSlotWell(Canvas c, int sx, int sy) {
        c.fillRect(sx, sy, 16, 16, SLOT_BG);
        c.fillRect(sx - 1, sy - 1, 17, 1, SLOT_TL); // top
        c.fillRect(sx - 1, sy - 1, 1, 17, SLOT_TL); // left
        c.fillRect(sx - 1, sy + 16, 18, 1, SLOT_BR); // bottom
        c.fillRect(sx + 16, sy - 1, 1, 18, SLOT_BR); // right
    }

    /** The 16×16 inventory icon for a material: flat sprite, isometric block, or the missing-texture placeholder. */
    private BufferedImage iconFor(String material) {
        ResolvedModel rm = assets.resolve(material);
        switch (rm.kind) {
            case FLAT:
                BufferedImage flat = sprites.render(rm);
                return flat != null ? flat : missingTexture();
            case BLOCK:
                return blocks.render(rm.block);
            default:
                return missingTexture();
        }
    }

    /** Minecraft's magenta/black missing-texture (a 2×2 of 8px quadrants) — only shown when assets are absent. */
    private BufferedImage missingTexture() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        int magenta = Argb.opaque(0xF800F8);
        int black = Argb.opaque(0x000000);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                boolean alt = (x < 8) ^ (y < 8);
                img.setRGB(x, y, alt ? magenta : black);
            }
        }
        return img;
    }

    private void drawText(Canvas c, String legacy, int x, int y, int defaultRgb, boolean shadow) {
        int cx = x;
        for (StyledRun run : LegacyText.parse(legacy, defaultRgb)) {
            if (shadow) {
                font.draw(c, run.text(), cx + 1, y + 1, McColors.shadow(run.rgb()), run.bold(), run.italic());
            }
            font.draw(c, run.text(), cx, y, run.rgb(), run.bold(), run.italic());
            cx += font.width(run.text(), run.bold());
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
