package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player ability cooldowns: a packed scope key &rarr; expiry tick (docs/architecture.md §5.4). Pipeline
 * gate 6 reserves a scope via {@link #tryAcquire} (an atomic check-and-arm); a later gate failure releases it
 * via {@link #release} and ACTIVATED commits the reservation implicitly, so the pass-then-arm window is
 * zero-width (§3.3). {@link #ready}/{@link #arm} remain the plain read/write seam (tests, read-back).
 *
 * <p>Concurrent, UUID-keyed (Folia: any region thread). TTL-evicting: an elapsed entry is dropped lazily
 * on the next {@link #ready}/{@link #remainingTicks}, so the maps stay bounded without a sweeper. Time is
 * an explicit caller-supplied tick, never wall-clock — deterministic, Folia-correct, server-free to test.
 * A {@link RetainedStore}: its entries survive a relog (only elapsed ones are shed on quit) so a landed
 * cooldown cannot be skipped by a quick reconnect.
 */
public final class CooldownStore implements RetainedStore {

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

    /**
     * Atomically RESERVE {@code scope}: check-and-arm in one step so two same-tick hits evaluated on two Folia
     * region threads cannot both pass — exactly one wins the CAS, the loser reads the winner's live reservation.
     * Returns {@code 0} when acquired (or ready with nothing to write for a check-only scope), else the ticks
     * remaining on the live reservation. A {@code durationTicks <= 0} scope is CHECK-ONLY: it still respects a
     * shared scope another ability armed, but writes no reservation of its own. Roll back a reservation with
     * {@link #release}; the reserved expiry is {@code nowTicks + durationTicks}.
     */
    public long tryAcquire(UUID player, long scope, long nowTicks, int durationTicks) {
        Map<Long, Long> scopes = expiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        long reserved = nowTicks + durationTicks;
        while (true) {
            Long cur = scopes.get(scope);
            if (cur == null) {
                if (durationTicks <= 0) {
                    return 0L; // check-only: ready, nothing to arm
                }
                if (scopes.putIfAbsent(scope, reserved) == null) {
                    return 0L; // won the reservation
                }
                continue; // lost the race — re-read
            }
            if (nowTicks >= cur) {
                // elapsed: take it over (or, check-only, just evict) — CAS so a concurrent acquirer can't be lost
                if (durationTicks <= 0) {
                    if (scopes.remove(scope, cur)) {
                        return 0L;
                    }
                } else if (scopes.replace(scope, cur, reserved)) {
                    return 0L;
                }
                continue; // CAS lost — re-read
            }
            return cur - nowTicks; // a live reservation holds the scope
        }
    }

    /** Roll back a {@link #tryAcquire} reservation — value-matched, so it can only ever delete our own write. */
    public void release(UUID player, long scope, long reservedExpiry) {
        Map<Long, Long> scopes = expiryByPlayer.get(player);
        if (scopes != null) {
            scopes.remove(scope, reservedExpiry);
        }
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

    /** Forget every cooldown for one player (a full clear — NOT the relog-preserving quit sweep). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
    }

    /** Drop {@code player}'s elapsed cooldowns at {@code nowTicks}, keeping live ones; drop an emptied map. */
    @Override
    public void evictElapsed(UUID player, long nowTicks) {
        // computeIfPresent is atomic on the key, so a concurrent arm/tryAcquire can't be lost to the empty-map drop.
        expiryByPlayer.computeIfPresent(player, (id, scopes) -> {
            scopes.values().removeIf(expiry -> nowTicks >= expiry);
            return scopes.isEmpty() ? null : scopes;
        });
    }

    /** Drop every player's elapsed cooldowns at {@code nowTicks} (the periodic offline-state sweep). */
    @Override
    public void evictElapsed(long nowTicks) {
        for (UUID player : expiryByPlayer.keySet()) {
            evictElapsed(player, nowTicks);
        }
    }

    /** Forget every cooldown for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
    }
}
