package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** The reforge-item identity codec (ADR-0070) over the fake state store — identity only, no counters. */
class ReforgeCodecTest {

    private final ReforgeCodec codec = new ReforgeCodec(ItemKeys.of(), new FakeItemStateStore());

    @Test
    void stampAndReadBackIdentity() {
        ItemStack stack = mock(ItemStack.class);
        assertFalse(codec.isReforge(stack));
        assertNull(codec.keyOf(stack));

        codec.stamp(stack, "reforges/singularity");
        assertTrue(codec.isReforge(stack));
        assertEquals("reforges/singularity", codec.keyOf(stack));
    }

    @Test
    void absentOrBlankReadsAsNotAReforge() {
        ItemStack stack = mock(ItemStack.class);
        codec.stamp(stack, "   ");          // a blank identity is treated as "not a reforge" (tolerant decode)
        assertFalse(codec.isReforge(stack));
        assertNull(codec.keyOf(stack));
    }
}
