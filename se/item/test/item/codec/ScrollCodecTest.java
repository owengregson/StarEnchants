package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import schema.spec.Ranges;

/** Heroic Black Scroll state is PDC/NBT-backed, range-normalized, and independent of rendered lore. */
class ScrollCodecTest {

    @Test
    void heroicRangeRoundTripsAndNormalizes() {
        FakeItemStateStore store = new FakeItemStateStore();
        ScrollCodec codec = new ScrollCodec("scroll", "convert", "heroic-min-v1", "heroic-max-v1", store);
        ItemStack scroll = new ItemStack(Material.PAPER);
        codec.mark(scroll, "heroic-black-scroll");
        codec.markHeroicRange(scroll, 140, -5);

        assertEquals("HEROIC-BLACK-SCROLL", codec.kind(scroll));
        assertEquals(new Ranges.IntRange(0, 100), codec.heroicRangeOf(scroll, 10, 35));
    }

    @Test
    void missingOrPartialLegacyStateFallsBackWithoutThrowing() {
        FakeItemStateStore store = new FakeItemStateStore();
        ScrollCodec codec = new ScrollCodec("scroll", "convert", "heroic-min-v1", "heroic-max-v1", store);
        ItemStack scroll = new ItemStack(Material.PAPER);
        store.writeInt(scroll, "heroic-min-v1", 80);

        assertDoesNotThrow(() -> codec.heroicRangeOf(scroll, 10, 35));
        assertEquals(new Ranges.IntRange(35, 80), codec.heroicRangeOf(scroll, 10, 35));

        ItemStack malformed = new ItemStack(Material.PAPER);
        store.write(malformed, "heroic-min-v1", "not-an-integer");
        store.write(malformed, "heroic-max-v1", "also-not-an-integer");
        assertDoesNotThrow(() -> codec.heroicRangeOf(malformed, 10, 35));
        assertEquals(new Ranges.IntRange(10, 35), codec.heroicRangeOf(malformed, 10, 35));
    }
}
