package feature.compat;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The era residue of {@link Sounds} (ADR-0044; docs/legacy-1.8.9-codeshare-design.md §4): playing a genuine
 * resource-key sound (a custom/resource-pack sound that matches no {@code Sound} constant) needs the
 * {@code playSound(Location, String, …)} String overload, which is 1.9.4+. The modern bindings supply the
 * String-overload method reference; the legacy bindings supply {@link #NONE} (1.8 skips key-form sounds).
 */
@FunctionalInterface
public interface KeySoundFallback {

    void play(Player player, Location at, String key, float volume, float pitch);

    /** No key-form playback (1.8.9, or any era with no String overload wired). */
    KeySoundFallback NONE = (player, at, key, volume, pitch) -> {
    };
}
