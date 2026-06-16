package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
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
}
