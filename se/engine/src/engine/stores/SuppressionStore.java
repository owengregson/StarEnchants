package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player timed suppression: an interned id &rarr; expiry tick
 * (docs/architecture.md §5.4). This is the home for the {@code DISABLE_*}-with-duration
 * effects — disabling a specific enchant / group / type id for a span of ticks. The
 * per-activation transient suppression set (silencing an id for the remainder of one
 * activation) is a SEPARATE arbiter and does not live here; this store only holds
 * suppressions that outlast the activation that created them.
 *
 * <p>Concurrent and UUID-keyed for Folia (any region thread may suppress or query a
 * player's ids), and TTL-evicting: an elapsed suppression is dropped lazily on the next
 * {@link #isSuppressed} call, so the maps stay bounded without a sweeper. Cleared on quit
 * ({@link #clear}) and on disable ({@link #clearAll}).
 *
 * <p>Time is an explicit tick count supplied by the caller (the current server/region
 * tick), never wall-clock — so behaviour is deterministic and Folia-correct, and the
 * store is unit-testable without a server.
 */
public final class SuppressionStore {

    private final Map<UUID, Map<Long, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    /**
     * Suppress packed scope key {@code id} for {@code durationTicks}, expiring at
     * {@code nowTicks + durationTicks}. The key is a {@link CooldownStore#key(int, int)}-packed
     * (scopeKind, scopeId) pair so the three DISABLE scopes (enchant/group/type) never collide —
     * shared with the gate's cooldown-scope namespace, so a {@code SUPPRESS} keys the same id the
     * suppressed abilities lower their scope to. A non-positive duration is a no-op. Re-suppressing
     * an already-suppressed key only ever EXTENDS it (the later expiry wins), so a fresh short
     * suppression never cuts an in-flight longer one short.
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
     * @return {@code true} if {@code id} has an active (non-elapsed) suppression for
     *     {@code player} at {@code nowTicks}. An elapsed suppression is evicted lazily and
     *     reported as not suppressed; the expiry tick itself counts as elapsed (the
     *     suppression covers {@code [start, expiry)}).
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
