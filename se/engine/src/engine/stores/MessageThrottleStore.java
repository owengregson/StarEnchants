package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player rate limit for the gate-verdict notices (currently the out-of-souls line). A blocked activation
 * is not a rare event — one hit walks every worn ability and each soul-cost one aborts on the same empty pool
 * — so the notice needs a floor between emissions or it becomes the spam it is warning about.
 */
public final class MessageThrottleStore implements PlayerScoped {

    private final Map<UUID, Long> nextAllowed = new ConcurrentHashMap<>();

    /**
     * Whether {@code player} may be told now, arming the next {@code throttleTicks} if so. Test-and-arm in one
     * step (atomic per player), because two region threads can walk the same player's abilities in one tick.
     */
    public boolean tryEmit(UUID player, long nowTicks, int throttleTicks) {
        if (player == null) {
            return false;
        }
        boolean[] allowed = new boolean[1];
        nextAllowed.compute(player, (id, prev) -> {
            if (prev != null && nowTicks < prev) {
                return prev;
            }
            allowed[0] = true;
            return nowTicks + Math.max(1, throttleTicks); // a zero throttle still costs a tick, never "unlimited"
        });
        return allowed[0];
    }

    @Override
    public void clear(UUID player) {
        nextAllowed.remove(player);
    }

    /** Drop every throttle (on disable). */
    public void clearAll() {
        nextAllowed.clear();
    }
}
