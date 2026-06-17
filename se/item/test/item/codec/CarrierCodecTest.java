package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure encode/decode tests for the carrier PDC payload (ADR-0016) — the {@code itemKey:grantKey:level}
 * format round-trips, an empty grant (a protect scroll) is preserved, and any malformed payload decodes
 * to {@code null} (not a carrier) rather than throwing. The on-{@code ItemStack} read/write + the guard
 * marker are exercised live ({@code CarrierSuite}); these pin the wire format with no server.
 */
class CarrierCodecTest {

    @Test
    void encodeDecodeRoundTrips() {
        CarrierData data = new CarrierData("items/book/thunder-book", "enchants/thunderstrike", 3);
        String encoded = CarrierCodec.encode(data);
        assertEquals("items/book/thunder-book:enchants/thunderstrike:3", encoded);
        assertEquals(data, CarrierCodec.decode(encoded));
    }

    @Test
    void emptyGrantIsPreservedForAScroll() {
        CarrierData scroll = new CarrierData("items/scroll/white", "", 0);
        CarrierData back = CarrierCodec.decode(CarrierCodec.encode(scroll));
        assertEquals(scroll, back);
        assertFalse(back.grants(), "a protect scroll grants no content");
    }

    @Test
    void grantsFlagReflectsTheGrantKey() {
        assertTrue(new CarrierData("book", "enchants/x", 1).grants());
        assertFalse(new CarrierData("book", "", 0).grants());
    }

    @Test
    void malformedPayloadsDecodeToNull() {
        assertNull(CarrierCodec.decode(null));
        assertNull(CarrierCodec.decode(""));
        assertNull(CarrierCodec.decode("only:two"));
        assertNull(CarrierCodec.decode("a:b:c:d"));
        assertNull(CarrierCodec.decode("item:grant:notAnInt"));
        assertNull(CarrierCodec.decode(":enchants/x:1")); // empty itemKey
    }
}
