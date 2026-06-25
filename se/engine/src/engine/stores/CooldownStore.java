package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player ability cooldowns: a packed scope key &rarr; expiry tick
 * (docs/architecture.md §5.4). Replaces a Cosmic Enchants-style {@code Cooldown.cooldowns} and
 * {@code Cooldowns} table. Gate 6 of the pipeline calls {@link #ready}; gate 11 calls
 * {@link #arm} (§3.3).
 *
 * <p>Concurrent and UUID-keyed for Folia (any region thread may touch a player's
 * cooldowns), and TTL-evicting: an expired entry is dropped lazily on the next access
 * to {@link #ready}/{@link #remainingTicks}, so the maps stay bounded without a sweeper
 * (fixing the unbounded {@code HashMap} the originals leak). Cleared on quit
 * ({@link #clear}) and on disable ({@link #clearAll}).
 *
 * <p>Time is an explicit tick count supplied by the caller (the current server/region
 * tick), never wall-clock — so behaviour is deterministic and Folia-correct, and the
 * store is unit-testable without a server.
 */
public final class CooldownStore {

    private final Map<UUID, Map<Long, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    /**
     * Pack a cooldown scope (its kind and interned id) into the single {@code long}
     * key this store is indexed by, so the three Cosmic Enchants-style cooldown scopes (enchant / group /
     * type) never collide.
     */
    public static long key(int scopeKind, int scopeId) {
        return ((long) scopeKind << 32) | (scopeId & 0xFFFF_FFFFL);
    }

    /** @return {@code true} if {@code scope} has no active cooldown for {@code player} at {@code nowTicks}. */
    public boolean ready(UUID player, long scope, long nowTicks) {
        Map<Long, Long> scopes = expiryByPlayer.get(player);
        if (scopes == null) {
            return true;
        }
        Long expiry = scopes.get(scope);
        if (expiry == null) {
            return true;
        }
        if (nowTicks >= expiry) {
            scopes.remove(scope, expiry); // lazy eviction of an elapsed cooldown
            return true;
        }
        return false;
    }

    /**
     * Start a cooldown of {@code durationTicks} for {@code scope}, expiring at
     * {@code nowTicks + durationTicks}. A non-positive duration is a no-op (no
     * cooldown). Overwrites any existing cooldown for the scope.
     */
    public void arm(UUID player, long scope, long nowTicks, int durationTicks) {
        if (durationTicks <= 0) {
            return;
        }
        expiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(scope, nowTicks + durationTicks);
    }

    /** Ticks remaining on {@code scope} for {@code player}, or {@code 0} if ready. */
    public long remainingTicks(UUID player, long scope, long nowTicks) {
        Map<Long, Long> scopes = expiryByPlayer.get(player);
        if (scopes == null) {
            return 0L;
        }
        Long expiry = scopes.get(scope);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - nowTicks;
        if (remaining <= 0) {
            scopes.remove(scope, expiry);
            return 0L;
        }
        return remaining;
    }

    /** Forget every cooldown for one player (call on quit). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
    }

    /** Forget every cooldown for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
    }
}
