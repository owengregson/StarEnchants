package engine.stores;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tick each player last changed held hotbar slot — the source of {@code %heldticks%}, the "how long have
 * you actually been holding this" fact that separates a committed weapon from a mid-fight swap. Only the
 * hotbar-slot change stamps it, not the broader worn-state refresh, or closing a chest would read as a swap.
 *
 * <p>A player with no stamp yet reads {@code 0}: the fact defaults like every other absent fact rather than
 * inventing an unbounded "held forever". UUID-keyed + concurrent — a player's slot changes fire on their own
 * region thread, which moves with them (Folia). {@code engine.stores} is a hot-path package
 * (EngineBoundaryArchTest).
 */
public final class HeldSlotStore implements PlayerScoped {

    private final Map<UUID, Long> changedAt = new ConcurrentHashMap<>();

    /** Stamp {@code player}'s held-slot change at {@code nowTicks}. */
    public void changed(UUID player, long nowTicks) {
        changedAt.put(player, nowTicks);
    }

    /** Ticks since {@code player}'s last {@link #changed} stamp; {@code 0} if never stamped (never negative). */
    public long ticksSince(UUID player, long nowTicks) {
        Long at = changedAt.get(player);
        return at == null ? 0L : Math.max(0L, nowTicks - at);
    }

    @Override
    public void clear(UUID player) {
        changedAt.remove(player);
    }
}
