package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.EnchantDef;
import item.codec.CombatState;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import schema.diag.Source;

/**
 * The pure line-building of {@link LoreRenderer} — verified with no server. The display-name
 * lookup is a plain function, so the rendered (colour-translated) lines are deterministic text.
 */
class LoreRendererTest {

    private static final Function<String, String> NAMES = Map.of(
            "enchants/venom", "Venom",
            "enchants/lifesteal", "&aLifesteal")::get;

    @Test
    void rendersOneOrderedLinePerEnchantWithRomanLevels() {
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("enchants/venom", 3);
        enchants.put("enchants/lifesteal", 1);
        CombatState state = new CombatState(enchants, List.of());

        List<String> lines = new LoreRenderer(LoreStyle.DEFAULT, NAMES).lines(state);

        // enchantColor &7, the (possibly self-coloured) display, then levelColor &f + Roman level.
        assertEquals(List.of("§7Venom §fIII", "§7§aLifesteal §fI"), lines);
    }

    @Test
    void rendersUnknownLabelForAStoredKeyAbsentFromTheCatalog() {
        CombatState state = new CombatState(Map.of("enchants/ghost", 2), List.of());

        List<String> lines = new LoreRenderer(LoreStyle.DEFAULT, NAMES).lines(state);

        assertEquals(List.of("§7§8Unknown Enchant §fII"), lines);
    }

    @Test
    void rendersArabicLevelsWhenTheStyleSaysSo() {
        LoreStyle arabic = new LoreStyle("&7", "&f", "&b", false, "&8Unknown Enchant");
        CombatState state = new CombatState(Map.of("enchants/venom", 5), List.of());

        List<String> lines = new LoreRenderer(arabic, NAMES).lines(state);

        assertEquals(List.of("§7Venom §f5"), lines);
    }

    @Test
    void rendersACrystalLinePerAppliedCrystal() {
        CombatState state = new CombatState(Map.of(), List.of("crystals/power"));

        List<String> lines = new LoreRenderer(LoreStyle.DEFAULT, NAMES).lines(state);

        assertEquals(List.of("§b§8Unknown Enchant"), lines);
    }

    @Test
    void isEmptyForAnItemWithNoCombatState() {
        assertTrue(new LoreRenderer(LoreStyle.DEFAULT, NAMES).lines(CombatState.EMPTY).isEmpty());
    }

    @Test
    void displayNamesLooksUpByKeyAndMissesToNull() {
        Function<String, String> names = LoreRenderer.displayNames(List.of(
                new EnchantDef("enchants/venom", "&2Venom", "poison on hit", List.of("SWORDS"), 4, Source.UNKNOWN)));

        assertEquals("&2Venom", names.apply("enchants/venom"));
        assertNull(names.apply("enchants/missing"));
    }
}
