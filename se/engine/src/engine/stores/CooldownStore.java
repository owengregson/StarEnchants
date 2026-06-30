package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player ability cooldowns: a packed scope key &rarr; expiry tick (docs/architecture.md §5.4).
 * Pipeline gate 6 calls {@link #ready}; gate 11 calls {@link #arm} (§3.3).
 *
 * <p>Concurrent, UUID-keyed (Folia: any region thread). TTL-evicting: an elapsed entry is dropped lazily
 * on the next {@link #ready}/{@link #remainingTicks}, so the maps stay bounded without a sweeper. Time is
 * an explicit caller-supplied tick, never wall-clock — deterministic, Folia-correct, server-free to test.
 */
public final class CooldownStore {

    private final Map<UUID, Map<Long, Long>> expiryByPlayer = new ConcurrentHashMap<>();

    /** Pack (kind, interned id) into one {@code long} so the three scopes (enchant/group/type) never collide. */
    public static long key(int scopeKind, int scopeId) {
        return ((long) scopeKind << 32) | (scopeId & 0xFFFF_FFFFL);
    }

    /**
     * As {@link #key(int, int)} but with a target bucket folded in above the scope kind, so the same ability cools
     * down independently per target kind (e.g. mob vs player — two separate cooldown routes). The scope kind only
     * ever takes a handful of values (enchant/group/type), so bit 40 is well clear of it. Bucket {@code 0} yields
     * the identical key to the two-arg form, so the non-bucketed (e.g. suppression) call sites are unaffected.
     */
    public static long key(int scopeKind, int scopeId, int targetBucket) {
        return key(scopeKind, scopeId) | ((long) targetBucket << 40);
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
