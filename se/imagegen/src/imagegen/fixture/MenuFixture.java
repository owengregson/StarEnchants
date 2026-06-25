package imagegen.fixture;

import java.util.Map;

/**
 * A chest GUI to render: a {@code rows×9} grid with a §-coded title, an optional filler material for empty cells,
 * and the occupied slots by raw index. {@code hoveredSlot >= 0} overlays that slot's tooltip, the way the GUI
 * looks when the cursor rests on it. Geometry mirrors the plugin's {@code feature.menu.MenuLayout}.
 *
 * @param id        output filename base (e.g. {@code "gui-enchanter"})
 * @param rows      chest height in rows, 1..6
 * @param title     §-coded GUI title drawn above the chest
 * @param filler    Bukkit material name for empty-cell filler, or {@code null}/blank for none
 * @param slots     occupied slots by raw index (0-based, row-major)
 * @param hoveredSlot raw index whose tooltip is overlaid, or {@code -1} for none
 */
public record MenuFixture(String id, int rows, String title, String filler,
                          Map<Integer, SlotFixture> slots, int hoveredSlot) {

    public MenuFixture {
        slots = slots == null ? Map.of() : Map.copyOf(slots);
    }

    public int size() {
        return rows * 9;
    }
}
