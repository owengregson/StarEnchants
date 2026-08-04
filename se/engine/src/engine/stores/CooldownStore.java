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
 * <p>Every operation has a {@code victim}-taking overload for {@code cooldown-per-victim} abilities, whose
 * windows live in a dimension of their own rather than in extra key bits; {@code null} is the ordinary coarse
 * dimension every other caller uses.
 *
 * <p>Concurrent, UUID-keyed (Folia: any region thread). TTL-evicting: an elapsed entry is dropped lazily
 * on the next {@link #ready}/{@link #remainingTicks}, so the maps stay bounded without a sweeper. Time is
 * an explicit caller-supplied tick, never wall-clock — deterministic, Folia-correct, server-free to test.
 * A {@link RetainedStore}: its entries survive a relog (only elapsed ones are shed on quit) so a landed
 * cooldown cannot be skipped by a quick reconnect.
 */
public final class CooldownStore implements RetainedStore {

    private final Map<UUID, Map<Long, Long>> expiryByPlayer = new ConcurrentHashMap<>();
    // Per-victim windows: actor -> victim -> the SAME packed scope key -> expiry. A separate dimension, not
    // extra key bits — a UUID does not fit in the remaining bits of the packed long, and hashing one down into
    // them would make a collision a silent phantom cooldown on an unrelated target. Actor-first because every
    // sweep and clear is keyed by actor; victim-first would rescan every victim on each quit.
    private final Map<UUID, Map<UUID, Map<Long, Long>>> perVictimByPlayer = new ConcurrentHashMap<>();

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
        return ready(player, null, scope, nowTicks);
    }

    /** As {@link #ready(UUID, long, long)} in {@code victim}'s dimension ({@code null} = the coarse one). */
    public boolean ready(UUID player, UUID victim, long scope, long nowTicks) {
        return remainingTicks(player, victim, scope, nowTicks) == 0L;
    }

    /**
     * Start a cooldown of {@code durationTicks} for {@code scope}, expiring at
     * {@code nowTicks + durationTicks}. A non-positive duration is a no-op (no
     * cooldown). Overwrites any existing cooldown for the scope.
     */
    public void arm(UUID player, long scope, long nowTicks, int durationTicks) {
        arm(player, null, scope, nowTicks, durationTicks);
    }

    /** As {@link #arm(UUID, long, long, int)} in {@code victim}'s dimension ({@code null} = the coarse one). */
    public void arm(UUID player, UUID victim, long scope, long nowTicks, int durationTicks) {
        if (durationTicks <= 0) {
            return;
        }
        scopesOf(player, victim).put(scope, nowTicks + durationTicks);
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
        return tryAcquire(player, null, scope, nowTicks, durationTicks);
    }

    /**
     * As {@link #tryAcquire(UUID, long, long, int)} in {@code victim}'s dimension ({@code null} = the coarse
     * one), with the identical fused check-and-arm contract.
     */
    public long tryAcquire(UUID player, UUID victim, long scope, long nowTicks, int durationTicks) {
        // A check-only scope never writes, so it must not materialise the maps either — otherwise every
        // cooldown-0 ability would leave an empty shell behind per victim it ever touched.
        Map<Long, Long> scopes = durationTicks <= 0 ? existingScopesOf(player, victim) : scopesOf(player, victim);
        if (scopes == null) {
            return 0L; // nothing stored: ready
        }
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
        release(player, null, scope, reservedExpiry);
    }

    /** As {@link #release(UUID, long, long)} in {@code victim}'s dimension ({@code null} = the coarse one). */
    public void release(UUID player, UUID victim, long scope, long reservedExpiry) {
        Map<Long, Long> scopes = existingScopesOf(player, victim);
        if (scopes != null) {
            scopes.remove(scope, reservedExpiry);
        }
    }

    /** Ticks remaining on {@code scope} for {@code player}, or {@code 0} if ready. */
    public long remainingTicks(UUID player, long scope, long nowTicks) {
        return remainingTicks(player, null, scope, nowTicks);
    }

    /**
     * As {@link #remainingTicks(UUID, long, long)} in {@code victim}'s dimension ({@code null} = the coarse
     * one) — the read gate 6 and the {@code {TIME_FORMATTED}} read-back must agree on.
     */
    public long remainingTicks(UUID player, UUID victim, long scope, long nowTicks) {
        Map<Long, Long> scopes = existingScopesOf(player, victim);
        if (scopes == null) {
            return 0L;
        }
        Long expiry = scopes.get(scope);
        if (expiry == null) {
            return 0L;
        }
        long remaining = expiry - nowTicks;
        if (remaining <= 0) {
            scopes.remove(scope, expiry); // lazy eviction of an elapsed cooldown
            return 0L;
        }
        return remaining;
    }

    /** Forget every cooldown for one player (a full clear — NOT the relog-preserving quit sweep). */
    public void clear(UUID player) {
        expiryByPlayer.remove(player);
        perVictimByPlayer.remove(player);
    }

    /** Drop {@code player}'s elapsed cooldowns at {@code nowTicks}, keeping live ones; drop an emptied map. */
    @Override
    public void evictElapsed(UUID player, long nowTicks) {
        // computeIfPresent is atomic on the key, so a concurrent arm/tryAcquire can't be lost to the empty-map drop.
        expiryByPlayer.computeIfPresent(player, (id, scopes) -> {
            scopes.values().removeIf(expiry -> nowTicks >= expiry);
            return scopes.isEmpty() ? null : scopes;
        });
        // A victim map that empties is dropped with it: a mob dies and never returns, so nothing would ever read
        // (and lazily evict) its key again — one retained entry per mob the player ever hit.
        perVictimByPlayer.computeIfPresent(player, (id, byVictim) -> {
            byVictim.values().removeIf(scopes -> {
                scopes.values().removeIf(expiry -> nowTicks >= expiry);
                return scopes.isEmpty();
            });
            return byVictim.isEmpty() ? null : byVictim;
        });
    }

    /** Drop every player's elapsed cooldowns at {@code nowTicks} (the periodic offline-state sweep). */
    @Override
    public void evictElapsed(long nowTicks) {
        for (UUID player : expiryByPlayer.keySet()) {
            evictElapsed(player, nowTicks);
        }
        for (UUID player : perVictimByPlayer.keySet()) {
            evictElapsed(player, nowTicks);
        }
    }

    /** Forget every cooldown for every player (call on disable). */
    public void clearAll() {
        expiryByPlayer.clear();
        perVictimByPlayer.clear();
    }

    /** How many victims {@code player} still holds windows against — the bounded-growth pin. */
    int trackedVictims(UUID player) {
        Map<UUID, Map<Long, Long>> byVictim = perVictimByPlayer.get(player);
        return byVictim == null ? 0 : byVictim.size();
    }

    /** {@code player}'s scope map in {@code victim}'s dimension, creating it; {@code victim == null} = coarse. */
    private Map<Long, Long> scopesOf(UUID player, UUID victim) {
        if (victim == null) {
            return expiryByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        }
        return perVictimByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(victim, v -> new ConcurrentHashMap<>());
    }

    /** As {@link #scopesOf}, but reads only — {@code null} when nothing is stored (the common miss). */
    private Map<Long, Long> existingScopesOf(UUID player, UUID victim) {
        if (victim == null) {
            return expiryByPlayer.get(player);
        }
        Map<UUID, Map<Long, Long>> byVictim = perVictimByPlayer.get(player);
        return byVictim == null ? null : byVictim.get(victim);
    }
}
