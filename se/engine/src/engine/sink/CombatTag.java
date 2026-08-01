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

    /** The ordinary combat window in milliseconds. */
    public static final long WINDOW_MS = 15_000L;

    private static final Map<UUID, Long> EXPIRES_AT = new ConcurrentHashMap<>();

    /** Reset {@code player}'s combat tag to the ordinary 15-second window. */
    public static void tag(UUID player) {
        tagFor(player, WINDOW_MS);
    }

    /**
     * Reset {@code player}'s combat tag to exactly {@code durationMs} from now. This is a reset rather than a
     * max/extension operation: mechanics such as Cosmic's Joker Mask deliberately shorten the wearer's fresh tag.
     */
    public static void tagFor(UUID player, long durationMs) {
        if (player != null) {
            EXPIRES_AT.put(player, System.currentTimeMillis() + Math.max(0L, durationMs));
        }
    }

    /** Whether {@code player} is within the combat window right now; lazily evicts an elapsed tag. */
    public static boolean inCombat(UUID player) {
        if (player == null) {
            return false;
        }
        Long expiresAt = EXPIRES_AT.get(player);
        if (expiresAt == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiresAt) {
            EXPIRES_AT.remove(player, expiresAt);
            return false;
        }
        return true;
    }

    /** Forget one player's tag (quit). */
    public static void clear(UUID player) {
        EXPIRES_AT.remove(player);
    }

    /** Forget all tags (disable). */
    public static void clearAll() {
        EXPIRES_AT.clear();
    }
}
