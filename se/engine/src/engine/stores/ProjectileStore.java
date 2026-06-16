package engine.stores;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic per-projectile runtime data: a projectile's {@link UUID} &rarr; the data it
 * carries plus an expiry tick (docs/architecture.md §5.4). Replaces the per-arrow
 * {@code AutoLockTask} timers — one scheduled task per live projectile — with a single
 * shared store, so a hundred arrows in flight cost one map, not a hundred timers.
 *
 * <p>Concurrent and UUID-keyed for Folia (a projectile may be spawned, ticked, and
 * resolved on different region threads), and TTL-evicting. The TTL matters here for a
 * specific reason: a projectile can vanish via chunk-unload (or simply despawn) without
 * ever firing the hit/land event that would {@link #remove} its entry. Without expiry
 * those orphaned entries would accumulate forever; with it, an elapsed entry is dropped
 * lazily on the next {@link #get}, so the map stays bounded without a sweeper.
 *
 * <p>This store is keyed by projectile, not by player, so it has no per-player
 * {@code clear} — entries leave either when the projectile resolves ({@link #remove}) or
 * when they elapse; everything is forgotten on disable ({@link #clearAll}).
 *
 * <p>Time is an explicit tick count supplied by the caller (the current server/region
 * tick), never wall-clock — so behaviour is deterministic and Folia-correct, and the
 * store is unit-testable without a server.
 *
 * @param <D> the data carried per projectile
 */
public final class ProjectileStore<D> {

    /** Sentinel expiry meaning "never elapses"; used when a non-positive TTL is given. */
    private static final long NO_EXPIRY = Long.MAX_VALUE;

    /** The data for one projectile together with the tick at which it elapses. */
    private static final class Entry<D> {
        final D data;
        final long expiryTick;

        Entry(D data, long expiryTick) {
            this.data = data;
            this.expiryTick = expiryTick;
        }
    }

    private final Map<UUID, Entry<D>> entryByProjectile = new ConcurrentHashMap<>();

    /**
     * Store {@code data} for {@code projectileId}, expiring at {@code nowTicks + ttlTicks}.
     * A non-positive {@code ttlTicks} stores the data with no expiry (held until it is
     * {@link #remove}d), represented internally as {@link Long#MAX_VALUE}. Overwrites any
     * existing entry for the projectile.
     */
    public void put(UUID projectileId, D data, long nowTicks, int ttlTicks) {
        long expiry = ttlTicks <= 0 ? NO_EXPIRY : nowTicks + ttlTicks;
        entryByProjectile.put(projectileId, new Entry<>(data, expiry));
    }

    /**
     * The data for {@code projectileId} if present and not yet elapsed at {@code nowTicks},
     * otherwise empty. An elapsed entry is evicted lazily here, so an orphaned projectile
     * (e.g. lost to chunk-unload) cannot leak past its TTL.
     */
    public Optional<D> get(UUID projectileId, long nowTicks) {
        Entry<D> entry = entryByProjectile.get(projectileId);
        if (entry == null) {
            return Optional.empty();
        }
        if (nowTicks >= entry.expiryTick) {
            entryByProjectile.remove(projectileId, entry); // lazy eviction of an elapsed entry
            return Optional.empty();
        }
        return Optional.ofNullable(entry.data);
    }

    /**
     * Remove and return the data for {@code projectileId} (call when the projectile hits or
     * lands), or empty if no entry is held. Returns the data even if it would have elapsed:
     * a resolving projectile is consuming its own entry, and removal is the correct outcome
     * either way.
     */
    public Optional<D> remove(UUID projectileId) {
        Entry<D> entry = entryByProjectile.remove(projectileId);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.data);
    }

    /** Forget every projectile's data (call on disable). */
    public void clearAll() {
        entryByProjectile.clear();
    }
}
