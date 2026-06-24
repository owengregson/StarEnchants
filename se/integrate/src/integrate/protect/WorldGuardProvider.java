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
 * A {@link ProtectionProvider} bridging WorldGuard's {@code BUILD} flag (docs/decisions/0027): an enchant
 * effect may act at {@code where} iff WorldGuard would let {@code actor} build there. This is the one
 * question the engine's gate 2 asks — see {@link ProtectionProvider}.
 *
 * <p>Bundled in the core jar but SOFT: WorldGuard's API is {@code compileOnly} (never shaded), and the
 * {@link integrate.Integrations} registrar only instantiates this class when WorldGuard is actually present,
 * so the class — and its references to WorldGuard types — never load on a server without it. Compiling
 * against the real WorldGuard API (rather than reflection) means a renamed/removed method is a compile
 * error here, not a silent fail-open in production.
 *
 * <p><b>Resolution.</b> For an online {@code actor} the check is per-player — a region member (or a player
 * with region-bypass) is allowed, a non-member in a build-protected region is denied — matching what
 * WorldGuard would allow if that player placed a block. An offline/unknown actor (no live {@code Player} to
 * wrap) is <b>allowed</b>: with no resolvable player there is no way to establish region membership, hence
 * no deny, and the SPI's stance is that protection only ever denies.
 *
 * <p><b>Threading.</b> Invoked on {@code where}'s region thread (see {@link ProtectionProvider}); the region
 * read at {@code where} is safe. Resolving the actor reads player state that on Folia could be owned by a
 * different region — acceptable only because WorldGuard is not Folia-aware and does not run on Folia
 * (the registrar gates on its presence). The method never throws — a WorldGuard hiccup degrades to allow
 * (logged once).
 */
public final class WorldGuardProvider implements ProtectionProvider {

    private final System.Logger log = System.getLogger("StarEnchants.WorldGuard");
    private final AtomicBoolean warned = new AtomicBoolean();

    /** Factory used by the registrar — returns the SPI type so referencing it never eagerly loads this class. */
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
            return true; // nothing locatable to protect
        }
        try {
            Player player = actor == null ? null : Bukkit.getPlayer(actor);
            WorldGuardPlugin wgPlugin = WorldGuardPlugin.inst();
            if (player == null || wgPlugin == null) {
                // Can't resolve the actor to a live player (offline/unknown), or WorldGuard isn't fully
                // initialised → can't establish region membership, so can't establish a DENY. Per the SPI's
                // permissive stance we ALLOW. Passing a null subject to WorldGuard instead would treat the
                // actor as a NON-member and wrongly deny build in every normal member-owned region.
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

    /**
     * The pure decision for a resolved online actor: does WorldGuard's {@code BUILD} flag allow the wrapped
     * {@code subject} at {@code at}? Extracted so the flag wiring is unit-testable against a mocked
     * {@link RegionQuery} without standing up the WorldGuard singletons.
     */
    static boolean buildAllowed(RegionQuery query, com.sk89q.worldedit.util.Location at, LocalPlayer subject) {
        return query.testState(at, subject, Flags.BUILD);
    }
}
