package item.view;

import item.codec.CombatCodec;
import item.codec.CombatState;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;

/**
 * The item read cache (§5.2): {@link #of} returns a cached {@link ItemView} keyed by the item's raw
 * combat blob's <em>full content</em> within the current generation, decoding once. Replaces a Cosmic
 * Enchants-style clone-and-Gson-parse per slot per hit — the biggest combat CPU win.
 *
 * <p>Key is content + generation, never {@code ItemMeta} identity: meta is copy-on-write, so an
 * identity key both misses constantly and can alias a stale view (§5.2). Full blob, not a truncated
 * hash, so a collision can never serve a stale view. Reload swaps a fresh per-generation map, so
 * prior views vanish atomically — no stale reads, no unbounded growth across reloads.
 *
 * <p>Lock-free across Folia region threads: {@link ItemView} is immutable, the generation holder is
 * {@code volatile}, the per-generation map is a {@link ConcurrentHashMap}. A read racing a reload
 * decodes into the doomed old map and recomputes next time — never stale or corrupt. The no-state
 * item is served by a shared empty view: zero allocation, no map touch.
 */
public final class ItemViewCache {

    private record Generation(int gen, ItemView empty, ConcurrentHashMap<String, ItemView> byBlob) {
        static Generation of(int gen) {
            return new Generation(gen, new ItemView(gen, CombatState.EMPTY), new ConcurrentHashMap<>());
        }
    }

    private final CombatCodec codec;
    private volatile Generation current;

    public ItemViewCache(CombatCodec codec, int generation) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.current = Generation.of(generation);
    }

    public ItemView of(ItemStack stack) {
        return ofBlob(codec.readBlob(stack));
    }

    /** Advance to a new generation: a fresh map, so every prior view is dropped. */
    public void reload(int generation) {
        current = Generation.of(generation);
    }

    public int generation() {
        return current.gen;
    }

    /**
     * Cache logic over an already-read blob (version-agnostic core): null/empty blob returns the shared
     * empty view; otherwise the full blob keys the map, decoding once and interning the contention winner.
     */
    ItemView ofBlob(String blob) {
        Generation g = current;
        if (blob == null || blob.isEmpty()) {
            return g.empty;
        }
        ItemView cached = g.byBlob.get(blob);
        if (cached != null) {
            return cached;
        }
        ItemView decoded = new ItemView(g.gen, codec.decode(blob));
        ItemView raced = g.byBlob.putIfAbsent(blob, decoded);
        return raced != null ? raced : decoded;
    }
}
