package item.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure encode/decode tests for the carrier PDC payload (ADR-0016, ADR-0019) — the
 * {@code itemKey:grantKey:level[:successBonus]} format round-trips, an empty grant (a protect scroll) is
 * preserved, a dust-accumulated success bonus survives, an old 3-field payload still decodes (with a zero
 * bonus, no migration), and any malformed payload decodes to {@code null} (not a carrier) rather than
 * throwing. The on-{@code ItemStack} read/write + the guard marker are exercised live
 * ({@code CarrierSuite}); these pin the wire format with no server.
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
    void zeroBonusOmitsTheFourthField() {
        // A freshly-minted (un-dusted) carrier encodes byte-for-byte as the original 3-field format.
        assertEquals("book:enchants/x:1", CarrierCodec.encode(new CarrierData("book", "enchants/x", 1, 0)));
    }

    @Test
    void successBonusRoundTripsAsAFourthField() {
        CarrierData dusted = new CarrierData("items/book/zap", "enchants/zap", 2, 25);
        String encoded = CarrierCodec.encode(dusted);
        assertEquals("items/book/zap:enchants/zap:2:25", encoded);
        CarrierData back = CarrierCodec.decode(encoded);
        assertEquals(dusted, back);
        assertEquals(25, back.successBonus());
    }

    @Test
    void legacyThreeFieldDecodesWithZeroBonus() {
        CarrierData back = CarrierCodec.decode("items/book/zap:enchants/zap:2");
        assertEquals(new CarrierData("items/book/zap", "enchants/zap", 2, 0), back);
        assertEquals(0, back.successBonus());
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
        assertNull(CarrierCodec.decode("a:b:c:d"));            // non-numeric bonus in the 4th field
        assertNull(CarrierCodec.decode("a:b:c:d:e"));          // too many fields
        assertNull(CarrierCodec.decode("item:grant:notAnInt"));
        assertNull(CarrierCodec.decode("item:grant:1:notAnInt")); // non-numeric bonus
        assertNull(CarrierCodec.decode(":enchants/x:1"));      // empty itemKey
    }
}
