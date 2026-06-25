package engine.stores;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player repeating-task handles: a player &rarr; (ability id &rarr; opaque task handle)
 * table (docs/architecture.md §5.4). New in StarEnchants — backs a Cosmic Enchants-style {@code RepeatingTrigger}
 * and the soul-drain timers ({@code DRAIN_SOULS_CONSTANT}, dead in a Cosmic Enchants-style plugin; §6), which schedule one
 * recurring task per active {@code (player, ability)} pair.
 *
 * <p>The handle type {@code H} is the platform's scheduled-task handle (Paper's
 * {@code BukkitTask}, Folia's {@code ScheduledTask}, …), kept opaque so this store stays
 * version- and platform-agnostic. <strong>This store never cancels a task</strong>: it owns the
 * <em>mapping</em>, not the lifecycle. Every removal hands the handle(s) back to the caller, who
 * cancels them on the correct thread via the scheduling abstraction. Keeping cancellation out of
 * the store is what lets it remain a plain, side-effect-free map under Folia's region model.
 *
 * <p>Concurrent and UUID-keyed for Folia (any region thread may arm or disarm a player's repeats),
 * and self-bounding: {@link #put} replaces — never accumulates — the handle for an ability, so at
 * most one live task exists per {@code (player, ability)}; the returned previous handle is the
 * caller's signal to cancel the task it just superseded (otherwise it leaks, like the Cosmic Enchants-style timers
 * neither original tears down). Drained on quit ({@link #removeAll}) and on disable
 * ({@link #removeEverything}).
 *
 * <p>This store has no time dependency: a handle is live until explicitly removed, so there is no
 * tick parameter and no TTL eviction — expiry is the scheduled task's concern, not the map's.
 */
public final class RepeatStore<H> {

    private final Map<UUID, Map<Integer, H>> handlesByPlayer = new ConcurrentHashMap<>();

    /**
     * Record {@code handle} as the live repeating task for {@code abilityId} on {@code player},
     * replacing any handle already there.
     *
     * @return the previous handle for this {@code (player, abilityId)} if one was replaced, so the
     *     caller can cancel the now-superseded task; empty if this is the first handle for the pair.
     */
    public Optional<H> put(UUID player, int abilityId, H handle) {
        H previous = handlesByPlayer
                .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(abilityId, handle);
        return Optional.ofNullable(previous);
    }

    /**
     * Remove and return the handle for {@code abilityId} on {@code player} (so the caller can cancel
     * the task), or empty if none was stored. Empties the player's inner map once its last handle is
     * gone, so a player who has stopped all repeats holds no residual entry.
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
     * Remove and return every handle for one player, dropping the player from the store (call on
     * quit — the caller cancels each handle). Order is unspecified.
     *
     * @return the player's handles, or an empty list if the player had none.
     */
    public List<H> removeAll(UUID player) {
        Map<Integer, H> handles = handlesByPlayer.remove(player);
        if (handles == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(handles.values());
    }

    /**
     * Remove and return every handle for every player, emptying the store (call on disable — the
     * caller cancels each handle). Order is unspecified.
     *
     * @return all live handles across all players, or an empty list if there were none.
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
