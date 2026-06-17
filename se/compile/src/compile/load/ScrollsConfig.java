package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of the SCROLL family (docs/v3-directives.md §I), loaded from the
 * top-level {@code items/scrolls.yml}. One file groups the scroll family (each a one-shot consumable whose
 * behaviour is decided by its kind) rather than a config-per-scroll, since they share the same item-data
 * marker ({@code ScrollCodec}) and gesture surface. Immutable; lives in the {@link ItemsConfig} snapshot
 * the runtime reads and {@code /se reload} swaps.
 *
 * <p>This wave carries the two book-economy scrolls; transmog / holy / nametag extend it later as further
 * nested sub-records.
 *
 * @param black      the black scroll (extract one enchant from gear into a book)
 * @param randomizer the randomizer scroll (reroll a book's success chance)
 */
public record ScrollsConfig(Black black, Randomizer randomizer) {

    public ScrollsConfig {
        Objects.requireNonNull(black, "black");
        Objects.requireNonNull(randomizer, "randomizer");
    }

    /**
     * The black scroll: dragged onto enchanted gear, it extracts one (random) enchant into an enchant book
     * with a {@link #successChance} roll. On failure the scroll is spent and nothing is extracted.
     */
    public record Black(String material, String name, List<String> lore, int successChance,
                        String messageSuccess, String messageFail, String messageNoEnchants) {
        public Black {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            successChance = Math.max(0, Math.min(100, successChance));
        }
    }

    /**
     * The randomizer scroll: dragged onto an enchant book, it rerolls the book's success chance to a random
     * value in {@code [minPercent, maxPercent]}.
     */
    public record Randomizer(String material, String name, List<String> lore, int minPercent, int maxPercent,
                             String messageSuccess, String messageNotBook) {
        public Randomizer {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(lore);
            int lo = Math.max(0, Math.min(100, minPercent));
            int hi = Math.max(0, Math.min(100, maxPercent));
            minPercent = Math.min(lo, hi);
            maxPercent = Math.max(lo, hi);
        }
    }

    /** The built-in scroll likenesses used when {@code items/scrolls.yml} is absent or omits fields. */
    public static ScrollsConfig defaults() {
        return new ScrollsConfig(
                new Black(
                        "INK_SAC",
                        "&8Black Scroll",
                        List.of("&7Drag onto enchanted gear to", "&7extract one enchant into a book."),
                        80,
                        "&aExtracted &f{ENCHANT}&a into a book.",
                        "&cThe black scroll crumbled — nothing was extracted.",
                        "&cThat item has no enchants to extract."),
                new Randomizer(
                        "SUGAR",
                        "&eRandomizer Scroll",
                        List.of("&7Drag onto an enchant book to", "&7reroll its success chance."),
                        25,
                        100,
                        "&aThe book's success chance was rerolled to &f{PERCENT}%&a.",
                        "&cThe randomizer only works on an enchant book."));
    }
}
