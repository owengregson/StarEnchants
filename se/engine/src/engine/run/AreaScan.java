package engine.run;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The injected world-access seam for selectors that reach beyond the captured activation actors
 * (docs/architecture.md §3.6): area scan, online-player roster, raytraces, vein flood-fill. Injected
 * rather than baked in because these touch {@code World}/server state and must run on the correct Folia
 * region thread — so the Folia-correct impl lives in the server-bound wiring layer while the executor and
 * selector kinds stay pure and unit-testable. The non-area lookups default to "absent" so scan-only and
 * {@link #NONE} providers need no change.
 */
@FunctionalInterface
public interface AreaScan {

    /** Living entities within {@code radius} blocks of {@code center}; never {@code null}. */
    Iterable<LivingEntity> nearbyLiving(Location center, double radius);

    /** The online player with this exact name, or {@code null} ({@code PlayerFromName}). */
    default Player playerByName(String name) {
        return null;
    }

    /**
     * The living entity {@code from} is looking at within {@code maxDistance} blocks, or {@code null}
     * ({@code EntityInSight}). Must run on {@code from}'s own region thread.
     */
    default LivingEntity entityInSight(Player from, double maxDistance) {
        return null;
    }

    /**
     * The first solid block {@code from} is looking at within {@code maxDistance} blocks, or {@code null}
     * ({@code Block}/{@code BlockInDistance}). Must run on {@code from}'s own region thread.
     */
    default Location targetBlock(Player from, double maxDistance) {
        return null;
    }

    /**
     * Up to {@code limit} blocks contiguous with — and the same material as — the block at {@code start}
     * (flood-fill; {@code Vein}). Must run on {@code start}'s region thread.
     */
    default List<Location> vein(Location start, int limit) {
        return List.of();
    }

    /**
     * No-op provider for synthetic/unit contexts: every world-access selector resolves to nothing. Direct
     * selectors (Self/Victim/Attacker) never call it, so combat using only those is unaffected.
     */
    AreaScan NONE = (center, radius) -> List.of();
}
