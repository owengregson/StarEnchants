package compile.load;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The configurable likeness + economy of the soul-gem item (docs/v3-directives.md §D), loaded from the
 * top-level {@code items/soul-gem.yml}. Immutable; lives in the {@link ItemsConfig} snapshot the runtime
 * reads and the {@code /se reload} transaction swaps. The soul subsystem renders the gem and runs its
 * economy from this — the gem is a DISTINCT configured item (material/name/lore), souls deposit on
 * <em>any</em> kill ({@link #soulsPerKill}, optionally per-mob via {@link #soulsPerMob}), and the
 * messages narrate the soul-mode toggle + each spend.
 *
 * <p><strong>Soul-colour tiers are configurable</strong> (EE hard-coded them): {@link #colorTiers} maps a
 * minimum soul count to a {@code &}-colour, walked highest-first by {@link #colorFor}; below every tier the
 * {@link #emptyColor} applies. <strong>Sounds</strong> are a master on/off ({@link #sounds}) plus three
 * tokens played through the cross-version {@code playSound(Location, String, …)} overload (no resolver
 * needed). <strong>Particle</strong> token lists are carried for the on-activate / on-deactivate /
 * while-active cosmetics — their spawning is wired alongside the HELD/REPEATING equip lifecycle (the
 * active loop) and the {@code Particle} resolver, so the fields are parsed and held here now for a complete,
 * forward-compatible schema but are not yet consumed.
 *
 * @param material          the gem's material token (resolved cross-version at use, e.g. {@code EMERALD})
 * @param name              the gem's display name (legacy {@code &} colour codes allowed)
 * @param lore              the gem's lore lines ({@code {AMOUNT}} / {@code {SOUL-COLOR}} placeholders allowed)
 * @param soulsPerKill      souls deposited into a carried gem per kill (≥ 0); the default when no per-mob entry matches
 * @param soulsPerMob       optional per-{@code EntityType} deposit overrides (keys upper-cased), empty for none
 * @param colorTiers        soul-colour tiers (min count → {@code &}-colour), highest-first via {@link #colorFor}
 * @param emptyColor        the {@code &}-colour used below every tier (e.g. an empty gem)
 * @param sounds            master on/off for the gem's sounds
 * @param soundActivate     sound key played when soul mode enables (e.g. {@code entity.player.levelup})
 * @param soundDeactivate   sound key played when soul mode disables
 * @param soundCombine      sound key played when two gems combine (e.g. {@code block.anvil.use})
 * @param particlesActive   particle tokens shown while a gem is active (spawning deferred — see class note)
 * @param particlesActivate particle tokens shown when soul mode enables (deferred)
 * @param particlesDeactivate particle tokens shown when soul mode disables (deferred)
 * @param messageActivate   chat message when soul mode is enabled ({@code null}/blank = silent)
 * @param messageDeactivate chat message when soul mode is disabled
 * @param messageSoulUse    chat message when souls are spent ({@code {AMOUNT}} = remaining)
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
        List<String> particlesDeactivate,
        String messageActivate,
        String messageDeactivate,
        String messageSoulUse) {

    /**
     * One soul-colour tier: at {@code min} souls or above (and below the next-higher tier), the gem's
     * {@code {SOUL-COLOR}} placeholder renders as {@code color} (a {@code &}-code or hex). EE's tiers were
     * hard-coded; ours come from config.
     *
     * @param min   the minimum soul count this tier covers (≥ 0)
     * @param color the {@code &}-colour token for this tier
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
                List.of(),
                "&aSoul mode &lON&a.",
                "&7Soul mode &lOFF&7.",
                "&7Souls remaining: &a{AMOUNT}");
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
