package compile.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The soul-gem item (§D), loaded from {@code items/soul-gem.yml}; souls deposit on <em>any</em> kill
 * ({@link #soulsPerKill}, optionally per-mob via {@link #soulsPerMob}). Colour tiers are configurable here
 * (a Cosmic Enchants-style plugin hard-coded them).
 *
 * @param soulsPerKill the deposit when no per-mob entry matches (≥ 0)
 * @param soulsPerMob  per-{@code EntityType} overrides, keys upper-cased
 */
public record SoulGemConfig(
        String material,
        String name,
        List<String> lore,
        int soulsPerKill,
        Map<String, Integer> soulsPerMob,
        List<ColorTier> colorTiers,
        String emptyColor,
        boolean sounds,
        String soundActivate,
        String soundDeactivate,
        String soundCombine,
        List<String> particlesActive,
        List<String> particlesActivate,
        List<String> particlesDeactivate) {

    /** A {@code &}-code/hex {@code color} that applies at {@code min} souls or above (and below the next-higher tier). */
    public record ColorTier(int min, String color) {
        public ColorTier {
            min = Math.max(0, min);
            Objects.requireNonNull(color, "color");
        }
    }

    public SoulGemConfig {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(lore);
        soulsPerKill = Math.max(0, soulsPerKill);
        soulsPerMob = upperKeyed(soulsPerMob);
        colorTiers = sortedDescending(colorTiers);
        Objects.requireNonNull(emptyColor, "emptyColor");
        Objects.requireNonNull(soundActivate, "soundActivate");
        Objects.requireNonNull(soundDeactivate, "soundDeactivate");
        Objects.requireNonNull(soundCombine, "soundCombine");
        particlesActive = List.copyOf(particlesActive);
        particlesActivate = List.copyOf(particlesActivate);
        particlesDeactivate = List.copyOf(particlesDeactivate);
    }

    public int soulsFor(String entityName) {
        if (entityName != null) {
            Integer per = soulsPerMob.get(entityName.toUpperCase(Locale.ROOT));
            if (per != null) {
                return Math.max(0, per);
            }
        }
        return soulsPerKill;
    }

    public String colorFor(int souls) {
        for (ColorTier tier : colorTiers) { // constructor sorted these highest-min first
            if (souls >= tier.min()) {
                return tier.color();
            }
        }
        return emptyColor;
    }

    public static SoulGemConfig defaults() {
        return new SoulGemConfig(
                "EMERALD",
                "&aSoul Gem",
                List.of("&7Souls: {SOUL-COLOR}{AMOUNT}", "&7Right-click to toggle soul mode."),
                1,
                Map.of(),
                List.of(new ColorTier(1024, "&d"), new ColorTier(256, "&b"),
                        new ColorTier(64, "&a"), new ColorTier(1, "&f")),
                "&7",
                true,
                "entity.player.levelup",
                "ui.button.click",
                "block.anvil.use",
                List.of(),
                List.of(),
                List.of());
    }

    private static Map<String, Integer> upperKeyed(Map<String, Integer> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<>(raw.size());
        raw.forEach((k, v) -> {
            if (k != null && v != null) {
                out.put(k.toUpperCase(Locale.ROOT), Math.max(0, v));
            }
        });
        return Map.copyOf(out);
    }

    private static List<ColorTier> sortedDescending(List<ColorTier> tiers) {
        List<ColorTier> out = new ArrayList<>(tiers);
        out.sort((a, b) -> Integer.compare(b.min(), a.min())); // descending: colorFor returns the first matching tier
        return List.copyOf(out);
    }
}
