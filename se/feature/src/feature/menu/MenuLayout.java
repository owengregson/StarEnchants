package feature.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * The config-driven geometry of a menu (docs/v3-directives.md §K, ADR-0030): a menu supplies a programmatic
 * default ({@link #paged}/{@link #sized}/{@link #form}) that the §L {@code menus/} layer may override from
 * YAML ({@link #from}). The bottom row is always reserved for navigation; the {@link Frame} decides which of
 * the remaining cells are decorative panes versus paged content, so {@link #contentSlot} is the single source
 * of where the i-th paged item lands (a {@code BORDER} insets content inside a one-cell perimeter). A nav slot
 * of {@code -1} means "not shown".
 *
 * @param rows           chest height in rows, 1..6
 * @param titleTemplate  the base title (legacy {@code &} colour codes); a page suffix is appended when paged
 * @param fillerMaterial material name for the decorative frame panes (resolved by name, cross-version), or
 *                       blank/{@code null} for no panes
 * @param frame          how the decorative panes wrap the content
 * @param prevSlot       raw slot of the "previous page" button, or {@code -1}
 * @param nextSlot       raw slot of the "next page" button, or {@code -1}
 * @param backSlot       raw slot of the "back" button, or {@code -1}
 * @param closeSlot      raw slot of the "close" button, or {@code -1}
 */
public record MenuLayout(int rows, String titleTemplate, String fillerMaterial, Frame frame,
                         int prevSlot, int nextSlot, int backSlot, int closeSlot) {

    public MenuLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("menu rows must be 1..6, got " + rows);
        }
        if (frame == null) {
            frame = Frame.BOTTOM;
        }
    }

    /**
     * Merge an operator's {@link compile.load.MenuLayoutConfig} ({@code menus/<name>.yml}, §L) onto {@code def}:
     * each set field wins, each unset keeps the default; a {@code null} override returns {@code def} unchanged.
     * Rows clamp to 1..6; a nav slot that would fall outside the resized inventory is hidden ({@code -1})
     * rather than crashing the render.
     */
    public static MenuLayout from(MenuLayout def, compile.load.MenuLayoutConfig override) {
        if (override == null) {
            return def;
        }
        int rows = Math.min(6, Math.max(1, override.rows().orElse(def.rows())));
        int size = rows * 9;
        return new MenuLayout(
                rows,
                override.title().orElse(def.titleTemplate()),
                override.filler().orElse(def.fillerMaterial()),
                Frame.parse(override.frame().orElse(null), def.frame()),
                fitSlot(override.prevSlot().orElse(def.prevSlot()), size),
                fitSlot(override.nextSlot().orElse(def.nextSlot()), size),
                fitSlot(override.backSlot().orElse(def.backSlot()), size),
                fitSlot(override.closeSlot().orElse(def.closeSlot()), size));
    }

    private static int fitSlot(int slot, int size) {
        return slot >= 0 && slot < size ? slot : -1;
    }

    public int size() {
        return rows * 9;
    }

    /** First slot of the reserved bottom navigation row. */
    public int navRowStart() {
        return (rows - 1) * 9;
    }

    /** How many paged content cells this layout exposes per page (frame-dependent). */
    public int contentSlotCount() {
        return switch (frame) {
            case BORDER -> Math.max(0, rows - 2) * 7;       // interior rows × 7 inner columns
            case BOTTOM, NONE -> navRowStart();             // every cell above the nav row
        };
    }

    /**
     * The raw inventory slot the {@code index}-th paged content item lands in, or {@code -1} when {@code index}
     * is out of range. The single source of content placement: tests and live suites read position from here
     * rather than assuming content starts at slot 0 (a {@code BORDER} starts it at slot 10).
     */
    public int contentSlot(int index) {
        if (index < 0 || index >= contentSlotCount()) {
            return -1;
        }
        return switch (frame) {
            case BORDER -> {
                int row = 1 + index / 7;     // skip the top border row
                int col = 1 + index % 7;     // skip the left border column
                yield row * 9 + col;
            }
            case BOTTOM, NONE -> index;
        };
    }

    /** The decorative frame-pane slots for this frame (the nav-row slots included; nav buttons overwrite them). */
    public List<Integer> paneSlots() {
        List<Integer> out = new ArrayList<>();
        int size = size();
        switch (frame) {
            case NONE -> { /* no panes */ }
            case BOTTOM -> {
                for (int slot = navRowStart(); slot < size; slot++) {
                    out.add(slot);
                }
            }
            case BORDER -> {
                for (int col = 0; col < 9; col++) {
                    out.add(col);                       // top row
                    out.add(navRowStart() + col);       // bottom (nav) row
                }
                for (int row = 1; row < rows - 1; row++) {
                    out.add(row * 9);                   // left column
                    out.add(row * 9 + 8);               // right column
                }
            }
        }
        return out;
    }

    /** A standard 6-row framed paged layout: prev bottom-left (45), next bottom-right (53), back (48), close (49). */
    public static MenuLayout paged(String title) {
        return new MenuLayout(6, title, "GRAY_STAINED_GLASS_PANE", Frame.BORDER, 45, 53, 48, 49);
    }

    /** A framed paged layout of a chosen height; the bottom row is reserved for nav as in {@link #paged}. */
    public static MenuLayout sized(int rows, String title) {
        int base = (rows - 1) * 9;
        return new MenuLayout(rows, title, "GRAY_STAINED_GLASS_PANE", Frame.BORDER,
                base, base + 8, base + 3, base + 4);
    }

    /**
     * A single-screen form layout (no page navigation) for a {@link FormMenu} bench: a back button in the
     * bottom-left corner (shown only when the bench was opened from a hub) and a close button in the
     * bottom-right. The bench fills its own backdrop, so the frame is {@link Frame#NONE} here.
     */
    public static MenuLayout form(int rows, String title) {
        int navRow = (rows - 1) * 9;
        return new MenuLayout(rows, title, "GRAY_STAINED_GLASS_PANE", Frame.NONE, -1, -1, navRow, rows * 9 - 1);
    }
}
