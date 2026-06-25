package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The SLOT ORB (§H), loaded from {@code items/slot-orb.yml}: each use raises a piece's slot count by
 * {@code orbAmount}, clamped to {@code hardCap} — the universal maximum TOTAL (base + added), not just added.
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

    public static SlotConfig defaults() {
        return new SlotConfig(
                "ENDER_EYE",
                "&5Slot Expander &7(+{AMOUNT})",
                List.of("&7Drag onto gear to add &f{AMOUNT}&7 enchant slots."),
                3,
                15);
    }
}
