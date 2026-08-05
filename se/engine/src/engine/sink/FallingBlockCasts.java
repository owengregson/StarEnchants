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
 * A whole grid shares the owner/damage but lands as several blocks — a "first block wins" dedup is left to a
 * short cooldown on the IMPACT ability (the gate). A rain FIELD needs more than that, so the per-victim re-hit
 * ceiling the profile authors lives here too ({@link #claimHit}): the cap is a property of the VICTIM, not of
 * one block or one wearer, and the landing is the only place that fact exists.
 */
public final class FallingBlockCasts {

    private FallingBlockCasts() {
    }

    /**
     * The IMPACT payload handed to the dispatch when a tracked block lands. {@code owner} may be null (no player
     * source → no IMPACT). {@code target} is the entity the grid was aimed at — the IMPACT lands only when THAT
     * entity is under the block, so a bystander who happens to be nearest can never eat the carried hit; also
     * nullable (an owner-less/targetless cosmetic). {@code rehitMax}/{@code rehitWindowTicks} ride the block
     * because the ceiling is authored on the field but only spendable at the landing; {@code rehitMax <= 0} is
     * uncapped (every pre-profile FALLING_BLOCK).
     */
    public record Cast(UUID owner, UUID target, double damage, int rehitMax, int rehitWindowTicks,
                       int sourceGroup, int gen) {
    }

    /** One victim's fixed re-hit bucket: the tick its window was anchored at, and how many hits it has taken. */
    private record Bucket(long anchor, int hits) {
    }

    private static final Map<UUID, Cast> BY_ENTITY = new ConcurrentHashMap<>();
    private static final Map<UUID, Bucket> BY_VICTIM = new ConcurrentHashMap<>();

    /**
     * Track a freshly-spawned cosmetic falling block so its placement is cancelled on landing. {@code owner} may
     * be {@code null} (no player source) — the block is still tracked (and so never places); it just fires no
     * IMPACT. {@code target} records the entity the grid was aimed at so the landing hits only it.
     */
    public static void bind(UUID entity, UUID owner, UUID target, double damage) {
        bind(entity, owner, target, damage, 0, 0, -1);
    }

    /**
     * {@link #bind(UUID, UUID, UUID, double)} carrying the field's per-victim re-hit ceiling to the landing, plus
     * the interned {@code group:} of the ability that armed it (ADR-0074) — {@code -1} for an ungrouped arm, which
     * keeps the historical "fire every IMPACT the owner wears" behaviour.
     */
    public static void bind(UUID entity, UUID owner, UUID target, double damage, int rehitMax,
                            int rehitWindowTicks, int sourceGroup) {
        if (entity != null) {
            // R-QC58: the group id is snapshot-relative, so the snapshot it was interned against rides with it.
            BY_ENTITY.put(entity, new Cast(owner, target, damage, rehitMax, rehitWindowTicks, sourceGroup,
                    CastGeneration.current()));
        }
    }

    /**
     * Claim one field hit against {@code victim}'s bucket — {@code true} when it lands, {@code false} when the
     * ceiling is already spent. At most {@code max} hits per {@code windowTicks}, the window ANCHORED at the
     * first claim and re-anchored only once it has fully elapsed: a fixed bucket, not a sliding window (measured
     * — four blocks landing together spend the whole allowance and the fifth waits out the remainder).
     *
     * <p>Keyed by the VICTIM alone, so every wearer raining on one player shares ONE ceiling: the cap exists to
     * bound a victim's incoming damage, and a crowd of wearers must not multiply it. {@code max <= 0} is
     * uncapped and books nothing at all.
     */
    public static boolean claimHit(UUID victim, int max, long windowTicks, long now) {
        if (victim == null || max <= 0) {
            return true;
        }
        boolean[] claimed = {false};
        // compute() runs the remap under the bin lock, so concurrent landings on separate region threads
        // cannot both read a not-yet-full bucket and each claim the last slot.
        BY_VICTIM.compute(victim, (id, current) -> {
            if (current == null || now - current.anchor() >= windowTicks) {
                claimed[0] = true;
                return new Bucket(now, 1);
            }
            if (current.hits() < max) {
                claimed[0] = true;
                return new Bucket(current.anchor(), current.hits() + 1);
            }
            return current;
        });
        return claimed[0];
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
        Cast cast = BY_ENTITY.remove(entity);
        // R-QC58: a reload re-interned the group table, so this row's int now names something else.
        // DROPPED rather than unscoped — an unscoped payload fires the owner's whole IMPACT roster.
        return cast == null || CastGeneration.stale(cast.gen()) ? null : cast;
    }

    /** Drop all tracking (call on disable). */
    public static void clearAll() {
        BY_ENTITY.clear();
        BY_VICTIM.clear();
    }
}
