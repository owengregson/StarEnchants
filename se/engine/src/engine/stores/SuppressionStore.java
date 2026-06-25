package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player timed suppression: an interned id &rarr; expiry tick (docs/architecture.md §5.4). Home for
 * the {@code DISABLE_*}-with-duration effects (an enchant/group/type id silenced for a span of ticks). The
 * per-activation transient suppression set is a SEPARATE arbiter, not this; this store holds only
 * suppressions that outlast the activation that created them.
 */
public final class SuppressionStore {

    private final Map<UUID, Map<Long, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    /**
     * Suppress packed scope key {@code id} for {@code durationTicks}, expiring at {@code nowTicks +
     * durationTicks}. The key is {@link CooldownStore#key(int, int)}-packed and shares the gate's
     * cooldown-scope namespace, so a {@code SUPPRESS} keys the same id the suppressed abilities lower their
     * scope to. Non-positive duration is a no-op; re-suppressing only EXTENDS (later expiry wins).
     */
    public void suppress(UUID player, long id, long nowTicks, int durationTicks) {
        if (durationTicks <= 0) {
            return;
        }
        long expiry = nowTicks + durationTicks;
        expiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .merge(id, expiry, Math::max);
    }

    /**
     * @return {@code true} if {@code id} has an active suppression for {@code player} at {@code nowTicks}.
     *     An elapsed one is evicted lazily; the window is half-open {@code [start, expiry)}.
     */
    public boolean isSuppressed(UUID player, long id, long nowTicks) {
        Map<Long, Long> ids = expiryByPlayer.get(player);
        if (ids == null) {
            return false;
        }
        Long expiry = ids.get(id);
        if (expiry == null) {
            return false;
        }
        if (nowTicks >= expiry) {
            ids.remove(id, expiry); // lazy eviction of an elapsed suppression
            return false;
        }
        return true;
    }

    /** Forget every suppression for one player (call on quit). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
    }

    /** Forget every suppression for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
    }
}
