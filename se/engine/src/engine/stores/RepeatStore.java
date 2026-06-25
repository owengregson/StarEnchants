package engine.stores;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player repeating-task handles: player &rarr; (ability id &rarr; opaque task handle)
 * (docs/architecture.md §5.4). One recurring task per active {@code (player, ability)} pair; the handle
 * type {@code H} is opaque so the store stays version- and platform-agnostic.
 *
 * <p><strong>This store never cancels a task</strong> — it owns the <em>mapping</em>, not the lifecycle.
 * Every removal (and a superseding {@link #put}) hands the handle(s) back so the caller cancels on the
 * correct thread via the scheduling abstraction, keeping the store a plain map under Folia's region model.
 * No TTL or tick parameter — a handle is live until explicitly removed; expiry is the task's concern.
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
