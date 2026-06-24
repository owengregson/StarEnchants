package engine.selector;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import schema.spec.Args;

/**
 * The read-only context a {@link SelectorKind} resolves against (docs/architecture.md
 * §3.5, §7). It exposes the activation's actors, its relevant location, and the
 * selector's already-typed arguments, plus a single area-scan helper so a selector
 * never reaches into a {@code World} directly — keeping the kinds uniform and
 * unit-testable with a mock context.
 *
 * <p>Like {@link engine.effect.EffectCtx}, everything reachable here is firing-thread
 * safe: a selector reads the actor (always on the firing thread) and the activation's
 * captured victim/attacker/location — it never reads a live cross-region entity (§3.4).
 */
public interface SelectorCtx {

    /** The player whose ability fired. */
    Player actor();

    /** The combat victim, or {@code null} for non-combat activations. */
    LivingEntity victim();

    /** The attacker that damaged the activator, or {@code null} if not an incoming hit. */
    LivingEntity attacker();

    /** The relevant location (e.g. an AoE centre or the activator's location), or {@code null}. */
    Location location();

    // ── Typed selector arguments (pre-validated; read by name, no parsing) ──

    Args args();

    double dbl(String name);

    int integer(String name);

    /**
     * The living entities within {@code radius} blocks of {@code center}, on that
     * location's own region thread. The engine supplies this so area selectors
     * ({@code AOE}, {@code NEAREST}) don't touch a {@code World} themselves; it never
     * returns {@code null} (an empty result when nothing is near).
     */
    Iterable<LivingEntity> nearbyLiving(Location center, double radius);

    /**
     * The online player with this exact name, or {@code null} if none is online (the {@code PlayerFromName}
     * selector). Defaults to {@code null} for a context with no roster lookup (unit/synthetic).
     */
    default Player playerByName(String name) {
        return null;
    }

    /**
     * The living entity the activator is looking at within {@code maxDistance} blocks, or {@code null}
     * (the {@code EntityInSight} selector). Defaults to {@code null} for a context with no raytrace.
     */
    default LivingEntity entityInSight(double maxDistance) {
        return null;
    }

    // ── Block/location world reads (§A block selectors; run on the actor's own region thread) ──

    /**
     * The location of the first solid block the activator is looking at within {@code maxDistance} blocks,
     * or {@code null} if none ({@code Block}/{@code BlockInDistance} selectors). Default {@code null} for a
     * context with no raytrace (unit/synthetic).
     */
    default Location targetBlock(double maxDistance) {
        return null;
    }

    /**
     * The locations of up to {@code limit} contiguous blocks of the same material as the block at
     * {@code start} (flood-fill — the {@code Vein} selector). Default empty for a context with no world.
     */
    default List<Location> vein(Location start, int limit) {
        return List.of();
    }
}
