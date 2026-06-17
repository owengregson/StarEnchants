package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of the SLOT items (docs/v3-directives.md §H) — the upgrade orb
 * (a configurable {@code +N} per use) and the slot gem ({@code +1}), loaded from the top-level
 * {@code items/slots.yml}. Both raise a piece's purchased enchant-slot count, clamped to one shared
 * {@link #hardCap universal maximum total} (base + added). Immutable; lives in the {@link ItemsConfig}
 * snapshot the runtime reads and {@code /se reload} swaps.
 *
 * <p>The orb and gem share the cap because it is a universal ceiling on an item's total slots, not a
 * per-item knob. {@code {AMOUNT}} in the orb's name/lore renders {@link #orbAmount}.
 *
 * @param orbMaterial   the upgrade-orb item material token (resolved cross-version at use)
 * @param orbName       the orb's display name ({@code &} colours; {@code {AMOUNT}} placeholder)
 * @param orbLore       the orb's lore lines ({@code {AMOUNT}} placeholder)
 * @param orbAmount     how many slots one orb grants (clamped &ge; 1)
 * @param gemMaterial   the slot-gem item material token
 * @param gemName       the gem's display name ({@code &} colours)
 * @param gemLore       the gem's lore lines
 * @param hardCap       the universal maximum TOTAL slot count (base + added) any item may reach
 * @param messageApply  chat on a successful slot increase ({@code {SLOTS}} renders the new total)
 * @param messageAtCap  chat when the gear is already at the hard cap
 */
public record SlotConfig(
        String orbMaterial,
        String orbName,
        List<String> orbLore,
        int orbAmount,
        String gemMaterial,
        String gemName,
        List<String> gemLore,
        int hardCap,
        String messageApply,
        String messageAtCap) {

    public SlotConfig {
        Objects.requireNonNull(orbMaterial, "orbMaterial");
        Objects.requireNonNull(orbName, "orbName");
        Objects.requireNonNull(gemMaterial, "gemMaterial");
        Objects.requireNonNull(gemName, "gemName");
        orbLore = List.copyOf(orbLore);
        gemLore = List.copyOf(gemLore);
        orbAmount = Math.max(1, orbAmount);
        hardCap = Math.max(1, hardCap);
    }

    /** The built-in slot config used when {@code items/slots.yml} is absent or omits fields. */
    public static SlotConfig defaults() {
        return new SlotConfig(
                "ENDER_EYE",
                "&5Slot Expander &7(+{AMOUNT})",
                List.of("&7Drag onto gear to add &f{AMOUNT}&7 enchant slots."),
                3,
                "AMETHYST_SHARD",
                "&dSlot Gem",
                List.of("&7Drag onto gear to add &f1&7 enchant slot."),
                15,
                "&aSlots increased — this item now has &f{SLOTS}&a total.",
                "&cThat item is already at the maximum slots.");
    }
}
