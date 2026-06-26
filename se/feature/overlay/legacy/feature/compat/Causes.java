package feature.compat;

import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Legacy (1.8.9) teleport-cause predicates — same-FQN counterpart to the {@code overlay/modern} impl. 1.8
 * has no {@code TeleportCause.CHORUS_FRUIT} (chorus fruit is 1.9), so it can never be the cause
 * (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Causes {

    private Causes() {
    }

    public static boolean isChorusFruit(TeleportCause cause) {
        return false; // no chorus fruit on 1.8
    }
}
