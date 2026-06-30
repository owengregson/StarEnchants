package compile.load;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The {@code &}/hex colour → RGB mapping that tints the set-equip dust to a set's own colour. */
class ChatColorRgbTest {

    @Test
    void mapsLegacyColourCodes() {
        assertArrayEquals(new int[] {170, 0, 0}, ChatColorRgb.of("&4Supreme")); // dark red
        assertArrayEquals(new int[] {85, 255, 85}, ChatColorRgb.of("&aHoly")); // green
        assertArrayEquals(new int[] {255, 255, 255}, ChatColorRgb.of("&f&lWhite"));
    }

    @Test
    void usesTheFirstColourCodeSkippingFormatCodes() {
        // a leading bold (format) code is skipped; the first COLOUR code wins
        assertArrayEquals(new int[] {0, 0, 170}, ChatColorRgb.of("&l&1Name"));
    }

    @Test
    void parsesAHexCode() {
        assertArrayEquals(new int[] {91, 245, 83}, ChatColorRgb.of("&#5BF553Glow"));
    }

    @Test
    void nullWhenNoColourCode() {
        assertNull(ChatColorRgb.of("Supreme")); // no code at all
        assertNull(ChatColorRgb.of(""));
        assertNull(ChatColorRgb.of(null));
        assertNull(ChatColorRgb.of("&lBold")); // only a format code → not a colour
    }

    @Test
    void isMultiColorDetectsTwoOrMoreDistinctColours() {
        // KOTH's rainbow name → multi-colour (the rainbow equip-dust trigger).
        assertTrue(ChatColorRgb.isMultiColor("&c&lK&6&l.&e&lO&2&l.&b&lT&5&l.&d&lH"));
        assertTrue(ChatColorRgb.isMultiColor("&#5BF553Glow&aLeaf")); // hex + legacy, distinct
        assertFalse(ChatColorRgb.isMultiColor("&4Supreme"));          // one colour
        assertFalse(ChatColorRgb.isMultiColor("&a&lHoly &aLight"));   // same colour twice → not multi
        assertFalse(ChatColorRgb.isMultiColor("&l&nPlain"));          // only format codes
        assertFalse(ChatColorRgb.isMultiColor(null));
    }
}
