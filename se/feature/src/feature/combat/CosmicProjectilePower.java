package feature.combat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cosmic's external {@code shootForce} metadata contract, represented without Bukkit metadata ownership.
 * A bow drawn below 75% suppresses offensive custom-enchant launch and impact procs; defensive effects still run.
 */
public final class CosmicProjectilePower {

    private static final long TTL_NANOS = 60_000_000_000L;
    private static final ConcurrentHashMap<UUID, Long> WEAK_UNTIL = new ConcurrentHashMap<>();

    private CosmicProjectilePower() {
    }

    public static void record(UUID projectile, float force) {
        if (projectile == null) {
            return;
        }
        long now = System.nanoTime();
        WEAK_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
        if (force < 0.75F) {
            WEAK_UNTIL.put(projectile, now + TTL_NANOS);
        } else {
            WEAK_UNTIL.remove(projectile);
        }
    }

    public static boolean weak(UUID projectile) {
        if (projectile == null) {
            return false;
        }
        Long until = WEAK_UNTIL.get(projectile);
        if (until == null) {
            return false;
        }
        if (until <= System.nanoTime()) {
            WEAK_UNTIL.remove(projectile, until);
            return false;
        }
        return true;
    }

    public static void forget(UUID projectile) {
        if (projectile != null) {
            WEAK_UNTIL.remove(projectile);
        }
    }

    static void clear() {
        WEAK_UNTIL.clear();
    }
}
