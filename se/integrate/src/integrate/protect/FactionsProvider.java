package integrate.protect;

import com.massivecraft.factions.Board;
import com.massivecraft.factions.FLocation;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.perms.Relation;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Location;
import platform.protect.ProtectionProvider;

/**
 * A {@link ProtectionProvider} bridging FactionsUUID territory access (docs/decisions/0027): an enchant
 * effect may act at {@code where} iff the {@code actor} is at least on truce terms with the faction owning
 * that land.
 *
 * <p>Bundled but SOFT: the FactionsUUID API ({@code com.massivecraft.factions}) is {@code compileOnly} and
 * {@link integrate.Integrations} only loads this class when Factions is present.
 *
 * <p><b>Resolution.</b> Unclaimed wilderness and the system zones (safezone / warzone) allow everything;
 * inside a player faction's claim the actor is allowed only when its relation to that faction is at least
 * {@code TRUCE} (i.e. member, ally or truce — but not neutral or enemy). This mirrors how Factions itself
 * gates building in foreign territory. Never throws — a Factions hiccup degrades to allow.
 */
public final class FactionsProvider implements ProtectionProvider {

    private final System.Logger log = System.getLogger("StarEnchants.Factions");
    private final AtomicBoolean warned = new AtomicBoolean();

    /** Factory used by the registrar — returns the SPI type so referencing it never eagerly loads this class. */
    public static ProtectionProvider create() {
        return new FactionsProvider();
    }

    @Override
    public String name() {
        return "Factions";
    }

    /**
     * Whether {@code at} is a player-faction claim whose access must be checked — split out for unit testing
     * without the Factions singletons (the {@code Relation} enum's static init needs the running plugin, so
     * the relation comparison itself can only be verified live). Wilderness and the system zones (safezone /
     * warzone) are <em>not</em> gated; a normal player faction's claim is.
     */
    static boolean isClaimGated(Faction at) {
        return at != null && !at.isWilderness() && !at.isSafeZone() && !at.isWarZone();
    }

    @Override
    public boolean allows(UUID actor, Location where) {
        if (where == null || where.getWorld() == null || actor == null) {
            return true;
        }
        try {
            Faction at = Board.getInstance().getFactionAt(new FLocation(where));
            if (!isClaimGated(at)) {
                return true; // wilderness / safezone / warzone — nothing to gate
            }
            FPlayer fActor = FPlayers.getInstance().getById(actor.toString());
            if (fActor == null) {
                return true; // unknown actor — can't establish a relation → allow (SPI stance)
            }
            // Allowed only when at least on truce terms with the owning faction (member/ally/truce).
            return at.getRelationTo(fActor).isAtLeast(Relation.TRUCE);
        } catch (Throwable factionsFailure) {
            if (warned.compareAndSet(false, true)) {
                log.log(System.Logger.Level.WARNING,
                        "Factions territory query failed; allowing this and future actions (logged once)",
                        factionsFailure);
            }
            return true;
        }
    }
}
