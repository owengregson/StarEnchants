package feature.mask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import item.head.EquipmentRepaint;
import item.head.TexturedHeads;
import item.view.ItemViewCache;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class MaskIllusionServiceTest {

    private static MaskIllusionService service(TexturedHeads heads) {
        return new MaskIllusionService(EquipmentRepaint.NONE, heads, item.head.HeadAttributes.NONE, () -> null, mock(ItemViewCache.class),
                () -> true, new MaskIllusionStore());
    }

    @Test
    void templateBuiltOncePerBase64() {
        // The seam mints a fresh profiled head per call — rebuilding on every refresh/equip would put head
        // construction on the equip spine; the cache pins build-once-per-texture.
        AtomicInteger builds = new AtomicInteger();
        ItemStack built = mock(ItemStack.class);
        MaskIllusionService service = service(base64 -> {
            builds.incrementAndGet();
            return built;
        });

        assertSame(built, service.template("dGV4dHVyZQ=="));
        assertSame(built, service.template("dGV4dHVyZQ=="));
        assertEquals(1, builds.get());
    }

    @Test
    void untexturableSeamYieldsNullAndNeverCachesTheMiss() {
        // null from TexturedHeads = untexturable server → no illusion; caching the miss would also be wrong for
        // a different base64 arriving later through the same map.
        AtomicInteger builds = new AtomicInteger();
        MaskIllusionService service = service(base64 -> {
            builds.incrementAndGet();
            return null;
        });

        assertNull(service.template("dGV4dHVyZQ=="));
        assertNull(service.template("dGV4dHVyZQ=="));
        assertEquals(2, builds.get()); // the miss was not cached — each call re-probes the seam
    }

    @org.junit.jupiter.api.Test
    void friendlyNameTitleCasesTheMaterial() {
        // The unnamed-helmet hover fallback (1.8.1 dress): never "Player Head", always the helmet's own name.
        org.junit.jupiter.api.Assertions.assertEquals("Diamond Helmet",
                MaskIllusionService.friendlyName(org.bukkit.Material.DIAMOND_HELMET));
        org.junit.jupiter.api.Assertions.assertEquals("Turtle Helmet",
                MaskIllusionService.friendlyName(org.bukkit.Material.TURTLE_HELMET));
    }
}
