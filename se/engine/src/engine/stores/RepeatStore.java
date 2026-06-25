package engine.stores;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player repeating-task handles: player &rarr; (ability id &rarr; opaque task handle)
 * (docs/architecture.md §5.4). Backs {@code RepeatingTrigger} and the soul-drain timers
 * ({@code DRAIN_SOULS_CONSTANT}; §6), one recurring task per active {@code (player, ability)} pair.
 *
 * <p>The handle type {@code H} is the platform's scheduled-task handle (Paper {@code BukkitTask}, Folia
 * {@code ScheduledTask}, …), opaque so this store stays version- and platform-agnostic.
 * <strong>This store never cancels a task</strong> — it owns the <em>mapping</em>, not the lifecycle.
 * Every removal hands the handle(s) back so the caller cancels on the correct thread via the scheduling
 * abstraction; that is what keeps the store a plain, side-effect-free map under Folia's region model.
 *
 * <p>Concurrent, UUID-keyed (Folia: any region thread may arm or disarm). Self-bounding: {@link #put}
 * replaces rather than accumulates, so at most one live task per {@code (player, ability)}; the returned
 * previous handle is the caller's signal to cancel the superseded task (or it leaks). Drained on quit
 * ({@link #removeAll}) and disable ({@link #removeEverything}).
 *
 * <p>No time dependency: a handle is live until explicitly removed, so no tick parameter and no TTL —
 * expiry is the scheduled task's concern, not the map's.
 */
public final class RepeatStore<H> {

    private final Map<UUID, Map<Integer, H>> handlesByPlayer = new ConcurrentHashMap<>();

    /**
     * Record {@code handle} as the live task for {@code (player, abilityId)}, replacing any already there.
     *
     * @return the replaced handle (the caller must cancel it), or empty if this is the pair's first.
     */
    public Optional<H> put(UUID player, int abilityId, H handle) {
        H previous = handlesByPlayer
                .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(abilityId, handle);
        return Optional.ofNullable(previous);
    }

    /**
     * Remove and return the handle for {@code (player, abilityId)} (caller cancels), or empty if none.
     * Drops the player's inner map once its last handle is gone, so no residual entry lingers.
     */
    public Optional<H> remove(UUID player, int abilityId) {
        Map<Integer, H> handles = handlesByPlayer.get(player);
        if (handles == null) {
            return Optional.empty();
        }
        H removed = handles.remove(abilityId);
        if (handles.isEmpty()) {
            handlesByPlayer.remove(player, handles);
        }
        return Optional.ofNullable(removed);
    }

    /** @return {@code true} if a live handle is stored for {@code abilityId} on {@code player}. */
    public boolean has(UUID player, int abilityId) {
        Map<Integer, H> handles = handlesByPlayer.get(player);
        return handles != null && handles.containsKey(abilityId);
    }

    /**
     * Remove and return every handle for one player, dropping the player (call on quit — caller cancels
     * each). Order unspecified.
     *
     * @return the player's handles, or an empty list if none.
     */
    public List<H> removeAll(UUID player) {
        Map<Integer, H> handles = handlesByPlayer.remove(player);
        if (handles == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(handles.values());
    }

    /**
     * Remove and return every handle for every player, emptying the store (call on disable — caller
     * cancels each). Order unspecified.
     *
     * @return all live handles, or an empty list if none.
     */
    public List<H> removeEverything() {
        List<H> all = new ArrayList<>();
        for (Map<Integer, H> handles : handlesByPlayer.values()) {
            all.addAll(handles.values());
        }
        handlesByPlayer.clear();
        return all;
    }
}
