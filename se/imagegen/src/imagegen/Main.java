package imagegen;

import imagegen.assets.AssetSource;
import imagegen.assets.VanillaAssets;
import imagegen.fixture.Fixtures;
import imagegen.fixture.ItemFixture;
import imagegen.fixture.MenuFixture;
import imagegen.font.GlyphFont;
import imagegen.font.MinecraftFont;
import imagegen.render.block.BlockRenderer;
import imagegen.render.gui.ChestRenderer;
import imagegen.render.sprite.ItemSpriteRenderer;
import imagegen.render.tooltip.TooltipRenderer;
import java.nio.file.Path;

/**
 * Renders every fixture (tooltips + GUIs) to a PNG. Wires the font, the vanilla-asset source, and the renderers,
 * then writes each fixture NEAREST-upscaled by {@code se.imagegen.scale} (default 4) into {@code se.imagegen.out}
 * (default {@code website/static/img/renders}). Tooltips need no assets (the font is bundled); item sprites do —
 * point at them with {@code MC_ASSETS_DIR} or let {@link VanillaAssets} fetch a pinned client jar.
 */
public final class Main {

    public static void main(String[] args) {
        Path out = Path.of(System.getProperty("se.imagegen.out", "website/static/img/renders"));
        int scale = Integer.getInteger("se.imagegen.scale", 4);

        AssetSource assets = VanillaAssets.create();
        System.out.println("[imagegen] scale=" + scale + " out=" + out
                + " assets=" + (assets.available() ? "available" : "MISSING (set MC_ASSETS_DIR)"));
        GlyphFont font = new MinecraftFont(assets); // renders the real font/ascii.png — needs assets

        TooltipRenderer tooltips = new TooltipRenderer(font);
        ChestRenderer chests = new ChestRenderer(font, assets, new ItemSpriteRenderer(), new BlockRenderer(), tooltips);

        for (ItemFixture f : Fixtures.tooltips()) {
            tooltips.render(f.name(), f.lore()).scaled(scale).writePng(out.resolve(f.id() + ".png"));
            System.out.println("  wrote " + f.id() + ".png");
        }
        for (MenuFixture m : Fixtures.menus()) {
            chests.render(m).scaled(scale).writePng(out.resolve(m.id() + ".png"));
            System.out.println("  wrote " + m.id() + ".png");
        }
        System.out.println("[imagegen] done");
    }

    private Main() {
    }
}
