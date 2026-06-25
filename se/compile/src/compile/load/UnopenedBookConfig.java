package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The UNOPENED / RANDOMIZED book (§I), loaded from {@code items/unopened-book.yml}. Tier-scoped: right-clicking
 * yields a concrete book of a random enchant from its tier, with a random level and success in {@code [minSuccess, maxSuccess]}.
 */
public record UnopenedBookConfig(
        String material,
        String name,
        List<String> lore,
        int minSuccess,
        int maxSuccess) {

    public UnopenedBookConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        int lo = Math.max(0, Math.min(100, minSuccess));
        int hi = Math.max(0, Math.min(100, maxSuccess));
        minSuccess = Math.min(lo, hi);
        maxSuccess = Math.max(lo, hi);
    }

    public static UnopenedBookConfig defaults() {
        return new UnopenedBookConfig(
                "BOOK",
                "&b{TIER} Mystery Book",
                List.of("&7Right-click to reveal a random", "&7{TIER} enchant book."),
                25,
                100);
    }
}
