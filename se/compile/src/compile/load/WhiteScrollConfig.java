package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The WHITE SCROLL (§I), loaded from {@code items/white-scroll.yml}: stamps a one-shot guard marker so the
 * NEXT failed enchant apply spares the item. Distinct from the {@code holy-white-scroll} (item loss on death).
 */
public record WhiteScrollConfig(String material, String name, List<String> lore) {

    public WhiteScrollConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }

    public static WhiteScrollConfig defaults() {
        return new WhiteScrollConfig(
                "PAPER",
                "&fWhite Scroll",
                List.of("&7Protects an item — a failed", "&7enchant will spare it once."));
    }
}
