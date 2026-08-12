package feature.compat;

import compile.load.SoundCue;
import java.lang.reflect.Field;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import platform.resolve.LegacyFallbacks;
import schema.spec.HandleCategory;

/**
 * Sound playback by config token — shared across eras (ADR-0044; docs/legacy-1.8.9-codeshare-design.md §4). The
 * token is resolved to a cross-version {@code Sound} CONSTANT by reflection — the constant is a
 * {@code public static final} field whether {@code Sound} is an enum (≤1.21.2, and 1.8.9) or the registry-backed
 * interface (1.21.3+) — so an enum-form token ({@code BLOCK_BEACON_POWER_SELECT}) plays via the enum overload of
 * {@code playSound} on every era, and a token absent under every known spelling here is skipped. ONLY a
 * key-form token (a custom/resource-pack sound) that matches no constant falls through to the
 * {@link KeySoundFallback}, the one era-specific bit: the modern bindings pass the String-overload method
 * reference (1.9.4+), the legacy bindings
 * pass {@link KeySoundFallback#NONE}. (The 1.8 lane thus widens from {@code Sound.valueOf} to constant-field
 * lookup — equivalent for an enum.)
 */
public final class Sounds {

    /** Renames + this platform's lossy degradations — the same table {@code RenameResolvers} builds. */
    private static final Map<String, String> TABLE = Aliases.mergedWith(
            HandleCategory.SOUND, LegacyFallbacks.forCategory(HandleCategory.SOUND));

    /** No key-form fallback (enum-form sounds still resolve) — the era-neutral default. */
    public static final Sounds NONE = new Sounds(KeySoundFallback.NONE);

    private final KeySoundFallback keyFallback;

    public Sounds(KeySoundFallback keyFallback) {
        this.keyFallback = keyFallback == null ? KeySoundFallback.NONE : keyFallback;
    }

    public void play(Player player, Location location, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isBlank()) {
            return;
        }
        Sound sound = resolve(SoundCue.canonical(soundName));
        if (sound != null) {
            player.playSound(location, sound, volume, pitch);
        } else if (isKeyForm(soundName)) {
            // not a known constant but a valid resource-location path → a custom/pack sound; the String overload
            // (1.9.4+) can play it via the era fallback. An enum-form token (uppercase/underscores) is NOT
            // key-form, so it is skipped here rather than crashing the namespaced-key parser.
            keyFallback.play(player, location, soundName, volume, pitch);
        }
    }

    /**
     * Play the first sound token that exists on this server. This is for the few hard-coded gameplay cues whose
     * Bukkit constant was renamed across the supported range (for example modern
     * {@code ENTITY_GENERIC_SPLASH} versus 1.8's {@code SPLASH}); exactly one matching constant is emitted.
     */
    public void playFirst(Player player, Location location, float volume, float pitch, String... soundNames) {
        if (player == null || soundNames == null) {
            return;
        }
        for (String soundName : soundNames) {
            if (soundName == null || soundName.isBlank()) {
                continue;
            }
            Sound sound = resolve(SoundCue.canonical(soundName));
            if (sound != null) {
                player.playSound(location, sound, volume, pitch);
                return;
            }
        }
    }

    /**
     * The {@code Sound} constant a config token names on THIS server, or {@code null} if neither it nor any
     * spelling of it exists here. Config cues go through the same bidirectional {@link Aliases} table the
     * compiled content DSL resolves against, so one authored token ({@code BLOCK_GLASS_BREAK}) serves the
     * modern lane directly and the 1.8.9 lane through its floor spelling ({@code GLASS}) — the alternative
     * being a cue that is silently swallowed on one era with no diagnostic.
     */
    private static Sound resolve(String constant) {
        if (constant.isEmpty()) {
            return null;
        }
        return HandleResolver.resolve(constant, TABLE, Sounds::exists)
                .map(Sounds::constant)
                .orElse(null);
    }

    private static boolean exists(String constant) {
        return constant(constant) != null;
    }

    private static Sound constant(String constant) {
        try {
            Field field = Sound.class.getField(constant);
            return field.get(null) instanceof Sound s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null; // not a constant on this version
        }
    }

    /** Whether {@code name} is a valid namespaced-key path ({@code [a-z0-9._-/]} + an optional {@code namespace:}). */
    private static boolean isKeyForm(String name) {
        return name.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '-' || c == '/' || c == ':');
    }
}
