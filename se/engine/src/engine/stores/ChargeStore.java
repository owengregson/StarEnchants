package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player stacking charge counters for Rage-style ramps: interned ability id &rarr; count + expiry tick
 * (docs/architecture.md §5.4). The TTL window is sliding — every {@link #increment} refreshes the expiry,
 * so only a genuine pause lets a charge lapse and reset to zero.
 */
public final class ChargeStore {

    private final Map<UUID, Map<Integer, Charge>> chargesByPlayer = new ConcurrentHashMap<>();

    /** Immutable so an update replaces it atomically — no torn reads from another region thread. */
    private record Charge(int count, long expiry) {
    }

    /**
     * Add one to {@code abilityId}'s charge for {@code player} and return the new count.
     *
     * <p>A lapsed entry ({@code nowTicks >= expiry}) restarts from zero rather than resuming mid-ramp;
     * the new count is {@code min(count + 1, max)} and the expiry slides to {@code nowTicks + ttlTicks}.
     * A {@code max < 1} or non-positive {@code ttlTicks} holds nothing — no-op returning {@code 0}.
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
            // No charge can be held: drop any prior entry rather than plant a doomed one.
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
