package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player stacking charge counters: an interned ability id &rarr; a count and its
 * expiry tick (docs/architecture.md §5.4). Replaces the per-effect maps the originals
 * keep for Rage-style ramps (a Cosmic Enchants-style {@code Rage} stack, per-effect counters), each
 * of which leaked its own unbounded {@code HashMap}.
 *
 * <p>A charge is a count that climbs toward a {@code max} on each {@link #increment} and
 * is reset to zero once it sits idle for longer than its TTL — so a player who keeps
 * triggering an ability builds the stack, and one who stops loses it. The TTL window is
 * sliding: every {@link #increment} refreshes the expiry, so only a genuine pause lets a
 * charge lapse.
 *
 * <p>Concurrent and UUID-keyed for Folia (any region thread may touch a player's charges),
 * and TTL-evicting: an elapsed entry is dropped lazily on the next access to
 * {@link #count}/{@link #increment}, so the maps stay bounded without a sweeper. Cleared
 * per ability on {@link #reset}, per player on {@link #clear} (quit), and wholesale on
 * {@link #clearAll} (disable).
 *
 * <p>Time is an explicit tick count supplied by the caller (the current server/region
 * tick), never wall-clock — so behaviour is deterministic and Folia-correct, and the
 * store is unit-testable without a server.
 */
public final class ChargeStore {

    private final Map<UUID, Map<Integer, Charge>> chargesByPlayer = new ConcurrentHashMap<>();

    /**
     * One ability's charge: its current {@code count} and the tick at or after which the
     * count is considered lapsed. Immutable, so an update replaces it atomically rather
     * than mutating it in place — no torn reads from another region thread.
     */
    private record Charge(int count, long expiry) {
    }

    /**
     * Add one to {@code abilityId}'s charge for {@code player} and return the new count.
     *
     * <p>If the entry is absent or already elapsed ({@code nowTicks >= expiry}) the count
     * restarts from zero, so a lapsed stack does not resume mid-ramp. The new count is
     * {@code min(count + 1, max)} clamped to never drop below zero, and the expiry is
     * refreshed to {@code nowTicks + ttlTicks} — a sliding window that keeps an actively
     * triggered charge alive.
     *
     * <p>A {@code max} below one (or a non-positive {@code ttlTicks}) cannot hold a charge:
     * the count is clamped to zero and nothing is stored, so the call is a no-op that
     * reports {@code 0}.
     */
    public int increment(UUID player, int abilityId, int max, long nowTicks, int ttlTicks) {
        Map<Integer, Charge> charges =
                chargesByPlayer.computeIfAbsent(player, k -> new ConcurrentHashMap<>());
        Charge existing = charges.get(abilityId);
        int base = (existing == null || nowTicks >= existing.expiry()) ? 0 : existing.count();
        int next = base + 1;
        if (next > max) {
            next = max;
        }
        if (next < 0) {
            next = 0;
        }
        if (next == 0 || ttlTicks <= 0) {
            // No charge can be held: don't plant a doomed entry, and drop any prior one.
            charges.remove(abilityId);
            return 0;
        }
        charges.put(abilityId, new Charge(next, nowTicks + ttlTicks));
        return next;
    }

    /**
     * The current charge on {@code abilityId} for {@code player}, or {@code 0} if absent
     * or elapsed. An elapsed entry is evicted lazily here.
     */
    public int count(UUID player, int abilityId, long nowTicks) {
        Map<Integer, Charge> charges = chargesByPlayer.get(player);
        if (charges == null) {
            return 0;
        }
        Charge charge = charges.get(abilityId);
        if (charge == null) {
            return 0;
        }
        if (nowTicks >= charge.expiry()) {
            charges.remove(abilityId, charge); // lazy eviction of a lapsed charge
            return 0;
        }
        return charge.count();
    }

    /** Drop one ability's charge for {@code player} (e.g. when its ramp is consumed). */
    public void reset(UUID player, int abilityId) {
        Map<Integer, Charge> charges = chargesByPlayer.get(player);
        if (charges != null) {
            charges.remove(abilityId);
        }
    }

    /** Forget every charge for one player (call on quit). */
    public void clear(UUID player) {
        chargesByPlayer.remove(player);
    }

    /** Forget every charge for every player (call on disable). */
    public void clearAll() {
        chargesByPlayer.clear();
    }
}
