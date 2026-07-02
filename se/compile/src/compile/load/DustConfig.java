package compile.load;

import java.util.List;
import java.util.Objects;
import schema.spec.Ranges;

/**
 * SUCCESS DUST (§I; ADR-0019), loaded from {@code items/dust.yml}: combined onto a book it raises its stored
 * success bonus, clamped so the book's effective success never exceeds 100%. A normal dust rolls a random
 * bonus in {@code [minBonus, maxBonus]}; one minted via {@code /se dust <percent>} confers a fixed percent,
 * bypassing the roll. The range is re-read live, not baked onto a random dust.
 */
public record DustConfig(String material, String name, List<String> lore, int minBonus, int maxBonus,
                         /** Apply-feedback sound — the legacy bare string still parses (SoundCue.fromField). */
                         SoundCue sound, List<String> particles) {

    public DustConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sound, "sound");
        lore = List.copyOf(lore);
        particles = List.copyOf(particles);
        Ranges.IntRange bonus = Ranges.percentRange(minBonus, maxBonus);
        minBonus = bonus.min(); // order the pair so [min, max] is always a valid range
        maxBonus = bonus.max();
    }

    public String bonusLabel() {
        return minBonus == maxBonus ? Integer.toString(minBonus) : minBonus + "–" + maxBonus;
    }

    public static DustConfig defaults() {
        return new DustConfig(
                "GLOWSTONE_DUST",
                "&aSuccess Dust",
                List.of("&7Combine onto an enchant book to", "&7boost its success by &a+{BONUS}%&7."),
                10,
                25,
                new SoundCue("block.amethyst_block.chime", 1.0f, 1.0f),
                List.of("HAPPY_VILLAGER"));
    }
}
