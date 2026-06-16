package item.view;

import item.codec.CombatCodec;
import item.codec.CombatState;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;

/**
 * The item read cache (docs/architecture.md §5.2): {@link #of} reads an item's raw combat blob and
 * returns a cached {@link ItemView} keyed by that blob's <em>full content</em> within the current
 * generation, decoding it exactly once. In combat the same helmet hit 20×/s is parsed once; every
 * later read is a map lookup — this replaces EE's clone-and-Gson-parse per slot per hit, the single
 * biggest combat CPU win.
 *
 * <p>The key is content + generation, never {@code ItemMeta} identity — meta is copy-on-write, so an
 * identity key both misses constantly <em>and</em> can alias a stale view (§5.2). The key is the full
 * blob string (not a truncated hash), so a collision can never serve a stale view. A reload swaps in
 * a fresh per-generation map, so every prior view vanishes atomically — no stale reads, no unbounded
 * growth across reloads.
 *
 * <p>Concurrent and lock-free: combat reads this from many region threads on Folia. {@link ItemView}
 * is immutable, the generation holder is published through a {@code volatile}, and the per-generation
 * map is a {@link ConcurrentHashMap}. A read racing a reload simply decodes into the doomed old map
 * and recomputes next time — never a stale or corrupt view. The common no-state item is served by a
 * shared empty view with zero allocation and no map touch.
 */
public final class ItemViewCache {

    /** One generation's cache: its id, its shared empty view, and its content&rarr;view map. */
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

    /** The cached view of {@code stack}: one PDC read + one map lookup on a hit, one decode on a miss. */
    public ItemView of(ItemStack stack) {
        return ofBlob(codec.readBlob(stack));
    }

    /** Advance to a new snapshot generation on reload: a fresh map, so every prior view is dropped. */
    public void reload(int generation) {
        current = Generation.of(generation);
    }

    /** The snapshot generation the cache is currently decoding against. */
    public int generation() {
        return current.gen;
    }

    /**
     * The cache logic over an already-read raw blob — the version-agnostic core. {@code null}/empty
     * blob (the common no-state item) returns the generation's shared empty view; otherwise the full
     * blob keys the per-generation map, decoding once and interning the winner under contention.
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
