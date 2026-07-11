package feature.pet;

import engine.stores.PlayerScoped;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The ACTIVE pets' armed windows (ADR-0052): {@code player → pet key → (expiry tick, arm generation)}. While
 * a window is open the pet's non-USE abilities join the player's {@code WornState} (via the
 * {@code PetWornSource} live read); the scheduled expiry clears it, sends the universal ENDED message and
 * requests a worn refresh. The GENERATION guards the race a re-arm opens: a stale expiry task for an
 * earlier window must never clear a newer one, so each arm mints a fresh generation and the expiry only
 * clears its own ({@link #clearIfGeneration}).
 *
 * <p>Cleared on quit (the module's {@link PlayerScoped} sweep) and death (the pets listener) — a buff never
 * survives either. Concurrent: armed reads happen at worn-resolve time on the player's thread, writes on the
 * same thread, quit sweeps elsewhere.
 */
public final class PetArmedStore implements PlayerScoped {

    /** One armed window: live until {@code expiryTick}; {@code generation} pairs it with its expiry task. */
    record Window(long expiryTick, long generation) {
    }

    private final Map<UUID, Map<String, Window>> windows = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();

    /** Open (or replace) {@code player}'s window for {@code petKey}; returns the arm generation. */
    public long arm(UUID player, String petKey, long expiryTick) {
        long generation = generations.incrementAndGet();
        windows.computeIfAbsent(player, id -> new ConcurrentHashMap<>())
                .put(petKey, new Window(expiryTick, generation));
        return generation;
    }

    public boolean isArmed(UUID player, String petKey, long nowTick) {
        Map<String, Window> mine = windows.get(player);
        if (mine == null) {
            return false;
        }
        Window window = mine.get(petKey);
        return window != null && window.expiryTick() > nowTick;
    }

    /**
     * Clear {@code petKey}'s window iff it is still generation {@code generation} (the scheduled expiry's own
     * arm) — a re-armed window survives its predecessor's task. Returns whether it cleared. All writes for one
     * player happen on that player's own thread, so get-compare-remove cannot interleave with an arm.
     */
    public boolean clearIfGeneration(UUID player, String petKey, long generation) {
        Map<String, Window> mine = windows.get(player);
        if (mine == null) {
            return false;
        }
        Window window = mine.get(petKey);
        if (window == null || window.generation() != generation) {
            return false;
        }
        mine.remove(petKey);
        return true;
    }

    @Override
    public void clear(UUID player) {
        windows.remove(player);
    }

    public void clearAll() {
        windows.clear();
    }
}
