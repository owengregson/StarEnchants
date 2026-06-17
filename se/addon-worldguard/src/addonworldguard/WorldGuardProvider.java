package addonworldguard;

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
 * A {@link ProtectionProvider} bridging WorldGuard's {@code BUILD} flag (docs/decisions/0017): an
 * enchant effect may act at {@code where} iff WorldGuard would let {@code actor} build there. This is
 * the one question the engine's gate 2 asks — see {@link ProtectionProvider}.
 *
 * <p>Lives in a SEPARATE plugin (never the core jar): per docs/architecture.md §1 StarEnchants bundles
 * no land-plugin reflection; a region plugin registers a provider through the {@code ServicesManager}.
 * This add-on compiles against the real WorldGuard API rather than the brittle reflection the legacy
 * plugins used, so a missing/renamed method is a compile error here, not a silent fail-open in production.
 *
 * <p><b>Resolution.</b> For an online {@code actor} the check is per-player — a region member (or a
 * player with region-bypass) is allowed, a non-member in a build-protected region is denied — matching
 * what WorldGuard itself would allow if that player tried to place a block. An offline/unknown actor
 * (no live {@code Player} to wrap) is <b>allowed</b>: with no resolvable player there is no way to
 * establish region membership, hence no way to establish a deny, and the SPI's stance is that protection
 * only ever denies (allow is the default). Passing a {@code null} subject to WorldGuard instead would be
 * read as a non-member and would wrongly deny build in every normal member-owned region.
 *
 * <p><b>Threading.</b> Invoked on {@code where}'s region thread (see {@link ProtectionProvider}); the
 * region read at {@code where} is region-owned and safe. Resolving the <em>actor</em>, however
 * ({@code Bukkit.getPlayer} → {@code wrapPlayer} → {@code hasBypass}), reads player state that on Folia
 * could be owned by a different region — an unsafe cross-region read. That is acceptable here ONLY because
 * WorldGuard is not Folia-aware and does not run on Folia, and this add-on hard-depends on WorldGuard
 * ({@code plugin.yml}), so it never loads on Folia in the first place; it is Paper-only by construction.
 * The method never throws — a WorldGuard hiccup degrades to allow (logged once) rather than blocking all
 * enchant activity, honouring the SPI's "must not throw" contract.
 */
public final class WorldGuardProvider implements ProtectionProvider {

    private final System.Logger log = System.getLogger("StarEnchants.WorldGuard");
    private final AtomicBoolean warned = new AtomicBoolean();

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
                // permissive stance (protection only ever denies; allow is the default) we ALLOW. Passing a
                // null subject to WorldGuard instead would treat the actor as a NON-member and wrongly deny
                // build in every normal member-owned region (blocking e.g. a delayed/projectile effect whose
                // owner just logged off).
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
            // Fail open — the engine's ProtectionService also treats a throw as allow, but the SPI asks a
            // well-behaved provider not to throw, so we swallow and log once rather than spamming.
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
