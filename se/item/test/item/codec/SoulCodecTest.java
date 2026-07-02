package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for the soul-gem payload format — no Bukkit. The PDC round-trip on a real item is
 * verified live; here the {@code <gemId>:<souls>} encode/decode is pinned, including the
 * never-throw-on-garbage contract.
 */
class SoulCodecTest {

    @Test
    void decodesAWellFormedPayload() {
        UUID id = UUID.fromString("12345678-1234-1234-1234-1234567890ab");
        SoulData data = SoulCodec.decode(id + ":42");
        assertEquals(id, data.gemId());
        assertEquals(42, data.souls());
    }

    @Test
    void malformedOrAbsentPayloadsDecodeToNull() {
        assertNull(SoulCodec.decode(null));
        assertNull(SoulCodec.decode(""));
        assertNull(SoulCodec.decode("not-a-uuid:5"));
        assertNull(SoulCodec.decode("12345678-1234-1234-1234-1234567890ab:notanumber"));
        assertNull(SoulCodec.decode("12345678-1234-1234-1234-1234567890ab")); // no souls field
        assertNull(SoulCodec.decode(":5")); // no uuid
    }

    @Test
    void soulDataClampsNegativeAndWithSoulsKeepsIdentity() {
        UUID id = UUID.randomUUID();
        assertEquals(0, new SoulData(id, -3).souls());
        SoulData next = SoulData.fresh(id).withSouls(7);
        assertEquals(id, next.gemId());
        assertEquals(7, next.souls());
    }

    @Test
    void writesAndReadsBackThroughTheStateStore() {
        // The seam (ADR-0044) makes the codec instance round-trip unit-testable: a map-backed FakeItemStateStore
        // stands in for PDC/NBT, so write→read exercises the store path without a server.
        ItemStack stack = mock(ItemStack.class);
        SoulCodec codec = new SoulCodec("soul", new FakeItemStateStore());
        UUID id = UUID.randomUUID();

        codec.write(stack, new SoulData(id, 42));
        SoulData back = codec.read(stack);
        assertEquals(id, back.gemId());
        assertEquals(42, back.souls());

        codec.write(stack, null); // clearing removes the gem
        assertNull(codec.read(stack));
    }
}
