package feature.compat;

import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Modern teleport-cause predicates. Same-FQN counterpart to the {@code overlay/legacy} impl;
 * {@code TeleportCause.CHORUS_FRUIT} is 1.9+ (absent on 1.8.9), so the membership check is a seam
 * (docs/legacy-1.8.9-codeshare-design.md §4).
 */
public final class Causes {

    private Causes() {
    }

    public static boolean isChorusFruit(TeleportCause cause) {
        return cause == TeleportCause.CHORUS_FRUIT;
    }
}
