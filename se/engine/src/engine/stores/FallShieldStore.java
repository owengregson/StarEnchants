package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player one-shot fall cancels ({@code FALL_SHIELD}). A shield sits unspent until either the player takes
 * fall damage — which consumes it and cancels the hit — or its window elapses.
 *
 * <p>One shield per player, never a stack: a second arm REFRESHES the window rather than banking a second
 * free fall. Two procs displacing the same person owe one landing between them, and a bankable shield would
 * let a repeating proc hand out permanent fall immunity.
 *
 * <p>Quit-volatile. It is a benefit somebody else armed, and the drop it was arming for does not survive the
 * logout either — carrying it across a relog would only ever be free fall immunity.
 */
public final class FallShieldStore implements PlayerScoped {

    private final Map<UUID, Long> expiry = new ConcurrentHashMap<>();

    /** Arm (or refresh) {@code player}'s one-shot fall cancel for {@code windowTicks}. Non-positive is a no-op. */
    public void arm(UUID player, long nowTicks, int windowTicks) {
        if (player == null || windowTicks <= 0) {
            return;
        }
        expiry.put(player, nowTicks + windowTicks);
    }

    /** Whether {@code player} carries an unspent, unelapsed shield — a peek, for {@code /se why}-style reads. */
    public boolean armed(UUID player, long nowTicks) {
        Long at = expiry.get(player);
        if (at == null) {
            return false;
        }
        if (nowTicks >= at) {
            expiry.remove(player, at);
            return false;
        }
        return true;
    }

    /** Spend {@code player}'s shield if one is live: {@code true} exactly once per arm. */
    public boolean consume(UUID player, long nowTicks) {
        if (player == null) {
            return false;
        }
        Long at = expiry.remove(player);
        return at != null && nowTicks < at;
    }

    @Override
    public void clear(UUID player) {
        expiry.remove(player);
    }

    /** Forget every armed shield (call on disable). */
    public void clearAll() {
        expiry.clear();
    }
}
