package platform.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ColorsTest {

    @Test
    void translatesValidAlternateCodes() {
        assertEquals("§7Hello", Colors.translate("&7Hello"));
        assertEquals("§aGreen §lBold", Colors.translate("&aGreen &lBold"));
        assertEquals("§x§f§f§f§fhex", Colors.translate("&x&f&f&f&fhex"));
    }

    @Test
    void leavesProseAmpersandsAndUnknownCodesAlone() {
        assertEquals("rock & roll", Colors.translate("rock & roll"));
        assertEquals("&z not a code", Colors.translate("&z not a code"));
        assertEquals("trailing &", Colors.translate("trailing &"));
        assertEquals("no codes here", Colors.translate("no codes here"));
    }

    @Test
    void passesNullThrough() {
        assertNull(Colors.translate(null));
    }

    @Test
    void expandsHexTokensToTheSectionXForm() {
        assertEquals("§x§f§f§5§7§3§3hi", Colors.translate("{#FF5733}hi"));
        assertEquals("§x§f§f§5§7§3§3hi", Colors.translate("&{#FF5733}hi")); // the documented &{COLOR} spelling
        assertEquals("§x§f§f§5§7§3§3hi", Colors.translate("&#FF5733hi"));   // the ChatColorRgb spelling
        assertEquals("§x§a§b§c§d§e§fhi", Colors.translate("{#abcdef}hi"));  // lowercase input
        // the sets message-uppercaser folds a token body to caps ({#3fbbd5} → {#3FBBD5}); case-insensitive parse
        assertEquals("§x§3§f§b§b§d§5hi", Colors.translate("{#3FBBD5}hi"));
    }

    @Test
    void hexTokensComposeWithFormatCodes() {
        assertEquals("§x§f§f§0§0§0§0§lBold", Colors.translate("{#FF0000}&lBold"));
        assertEquals("§7grey §x§0§0§f§f§0§0§ngreen", Colors.translate("&7grey {#00FF00}&ngreen"));
    }

    @Test
    void malformedHexTokensPassThroughAsLiterals() {
        assertEquals("{#GG0000}x", Colors.translate("{#GG0000}x"));  // non-hex digits
        assertEquals("{#FFF}x", Colors.translate("{#FFF}x"));        // wrong length
        assertEquals("{#FF5733 x", Colors.translate("{#FF5733 x"));  // no closing brace
        assertEquals("{no token}", Colors.translate("{no token}"));
        assertEquals("§lkeep {braces}", Colors.translate("&lkeep {braces}"));
    }

    @Test
    void nearestLegacyModeDegradesHexToTheClosestOfTheSixteen() {
        // exact palette hits
        assertEquals("§chi", Colors.translate("{#FF5555}hi", Colors.HexMode.NEAREST_LEGACY));
        assertEquals("§0hi", Colors.translate("{#000000}hi", Colors.HexMode.NEAREST_LEGACY));
        assertEquals("§fhi", Colors.translate("{#FFFFFF}hi", Colors.HexMode.NEAREST_LEGACY));
        assertEquals("§6hi", Colors.translate("{#FFAA00}hi", Colors.HexMode.NEAREST_LEGACY));
        // off-palette: Euclidean nearest (hand-computed: #123456 → dark gray, d²=5579)
        assertEquals("§8hi", Colors.translate("{#123456}hi", Colors.HexMode.NEAREST_LEGACY));
        // a tie keeps the first (lowest) code: #550000 is 7225 from both '0' and '4'
        assertEquals("§0hi", Colors.translate("{#550000}hi", Colors.HexMode.NEAREST_LEGACY));
        // an authored §x/&x escape run collapses too (a modern-authored string reaching the 1.8 lane)
        assertEquals("§chi", Colors.translate("&x&F&F&5&5&5&5hi", Colors.HexMode.NEAREST_LEGACY));
        assertEquals("§chi", Colors.translate("§x§f§f§5§5§5§5hi", Colors.HexMode.NEAREST_LEGACY));
        // plain codes are identical in either mode
        assertEquals("§a§lhi", Colors.translate("&a&lhi", Colors.HexMode.NEAREST_LEGACY));
    }
}
