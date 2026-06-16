package api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a content reload has been published (docs/architecture.md §10, §13) — the seam a
 * third-party plugin uses to refresh its own caches keyed by StarEnchants content. Carries the new
 * snapshot's generation and ability count. Fires only on a CLEAN swap (a reload with blocking
 * diagnostics keeps the old content and does not fire this).
 *
 * <p>The published snapshot is already live when this fires, but StarEnchants' own per-player
 * re-resolution (each online player's {@code WornState} is rebuilt on that player's region thread)
 * is dispatched alongside and completes asynchronously — so a listener must NOT assume every online
 * player has been re-resolved against the new generation by the time it runs. On Folia this fires on
 * the global region thread; route any cross-region work through a scheduler.
 */
public final class StarEnchantsReloadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int generation;
    private final int abilityCount;

    public StarEnchantsReloadEvent(int generation, int abilityCount) {
        this.generation = generation;
        this.abilityCount = abilityCount;
    }

    /** The published snapshot's generation (strictly higher than the previous one). */
    public int getGeneration() {
        return generation;
    }

    /** How many compiled abilities the new snapshot holds. */
    public int getAbilityCount() {
        return abilityCount;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
