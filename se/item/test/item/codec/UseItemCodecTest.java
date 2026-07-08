package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Pure round-trip tests for the use-item codec (§3.5): the stamped def key survives write→read through the
 * state-store seam (ADR-0044), presence of the key IS the identity, and an unstamped stack is inert — no
 * server. The live PDC round-trip across the mapping flip is proven by the tester suites.
 */
class UseItemCodecTest {

    private static UseItemCodec codec() {
        return new UseItemCodec("useitem", new FakeItemStateStore());
    }

    @Test
    void stampedKeyRoundTripsAndIsTheIdentity() {
        ItemStack stack = mock(ItemStack.class);
        UseItemCodec codec = codec();

        codec.stamp(stack, "rage-crystal");
        assertEquals("rage-crystal", codec.keyOf(stack));
        assertTrue(codec.isUseItem(stack));
    }

    @Test
    void anUnstampedStackIsNotAUseItem() {
        ItemStack stack = mock(ItemStack.class);
        UseItemCodec codec = codec();

        assertNull(codec.keyOf(stack));
        assertFalse(codec.isUseItem(stack));
    }
}
