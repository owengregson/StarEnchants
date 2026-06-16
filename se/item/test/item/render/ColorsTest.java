package item.render;

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
}
