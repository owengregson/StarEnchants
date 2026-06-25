package compile.load;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Operator-surfaced layout of ONE GUI (§L), loaded from {@code menus/<name>.yml}. Pure data, independent of
 * the Bukkit-adjacent {@code feature.menu.MenuLayout}; the framework merges this onto its programmatic
 * default via {@code MenuLayout.from}, so every field is optional and an absent field keeps the code default.
 * Only layout is surfaced — behaviour (input slots, click actions, icon semantics) stays in code.
 *
 * @param rows     chest height 1–6
 * @param title    base title, legacy {@code &} codes
 * @param filler   filler-pane material name, blank for none
 * @param prevSlot raw slot of the previous-page button, {@code -1} to hide
 * @param nextSlot raw slot of the next-page button
 * @param backSlot raw slot of the back button
 * @param closeSlot raw slot of the close button
 */
public record MenuLayoutConfig(OptionalInt rows, Optional<String> title, Optional<String> filler,
                               OptionalInt prevSlot, OptionalInt nextSlot, OptionalInt backSlot,
                               OptionalInt closeSlot) {

    public MenuLayoutConfig {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(prevSlot, "prevSlot");
        Objects.requireNonNull(nextSlot, "nextSlot");
        Objects.requireNonNull(backSlot, "backSlot");
        Objects.requireNonNull(closeSlot, "closeSlot");
    }
}
