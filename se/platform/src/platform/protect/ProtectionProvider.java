package platform.protect;

import java.util.UUID;
import org.bukkit.Location;

/**
 * A first-party protection/region SPI (docs/architecture.md §1, §2): "may the player identified by
 * {@code actor} have an ability act at {@code where}?" One implementation bridges one external
 * land/region plugin (WorldGuard, GriefPrevention, Towny, Lands, Factions, BentoBox, …) or a server's
 * own rules. The engine's gate 2 ({@code ActivationPipeline.Guard}) consults the composed
 * {@link ProtectionService}; register a provider through Bukkit's {@code ServicesManager}.
 *
 * <p>This replaces the brittle bundled bridges of the legacy plugins with one narrow contract — the
 * only question StarEnchants ever asks a protection plugin is whether an enchant effect may fire at a
 * location for a player.
 *
 * <p><b>Threading (Folia).</b> A provider is invoked on the firing event's region thread, with a
 * {@code where} that region owns — so reading the region/claim at {@code where} is safe. The actor is
 * given as a {@link UUID} (identity, thread-safe), NOT a live {@code Player}: on Folia the actor may be
 * owned by a <em>different</em> region than {@code where} (e.g. a projectile shooter hitting across a
 * region boundary), so a provider that needs the actor's region-owned state (a region-bypass session,
 * inventory, …) must resolve it Folia-safely itself and must never block on a cross-region read. A
 * provider must not mutate the world.
 */
@FunctionalInterface
public interface ProtectionProvider {

    /**
     * Whether the player {@code actor} may have an ability act at {@code where}. Return {@code true} to
     * allow (the default stance — protection only ever <em>denies</em>). Must not throw for an ordinary
     * query; {@link ProtectionService} defensively treats a thrown exception as "allow" so a buggy
     * bridge never blocks gameplay, but a well-behaved provider returns a boolean.
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
