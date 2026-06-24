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
 * A {@link ProtectionProvider} bridging the Lands {@code BLOCK_PLACE} role-flag (docs/decisions/0027): an
 * enchant effect may act at {@code where} iff Lands would let {@code actor} place a block there.
 *
 * <p>Bundled but SOFT: Lands' API is {@code compileOnly} and {@link integrate.Integrations} only loads this
 * class (and constructs the {@link LandsIntegration} handle) when Lands is present. Unclaimed land allows
 * everything; inside a claim the decision is the actor's {@code BLOCK_PLACE} role-flag, resolved by UUID —
 * which works for any actor, online or not, so there is no offline special-case here. Lands IS Folia-aware,
 * so reading the area at {@code where}'s region thread is correct on Folia too. Never throws — a Lands hiccup
 * degrades to allow.
 */
public final class LandsProvider implements ProtectionProvider {

    private final LandsIntegration lands;
    private final System.Logger log = System.getLogger("StarEnchants.Lands");
    private final AtomicBoolean warned = new AtomicBoolean();

    LandsProvider(LandsIntegration lands) {
        this.lands = lands;
    }

    /** Factory used by the registrar — builds the API handle and returns the SPI type (lazy-load safe). */
    public static ProtectionProvider create(Plugin plugin) {
        return new LandsProvider(LandsIntegration.of(plugin));
    }

    @Override
    public String name() {
        return "Lands";
    }

    /**
     * The gate, split out for unit testing without a live {@code LandsIntegration}: unclaimed land
     * ({@code area == null}) allows everything; a claim defers to the actor's BLOCK_PLACE role-flag.
     */
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
