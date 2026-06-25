package integrate.protect;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import platform.protect.ProtectionProvider;

/**
 * A {@link ProtectionProvider} bridging WorldGuard's {@code BUILD} flag (docs/decisions/0027): for an online
 * actor the check is per-player (member or bypass allowed, non-member in a protected region denied); an
 * offline/unknown actor is allowed, since with no resolvable player there is no membership to establish and the
 * SPI only ever denies.
 *
 * <p>Resolving the actor reads player state that on Folia could be owned by another region — acceptable only
 * because WorldGuard is not Folia-aware and does not run on Folia. Never throws — a hiccup degrades to allow.
 */
public final class WorldGuardProvider implements ProtectionProvider {

    private final System.Logger log = System.getLogger("StarEnchants.WorldGuard");
    private final AtomicBoolean warned = new AtomicBoolean();

    /** Registrar factory; returns the SPI type so referencing it never eagerly loads this class. */
    public static ProtectionProvider create() {
        return new WorldGuardProvider();
    }

    @Override
    public String name() {
        return "WorldGuard";
    }

    @Override
    public boolean allows(UUID actor, Location where) {
        if (where == null || where.getWorld() == null) {
            return true;
        }
        try {
            Player player = actor == null ? null : Bukkit.getPlayer(actor);
            WorldGuardPlugin wgPlugin = WorldGuardPlugin.inst();
            if (player == null || wgPlugin == null) {
                // No membership to establish → allow (SPI stance); a null subject is treated as a NON-member
                // and would wrongly deny build in every member-owned region.
                return true;
            }
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location at = BukkitAdapter.adapt(where);
            LocalPlayer local = wgPlugin.wrapPlayer(player);
            if (WorldGuard.getInstance().getPlatform().getSessionManager()
                    .hasBypass(local, BukkitAdapter.adapt(where.getWorld()))) {
                return true; // a region-bypassing player (op / //bypass) is never gated
            }
            return buildAllowed(query, at, local);
        } catch (Throwable wgFailure) {
            if (warned.compareAndSet(false, true)) {
                log.log(System.Logger.Level.WARNING,
                        "WorldGuard protection query failed; allowing this and future actions (logged once)",
                        wgFailure);
            }
            return true;
        }
    }

    /** Does WorldGuard's {@code BUILD} flag allow {@code subject} at {@code at}? (split out for unit testing) */
    static boolean buildAllowed(RegionQuery query, com.sk89q.worldedit.util.Location at, LocalPlayer subject) {
        return query.testState(at, subject, Flags.BUILD);
    }
}
