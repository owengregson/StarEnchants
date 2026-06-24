package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness of the WHITE SCROLL (docs/v3-directives.md §I), loaded from the top-level
 * {@code items/white-scroll.yml}. The white scroll PROTECTS an item from enchant destruction: dragging it
 * onto gear stamps a one-shot guard marker so the NEXT failed enchant apply spares the item instead of
 * shattering it. It is its own physical item (distinct from the {@code holy-white-scroll}, which prevents
 * item loss on death). Carries no content grant — the guard is a marker the {@code CarrierService} stamps.
 * Immutable; lives in the {@link ItemsConfig} snapshot the runtime reads and {@code /se reload} swaps.
 *
 * @param material the white scroll's material token (cross-version resolved at mint time)
 * @param name     the white scroll's display name ({@code &} colour codes)
 * @param lore     the white scroll's lore lines
 */
public record WhiteScrollConfig(String material, String name, List<String> lore) {

    public WhiteScrollConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
    }

    /** The built-in white-scroll likeness used when {@code items/white-scroll.yml} is absent or omits fields. */
    public static WhiteScrollConfig defaults() {
        return new WhiteScrollConfig(
                "PAPER",
                "&fWhite Scroll",
                List.of("&7Protects an item — a failed", "&7enchant will spare it once."));
    }
}
