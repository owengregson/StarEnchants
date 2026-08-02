package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Each player's last-known TOTAL souls across every carried gem — the source of {@code %actor.souls%} /
 * {@code %victim.souls%} and of the PlaceholderAPI soul feed. The soul service refreshes it on the holder's
 * own region thread each maintenance tick; every reader takes the cached number, so a soul fact never walks
 * another player's inventory from a foreign region (§5.5).
 *
 * <p>UUID-keyed + concurrent, and swept VOLATILE on quit — the total is derived from carried gems and
 * re-establishes itself on the first tick after rejoin. {@code engine.stores} is a hot-path package
 * (EngineBoundaryArchTest).
 */
public final class SoulTotalStore implements PlayerScoped {

    private final Map<UUID, Integer> byPlayer = new ConcurrentHashMap<>();

    /** Record {@code player}'s total across all carried gems (clamped at 0). */
    public void set(UUID player, int total) {
        byPlayer.put(player, Math.max(0, total));
    }

    /** {@code player}'s last recorded total, or {@code 0} if none — the read every soul fact takes. */
    public int current(UUID player) {
        Integer total = byPlayer.get(player);
        return total == null ? 0 : total;
    }

    @Override
    public void clear(UUID player) {
        byPlayer.remove(player);
    }
}
