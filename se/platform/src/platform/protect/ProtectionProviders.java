package platform.protect;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers the bundled {@link ProtectionProvider} bridges available on this server (docs/architecture.md
 * §2). v1 bundles a best-effort WorldGuard bridge; other region plugins are served by the
 * {@link ProtectionProvider} SPI (a server or companion plugin registers its own). Discovery is
 * graceful — an absent or API-incompatible plugin contributes nothing rather than failing the boot.
 */
public final class ProtectionProviders {

    private ProtectionProviders() {
    }

    /**
     * The protection bridges present on this server, in query order. Empty when no supported land
     * plugin is installed — {@link ProtectionService} then allows everything.
     */
    public static List<ProtectionProvider> discover(System.Logger log) {
        List<ProtectionProvider> found = new ArrayList<>();
        WorldGuardProtection worldGuard = WorldGuardProtection.tryCreate(log);
        if (worldGuard != null) {
            log.log(System.Logger.Level.INFO, "protection: WorldGuard bridge active");
            found.add(worldGuard);
        }
        return found;
    }
}
