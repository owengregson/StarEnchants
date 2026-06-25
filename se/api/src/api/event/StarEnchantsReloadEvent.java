package api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a content reload is published (docs/architecture.md §10, §13). Fires only on a CLEAN swap
 * (a reload with blocking diagnostics keeps the old content and does not fire). The snapshot is live, but
 * per-player {@code WornState} re-resolution is dispatched alongside and finishes asynchronously — a listener
 * must NOT assume every player is re-resolved yet. On Folia this fires on the global region thread.
 */
public final class StarEnchantsReloadEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int generation;
    private final int abilityCount;

    public StarEnchantsReloadEvent(int generation, int abilityCount) {
        this.generation = generation;
        this.abilityCount = abilityCount;
    }

    /** Strictly higher than the previous one. */
    public int getGeneration() {
        return generation;
    }

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
