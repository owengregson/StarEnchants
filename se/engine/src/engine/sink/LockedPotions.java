package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-(entity, potion-type) potion LOCKS: while a lock is live the type is continuously denied, so a passive
 * driver (or any other source) that keeps re-asserting the effect cannot make it stick — druid's 5s Speed lock,
 * fantasy's Speed-while-webbed, grim's overhealth window. Written by the {@code POTION_LOCK} effect, consulted by
 * the modern {@code PotionLockListener} which CANCELS the {@code EntityPotionEffectEvent} re-application (genuine
 * prevention, not just a same-tick re-strip — the re-strip always lost one tick to the driver, so the effect
 * flashed in and out). Static + era-agnostic (no store threading); wall-clock expiry, self-evicting.
 *
 * <p>Keyed by the version-stable {@link org.bukkit.potion.PotionEffectType#getName()} string so the writer (which
 * holds an int id resolved to a type) and the reader (which holds the event's type) can never disagree on the key
 * across the whole 1.8.9 → 26.x range. Below 1.13 there is no {@code EntityPotionEffectEvent}; the Sink's per-tick
 * re-strip is the sole enforcer there, and this registry is simply never read.
 */
public final class LockedPotions {

    private LockedPotions() {
    }

    private static final Map<UUID, Map<String, Long>> LOCKS = new ConcurrentHashMap<>();

    /** Lock {@code potionName} on {@code entity} for {@code durationMs} — deny every (re)application until it lapses. */
    public static void lock(UUID entity, String potionName, long durationMs) {
        if (entity == null || potionName == null || durationMs <= 0) {
            return;
        }
        LOCKS.computeIfAbsent(entity, k -> new ConcurrentHashMap<>())
                .put(potionName, System.currentTimeMillis() + durationMs);
    }

    /** Whether {@code potionName} is currently denied on {@code entity} (false once the window lapses; self-evicts). */
    public static boolean isLocked(UUID entity, String potionName) {
        if (entity == null || potionName == null) {
            return false;
        }
        Map<String, Long> byType = LOCKS.get(entity);
        if (byType == null) {
            return false;
        }
        Long expiry = byType.get(potionName);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            byType.remove(potionName, expiry);
            if (byType.isEmpty()) {
                LOCKS.remove(entity, byType);
            }
            return false;
        }
        return true;
    }

    /** Forget one entity's locks (quit). */
    public static void clear(UUID entity) {
        LOCKS.remove(entity);
    }

    /** Forget all locks (disable). */
    public static void clearAll() {
        LOCKS.clear();
    }
}
