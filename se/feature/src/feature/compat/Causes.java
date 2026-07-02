package feature.compat;

import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Teleport-cause predicates — shared across eras (ADR-0044; docs/legacy-1.8.9-codeshare-design.md §4).
 * {@code TeleportCause.CHORUS_FRUIT} is 1.9+ (absent on 1.8.9), so the membership test compares the enum NAME
 * rather than the 1.8-absent constant: on modern the name compare equals the identity compare, and on 1.8 the
 * cause can never be chorus fruit (chorus fruit is 1.9), so it is always false there.
 */
public final class Causes {

    private Causes() {
    }

    public static boolean isChorusFruit(TeleportCause cause) {
        return cause != null && cause.name().equals("CHORUS_FRUIT");
    }
}
