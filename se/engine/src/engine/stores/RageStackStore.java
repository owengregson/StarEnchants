package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player Rage stack tracker — source of the {@code %ragestacks%} fact and the state the rage-stacks combat
 * service reads for its stack/break fx. Stacks are {@code min(combo streak, rage level)}, refreshed each
 * qualifying hit; {@link #lastTick} lets a delayed expiry probe tell whether the window it armed is still the
 * live one (a later hit stamps a fresh tick, so a stale probe no-ops). UUID-keyed + concurrent: a player's hits
 * fire on different region threads as they move (Folia). Combat-local (only the rage service writes it) and swept
 * VOLATILE on quit like the combo streak. {@code engine.stores} is a hot-path package (EngineBoundaryArchTest).
 */
public final class RageStackStore implements PlayerScoped {

    /** A player's current rage stacks and the monotonic tick they were last set. */
    public record Stacks(int stacks, long lastTick) {
    }

    private final Map<UUID, Stacks> byPlayer = new ConcurrentHashMap<>();

    /** Set {@code player}'s stacks as of {@code nowTicks} (clamped at 0). */
    public void set(UUID player, int stacks, long nowTicks) {
        byPlayer.put(player, new Stacks(Math.max(0, stacks), nowTicks));
    }

    /** The current stacks for {@code player}, or {@code 0} if none. */
    public int current(UUID player) {
        Stacks s = byPlayer.get(player);
        return s == null ? 0 : s.stacks();
    }

    /** The tick {@code player}'s stacks were last {@link #set}, or {@link Long#MIN_VALUE} if none (never a live tick). */
    public long lastTick(UUID player) {
        Stacks s = byPlayer.get(player);
        return s == null ? Long.MIN_VALUE : s.lastTick();
    }

    @Override
    public void clear(UUID player) {
        byPlayer.remove(player);
    }
}
