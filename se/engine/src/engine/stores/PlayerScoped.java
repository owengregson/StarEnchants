package engine.stores;

import java.util.UUID;

/**
 * The per-player-store cleanup seam the single quit-cleanup authority iterates (docs/architecture.md §5.4):
 * every engine store keyed by player implements it so {@link EngineStores#clearAll} can free them uniformly,
 * and a newly added store structurally cannot miss the quit sweep.
 */
public interface PlayerScoped {

    /** Forget every entry for one player (call on quit). */
    void clear(UUID player);
}
