package item.render;

import java.util.Objects;

/**
 * The presentation knobs for {@link LoreRenderer} (docs/architecture.md §4.2) — colours, the
 * level-numeral style, and the label shown for a stored key no longer in the catalog. A pure,
 * immutable value (the colours are legacy {@code '&'} codes, translated at render time), so a
 * server config can later swap a snapshot of this by reference exactly like the content snapshot.
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

    /** The built-in default look (grey enchant, white Roman level, aqua crystals). */
    public static final LoreStyle DEFAULT = new LoreStyle("&7", "&f", "&b", true, "&8Unknown Enchant");

    public LoreStyle {
        Objects.requireNonNull(enchantColor, "enchantColor");
        Objects.requireNonNull(levelColor, "levelColor");
        Objects.requireNonNull(crystalColor, "crystalColor");
        Objects.requireNonNull(unknownLabel, "unknownLabel");
    }
}
