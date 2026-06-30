package engine.sink;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wall-clock combat tag: a player is "in combat" for a window after they deal or take combat damage. Written by
 * {@code CombatDispatch} on every hit, read by the {@code FLY_MODE} effect (supreme's Gifted Child — fly only
 * while NOT in combat). Static + era-agnostic (no store threading); self-evicting via lazy expiry.
 */
public final class CombatTag {

    private CombatTag() {
    }

    /** A player counts as in-combat for this long (ms) after their last hit. */
    private static final long WINDOW_MS = 15_000L;

    private static final Map<UUID, Long> LAST_HIT = new ConcurrentHashMap<>();

    /** Tag {@code player} as having just fought (wall clock). */
    public static void tag(UUID player) {
        if (player != null) {
            LAST_HIT.put(player, System.currentTimeMillis());
        }
    }

    /** Whether {@code player} is within the combat window right now; lazily evicts an elapsed tag. */
    public static boolean inCombat(UUID player) {
        Long last = LAST_HIT.get(player);
        if (last == null) {
            return false;
        }
        if (System.currentTimeMillis() - last >= WINDOW_MS) {
            LAST_HIT.remove(player, last);
            return false;
        }
        return true;
    }

    /** Forget one player's tag (quit). */
    public static void clear(UUID player) {
        LAST_HIT.remove(player);
    }

    /** Forget all tags (disable). */
    public static void clearAll() {
        LAST_HIT.clear();
    }
}
