package engine.stores;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player soul mode: which soul gem is currently active, keyed by the gem's PDC UUID
 * (docs/architecture.md §5.4). Keyed by gem identity, not hotbar slot, deliberately: the gem id is stable
 * across moves, drops, and re-pickups, so the active selection follows the gem rather than a slot that a
 * rearrange would silently repoint.
 *
 * <p>Concurrent, UUID-keyed (Folia: any region thread may toggle).
 *
 * <p>No time dependency: no TTL, no eviction. The mode is a deliberate toggle that persists until switched
 * off ({@link #deactivate}/{@link #clear}), replaced ({@link #activate}), or the player quits — hence no
 * {@code nowTicks} parameter.
 */
public final class SoulModeStore {

    private final Map<UUID, UUID> activeGemByPlayer = new ConcurrentHashMap<>();

    /** Make {@code gemId} the active gem, replacing any prior. A player has at most one active gem. */
    public void activate(UUID player, UUID gemId) {
        activeGemByPlayer.put(player, gemId);
    }

    /** The gem id active for {@code player}, or empty if soul mode is off. */
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

    /** Forget the active gem for one player (call on quit); a {@code clear} alias for {@link #deactivate}. */
    public void clear(UUID player) {
        activeGemByPlayer.remove(player);
    }

    /** Forget the active gem for every player (call on disable). */
    public void clearAll() {
        activeGemByPlayer.clear();
    }
}
