package feature.menu;

/**
 * The config-driven layout of a menu (docs/v3-directives.md §K): a menu supplies a programmatic default
 * ({@link #paged}/{@link #sized}) that the §L {@code menus/} layer may override from YAML. A paged menu
 * reserves the last row for navigation; a nav slot of {@code -1} means "not shown".
 *
 * @param rows           chest height in rows, 1..6
 * @param titleTemplate  the base title (legacy {@code &} colour codes); a page suffix is appended when paged
 * @param fillerMaterial material name for the bottom-row filler panes (resolved by name, cross-version), or
 *                       blank/{@code null} for no filler
 * @param prevSlot       raw slot of the "previous page" button, or {@code -1}
 * @param nextSlot       raw slot of the "next page" button, or {@code -1}
 * @param backSlot       raw slot of the "back" button, or {@code -1}
 * @param closeSlot      raw slot of the "close" button, or {@code -1}
 */
public record MenuLayout(int rows, String titleTemplate, String fillerMaterial,
                         int prevSlot, int nextSlot, int backSlot, int closeSlot) {

    public MenuLayout {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("menu rows must be 1..6, got " + rows);
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

    /** Content cells per page: every cell above the reserved bottom navigation row. */
    public int contentSlots() {
        return (rows - 1) * 9;
    }

    public int navRowStart() {
        return contentSlots();
    }

    /** The title for {@code page} of {@code pages} (1-based); a single-page menu shows no page suffix. */
    public String titleFor(int page, int pages) {
        return pages > 1 ? titleTemplate + "  (" + page + "/" + pages + ")" : titleTemplate;
    }

    /** A standard 6-row paged layout: prev bottom-left (45), next bottom-right (53), back (48), close (49). */
    public static MenuLayout paged(String title) {
        return new MenuLayout(6, title, "GRAY_STAINED_GLASS_PANE", 45, 53, 48, 49);
    }

    /** A paged layout of a chosen height; the bottom row is reserved for nav as in {@link #paged}. */
    public static MenuLayout sized(int rows, String title) {
        int base = (rows - 1) * 9;
        return new MenuLayout(rows, title, "GRAY_STAINED_GLASS_PANE", base, base + 8, base + 3, base + 4);
    }

    /** A single-screen form layout (no navigation row) for a {@link FormMenu} bench. */
    public static MenuLayout form(int rows, String title) {
        return new MenuLayout(rows, title, "GRAY_STAINED_GLASS_PANE", -1, -1, -1, -1);
    }
}
