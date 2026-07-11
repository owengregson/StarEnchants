package feature.mask;

import engine.stores.PlayerScoped;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;

/**
 * Each masked wearer's pre-built shown head (ADR-0053 §5) — what the sweep and join pushes re-assert without
 * re-deriving worn state. Written on the wearer's own thread ({@code refresh}), read from the global sweep and
 * recipients' threads, so entries are cloned on BOTH sides — a stored head is never handed out live. Cleared on
 * quit via the module's {@link PlayerScoped} sweep and on disable ({@link #clearAll}).
 */
public final class MaskIllusionStore implements PlayerScoped {

    private final Map<UUID, ItemStack> shown = new ConcurrentHashMap<>();

    /** Record {@code wearer}'s shown head (defensively cloned). */
    public void put(UUID wearer, ItemStack head) {
        shown.put(wearer, head.clone());
    }

    /** A fresh clone of {@code wearer}'s shown head, or {@code null} when unmasked. */
    public ItemStack get(UUID wearer) {
        ItemStack head = shown.get(wearer);
        return head == null ? null : head.clone();
    }

    /** Forget {@code wearer}'s illusion; returns whether one was stored (the restore-broadcast signal). */
    public boolean remove(UUID wearer) {
        return shown.remove(wearer) != null;
    }

    /** A snapshot of the currently-masked wearers (sweep / join-push iteration). */
    public Set<UUID> wearers() {
        return Set.copyOf(shown.keySet());
    }

    @Override
    public void clear(UUID player) {
        shown.remove(player);
    }

    public void clearAll() {
        shown.clear();
    }
}
