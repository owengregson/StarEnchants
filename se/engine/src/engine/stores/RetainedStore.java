package engine.stores;

import java.util.UUID;

/**
 * A {@link PlayerScoped} store whose entries are RETAINED across a relog: the quit sweep drops only entries that
 * have already elapsed rather than forgetting the player wholesale, so a landed combat window (a cooldown, a
 * teleport block, a timed suppression) cannot be shed by a quick disconnect+reconnect. Time is the same monotonic
 * caller-supplied tick the store already reads, so a surviving entry stays correct on rejoin with no re-arm.
 *
 * <p>Because an offline player's entries are only ever read again on rejoin, lazy eviction alone would leak one
 * small map per never-returning quitter with a live window; the composition root runs a periodic all-players
 * {@link #evictElapsed(long)} to bound that.
 */
public interface RetainedStore extends PlayerScoped {

    /** Drop {@code player}'s elapsed entries at {@code nowTicks}, keeping live windows (the quit sweep). */
    void evictElapsed(UUID player, long nowTicks);

    /** Drop every player's elapsed entries at {@code nowTicks} (the periodic offline-state sweep). */
    void evictElapsed(long nowTicks);
}
