package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/** The mask-item identity codec (ADR-0053) over the fake state store — identity only, no counters. */
class MaskCodecTest {

    private final MaskCodec codec = new MaskCodec(ItemKeys.of(), new FakeItemStateStore());

    @Test
    void stampAndReadBackIdentity() {
        ItemStack stack = mock(ItemStack.class);
        assertFalse(codec.isMask(stack));
        assertNull(codec.keyOf(stack));

        codec.stamp(stack, "masks/agent");
        assertTrue(codec.isMask(stack));
        assertEquals("masks/agent", codec.keyOf(stack));
    }

    @Test
    void absentOrBlankReadsAsNotAMask() {
        ItemStack stack = mock(ItemStack.class);
        codec.stamp(stack, "   ");          // a blank identity is treated as "not a mask" (tolerant decode)
        assertFalse(codec.isMask(stack));
        assertNull(codec.keyOf(stack));
    }
}
