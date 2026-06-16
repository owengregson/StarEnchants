package platform.protect;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * A first-party protection/region SPI (docs/architecture.md §1, §2): "can {@code actor} have an
 * ability act at {@code where}?" One implementation bridges one external land/region plugin
 * (WorldGuard, GriefPrevention, Towny, Lands, Factions, …); a server may also register its own.
 * The engine's gate 2 ({@code ActivationPipeline.Guard}) consults the composed {@link ProtectionService}.
 *
 * <p>Replaces the brittle bundled bridges of the legacy plugins with one narrow contract: the only
 * question StarEnchants ever asks a protection plugin is whether an enchant effect may fire at a
 * location for a player. Implementations resolve their own regions/claims; they must not mutate the
 * world and must tolerate being called on a region thread (Folia) — the call always happens on the
 * thread that owns {@code where}, so reading that region is safe.
 */
@FunctionalInterface
public interface ProtectionProvider {

    /**
     * Whether {@code actor} may have an ability act at {@code where}. Return {@code true} to allow
     * (the default stance — protection only ever <em>denies</em>). Must not throw for an ordinary
     * query; {@link ProtectionService} defensively treats a thrown exception as "allow" so a buggy
     * bridge never blocks gameplay, but a well-behaved provider returns a boolean.
     */
    boolean allows(Player actor, Location where);

    /** A short id for logging/diagnostics (e.g. {@code "WorldGuard"}). */
    default String name() {
        return getClass().getSimpleName();
    }

    /** A provider that allows everything — the absent-plugin / empty default. */
    ProtectionProvider ALLOW = new ProtectionProvider() {
        @Override
        public boolean allows(Player actor, Location where) {
            return true;
        }

        @Override
        public String name() {
            return "allow-all";
        }
    };
}
