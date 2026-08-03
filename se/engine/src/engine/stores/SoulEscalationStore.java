package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-ability escalation counters for an escalating {@code soul-cost} (gate 10): each successful
 * charge steps the counter up one rung of the price ladder, and one rung is shed per elapsed decay period.
 * Decay is applied lazily on read against a caller-supplied monotonic tick — no scheduler of its own, and
 * {@code engine.stores} is architecturally barred from {@code platform.sched} anyway.
 *
 * <p>{@link PlayerScoped}, not {@link RetainedStore}: a relog resets the price to the authored base, which is
 * the direction that can only ever undercharge.
 */
public final class SoulEscalationStore implements PlayerScoped {

    private record Rungs(int steps, long lastCharge) {
    }

    private final Map<UUID, Map<Long, Rungs>> byPlayer = new ConcurrentHashMap<>();

    /**
     * The price of the next charge: {@code base * growth^steps}, clamped to {@code cap} when capped.
     * The single source of the ladder — the pipeline and the docs both quote this formula.
     */
    public static int escalatedCost(int baseCost, double growth, int cap, int steps) {
        if (baseCost <= 0) {
            return baseCost;
        }
        long ceiling = cap > 0 ? cap : Integer.MAX_VALUE;
        // A NaN/infinite/non-positive growth would price the ability at zero — i.e. free; degrade to static.
        long raw = steps <= 0 || !Double.isFinite(growth) || growth <= 0.0
                ? baseCost
                : Math.round((double) baseCost * Math.pow(growth, steps)); // saturates at Long.MAX, never wraps
        return (int) Math.max(0L, Math.min(raw, ceiling));
    }

    /** {@code player}'s decayed step count for {@code scopeKey}; {@code decayPeriodTicks <= 0} never decays. */
    public int steps(UUID player, long scopeKey, long nowTicks, int decayPeriodTicks) {
        Map<Long, Rungs> scopes = byPlayer.get(player);
        if (scopes == null) {
            return 0;
        }
        Rungs rungs = scopes.get(scopeKey);
        if (rungs == null) {
            return 0;
        }
        int decayed = decay(rungs, nowTicks, decayPeriodTicks);
        if (decayed <= 0) {
            scopes.remove(scopeKey, rungs); // fully decayed: evict lazily so the map stays bounded
            return 0;
        }
        return decayed;
    }

    /** Register one successful charge — the counter advances from its DECAYED value, and the clock restarts. */
    public void step(UUID player, long scopeKey, long nowTicks, int decayPeriodTicks) {
        byPlayer.computeIfAbsent(player, id -> new ConcurrentHashMap<>()).compute(scopeKey,
                (key, prev) -> new Rungs(prev == null ? 1 : decay(prev, nowTicks, decayPeriodTicks) + 1, nowTicks));
    }

    /** Drop {@code player}'s counters (on quit — the price falls back to the authored base). */
    public void clear(UUID player) {
        byPlayer.remove(player);
    }

    /** Drop every counter (on disable). */
    public void clearAll() {
        byPlayer.clear();
    }

    private static int decay(Rungs rungs, long nowTicks, int decayPeriodTicks) {
        if (decayPeriodTicks <= 0) {
            return rungs.steps();
        }
        long elapsed = nowTicks - rungs.lastCharge();
        if (elapsed < decayPeriodTicks) {
            return rungs.steps(); // a partial period sheds nothing
        }
        long shed = elapsed / decayPeriodTicks;
        return (int) Math.max(0L, rungs.steps() - shed);
    }
}
