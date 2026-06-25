package compile.load;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Layout overrides for ONE GUI (§L), from {@code menus/<name>.yml}. The framework merges this onto a
 * programmatic default via {@code MenuLayout.from}, so every field is optional and an absent one keeps the
 * code default. Only layout is surfaced — behaviour (input slots, click actions, icon semantics) stays in code.
 *
 * @param rows     chest height 1–6
 * @param filler   filler-pane material name, blank for none
 * @param prevSlot raw slot of the previous-page button, {@code -1} to hide
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
