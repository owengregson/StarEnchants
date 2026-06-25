package integrate.protect;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.TownyPermission.ActionType;
import com.palmergames.bukkit.towny.utils.PlayerCacheUtil;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import platform.protect.ProtectionProvider;

/**
 * A {@link ProtectionProvider} bridging Towny's {@code BUILD} permission (docs/decisions/0027): outside a
 * Towny world everything is allowed; inside one Towny's per-player BUILD cache for the block at {@code where}
 * decides. An offline/unknown actor is allowed (no resolvable player ⇒ no deny — SPI's permissive stance).
 * Towny is Paper-only, so the region-owned block read at {@code where} is safe. Never throws — a hiccup
 * degrades to allow.
 */
public final class TownyProvider implements ProtectionProvider {

    private final System.Logger log = System.getLogger("StarEnchants.Towny");
    private final AtomicBoolean warned = new AtomicBoolean();

    /** Registrar factory; returns the SPI type so referencing it never eagerly loads this class. */
    public static ProtectionProvider create() {
        return new TownyProvider();
    }

    @Override
    public String name() {
        return "Towny";
    }

    /** The gate, split out for unit testing without Towny statics: a non-Towny world allows everything. */
    static boolean decide(boolean townyWorld, boolean cacheAllowsBuild) {
        return !townyWorld || cacheAllowsBuild;
    }

    @Override
    public boolean allows(UUID actor, Location where) {
        if (where == null || where.getWorld() == null) {
            return true;
        }
        Player player = actor == null ? null : Bukkit.getPlayer(actor);
        if (player == null) {
            return true; // offline/unknown actor → allow (SPI stance)
        }
        try {
            boolean townyWorld = TownyAPI.getInstance().isTownyWorld(where.getWorld());
            boolean cacheAllowsBuild = townyWorld
                    && PlayerCacheUtil.getCachePermission(player, where, where.getBlock().getType(), ActionType.BUILD);
            return decide(townyWorld, cacheAllowsBuild);
        } catch (Throwable townyFailure) {
            if (warned.compareAndSet(false, true)) {
                log.log(System.Logger.Level.WARNING,
                        "Towny permission query failed; allowing this and future actions (logged once)",
                        townyFailure);
            }
            return true;
        }
    }
}
