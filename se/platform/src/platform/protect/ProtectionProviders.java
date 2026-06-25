package platform.protect;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Discovers {@link ProtectionProvider}s registered via the {@code ServicesManager} (docs/architecture.md §2).
 * Core bundles NO land-plugin reflection: a Folia-correct bridge belongs in a separately-verified add-on
 * registering through this same SPI.
 */
public final class ProtectionProviders {

    private ProtectionProviders() {
    }

    /** Registered providers in registration order; empty ⇒ allow-all. Providers must register before StarEnchants enables. */
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
