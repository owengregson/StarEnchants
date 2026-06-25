package engine.selector;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * Read-only context a {@link SelectorKind} resolves against (docs/architecture.md §3.5). Selectors reach
 * the world only through the helpers here, never a {@code World} directly. Everything is firing-thread
 * safe: the actor and the activation's captured victim/attacker/location, never a live cross-region entity.
 */
public interface SelectorCtx {

    Player actor();

    /** {@code null} for non-combat activations. */
    LivingEntity victim();

    /** {@code null} unless this is an incoming hit. */
    LivingEntity attacker();

    /** Relevant location (AoE centre, activator's location, …), or {@code null}. */
    Location location();

    // Typed, pre-validated selector arguments (read by name, no parsing).

    Args args();

    double dbl(String name);

    int integer(String name);

    /** Living entities within {@code radius} of {@code center}, on that location's region thread; never null. */
    Iterable<LivingEntity> nearbyLiving(Location center, double radius);

    /** Online player with this exact name, or {@code null}; null for a synthetic context with no roster. */
    default Player playerByName(String name) {
        return null;
    }

    /** Living entity the actor is looking at within {@code maxDistance}, or {@code null}; null without a raytrace. */
    default LivingEntity entityInSight(double maxDistance) {
        return null;
    }

    // Block/location world reads (§A block selectors; run on the actor's own region thread).

    /** First solid block the actor looks at within {@code maxDistance}, or {@code null}; null without a raytrace. */
    default Location targetBlock(double maxDistance) {
        return null;
    }

    /** Up to {@code limit} blocks flood-filled from the material at {@code start}; empty without a world. */
    default List<Location> vein(Location start, int limit) {
        return List.of();
    }
}
