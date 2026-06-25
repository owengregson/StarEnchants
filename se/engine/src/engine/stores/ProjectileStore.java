package engine.stores;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic per-projectile runtime data: a projectile's {@link UUID} &rarr; its data + expiry tick
 * (docs/architecture.md §5.4). One shared store for all projectiles, not a timer per arrow.
 *
 * <p>The TTL is load-bearing: a projectile can vanish via chunk-unload or despawn without ever firing the
 * hit/land event that would {@link #remove} its entry, so without expiry those orphans would leak. Keyed
 * by projectile, not player — hence no per-player {@code clear}; entries leave on {@link #remove}, on
 * expiry, or on disable.
 *
 * @param <D> the data carried per projectile
 */
public final class ProjectileStore<D> {

    /** Sentinel expiry meaning "never elapses"; used when a non-positive TTL is given. */
    private static final long NO_EXPIRY = Long.MAX_VALUE;

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
     * Store {@code data} for {@code projectileId}, expiring at {@code nowTicks + ttlTicks}. A non-positive
     * {@code ttlTicks} stores it with no expiry (held until {@link #remove}d). Overwrites any prior entry.
     */
    public void put(UUID projectileId, D data, long nowTicks, int ttlTicks) {
        long expiry = ttlTicks <= 0 ? NO_EXPIRY : nowTicks + ttlTicks;
        entryByProjectile.put(projectileId, new Entry<>(data, expiry));
    }

    /**
     * The data for {@code projectileId} if present and not yet elapsed, else empty. An elapsed entry is
     * evicted lazily, so an orphaned projectile (e.g. lost to chunk-unload) cannot leak past its TTL.
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
     * Remove and return the data for {@code projectileId} (call when it hits or lands), or empty if none.
     * Returns the data even if it would have elapsed — a resolving projectile consumes its own entry.
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
