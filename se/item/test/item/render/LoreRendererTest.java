package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import item.codec.CombatState;
import item.codec.HeroicStat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * The pure line-building of {@link LoreRenderer} — verified with no server. The display-name
 * lookup is a plain function, so the rendered (colour-translated) lines are deterministic text.
 */
class LoreRendererTest {

    private static final Function<String, String> NAMES = Map.of(
            "enchants/venom", "Venom",
            "enchants/lifesteal", "&aLifesteal")::get;

    @Test
    void heroicBodyLineSignsTheKindPercentByWeaponVsArmour() {
        String template = "&6&lHEROIC {TYPE} (&e{+/-}{AMOUNT}% DMG&7)";
        // A weapon (percentDamage > 0) → +outgoing; {TYPE} taken from the kind string.
        assertEquals("§6§lHEROIC SWORD (§e+10% DMG§7)",
                LoreRenderer.heroicBodyLine(new HeroicStat(0.10, 0.0, 0.20), "SWORD", template));
        // Armour (percentReduction only) → -incoming.
        assertEquals("§6§lHEROIC BOOTS (§e-10% DMG§7)",
                LoreRenderer.heroicBodyLine(new HeroicStat(0.0, 0.10, 0.20), "BOOTS", template));
        // Not heroic → no line; a blank template → the plain legacy marker.
        assertNull(LoreRenderer.heroicBodyLine(HeroicStat.NONE, "SWORD", template));
        assertEquals("§6§lHEROIC", LoreRenderer.heroicBodyLine(new HeroicStat(0.10, 0.0, 0.0), "SWORD", ""));
    }

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
    void coloursEachEnchantNameByItsRarityTier() {
        // The wiring supplies a base key -> tier '&'-code lookup; a blank/absent code falls back to
        // the style's universal enchantColor (&7). venom = legendary gold (&6); lifesteal has no tier
        // colour, so its &7 fallback prefixes its own self-coloured (&a) display.
        Function<String, String> tierColors = Map.of(
                "enchants/venom", "&6",
                "enchants/lifesteal", "")::get;
        Map<String, Integer> enchants = new LinkedHashMap<>();
        enchants.put("enchants/venom", 3);
        enchants.put("enchants/lifesteal", 1);
        CombatState state = new CombatState(enchants, List.of());

        List<String> lines = new LoreRenderer(LoreStyle.DEFAULT, NAMES, tierColors).lines(state);

        assertEquals(List.of("§6Venom §fIII", "§7§aLifesteal §fI"), lines);
    }

    @Test
    void levelNumeralInheritsTheTierColourWhenLevelColorIsBlank() {
        // Blank level-color => the level numeral takes the name's (tier) colour, not a fixed one.
        LoreStyle inherit = new LoreStyle("&7", "", "&b", true, "&8Unknown Enchant");
        Function<String, String> tierColors = Map.of("enchants/venom", "&6")::get;
        CombatState state = new CombatState(Map.of("enchants/venom", 3), List.of());

        List<String> lines = new LoreRenderer(inherit, NAMES, tierColors).lines(state);

        assertEquals(List.of("§6Venom §6III"), lines); // name AND level both gold
    }

    @Test
    void blankLevelColorFallsBackToEnchantColorWhenNoTier() {
        // No tier colour + blank level-color => the level uses the universal enchantColor (&7), like the name.
        LoreStyle inherit = new LoreStyle("&7", "", "&b", true, "&8Unknown Enchant");
        CombatState state = new CombatState(Map.of("enchants/venom", 2), List.of());

        List<String> lines = new LoreRenderer(inherit, NAMES).lines(state);

        assertEquals(List.of("§7Venom §7II"), lines);
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
    void armourSetLorePreservesAuthoredBlankSeparatorLines() {
        // The set's authored armour lore carries blank separator lines (between the bonus block and the
        // ability/footer). They MUST survive rendering — the bug was a per-line wrap() that drops an empty line;
        // wrapAll keeps it. Wrap width 0 isolates this from the wrap algorithm (owned by TextWrapTest).
        item.mint.ItemFactory.itemWrapWidth(() -> 0);
        LoreRenderer.SetLore setLore = new LoreRenderer.SetLore() {
            @Override public List<String> armor(String setKey) {
                return List.of("&2&lDRUID SET BONUS", "&2* Deal more damage", "", "&2&lTERRABLENDER",
                        "&7&o(Requires all four.)");
            }

            @Override public List<String> weapon(String setKey) {
                return List.of();
            }
        };
        CombatState state = new CombatState(Map.of(), List.of(), "sets/druid", false);

        List<String> lines = new LoreRenderer(() -> LoreStyle.DEFAULT, NAMES, setLore).lines(state);

        assertEquals(List.of("§2§lDRUID SET BONUS", "§2* Deal more damage", "", "§2§lTERRABLENDER",
                "§7§o(Requires all four.)"), lines);
    }

    @Test
    void rendersTheOrbSlotsLineOnlyWhenSlotsWereAddedBelowTheBody() {
        // §H the orb "Enchantment Slots" line: rendered ONLY when added>0, LAST in the body (so apply() places
        // it below the enchant lines, above the protection/trak lines). Template + base are the test's own input.
        String slotsTemplate = "&a&l{TOTAL} Enchantment Slots &r&7(Orb [&a+{ADDED}&7])";
        LoreRenderer renderer = new LoreRenderer(
                () -> LoreStyle.DEFAULT, NAMES, key -> null, LoreRenderer.SetLore.NONE,
                stack -> List.of(), line -> false,
                () -> null,   // no count suffix (the name-stamp transform is pinned by EnchantCountSuffixTest)
                () -> 9,      // base slots
                () -> slotsTemplate);

        CombatState noOrb = new CombatState(Map.of("enchants/venom", 1), List.of());
        assertEquals(List.of("§7Venom §fI"), renderer.lines(noOrb), "no orb applied -> no slots line");

        CombatState withOrb = new CombatState(Map.of("enchants/venom", 1), List.of()).withAdded(2);
        assertEquals(List.of("§7Venom §fI", "§a§l11 Enchantment Slots §r§7(Orb [§a+2§7])"),
                renderer.lines(withOrb), "orb +2 over base 9 -> total 11, line renders below the body");
    }
}
