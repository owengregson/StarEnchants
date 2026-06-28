package feature.scroll;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;

/**
 * Holds the holy-protected items pulled out of a player's death drops until they respawn (§I). A player's
 * death and respawn both fire on that player's own region thread, so a single player's stash/drain never
 * races; the {@link ConcurrentHashMap} only guards cross-player concurrency on Folia.
 *
 * <p>Cleared on quit to avoid leaking — the narrow window of "died, quit before respawning" forfeits the
 * stash, an acceptable trade for not retaining items indefinitely.
 */
public final class KeptItemsStore {

    private final Map<UUID, List<ItemStack>> pending = new ConcurrentHashMap<>();

    public void stash(UUID id, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        pending.computeIfAbsent(id, k -> new ArrayList<>()).addAll(items);
    }

    /** Remove and return the player's stashed items (empty if none). */
    public List<ItemStack> drain(UUID id) {
        List<ItemStack> got = pending.remove(id);
        return got == null ? List.of() : got;
    }

    public void clear(UUID id) {
        pending.remove(id);
    }
}
