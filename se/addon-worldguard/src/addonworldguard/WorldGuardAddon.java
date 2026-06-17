package addonworldguard;

import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import platform.protect.ProtectionProvider;

/**
 * The WorldGuard add-on plugin (docs/decisions/0017): on enable it registers a {@link WorldGuardProvider}
 * through Bukkit's {@code ServicesManager} so StarEnchants discovers it at boot. The {@code plugin.yml}
 * declares {@code depend: [WorldGuard]} (so this only loads when WorldGuard is present) and
 * {@code loadbefore: [StarEnchants]} (so this enables — and registers — before StarEnchants runs its
 * one-shot provider discovery, and so its classloader may resolve the StarEnchants-owned
 * {@link ProtectionProvider} interface).
 *
 * <p>The add-on holds no state and never touches the world; all the work is the one registration plus the
 * stateless provider it registers.
 */
public final class WorldGuardAddon extends JavaPlugin {

    @Override
    public void onEnable() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            // Should not happen (plugin.yml hard-depends on WorldGuard), but never NPE if the load order
            // is somehow subverted — just decline to register.
            getLogger().severe("WorldGuard is not installed; the StarEnchants WorldGuard bridge will not register.");
            return;
        }
        getServer().getServicesManager().register(
                ProtectionProvider.class, new WorldGuardProvider(), this, ServicePriority.Normal);
        getLogger().info("Registered the WorldGuard protection provider — StarEnchants will gate enchant"
                + " effects by WorldGuard's BUILD flag.");
    }
}
