package feature.menu;

import item.mint.ItemFactory;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Shared icon builders for the themed menu chrome (ADR-0030) — the one place a decorative pane, a navigation
 * button with its live page suffix, an info pane, or a hub tile is assembled, so every menu reads as one
 * hand-crafted surface instead of each re-rolling its own look. Pure construction (no entity/world read), so
 * it is Folia-safe from any thread, like {@link ItemFactory}.
 */
public final class MenuIcons {

    private MenuIcons() {
    }

    /** A blank decorative frame pane from a material token (cross-version), or {@code null} for a blank token. */
    public static ItemStack pane(String materialToken) {
        Material material = ItemFactory.material(materialToken, Material.AIR);
        if (material == Material.AIR) {
            return null; // blank/unknown token → no pane (the cell stays empty)
        }
        return ItemFactory.build(material, " ", List.of());
    }

    /** A previous/next page button, with a live "Page x of y" line appended under its themed lore. */
    public static ItemStack page(NavButton button, int targetPage, int pageCount) {
        return button.icon(List.of("&8Page &7" + targetPage + " &8of &7" + pageCount), false);
    }

    /** The back / close / info buttons — themed name + lore, no dynamic suffix. */
    public static ItemStack plain(NavButton button) {
        return button.icon(List.of(), false);
    }

    /**
     * A hub / category tile: a featured icon with a coloured title, descriptive lore, and a closing
     * "&amp;e▸ Click ..." call-to-action so every actionable tile tells the player what the click does. The
     * tile gently glows so it reads as interactive against the flat frame.
     *
     * @param materialToken icon material/custom-item token (cross-version)
     * @param fallback      vanilla material when the token resolves to nothing
     * @param name          coloured title (legacy {@code &} codes)
     * @param description   body lines (legacy {@code &} codes); blank entries become spacer lines
     * @param action        the call-to-action line (e.g. "&eClick to open."), or blank for a non-interactive tile
     */
    public static ItemStack tile(String materialToken, Material fallback, String name,
                                 List<String> description, String action) {
        List<String> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(line == null ? "" : line);
        }
        boolean interactive = action != null && !action.isBlank();
        if (interactive) {
            lore.add("");
            lore.add(action);
        }
        ItemStack icon = ItemFactory.build(materialToken, fallback, name, lore);
        return interactive ? ItemFactory.glow(icon) : icon;
    }

    /** Convenience tile from a vanilla material. */
    public static ItemStack tile(Material material, String name, List<String> description, String action) {
        return tile(material.name(), material, name, description, action);
    }
}
