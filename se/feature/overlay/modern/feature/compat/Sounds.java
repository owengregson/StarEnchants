package feature.compat;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Modern sound playback by config token. Uses the String {@code playSound} overload (1.9.4+), so any
 * version-named sound resolves server-side. Same-FQN counterpart to the {@code overlay/legacy} impl
 * (which maps the token to the 1.8 {@code Sound} enum), docs/legacy-1.8.9-codeshare-design.md §4.
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, Location location, String soundName, float volume, float pitch) {
        if (player == null || soundName == null || soundName.isBlank()) {
            return;
        }
        player.playSound(location, soundName, volume, pitch);
    }
}
