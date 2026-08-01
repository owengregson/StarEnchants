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

    private final Map<UUID, Stacks> pvpByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Stacks> pveByPlayer = new ConcurrentHashMap<>();

    /** Compatibility accessor for the PvP bucket. */
    public void set(UUID player, int stacks, long nowTicks) {
        set(player, true, stacks, nowTicks);
    }

    /** Set one of Cosmic Rage's independent PvP/PvE buckets. */
    public void set(UUID player, boolean pvp, int stacks, long nowTicks) {
        map(pvp).put(player, new Stacks(Math.max(0, stacks), nowTicks));
    }

    /** Compatibility accessor for the PvP bucket. */
    public int current(UUID player) {
        return current(player, true);
    }

    /** Current stacks in the requested PvP/PvE bucket. */
    public int current(UUID player, boolean pvp) {
        Stacks s = map(pvp).get(player);
        return s == null ? 0 : s.stacks();
    }

    /** Compatibility accessor for the PvP bucket. */
    public long lastTick(UUID player) {
        return lastTick(player, true);
    }

    public long lastTick(UUID player, boolean pvp) {
        Stacks s = map(pvp).get(player);
        return s == null ? Long.MIN_VALUE : s.lastTick();
    }

    private Map<UUID, Stacks> map(boolean pvp) {
        return pvp ? pvpByPlayer : pveByPlayer;
    }

    @Override
    public void clear(UUID player) {
        pvpByPlayer.remove(player);
        pveByPlayer.remove(player);
    }
}
