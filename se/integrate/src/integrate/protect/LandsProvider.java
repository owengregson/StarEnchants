package integrate.protect;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.flags.type.Flags;
import me.angeschossen.lands.api.land.Area;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import platform.protect.ProtectionProvider;

/**
 * A {@link ProtectionProvider} bridging the Lands {@code BLOCK_PLACE} role-flag (docs/decisions/0027):
 * unclaimed land allows everything; inside a claim the actor's {@code BLOCK_PLACE} role-flag decides, resolved
 * by UUID (no offline special-case). Lands is Folia-aware, so the area read at {@code where}'s region thread is
 * correct on Folia too. Never throws — a hiccup degrades to allow.
 */
public final class LandsProvider implements ProtectionProvider {

    private final LandsIntegration lands;
    private final System.Logger log = System.getLogger("StarEnchants.Lands");
    private final AtomicBoolean warned = new AtomicBoolean();

    LandsProvider(LandsIntegration lands) {
        this.lands = lands;
    }

    /** Registrar factory; builds the API handle and returns the SPI type (lazy-load safe). */
    public static ProtectionProvider create(Plugin plugin) {
        return new LandsProvider(LandsIntegration.of(plugin));
    }

    @Override
    public String name() {
        return "Lands";
    }

    /** The gate (split out for unit testing): unclaimed land allows everything, else defer to the role-flag. */
    static boolean buildAllowed(Area area, UUID actor) {
        return area == null || area.hasRoleFlag(actor, Flags.BLOCK_PLACE);
    }

    @Override
    public boolean allows(UUID actor, Location where) {
        if (where == null || where.getWorld() == null || actor == null) {
            return true;
        }
        try {
            return buildAllowed(lands.getArea(where), actor);
        } catch (Throwable landsFailure) {
            if (warned.compareAndSet(false, true)) {
                log.log(System.Logger.Level.WARNING,
                        "Lands flag query failed; allowing this and future actions (logged once)", landsFailure);
            }
            return true;
        }
    }
}
