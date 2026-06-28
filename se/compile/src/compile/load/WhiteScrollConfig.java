package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The WHITE SCROLL (§I), loaded from {@code items/white-scroll.yml}: stamps a one-shot guard marker so the
 * NEXT failed enchant apply spares the item. Distinct from the {@code holy-white-scroll} (item loss on death).
 * Each minted scroll rolls a success chance in {@code [minSuccess, maxSuccess]} (default {@code 100/100} =
 * always succeeds); a failed apply consumes the scroll without stamping the guard and never destroys the gear.
 * {@code {SUCCESS}}/{@code {FAILURE}} render in the name/lore.
 */
public record WhiteScrollConfig(String material, String name, List<String> lore, int minSuccess, int maxSuccess) {

    public WhiteScrollConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        int lo = Math.max(0, Math.min(100, minSuccess));
        int hi = Math.max(0, Math.min(100, maxSuccess));
        minSuccess = Math.min(lo, hi); // order the pair so [min, max] is always valid (matches the other configs)
        maxSuccess = Math.max(lo, hi);
    }

    public static WhiteScrollConfig defaults() {
        return new WhiteScrollConfig(
                "PAPER",
                "&fWhite Scroll",
                List.of("&7Protects an item — a failed", "&7enchant will spare it once."),
                100,
                100);
    }
}
