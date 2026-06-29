package feature.menu;

/**
 * How a menu's decorative panes are laid out around its paged content (docs/v3-directives.md §K, ADR-0030).
 * The bottom row is ALWAYS reserved for navigation regardless of frame; the frame only decides which of the
 * remaining cells are decorative chrome versus paged content — so it drives {@link MenuLayout#contentSlot}.
 */
public enum Frame {

    /** No decorative panes at all; content fills every cell above the navigation row. */
    NONE,

    /** Only the reserved bottom navigation row is panelled; content fills every cell above it (the original look). */
    BOTTOM,

    /**
     * A one-cell decorative perimeter (top row, both side columns, bottom navigation row); content is inset
     * inside it. The framed, gallery-style default that makes a menu read as hand-crafted rather than a raw grid.
     */
    BORDER;

    /** Parse a config token (case-insensitive) to a frame, or {@code fallback} for null/blank/unknown. */
    public static Frame parse(String token, Frame fallback) {
        if (token == null || token.isBlank()) {
            return fallback;
        }
        return switch (token.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "NONE" -> NONE;
            case "BOTTOM" -> BOTTOM;
            case "BORDER" -> BORDER;
            default -> fallback;
        };
    }
}
