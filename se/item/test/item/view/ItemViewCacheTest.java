package item.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import item.codec.CombatCodec;
import item.codec.CombatState;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link ItemViewCache} (docs/architecture.md §5.2). The caching policy is pure
 * — decode-once by full content, share one empty view, invalidate on reload, intern under contention
 * — and is pinned here without a server (the blob is built straight from the codec; only the
 * read-through test mocks an {@link ItemStack}). The read over a real item's PDC is pinned live.
 */
class ItemViewCacheTest {

    private static final NamespacedKey KEY = NamespacedKey.minecraft("combat");

    private static CombatCodec codec() {
        return new CombatCodec(KEY);
    }

    @Test
    void decodesOnceAndReturnsTheSameViewForIdenticalContent() {
        CombatCodec codec = codec();
        ItemViewCache cache = new ItemViewCache(codec, 0);
        String blob = codec.encode(new CombatState(Map.of("sharpness", 3), List.of("fire_crystal")));

        ItemView first = cache.ofBlob(blob);
        ItemView second = cache.ofBlob(blob);

        assertSame(first, second, "identical content must hit the cache, not re-decode");
        assertEquals(3, (int) first.combat().enchants().get("sharpness"));
        assertEquals(List.of("fire_crystal"), first.combat().crystals());
        assertEquals(0, first.gen());
    }

    @Test
    void nullAndEmptyBlobsShareOneEmptyView() {
        ItemViewCache cache = new ItemViewCache(codec(), 0);

        ItemView fromNull = cache.ofBlob(null);
        ItemView fromEmpty = cache.ofBlob("");

        assertSame(fromNull, fromEmpty, "no-state items must share the generation's empty view");
        assertTrue(fromNull.isEmpty());
    }

    @Test
    void differentContentDecodesToDistinctViews() {
        CombatCodec codec = codec();
        ItemViewCache cache = new ItemViewCache(codec, 0);

        ItemView sharp1 = cache.ofBlob(codec.encode(new CombatState(Map.of("sharpness", 1), List.of())));
        ItemView sharp5 = cache.ofBlob(codec.encode(new CombatState(Map.of("sharpness", 5), List.of())));

        assertNotSame(sharp1, sharp5);
        assertEquals(1, (int) sharp1.combat().enchants().get("sharpness"));
        assertEquals(5, (int) sharp5.combat().enchants().get("sharpness"));
    }

    @Test
    void reloadDropsCachedViewsAndBumpsGeneration() {
        CombatCodec codec = codec();
        ItemViewCache cache = new ItemViewCache(codec, 0);
        String blob = codec.encode(new CombatState(Map.of("sharpness", 2), List.of()));

        ItemView before = cache.ofBlob(blob);
        cache.reload(7);
        ItemView after = cache.ofBlob(blob);

        assertNotSame(before, after, "a reload must invalidate cached views");
        assertEquals(0, before.gen());
        assertEquals(7, after.gen());
        assertEquals(7, cache.generation());
    }

    @Test
    void ofReadsTheBlobThroughTheItemStack() {
        CombatCodec codec = codec();
        ItemViewCache cache = new ItemViewCache(codec, 0);
        String blob = codec.encode(new CombatState(Map.of("protection", 4), List.of()));

        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(KEY, PersistentDataType.STRING)).thenReturn(blob);

        ItemView view = cache.of(stack);
        assertEquals(4, (int) view.combat().enchants().get("protection"));
    }

    @Test
    void concurrentReadsOfTheSameContentInternOneView() throws InterruptedException {
        CombatCodec codec = codec();
        ItemViewCache cache = new ItemViewCache(codec, 0);
        String blob = codec.encode(new CombatState(Map.of("unbreaking", 3), List.of("guard")));

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ConcurrentLinkedQueue<ItemView> results = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < threads; i++) {
            pool.execute(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                results.add(cache.ofBlob(blob));
            });
        }
        ready.await();
        go.countDown(); // release all workers at once to maximise contention
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "workers did not finish");

        ItemView canonical = cache.ofBlob(blob);
        assertEquals(threads, results.size());
        for (ItemView observed : results) {
            assertSame(canonical, observed, "all concurrent readers must observe one interned view");
        }
    }
}
