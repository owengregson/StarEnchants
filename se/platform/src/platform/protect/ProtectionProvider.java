package platform.protect;

import java.util.UUID;
import org.bukkit.Location;

/**
 * A first-party protection/region SPI (docs/architecture.md §2): "may {@code actor} have an ability act
 * at {@code where}?" One implementation bridges one land plugin (WorldGuard, GriefPrevention, …) or a
 * server's own rules; register through Bukkit's {@code ServicesManager}.
 *
 * <p><b>Threading (Folia).</b> Invoked on the firing region's thread with a {@code where} that region
 * owns. The actor is a {@link UUID}, NOT a live {@code Player}: on Folia it may be owned by a different
 * region than {@code where} (a projectile shooter across a boundary), so a provider needing the actor's
 * region-owned state must resolve it Folia-safely and never block on a cross-region read. Must not mutate.
 */
@FunctionalInterface
public interface ProtectionProvider {

    /**
     * {@code true} to allow (protection only ever <em>denies</em>). {@link ProtectionService} treats a
     * thrown exception as "allow" so a buggy bridge never blocks gameplay, but a provider should not throw.
     */
    boolean allows(UUID actor, Location where);

    /** A short id for logging/diagnostics (e.g. {@code "WorldGuard"}). */
    default String name() {
        return getClass().getSimpleName();
    }

    /** A provider that allows everything — the absent-plugin / empty default. */
    ProtectionProvider ALLOW = new ProtectionProvider() {
        @Override
        public boolean allows(UUID actor, Location where) {
            return true;
        }

        @Override
        public String name() {
            return "allow-all";
        }
    };
}
