package compile.load;

import java.util.List;
import java.util.Objects;
import schema.spec.Ranges;

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
        Ranges.IntRange success = Ranges.percentRange(minSuccess, maxSuccess);
        minSuccess = success.min();
        maxSuccess = success.max();
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
