package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * The configurable likeness + mechanics of SUCCESS DUST (docs/v3-directives.md §I; ADR-0019), loaded from
 * the top-level {@code items/dust.yml}. Dust is its own physical item (no category, no tier): dragging it
 * onto an enchant book raises that book's stored success bonus by {@link #successBonus}, clamped so the
 * book's effective success can never exceed 100%. Pure economy metadata — it never compiles to an ability
 * and never touches the combat hot path. Immutable; lives in the {@link ItemsConfig} snapshot the runtime
 * reads and {@code /se reload} swaps (so the conferred bonus is re-read live, not baked onto the item).
 *
 * @param material     the dust item's material token (cross-version resolved at mint time)
 * @param name         the dust item's display name ({@code &} colour codes); {@code {BONUS}} → the bonus
 * @param lore         the dust item's lore lines; {@code {BONUS}} → the success bonus it confers
 * @param successBonus the success-chance bonus a single dust confers onto a book ({@code [0, 100]})
 * @param sound        the namespaced sound played on a successful combine (range-stable), or blank for none
 * @param particles    particle tokens spawned on a successful combine (alias-resolved), or empty for none
 */
public record DustConfig(String material, String name, List<String> lore, int successBonus,
                         String sound, List<String> particles) {

    public DustConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sound, "sound");
        lore = List.copyOf(lore);
        particles = List.copyOf(particles);
        successBonus = Math.max(0, Math.min(100, successBonus));
    }

    /** The built-in dust likeness used when {@code items/dust.yml} is absent or omits fields. */
    public static DustConfig defaults() {
        return new DustConfig(
                "GLOWSTONE_DUST",
                "&aSuccess Dust",
                List.of("&7Combine onto an enchant book to", "&7boost its success by &a+{BONUS}%&7."),
                15,
                "block.amethyst_block.chime",
                List.of("HAPPY_VILLAGER"));
    }
}
