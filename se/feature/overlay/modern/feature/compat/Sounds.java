package feature.compat;

import compile.load.SoundCue;
import java.lang.reflect.Field;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Modern sound playback by config token. Resolves the token to a cross-version {@code Sound} CONSTANT by
 * reflection — the constant is a {@code public static final} field whether {@code Sound} is an enum (≤1.21.2)
 * or the registry-backed interface (1.21.3+, ADR cross-version) — so an enum-form token
 * ({@code BLOCK_BEACON_POWER_SELECT}) or a resource-key token ({@code entity.player.levelup}) both play, and a
 * token absent on this version is skipped. ONLY a genuine key-form token (a custom/resource-pack sound) that
 * matches no constant falls through to the String overload; an enum-form token NEVER does (the namespaced-key
 * parser rejects uppercase/underscores → {@code IdentifierException}). Same-FQN counterpart to the
 * {@code overlay/legacy} impl, docs/legacy-1.8.9-codeshare-design.md §4.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, Location location, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isBlank()) {
            return;
        }
        Sound sound = resolve(SoundCue.canonical(soundName));
        if (sound != null) {
            player.playSound(location, sound, volume, pitch);
        } else if (isKeyForm(soundName)) {
            // not a known constant but a valid resource-location path → a custom/pack sound; the String
            // overload (1.9.4+) can play it. An enum-form token (uppercase/underscores) is NOT key-form, so it
            // is skipped here rather than crashing the namespaced-key parser.
            player.playSound(location, soundName, volume, pitch);
        }
    }

    /** The {@code Sound} constant named {@code constant}, or {@code null} if it is absent on this version. */
    private static Sound resolve(String constant) {
        if (constant.isEmpty()) {
            return null;
        }
        try {
            Field field = Sound.class.getField(constant);
            return field.get(null) instanceof Sound s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException absent) {
            return null; // not a constant on this version → caller skips
        }
    }

    /** Whether {@code name} is a valid namespaced-key path ({@code [a-z0-9._-/]} + an optional {@code namespace:}). */
    private static boolean isKeyForm(String name) {
        return name.chars().allMatch(c -> (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '.' || c == '_' || c == '-' || c == '/' || c == ':');
    }
}
