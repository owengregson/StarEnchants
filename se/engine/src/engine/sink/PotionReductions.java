package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Temporary per-entity potion amplifier reductions. Unlike {@link LockedPotions}, a reduction lets the potion
 * remain active at a lower level. The largest live reduction wins and expired entries self-evict.
 */
public final class PotionReductions {

    private PotionReductions() {
    }

    private record Reduction(int steps, long expiresAtMs) {
    }

    private static final Map<UUID, Map<String, Reduction>> REDUCTIONS = new ConcurrentHashMap<>();

    public static void reduce(UUID entity, String potionName, int steps, long durationMs) {
        if (entity == null || potionName == null || steps <= 0 || durationMs <= 0L) {
            return;
        }
        long expiry = System.currentTimeMillis() + durationMs;
        REDUCTIONS.computeIfAbsent(entity, ignored -> new ConcurrentHashMap<>())
                .merge(potionName, new Reduction(steps, expiry), (current, next) ->
                        current.expiresAtMs() >= next.expiresAtMs() ? current : next);
    }

    /** Returns the adjusted Bukkit amplifier, or {@code -1} when the potion is fully suppressed. */
    public static int adjust(UUID entity, String potionName, int amplifier) {
        if (entity == null || potionName == null) {
            return amplifier;
        }
        Map<String, Reduction> byType = REDUCTIONS.get(entity);
        if (byType == null) {
            return amplifier;
        }
        Reduction reduction = byType.get(potionName);
        if (reduction == null) {
            return amplifier;
        }
        if (System.currentTimeMillis() >= reduction.expiresAtMs()) {
            byType.remove(potionName, reduction);
            if (byType.isEmpty()) {
                REDUCTIONS.remove(entity, byType);
            }
            return amplifier;
        }
        return amplifier - reduction.steps();
    }

    public static void clear(UUID entity) {
        REDUCTIONS.remove(entity);
    }

    public static void clearAll() {
        REDUCTIONS.clear();
    }
}
