package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player soul-cost exemption windows ({@code SOUL_COST_EXEMPT}, Tesla pet): while a window is live every
 * soul debit charged to its holder is waived, and the waived amount is what the refund line reports.
 *
 * <p>Consulted at gate 10 on every soul-costed activation, so the read is one map get behind an emptiness
 * fast path — on a server where nobody is exempt, {@link #waives} never touches a key at all.
 * {@code engine.stores} is a hot-path package (EngineBoundaryArchTest): UUID-keyed concurrent maps only.
 */
public final class SoulExemptStore implements PlayerScoped {

    /**
     * One live exemption: its expiry plus the feedback the arming effect authored. {@code threshold} is the
     * amount a single waiver must EXCEED before {@code message} is sent, so the trickle of cheap procs stays
     * quiet while a real refund is announced.
     */
    public record Window(long expiryTick, int threshold, String message) {
    }

    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    /** Arm/replace {@code holder}'s exemption. A non-positive duration clears nothing and arms nothing. */
    public void arm(UUID holder, long nowTicks, int durationTicks, int threshold, String message) {
        if (holder == null || durationTicks <= 0) {
            return;
        }
        windows.put(holder, new Window(nowTicks + durationTicks,
                Math.max(0, threshold), message == null ? "" : message));
    }

    /** {@code holder}'s live window at {@code nowTicks}, or {@code null} (elapsed entries lazily removed). */
    public Window window(UUID holder, long nowTicks) {
        if (windows.isEmpty() || holder == null) {
            return null; // the whole-server fast path: no exemption anywhere costs one field read
        }
        Window live = windows.get(holder);
        if (live == null) {
            return null;
        }
        if (nowTicks >= live.expiryTick()) {
            windows.remove(holder, live);
            return null;
        }
        return live;
    }

    /** Whether {@code holder}'s soul debits are currently waived — gate 10's O(1) consult. */
    public boolean waives(UUID holder, long nowTicks) {
        return window(holder, nowTicks) != null;
    }

    /**
     * The line to send {@code holder} after waiving {@code souls}, or {@code ""} when there is nothing to say
     * (no window, no authored message, or an amount at or below the window's threshold).
     */
    public String refundMessage(UUID holder, long nowTicks, int souls) {
        Window live = window(holder, nowTicks);
        if (live == null || souls <= live.threshold() || live.message().isEmpty()) {
            return "";
        }
        return live.message();
    }

    @Override
    public void clear(UUID player) {
        windows.remove(player);
    }

    /** Forget every player's exemption (call on disable). */
    public void clearAll() {
        windows.clear();
    }
}
