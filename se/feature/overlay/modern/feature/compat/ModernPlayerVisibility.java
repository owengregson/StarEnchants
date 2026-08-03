package feature.compat;

import engine.sink.PlayerVisibility;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * The modern (1.17.1 → 26.1.x) {@link PlayerVisibility}: the plugin-scoped hide, so unloading StarEnchants
 * restores everyone it had hidden instead of stranding them invisible until a relog.
 */
public final class ModernPlayerVisibility implements PlayerVisibility {

    private final Plugin plugin;

    public ModernPlayerVisibility(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void setVisible(Player viewer, Player subject, boolean visible) {
        if (visible) {
            viewer.showPlayer(plugin, subject);
        } else {
            viewer.hidePlayer(plugin, subject);
        }
    }
}
