package engine.run;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * The injected world-access seam an {@link AbilityExecutor} hands to selectors that must reach beyond the
 * captured activation actors (docs/architecture.md §3.6): the area scan ({@code AOE}/{@code NEAREST}/
 * {@code AllPlayers}/{@code NearestPlayer}), the online-player roster ({@code PlayerFromName}), and the
 * actor's line-of-sight raytrace ({@code EntityInSight}). It is a dependency rather than baked in because
 * these are the parts of selector resolution that touch a {@code World}/server state and must run on the
 * correct region thread on Folia — so the Folia-correct implementation lives in the server-bound wiring
 * layer, while the executor and the selector kinds stay pure and unit-testable.
 *
 * <p>{@link #nearbyLiving} is the single abstract method (so a simple area scan can still be a lambda);
 * the roster and raytrace lookups are defaulted to "absent" so a scan-only or synthetic provider — and
 * every existing {@link #NONE} call site — needs no change.
 */
@FunctionalInterface
public interface AreaScan {

    /** The living entities within {@code radius} blocks of {@code center}; never {@code null}. */
    Iterable<LivingEntity> nearbyLiving(Location center, double radius);

    /**
     * The online player with this exact name, or {@code null} if none is online (the {@code PlayerFromName}
     * selector). Defaults to {@code null} for providers that do not supply a roster lookup.
     */
    default Player playerByName(String name) {
        return null;
    }

    /**
     * The living entity {@code from} is looking at within {@code maxDistance} blocks, or {@code null} if
     * nothing is in sight (the {@code EntityInSight} selector). Run on {@code from}'s own region thread.
     * Defaults to {@code null} for providers that do not supply a raytrace.
     */
    default LivingEntity entityInSight(Player from, double maxDistance) {
        return null;
    }

    /**
     * The location of the first solid block {@code from} is looking at within {@code maxDistance} blocks, or
     * {@code null} if none (the {@code Block}/{@code BlockInDistance} selectors). Run on {@code from}'s own
     * region thread. Defaults to {@code null} for providers that do not supply a block raytrace.
     */
    default Location targetBlock(Player from, double maxDistance) {
        return null;
    }

    /**
     * Up to {@code limit} locations of blocks contiguous with — and the same material as — the block at
     * {@code start} (flood-fill; the {@code Vein} selector). Run on {@code start}'s region thread. Defaults
     * to empty for providers that do not supply a world scan.
     */
    default List<Location> vein(Location start, int limit) {
        return List.of();
    }

    /**
     * The default for contexts with no world-access provider: area/roster/raytrace/block selectors resolve
     * to nothing. Used in unit/synthetic contexts; direct selectors (Self/Victim/Attacker) never call it, so
     * combat that uses only those is unaffected.
     */
    AreaScan NONE = (center, radius) -> List.of();
}
