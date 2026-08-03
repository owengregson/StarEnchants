package feature.compat;

import engine.sink.PlayerVisibility;
import org.bukkit.entity.Player;

/**
 * The 1.8.9 {@link PlayerVisibility}: only the plugin-less {@code hidePlayer}/{@code showPlayer} pair exists
 * here, so a disable mid-window leaves the restore to the viewer's next relog — the hidden set is
 * per-connection, and VIEWER_HIDE windows are seconds long.
 */
public final class LegacyPlayerVisibility implements PlayerVisibility {

    @Override
    @SuppressWarnings("deprecation") // hidePlayer/showPlayer(Player): the 1.8 single-arg API.
    public void setVisible(Player viewer, Player subject, boolean visible) {
        if (visible) {
            viewer.showPlayer(subject);
        } else {
            viewer.hidePlayer(subject);
        }
    }
}
