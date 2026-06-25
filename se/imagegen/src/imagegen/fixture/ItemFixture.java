package imagegen.fixture;

import java.util.List;

/**
 * One item to render as a hovered tooltip: its sprite material, the §-coded display name shown as the tooltip's
 * title, and the §-coded lore lines beneath it. The lore is supplied already-rendered — for enchant/set previews
 * it comes straight from the plugin's {@code item.render.LoreRenderer}, so the screenshot matches the live game.
 *
 * @param id       output filename base (e.g. {@code "tooltip-venom-sword"})
 * @param material Bukkit material name for the sprite (e.g. {@code "DIAMOND_SWORD"})
 * @param name     §-coded display name (the tooltip title line)
 * @param lore     §-coded lore lines, in order
 */
public record ItemFixture(String id, String material, String name, List<String> lore) {

    public ItemFixture {
        lore = lore == null ? List.of() : List.copyOf(lore);
    }
}
