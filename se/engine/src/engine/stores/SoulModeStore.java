package engine.stores;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player soul mode: which soul gem is currently <em>active</em> for a player, keyed
 * by the gem's PDC UUID (docs/architecture.md §5.4). Tracking the gem identity — not a
 * hotbar slot index — is deliberate: the original keyed on the slot, which silently
 * pointed at the wrong gem the moment the hotbar was rearranged. The gem id is stable
 * across moves, drops, and re-pickups, so the active selection follows the gem itself.
 *
 * <p>Concurrent and UUID-keyed for Folia (any region thread may toggle a player's soul
 * mode). Cleared on quit ({@link #clear}) and on disable ({@link #clearAll}).
 *
 * <p>Unlike the other component stores in this package, soul mode has <em>no time
 * dependency</em>: there is no TTL and no eviction. The mode is a deliberate toggle that
 * persists until it is switched off ({@link #deactivate}/{@link #clear}), replaced by
 * activating another gem ({@link #activate}), or the player quits. Hence no method takes
 * a {@code nowTicks} parameter.
 */
public final class SoulModeStore {

    private final Map<UUID, UUID> activeGemByPlayer = new ConcurrentHashMap<>();

    /**
     * Make {@code gemId} the active soul gem for {@code player}, replacing whatever gem
     * (if any) was active before. A player has at most one active gem at a time.
     */
    public void activate(UUID player, UUID gemId) {
        activeGemByPlayer.put(player, gemId);
    }

    /**
     * The gem id currently active for {@code player}, or {@link Optional#empty()} if soul
     * mode is off for that player.
     */
    public Optional<UUID> active(UUID player) {
        return Optional.ofNullable(activeGemByPlayer.get(player));
    }

    /** @return {@code true} if any soul gem is active for {@code player}. */
    public boolean isActive(UUID player) {
        return activeGemByPlayer.containsKey(player);
    }

    /** Switch soul mode off for {@code player}. A no-op if no gem was active. */
    public void deactivate(UUID player) {
        activeGemByPlayer.remove(player);
    }

    /**
     * Forget the active gem for one player (call on quit). Identical in effect to
     * {@link #deactivate}; provided so every store in the package offers a uniform
     * {@code clear}.
     */
    public void clear(UUID player) {
        activeGemByPlayer.remove(player);
    }

    /** Forget the active gem for every player (call on disable). */
    public void clearAll() {
        activeGemByPlayer.clear();
    }
}
