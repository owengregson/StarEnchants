package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of SUCCESS DUST (docs/v3-directives.md §I; ADR-0019), loaded from
 * the top-level {@code items/dust.yml}. Dust is its own physical item (no category, no tier): dragging it
 * onto an enchant book raises that book's stored success bonus, clamped so the book's effective success can
 * never exceed 100%. A normal dust rolls a RANDOM bonus in {@code [minBonus, maxBonus]} when combined; a
 * dust minted for a FIXED percent (via {@code /se dust <percent>}) confers exactly that, bypassing the roll.
 * Pure economy metadata — it never compiles to an ability and never touches the combat hot path. Immutable;
 * lives in the {@link ItemsConfig} snapshot the runtime reads and {@code /se reload} swaps (the range is
 * re-read live, not baked onto a random dust).
 *
 * @param material  the dust item's material token (cross-version resolved at mint time)
 * @param name      the dust item's display name ({@code &} colours); {@code {BONUS}}→range-or-fixed, {@code {MIN}}/{@code {MAX}}
 * @param lore      the dust item's lore lines; same placeholders as {@code name}
 * @param minBonus  the low end of the random success-chance bonus a dust confers ({@code [0, 100]}, ≤ maxBonus)
 * @param maxBonus  the high end of the random success-chance bonus ({@code [0, 100]}, ≥ minBonus)
 * @param sound     the namespaced sound played on a successful combine (range-stable), or blank for none
 * @param particles particle tokens spawned on a successful combine (alias-resolved), or empty for none
 */
public record DustConfig(String material, String name, List<String> lore, int minBonus, int maxBonus,
                         String sound, List<String> particles) {

    public DustConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sound, "sound");
        lore = List.copyOf(lore);
        particles = List.copyOf(particles);
        int lo = Math.max(0, Math.min(100, minBonus));
        int hi = Math.max(0, Math.min(100, maxBonus));
        minBonus = Math.min(lo, hi); // order the pair so [min, max] is always a valid range
        maxBonus = Math.max(lo, hi);
    }

    /** The human-readable bonus label for the dust's name/lore: {@code "X"} when fixed, {@code "X–Y"} when a range. */
    public String bonusLabel() {
        return minBonus == maxBonus ? Integer.toString(minBonus) : minBonus + "–" + maxBonus;
    }

    /** The built-in dust likeness used when {@code items/dust.yml} is absent or omits fields. */
    public static DustConfig defaults() {
        return new DustConfig(
                "GLOWSTONE_DUST",
                "&aSuccess Dust",
                List.of("&7Combine onto an enchant book to", "&7boost its success by &a+{BONUS}%&7."),
                10,
                25,
                "block.amethyst_block.chime",
                List.of("HAPPY_VILLAGER"));
    }
}
