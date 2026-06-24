package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of the SLOT ORB (docs/v3-directives.md §H) — the upgrade orb that
 * grants a configurable {@code +N} per use, loaded from the top-level {@code items/slot-orb.yml}. It raises
 * a piece's purchased enchant-slot count, clamped to a {@link #hardCap universal maximum total} (base +
 * added). Immutable; lives in the {@link ItemsConfig} snapshot the runtime reads and {@code /se reload}
 * swaps. {@code {AMOUNT}} in the orb's name/lore renders {@link #orbAmount}.
 *
 * @param orbMaterial   the upgrade-orb item material token (resolved cross-version at use)
 * @param orbName       the orb's display name ({@code &} colours; {@code {AMOUNT}} placeholder)
 * @param orbLore       the orb's lore lines ({@code {AMOUNT}} placeholder)
 * @param orbAmount     how many slots one orb grants (clamped &ge; 1)
 * @param hardCap       the universal maximum TOTAL slot count (base + added) any item may reach
 *
 * <p>The apply/at-cap messages now live in {@code lang.yml} ({@code slot.apply} / {@code slot.at-cap}) — §L
 * centralised them out of this likeness config.
 */
public record SlotConfig(
        String orbMaterial,
        String orbName,
        List<String> orbLore,
        int orbAmount,
        int hardCap) {

    public SlotConfig {
        Objects.requireNonNull(orbMaterial, "orbMaterial");
        Objects.requireNonNull(orbName, "orbName");
        orbLore = List.copyOf(orbLore);
        orbAmount = Math.max(1, orbAmount);
        hardCap = Math.max(1, hardCap);
    }

    /** The built-in slot-orb config used when {@code items/slot-orb.yml} is absent or omits fields. */
    public static SlotConfig defaults() {
        return new SlotConfig(
                "ENDER_EYE",
                "&5Slot Expander &7(+{AMOUNT})",
                List.of("&7Drag onto gear to add &f{AMOUNT}&7 enchant slots."),
                3,
                15);
    }
}
