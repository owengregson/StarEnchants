package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of the SLOT ORB (§H), loaded from {@code items/slot-orb.yml}. Each
 * use raises a piece's purchased enchant-slot count by {@link #orbAmount}, clamped to the {@link #hardCap}
 * universal total (base + added). {@code {AMOUNT}} in the name/lore renders {@link #orbAmount}. Immutable;
 * lives in the {@link ItemsConfig} snapshot {@code /se reload} swaps.
 *
 * @param orbMaterial   the upgrade-orb item material token (resolved cross-version at use)
 * @param orbName       the orb's display name ({@code &} colours; {@code {AMOUNT}} placeholder)
 * @param orbLore       the orb's lore lines ({@code {AMOUNT}} placeholder)
 * @param orbAmount     how many slots one orb grants (clamped &ge; 1)
 * @param hardCap       the universal maximum TOTAL slot count (base + added) any item may reach
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
