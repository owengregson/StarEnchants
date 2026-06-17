package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of the UNOPENED / RANDOMIZED book (docs/v3-directives.md §I),
 * loaded from the top-level {@code items/unopened-book.yml}. Tier-scoped: right-clicking it yields a
 * concrete enchant book of a random enchant from its tier, with a random level and a random success
 * chance in {@code [minSuccess, maxSuccess]}. Immutable; lives in the {@link ItemsConfig} snapshot the
 * runtime reads and {@code /se reload} swaps. {@code {TIER}} in the name/lore renders the scoped tier.
 *
 * @param material        the unopened-book item material token (resolved cross-version at use)
 * @param name            its display name ({@code &} colours; {@code {TIER}} placeholder)
 * @param lore            its lore lines ({@code {TIER}} placeholder)
 * @param minSuccess      the lower bound of the produced book's random success chance, 0..100
 * @param maxSuccess      the upper bound of the produced book's random success chance, 0..100
 * @param messageOpen     chat when a book is rolled ({@code {ENCHANT}}, {@code {LEVEL}}, {@code {PERCENT}})
 * @param messageEmptyTier chat when the scoped tier has no enchants to roll
 */
public record UnopenedBookConfig(
        String material,
        String name,
        List<String> lore,
        int minSuccess,
        int maxSuccess,
        String messageOpen,
        String messageEmptyTier) {

    public UnopenedBookConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        int lo = Math.max(0, Math.min(100, minSuccess));
        int hi = Math.max(0, Math.min(100, maxSuccess));
        minSuccess = Math.min(lo, hi);
        maxSuccess = Math.max(lo, hi);
    }

    /** The built-in unopened-book used when {@code items/unopened-book.yml} is absent or omits fields. */
    public static UnopenedBookConfig defaults() {
        return new UnopenedBookConfig(
                "BOOK",
                "&b{TIER} Mystery Book",
                List.of("&7Right-click to reveal a random", "&7{TIER} enchant book."),
                25,
                100,
                "&aYou revealed &f{ENCHANT} {LEVEL}&a (&f{PERCENT}%&a success)!",
                "&cThere are no enchants in that tier to reveal.");
    }
}
