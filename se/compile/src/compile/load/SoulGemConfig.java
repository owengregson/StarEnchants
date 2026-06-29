package compile.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The soul-gem item (§D), loaded from {@code items/soul-gem.yml}; souls deposit on <em>any</em> kill
 * ({@link #soulsPerKill}, optionally per-mob via {@link #soulsPerMob}). Colour tiers are configurable here
 * (a Cosmic Enchants-style plugin hard-coded them). The {@link Sounds} and {@link Particles} sub-records use
 * our unified bracket form (the same {@code { sound: … }} / {@code { particle: … }} shape the effect DSL uses)
 * so an operator reads one convention everywhere.
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
        Sounds sounds,
        Particles particles) {

    /** A {@code &}-code/hex {@code color} that applies at {@code min} souls or above (and below the next-higher tier). */
    public record ColorTier(int min, String color) {
        public ColorTier {
            min = Math.max(0, min);
            Objects.requireNonNull(color, "color");
        }
    }

    /**
     * Per-action sound cues, each a list played together — toggling soul mode on/off, spending souls, combining
     * gems, and splitting a gem. Empty list = silent for that action.
     */
    public record Sounds(List<SoundCue> toggleOn, List<SoundCue> toggleOff, List<SoundCue> use,
                         List<SoundCue> combine, List<SoundCue> split) {
        public Sounds {
            toggleOn = List.copyOf(toggleOn);
            toggleOff = List.copyOf(toggleOff);
            use = List.copyOf(use);
            combine = List.copyOf(combine);
            split = List.copyOf(split);
        }

        public static Sounds none() {
            return new Sounds(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * Per-state particle specs: {@code enable}/{@code disable} spawn at the toggle, {@code idle} is the
     * while-active aura spawned each tick at every player in soul mode, {@code use} spawns when souls are spent.
     * An empty spec spawns nothing.
     */
    public record Particles(ParticleSpec enable, ParticleSpec disable, ParticleSpec idle, ParticleSpec use) {
        public Particles {
            enable = enable == null ? ParticleSpec.none() : enable;
            disable = disable == null ? ParticleSpec.none() : disable;
            idle = idle == null ? ParticleSpec.none() : idle;
            use = use == null ? ParticleSpec.none() : use;
        }

        public static Particles none() {
            return new Particles(ParticleSpec.none(), ParticleSpec.none(), ParticleSpec.none(), ParticleSpec.none());
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
        Objects.requireNonNull(sounds, "sounds");
        Objects.requireNonNull(particles, "particles");
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
                Sounds.none(),
                Particles.none());
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
