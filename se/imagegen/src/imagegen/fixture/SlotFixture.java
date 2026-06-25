package imagegen.fixture;

import java.util.List;

/**
 * One occupied slot in a GUI fixture: the sprite material, stack count (the white number drawn bottom-right when
 * {@code > 1}), and the §-coded name/lore used if this slot is the hovered one whose tooltip is overlaid.
 *
 * @param material Bukkit material name for the slot sprite
 * @param count    stack size; {@code <= 1} draws no number
 * @param name     §-coded item name (for the hovered tooltip)
 * @param lore     §-coded lore lines (for the hovered tooltip)
 */
public record SlotFixture(String material, int count, String name, List<String> lore) {

    public SlotFixture {
        lore = lore == null ? List.of() : List.copyOf(lore);
    }

    public static SlotFixture of(String material, String name) {
        return new SlotFixture(material, 1, name, List.of());
    }
}
