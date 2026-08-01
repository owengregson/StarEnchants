package engine.sink;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tick-exact last-held-slot-change stamps used by Cosmic's anti-hot-swap soul gates. */
public final class HeldChanges {
    private HeldChanges() {
    }

    private static final ConcurrentHashMap<UUID, Long> LAST = new ConcurrentHashMap<>();

    public static void mark(UUID player, long nowTick) {
        if (player != null) {
            LAST.put(player, nowTick);
        }
    }

    /** Cosmic uses {@code currentTick - lastItemHeldChange > ticks}. No stamp is immediately settled. */
    public static boolean settled(UUID player, long nowTick, int ticks) {
        Long last = player == null ? null : LAST.get(player);
        return last == null || nowTick - last > ticks;
    }

    public static void clear(UUID player) {
        if (player != null) {
            LAST.remove(player);
        }
    }

    public static void clearAll() {
        LAST.clear();
    }
}
