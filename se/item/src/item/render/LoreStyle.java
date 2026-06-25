package item.render;

import java.util.Objects;

/**
 * Presentation knobs for {@link LoreRenderer} (§4.2) — colours, numeral style, the label for a stored key
 * no longer in the catalog. Immutable value (colours are legacy {@code '&'} codes, translated at render
 * time) so a config snapshot of it can be swapped by reference like the content snapshot.
 *
 * @param enchantColor colour prefix for an enchant's display name (e.g. {@code "&7"})
 * @param levelColor   colour prefix for the level numeral (e.g. {@code "&f"})
 * @param crystalColor colour prefix for a crystal line (e.g. {@code "&b"})
 * @param roman        whether levels render as Roman numerals ({@code Venom III}) or Arabic ({@code Venom 3})
 * @param unknownLabel the name rendered for a stored stable key absent from the catalog (§5.3)
 */
public record LoreStyle(
        String enchantColor,
        String levelColor,
        String crystalColor,
        boolean roman,
        String unknownLabel) {

    public static final LoreStyle DEFAULT = new LoreStyle("&7", "&f", "&b", true, "&8Unknown Enchant");

    public LoreStyle {
        Objects.requireNonNull(enchantColor, "enchantColor");
        Objects.requireNonNull(levelColor, "levelColor");
        Objects.requireNonNull(crystalColor, "crystalColor");
        Objects.requireNonNull(unknownLabel, "unknownLabel");
    }
}
