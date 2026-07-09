package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient registry of every spawned cosmetic falling block (by entity UUID) and its IMPACT cast — the owner
 * and the carried {@code %damage%} — so the landing listener can (a) cancel its placement (a FALLING_BLOCK is
 * always cosmetic and must never stick) and (b) fire the owner's {@code IMPACT}-triggered abilities on whatever
 * it lands on. The abstractable impact: any effects can hang off {@code IMPACT}. The owner may be {@code null}
 * (an environment-fired cosmetic with no player source): such a block is still tracked so its placement is
 * cancelled, it just fires no IMPACT.
 *
 * <p>Era-agnostic on purpose (no entity PDC, which 1.8 lacks; no plugin-bound metadata) and cleared on disable.
 * A whole grid shares the owner/damage but lands as several blocks — the "first block wins" dedup is left to a
 * short cooldown on the IMPACT ability (the gate), so this registry stays a plain map.
 */
public final class FallingBlockCasts {

    private FallingBlockCasts() {
    }

    /**
     * The IMPACT payload handed to the dispatch when a tracked block lands. {@code owner} may be null (no player
     * source → no IMPACT). {@code target} is the entity the grid was aimed at — the IMPACT lands only when THAT
     * entity is under the block, so a bystander who happens to be nearest can never eat the carried hit; also
     * nullable (an owner-less/targetless cosmetic).
     */
    public record Cast(UUID owner, UUID target, double damage) {
    }

    private static final Map<UUID, Cast> BY_ENTITY = new ConcurrentHashMap<>();

    /**
     * Track a freshly-spawned cosmetic falling block so its placement is cancelled on landing. {@code owner} may
     * be {@code null} (no player source) — the block is still tracked (and so never places); it just fires no
     * IMPACT. {@code target} records the entity the grid was aimed at so the landing hits only it.
     */
    public static void bind(UUID entity, UUID owner, UUID target, double damage) {
        if (entity != null) {
            BY_ENTITY.put(entity, new Cast(owner, target, damage));
        }
    }

    /** Forget a block removed without landing (its TTL elapsed) — keeps the map from leaking on a miss. */
    public static void forget(UUID entity) {
        BY_ENTITY.remove(entity);
    }

    /** Whether {@code entity} is a tracked impact block (the listener cancels its placement + claims the cast). */
    public static boolean isTracked(UUID entity) {
        return BY_ENTITY.containsKey(entity);
    }

    /** Claim and unbind the cast for a landed block (the IMPACT cooldown dedups a grid's many landings). */
    public static Cast onLand(UUID entity) {
        return BY_ENTITY.remove(entity);
    }

    /** Drop all tracking (call on disable). */
    public static void clearAll() {
        BY_ENTITY.clear();
    }
}
