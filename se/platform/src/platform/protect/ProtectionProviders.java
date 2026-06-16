package platform.protect;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Discovers the {@link ProtectionProvider}s registered on this server (docs/architecture.md §1, §2).
 * Per the "first-party SPI, drop the brittle bundled bridges" direction (§1), StarEnchants bundles NO
 * land-plugin-specific reflection: a region plugin (or a server's own integration plugin) registers a
 * {@link ProtectionProvider} through Bukkit's {@code ServicesManager}, and this collects them. A
 * cross-version-and-Folia-correct WorldGuard/GriefPrevention/… bridge — which must read actor state on
 * the actor's own region thread, untestable on the matrix here — belongs in a separately-verified
 * add-on that registers through this same SPI, not in the core jar.
 */
public final class ProtectionProviders {

    private ProtectionProviders() {
    }

    /**
     * The protection providers registered via the server's {@code ServicesManager}, in registration
     * order. Empty when none are registered — {@link ProtectionService} then allows everything.
     * (Providers must register before StarEnchants enables; a companion plugin uses {@code loadbefore}
     * or {@code depend} on StarEnchants' load order accordingly.)
     */
    public static List<ProtectionProvider> discover(Server server, System.Logger log) {
        List<ProtectionProvider> found = new ArrayList<>();
        for (RegisteredServiceProvider<ProtectionProvider> reg
                : server.getServicesManager().getRegistrations(ProtectionProvider.class)) {
            ProtectionProvider provider = reg.getProvider();
            if (provider != null) {
                log.log(System.Logger.Level.INFO, "protection: provider '" + provider.name()
                        + "' registered by " + reg.getPlugin().getName());
                found.add(provider);
            }
        }
        return found;
    }
}
