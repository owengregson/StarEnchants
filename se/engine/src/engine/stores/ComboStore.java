package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player combat-streak tracker — source of the {@code %combo%} fact (docs/architecture.md §3.4). A combo is
 * CONSECUTIVE hits on the SAME target: each attack within {@link #windowTicks} of the previous on the same victim
 * extends the streak; a longer gap OR a switch to a different victim resets it to 1 (so pre-charging hits on a
 * penned mob cannot carry into a player fight). Combat-local: only the combat dispatch writes it, so it is owned
 * there, not at the composition root. UUID-keyed because a player's attacks may fire on different region threads
 * as they move (Folia).
 */
public final class ComboStore implements PlayerScoped {

    /** Default streak window: 100 ticks (5 seconds). */
    public static final long DEFAULT_WINDOW_TICKS = 100L;

    private record Streak(int count, long lastHit, UUID victim) {
    }

    private final Map<UUID, Streak> byPlayer = new ConcurrentHashMap<>();
    private final long windowTicks;

    public ComboStore() {
        this(DEFAULT_WINDOW_TICKS);
    }

    public ComboStore(long windowTicks) {
        this.windowTicks = Math.max(1L, windowTicks);
    }

    /** Register a hit on {@code victim} and return the resulting consecutive-same-target count (always {@code >= 1}). */
    public int hit(UUID player, UUID victim, long nowTicks) {
        Streak updated = byPlayer.compute(player, (id, prev) -> {
            boolean continues = prev != null && nowTicks - prev.lastHit() <= windowTicks
                    && java.util.Objects.equals(prev.victim(), victim);
            return new Streak(continues ? prev.count() + 1 : 1, nowTicks, victim);
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
