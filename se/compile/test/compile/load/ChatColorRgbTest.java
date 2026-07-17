package compile.load;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void parsesABraceHexToken() {
        assertArrayEquals(new int[] {91, 245, 83}, ChatColorRgb.of("{#5BF553}Glow")); // ADR-0062 spelling
        assertNull(ChatColorRgb.of("{#5BF5}Short"));    // wrong length → not a colour
        assertNull(ChatColorRgb.of("{#GGGGGG}Bad"));    // non-hex → not a colour
    }

    @Test
    void braceTokensCountTowardMultiColour() {
        assertTrue(ChatColorRgb.isMultiColor("{#5BF553}Glow&aLeaf"));                 // hex + legacy, distinct
        assertFalse(ChatColorRgb.isMultiColor("{#5BF553}One&#5bf553Same"));           // same colour, two spellings
    }

    @Test
    void nearestCodeIsEuclideanOverTheSixteenWithFirstCodeTieBreak() {
        assertEquals('c', ChatColorRgb.nearestCode(255, 85, 85));      // exact palette hit → red
        assertEquals('0', ChatColorRgb.nearestCode(0, 0, 0));          // exact → black
        assertEquals('6', ChatColorRgb.nearestCode(255, 170, 0));      // exact → gold
        assertEquals('8', ChatColorRgb.nearestCode(0x12, 0x34, 0x56)); // off-palette → dark gray (d²=5579 beats '0'/'1' 10424)
        assertEquals('0', ChatColorRgb.nearestCode(85, 0, 0));         // equidistant to '0' and '4' (7225) → first code wins
    }
}
