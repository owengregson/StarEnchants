package integrate.protect;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.IslandPrivilege;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import platform.protect.ProtectionProvider;

/**
 * A {@link ProtectionProvider} bridging SuperiorSkyblock2 island privileges (docs/decisions/0027): outside any
 * island everything is allowed; on an island the actor's {@code BUILD} privilege decides. {@code BUILD} is
 * resolved by name once at construction — SuperiorSkyblock registers its default privileges on enable, before
 * StarEnchants boots. Never throws — a hiccup degrades to allow.
 */
public final class SuperiorSkyblockProvider implements ProtectionProvider {

    private final IslandPrivilege buildPrivilege;
    private final System.Logger log = System.getLogger("StarEnchants.SuperiorSkyblock");
    private final AtomicBoolean warned = new AtomicBoolean();

    private SuperiorSkyblockProvider(IslandPrivilege buildPrivilege) {
        this.buildPrivilege = buildPrivilege;
    }

    /** Registrar factory; resolves the BUILD privilege and returns the SPI type (lazy-load safe). */
    public static ProtectionProvider create() {
        return new SuperiorSkyblockProvider(IslandPrivilege.getByName("BUILD"));
    }

    @Override
    public String name() {
        return "SuperiorSkyblock";
    }

    /** The gate (split out for unit testing): off any island, or with no BUILD privilege, allows everything. */
    static boolean buildAllowed(Island island, SuperiorPlayer actor, IslandPrivilege buildPrivilege) {
        if (island == null || buildPrivilege == null) {
            return true;
        }
        return island.hasPermission(actor, buildPrivilege);
    }

    @Override
    public boolean allows(UUID actor, Location where) {
        if (where == null || where.getWorld() == null || actor == null) {
            return true;
        }
        try {
            Island island = SuperiorSkyblockAPI.getGrid().getIslandAt(where);
            if (island == null) {
                return true;
            }
            return buildAllowed(island, SuperiorSkyblockAPI.getPlayer(actor), buildPrivilege);
        } catch (Throwable ssbFailure) {
            if (warned.compareAndSet(false, true)) {
                log.log(System.Logger.Level.WARNING,
                        "SuperiorSkyblock privilege query failed; allowing this and future actions (logged once)",
                        ssbFailure);
            }
            return true;
        }
    }
}
