package compile.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The configurable likeness + economy of the soul-gem item (§D), loaded from {@code items/soul-gem.yml}.
 * The gem is a DISTINCT configured item; souls deposit on <em>any</em> kill ({@link #soulsPerKill},
 * optionally per-mob via {@link #soulsPerMob}). Immutable; lives in the {@link ItemsConfig} snapshot the
 * {@code /se reload} transaction swaps.
 *
 * <p>Soul-colour tiers are configurable (a Cosmic Enchants-style plugin hard-coded them): {@link #colorTiers}
 * maps a min soul count to a {@code &}-colour, walked highest-first by {@link #colorFor}; below every tier
 * the {@link #emptyColor} applies. Sound tokens go through the cross-version
 * {@code playSound(Location, String, …)} overload (no resolver needed); particle tokens through the
 * alias-aware {@code ParticleFx} resolver.
 *
 * @param lore              lore lines ({@code {AMOUNT}} / {@code {SOUL-COLOR}} placeholders allowed)
 * @param soulsPerKill      souls deposited per kill (≥ 0); the default when no per-mob entry matches
 * @param soulsPerMob       optional per-{@code EntityType} deposit overrides (keys upper-cased), empty for none
 * @param colorTiers        soul-colour tiers (min count → {@code &}-colour), highest-first via {@link #colorFor}
 * @param emptyColor        the {@code &}-colour used below every tier (e.g. an empty gem)
 * @param particlesActive   particle tokens spawned each tick while a gem is active (the §D aura loop)
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

    /**
     * One soul-colour tier: at {@code min} souls or above (and below the next-higher tier), the gem's
     * {@code {SOUL-COLOR}} placeholder renders as {@code color} (a {@code &}-code or hex).
     *
     * @param min the minimum soul count this tier covers (≥ 0)
     */
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

    /** The souls to deposit for a kill of {@code entityName}: the per-mob override if present, else {@link #soulsPerKill}. */
    public int soulsFor(String entityName) {
        if (entityName != null) {
            Integer per = soulsPerMob.get(entityName.toUpperCase(Locale.ROOT));
            if (per != null) {
                return Math.max(0, per);
            }
        }
        return soulsPerKill;
    }

    /** The {@code &}-colour for a {@code souls} count: the highest tier whose {@code min} it meets, else {@link #emptyColor}. */
    public String colorFor(int souls) {
        for (ColorTier tier : colorTiers) { // sorted highest-min first
            if (souls >= tier.min()) {
                return tier.color();
            }
        }
        return emptyColor;
    }

    /** The built-in soul gem used when {@code items/soul-gem.yml} is absent or omits fields. */
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
        out.sort((a, b) -> Integer.compare(b.min(), a.min())); // highest min first
        return List.copyOf(out);
    }
}
