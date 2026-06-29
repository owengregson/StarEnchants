package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The SLOT ORB (§H), loaded from {@code items/slot-orb.yml}: each use raises a piece's slot count by
 * {@code orbAmount}, clamped to {@code hardCap} — the universal maximum TOTAL (base + added), not just added.
 * Each minted orb rolls a success chance in {@code [minSuccess, maxSuccess]} (default {@code 100/100} = always
 * succeeds); a failed apply consumes the orb without raising the count and never destroys the gear (only books
 * destroy). {@code {SUCCESS}}/{@code {FAILURE}}/{@code {MAX}} render in the name/lore alongside {@code {AMOUNT}}.
 */
public record SlotConfig(
        String orbMaterial,
        String orbName,
        List<String> orbLore,
        int orbAmount,
        int hardCap,
        int minSuccess,
        int maxSuccess,
        /** Item-group kinds the orb may be applied to (e.g. {@code ARMOR}, {@code WEAPON}); {@code ALL} = any item. */
        List<String> appliesTo) {

    public SlotConfig {
        Objects.requireNonNull(orbMaterial, "orbMaterial");
        Objects.requireNonNull(orbName, "orbName");
        orbLore = List.copyOf(orbLore);
        appliesTo = List.copyOf(appliesTo);
        orbAmount = Math.max(1, orbAmount);
        hardCap = Math.max(1, hardCap);
        int lo = Math.max(0, Math.min(100, minSuccess));
        int hi = Math.max(0, Math.min(100, maxSuccess));
        minSuccess = Math.min(lo, hi); // order the pair so [min, max] is always valid (matches the other configs)
        maxSuccess = Math.max(lo, hi);
    }

    public static SlotConfig defaults() {
        return new SlotConfig(
                "ENDER_EYE",
                "&5Slot Expander &7(+{AMOUNT})",
                List.of("&7Drag onto gear to add &f{AMOUNT}&7 enchant slots.",
                        "",
                        "&eApplies to: &r&f&n{KINDS}"),
                3,
                15,
                100,
                100,
                List.of("ARMOR", "WEAPON", "TOOL"));
    }
}
