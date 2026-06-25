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
 * A {@link ProtectionProvider} bridging FactionsUUID territory access (docs/decisions/0027): wilderness and
 * the system zones (safezone / warzone) allow everything; inside a player faction's claim the actor is allowed
 * only when its relation is at least {@code TRUCE} (member/ally/truce). Never throws — a hiccup degrades to
 * allow.
 */
public final class FactionsProvider implements ProtectionProvider {

    private final System.Logger log = System.getLogger("StarEnchants.Factions");
    private final AtomicBoolean warned = new AtomicBoolean();

    /** Registrar factory; returns the SPI type so referencing it never eagerly loads this class. */
    public static ProtectionProvider create() {
        return new FactionsProvider();
    }

    @Override
    public String name() {
        return "Factions";
    }

    /**
     * Whether {@code at} is a player-faction claim whose access must be checked (split out for unit testing).
     * The relation comparison itself can only be verified live — the {@code Relation} enum's static init needs
     * the running plugin.
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
                return true;
            }
            FPlayer fActor = FPlayers.getInstance().getById(actor.toString());
            if (fActor == null) {
                return true; // unknown actor — no relation to establish → allow (SPI stance)
            }
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
