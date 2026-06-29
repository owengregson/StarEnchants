package compile.load;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Layout + chrome overrides for ONE GUI (§L, ADR-0030), from {@code menus/<name>.yml}. The framework merges
 * this onto programmatic defaults via {@code MenuLayout.from} (geometry) and {@code MenuTheme.from} (chrome),
 * so every field is optional and an absent one keeps the code default. Only layout + chrome are surfaced —
 * behaviour (input slots, click actions, icon semantics) stays in code.
 *
 * <p>Geometry: {@code rows} (1–6), {@code title}, {@code filler} (frame-pane material, blank for none),
 * {@code frame} ({@code none}/{@code bottom}/{@code border}), and the four nav-button raw slots.
 * Chrome: per-button {@code material}/{@code name} for prev/next/back/close, and the {@code info} pane's
 * material/name/slot. A blank value is treated per field (see {@code MenusLoader}).
 */
public record MenuLayoutConfig(OptionalInt rows, Optional<String> title, Optional<String> filler,
                               Optional<String> frame, OptionalInt prevSlot, OptionalInt nextSlot,
                               OptionalInt backSlot, OptionalInt closeSlot,
                               Optional<String> prevButtonMaterial, Optional<String> prevButtonName,
                               Optional<String> nextButtonMaterial, Optional<String> nextButtonName,
                               Optional<String> backButtonMaterial, Optional<String> backButtonName,
                               Optional<String> closeButtonMaterial, Optional<String> closeButtonName,
                               Optional<String> infoMaterial, Optional<String> infoName, OptionalInt infoSlot) {

    public MenuLayoutConfig {
        Objects.requireNonNull(rows, "rows");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(filler, "filler");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(prevSlot, "prevSlot");
        Objects.requireNonNull(nextSlot, "nextSlot");
        Objects.requireNonNull(backSlot, "backSlot");
        Objects.requireNonNull(closeSlot, "closeSlot");
        Objects.requireNonNull(prevButtonMaterial, "prevButtonMaterial");
        Objects.requireNonNull(prevButtonName, "prevButtonName");
        Objects.requireNonNull(nextButtonMaterial, "nextButtonMaterial");
        Objects.requireNonNull(nextButtonName, "nextButtonName");
        Objects.requireNonNull(backButtonMaterial, "backButtonMaterial");
        Objects.requireNonNull(backButtonName, "backButtonName");
        Objects.requireNonNull(closeButtonMaterial, "closeButtonMaterial");
        Objects.requireNonNull(closeButtonName, "closeButtonName");
        Objects.requireNonNull(infoMaterial, "infoMaterial");
        Objects.requireNonNull(infoName, "infoName");
        Objects.requireNonNull(infoSlot, "infoSlot");
    }

    /** A geometry-only override (no chrome) — the historical constructor, kept for tests and the loader. */
    public static MenuLayoutConfig geometry(OptionalInt rows, Optional<String> title, Optional<String> filler,
                                            OptionalInt prevSlot, OptionalInt nextSlot, OptionalInt backSlot,
                                            OptionalInt closeSlot) {
        return new MenuLayoutConfig(rows, title, filler, Optional.empty(), prevSlot, nextSlot, backSlot,
                closeSlot, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), OptionalInt.empty());
    }
}
