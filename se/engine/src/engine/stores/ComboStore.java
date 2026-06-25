package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player combat-streak tracker — source of the {@code %combo%} fact (docs/architecture.md §3.4). Each
 * attack within {@link #windowTicks} of the previous extends the streak; a longer gap resets it to 1.
 * Combat-local: only the combat dispatch writes it, so it is owned there, not at the composition root.
 *
 * <p>Concurrent, UUID-keyed (Folia: a player's attacks may fire on different region threads as they move).
 * Time is an explicit caller-supplied tick, never wall-clock — deterministic, Folia-correct, server-free
 * to test.
 */
public final class ComboStore {

    /** Default streak window: 100 ticks (5 seconds). */
    public static final long DEFAULT_WINDOW_TICKS = 100L;

    private record Streak(int count, long lastHit) {
    }

    private final Map<UUID, Streak> byPlayer = new ConcurrentHashMap<>();
    private final long windowTicks;

    public ComboStore() {
        this(DEFAULT_WINDOW_TICKS);
    }

    public ComboStore(long windowTicks) {
        this.windowTicks = Math.max(1L, windowTicks);
    }

    /** Register a hit and return the resulting consecutive-hit count (always {@code >= 1}). */
    public int hit(UUID player, long nowTicks) {
        Streak updated = byPlayer.compute(player, (id, prev) -> {
            boolean continues = prev != null && nowTicks - prev.lastHit() <= windowTicks;
            return new Streak(continues ? prev.count() + 1 : 1, nowTicks);
        });
        return updated.count();
    }

    /** The current streak for {@code player} without registering a hit — 0 if none or the window lapsed. */
    public int current(UUID player, long nowTicks) {
        Streak s = byPlayer.get(player);
        return s != null && nowTicks - s.lastHit() <= windowTicks ? s.count() : 0;
    }

    /** Drop {@code player}'s streak (on quit). */
    public void clear(UUID player) {
        byPlayer.remove(player);
    }

    /** Drop every streak (on disable). */
    public void clearAll() {
        byPlayer.clear();
    }
}
