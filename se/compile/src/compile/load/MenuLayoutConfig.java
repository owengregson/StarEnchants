package compile.load;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The operator-surfaced layout of ONE GUI (docs/v3-directives.md §L), loaded from {@code menus/<name>.yml}.
 * Pure data, independent of {@code feature.menu.MenuLayout} (which is Bukkit-adjacent and lives above this
 * module) — the menu framework merges this onto its programmatic default via {@code MenuLayout.from}, so any
 * field left unset here falls back to the code default. Only genuinely-layout fields are surfaced: rows,
 * title, filler material, and the four nav-button slots. Behaviour (input slots, click actions, icon
 * semantics) stays in code.
 *
 * <p>Every field is optional: an absent field means "keep the programmatic default for this menu". This is
 * how a {@code menus/<name>.yml} that only re-colours the title leaves everything else untouched.
 *
 * @param rows     chest height 1–6 (absent → default)
 * @param title    base title, legacy {@code &} codes (absent → default)
 * @param filler   filler-pane material name, blank for none (absent → default)
 * @param prevSlot raw slot of the previous-page button, {@code -1} to hide (absent → default)
 * @param nextSlot raw slot of the next-page button (absent → default)
 * @param backSlot raw slot of the back button (absent → default)
 * @param closeSlot raw slot of the close button (absent → default)
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
