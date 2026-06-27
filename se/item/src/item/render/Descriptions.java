package item.render;

import java.util.List;

/**
 * Splits a content {@code description} into its display lines. Authored descriptions are stored as one
 * string with {@code '\n'} line separators (the loader joins a YAML list, or reads a multi-line scalar, the
 * same way — {@code compile.load.ContentParse}); the importers likewise join each source line with a newline.
 * Item lore is a LIST of lines, so every render site (menu icons, the enchant book) must split here rather
 * than emit one lore entry carrying embedded newlines — those don't render as line breaks across the range.
 */
public final class Descriptions {

    private Descriptions() {
    }

    /** The description's lines in order; empty for a {@code null}/blank description. Blank lines are kept (separators). */
    public static List<String> lines(String description) {
        if (description == null || description.isBlank()) {
            return List.of();
        }
        return List.of(description.split("\n", -1));
    }
}
