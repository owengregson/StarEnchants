package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player combat-streak tracker — the source of the {@code %combo%} fact (docs/architecture.md §3.4).
 * Each player attack within a short window of the previous one extends the streak; a gap longer than the
 * window resets it to 1. This is the model EE's {@code RAGE} relied on (a stacking per-hit bonus that
 * decays after ~5s of no hits) and the value AdvancedEnchantments exposes as {@code %combo%}; it stays
 * combat-local (only the combat dispatch writes it), so it is owned there rather than threaded through the
 * composition root.
 *
 * <p>Concurrent and UUID-keyed for Folia (a player's attacks may fire on different region threads as they
 * move). Time is an explicit tick count supplied by the caller (never wall-clock) — deterministic,
 * Folia-correct, and unit-testable without a server. The window defaults to 100 ticks (5s), matching the
 * decay EE used.
 */
public final class ComboStore {

    /** Default streak window: 100 ticks (5 seconds), the decay EE's RAGE used. */
    public static final long DEFAULT_WINDOW_TICKS = 100L;

    /** A live streak: its current count and the tick of the most recent hit. */
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

    /**
     * Register a hit by {@code player} at {@code nowTicks} and return the resulting consecutive-hit count
     * (always {@code >= 1}). A hit within {@link #windowTicks} of the previous one extends the streak; a
     * longer gap restarts it at 1.
     */
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
