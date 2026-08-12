package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player rate limit for the gate-verdict notices. A blocked activation is not a rare event — one hit walks
 * every worn ability and each aborts on the same reason — so a notice needs a floor between emissions or it
 * becomes the spam it is warning about. Each {@link Notice} family gets its OWN window: an empty soul pool and
 * a blocked proc are different news, and one must not silence the other.
 */
public final class MessageThrottleStore implements PlayerScoped {

    /** The notice families, each throttled independently per player. */
    public enum Notice {
        OUT_OF_SOULS,
        /** ADR-0076 part E's rebate line, coalescing every sibling ability a single hit rebates. */
        REBATE
    }

    private static final int CHANNELS = Notice.values().length;

    private final Map<UUID, long[]> nextAllowed = new ConcurrentHashMap<>();

    /**
     * Whether {@code player} may be told now on {@code notice}'s channel, arming the next {@code throttleTicks}
     * if so. Test-and-arm in one step (atomic per player), because two region threads can walk the same
     * player's abilities in one tick.
     */
    public boolean tryEmit(UUID player, Notice notice, long nowTicks, int throttleTicks) {
        if (player == null) {
            return false;
        }
        int channel = notice.ordinal();
        boolean[] allowed = new boolean[1];
        nextAllowed.compute(player, (id, prev) -> {
            long[] windows = prev == null ? new long[CHANNELS] : prev;
            if (windows[channel] > nowTicks) {
                return windows;
            }
            allowed[0] = true;
            windows[channel] = nowTicks + Math.max(1, throttleTicks); // a zero throttle still costs a tick
            return windows;
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
