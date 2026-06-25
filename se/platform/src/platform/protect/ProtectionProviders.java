package platform.protect;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Server;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Discovers the {@link ProtectionProvider}s registered via Bukkit's {@code ServicesManager}
 * (docs/architecture.md §1, §2). Core bundles NO land-plugin reflection: a cross-version-and-Folia-correct
 * WorldGuard/GriefPrevention/… bridge must read actor state on the actor's own region thread (untestable
 * on this matrix), so it belongs in a separately-verified add-on registering through this same SPI.
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
