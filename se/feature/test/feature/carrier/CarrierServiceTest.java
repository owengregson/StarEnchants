package feature.carrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.ItemDef;
import java.util.List;
import org.junit.jupiter.api.Test;
import schema.diag.Source;

/**
 * Pure tests for the carrier service's grant/kind resolution (ADR-0016) — which content key a carrier
 * def confers, and whether a def is a protect scroll. The mint + apply mutation (touching {@code ItemStack})
 * is verified live ({@code CarrierSuite}); these need no server.
 */
class CarrierServiceTest {

    private static ItemDef item(String kind, ItemDef.Grant grant) {
        return new ItemDef("items/" + kind + "/x", "&fX", "", "common", kind, "PAPER", false,
                grant, new ItemDef.Apply(100, false, true, List.of()), Source.ofFile("test"));
    }

    @Test
    void grantKeyResolvesEnchantCrystalSetOrEmpty() {
        assertEquals("enchants/thunder",
                CarrierService.grantKeyOf(item("book", new ItemDef.Grant("enchants/thunder", null, null, 3, null, null))));
        assertEquals("crystals/ember",
                CarrierService.grantKeyOf(item("gem", new ItemDef.Grant(null, "crystals/ember", null, 0, null, null))));
        assertEquals("sets/titan",
                CarrierService.grantKeyOf(item("tome", new ItemDef.Grant(null, null, "sets/titan", 0, null, null))));
        assertEquals("", CarrierService.grantKeyOf(item("scroll", new ItemDef.Grant(null, null, null, 0, null, "PROTECT"))));
        assertEquals("", CarrierService.grantKeyOf(item("dust", null)));
    }

    @Test
    void onlyAProtectScrollIsRecognised() {
        assertTrue(CarrierService.isProtectScroll(item("scroll", new ItemDef.Grant(null, null, null, 0, null, "PROTECT"))));
        assertFalse(CarrierService.isProtectScroll(item("scroll", new ItemDef.Grant(null, null, null, 0, null, "TRANSMOG"))));
        assertFalse(CarrierService.isProtectScroll(item("book", new ItemDef.Grant("enchants/x", null, null, 1, null, null))));
    }
}
