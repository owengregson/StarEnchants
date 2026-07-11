package compile.load;

import java.util.List;
import java.util.Objects;

/**
 * PET FOOD (ADR-0052), loaded from {@code items/pet-food.yml} ({@code type: pet-food}): dragged onto a pet
 * head it raises the pet's level by {@code levels}, clamped to the universal {@code pets.max-level}. The
 * grant is BAKED onto each minted food (the dust pattern) so an already-minted food keeps its printed value
 * across config re-tunes; {@code {AMOUNT}} in the name/lore renders it at mint.
 */
public record PetFoodConfig(String material, String name, List<String> lore, int levels, boolean shiny,
                            /** Apply-feedback sound — the legacy bare string still parses (SoundCue.fromField). */
                            SoundCue sound, List<String> particles) {

    public PetFoodConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(sound, "sound");
        lore = List.copyOf(lore);
        particles = List.copyOf(particles);
        levels = Math.max(1, levels);
    }

    public static PetFoodConfig defaults() {
        return new PetFoodConfig(
                "GOLDEN_CARROT",
                "&e&lPet Food [&r&a&n+{AMOUNT}&r&e&l]",
                List.of("&eBoosts the level of your pet by {AMOUNT} levels, increasing the strength of their abilities.",
                        "&7Drag n' Drop on an item to apply."),
                10,
                true,
                new SoundCue("entity.player.levelup", 0.7f, 1.4f),
                List.of("HAPPY_VILLAGER"));
    }
}
