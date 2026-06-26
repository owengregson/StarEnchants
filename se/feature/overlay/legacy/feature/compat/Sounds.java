package feature.compat;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Legacy (1.8.9) sound playback by config token. 1.8 has only the {@code Sound} enum overload (no String
 * overload), so the token is resolved via {@code Sound.valueOf}; an unknown name is skipped. Same-FQN
 * counterpart to the {@code overlay/modern} impl (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, Location location, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isBlank()) {
            return;
        }
        try {
            player.playSound(location, Sound.valueOf(soundName.toUpperCase(Locale.ROOT)), volume, pitch);
        } catch (IllegalArgumentException unknownOn1_8) {
            // token is not a 1.8 Sound constant — skip (the §6 R3 legacy sound table completes this in Phase 3)
        }
    }
}
