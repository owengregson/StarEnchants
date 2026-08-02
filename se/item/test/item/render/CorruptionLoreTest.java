package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import item.render.CorruptionLore.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The holy-scroll corruption stage + line (§I). Stage is a PERCENTAGE of the allowance, so the same thresholds
 * hold at any configured maximum — these pin the boundaries at the shipped max of 7 and at a maximum where the
 * 50% line lands exactly, which is where an off-by-one would hide.
 */
class CorruptionLoreTest {

    private static final String SEMI = "&c&lSEMI CORRUPT (&r&f&n{AMOUNT}&r&7 / {MAX} Holy Protections&r&c&l)";
    private static final String VERY = "&c&lVERY CORRUPT (&r&f&n{AMOUNT}&r&7 / {MAX} Holy Protections&r&c&l)";
    private static final String FULL = "&c&lCORRUPTED (&r&f&n{AMOUNT}&r&7 / {MAX} Holy Protections&r&c&l)";

    @ParameterizedTest(name = "{0}/{1} → {2}")
    @CsvSource({
            // The shipped allowance: 3/7 is 42% (semi), 4/7 is 57% (very), 7/7 exhausts it.
            "0, 7, NONE",
            "1, 7, SEMI",
            "3, 7, SEMI",
            "4, 7, VERY",
            "6, 7, VERY",
            "7, 7, FULL",
            // An even allowance puts a count exactly on the 50% line — it must read VERY, not SEMI.
            "1, 4, SEMI",
            "2, 4, VERY",
            "3, 4, VERY",
            "4, 4, FULL",
            // A single-use allowance has no middle: the first protection spent exhausts it.
            "1, 1, FULL",
    })
    void stageIsAPercentageOfTheAllowance(int count, int max, Stage expected) {
        assertEquals(expected, CorruptionLore.stageOf(count, max));
    }

    @Test
    void aNonPositiveMaximumDisablesCorruptionEntirely() {
        // 0 = unlimited holy scrolls: no line ever renders and no apply is ever refused, however many are spent.
        assertEquals(Stage.NONE, CorruptionLore.stageOf(0, 0));
        assertEquals(Stage.NONE, CorruptionLore.stageOf(99, 0));
        assertEquals(Stage.NONE, CorruptionLore.stageOf(99, -1));
        assertNull(CorruptionLore.line(99, 0, SEMI, VERY, FULL));
    }

    @Test
    void aCountBeyondTheMaximumStaysCorrupted() {
        // Lowering max-protections must retroactively corrupt an item that has already outrun the new allowance,
        // never un-corrupt it back to a middle stage.
        assertEquals(Stage.FULL, CorruptionLore.stageOf(9, 7));
    }

    @Test
    void theLineCarriesTheCountAndAllowanceWithColoursTranslated() {
        String line = CorruptionLore.line(4, 7, SEMI, VERY, FULL);
        assertEquals("§c§lVERY CORRUPT (§r§f§n4§r§7 / 7 Holy Protections§r§c§l)", line);
    }

    @Test
    void blankingOneStagesTemplateHidesOnlyThatStage() {
        assertNull(CorruptionLore.line(1, 7, "", VERY, FULL), "semi blanked → no line at semi");
        assertEquals("§c§lCORRUPTED (§r§f§n7§r§7 / 7 Holy Protections§r§c§l)",
                CorruptionLore.line(7, 7, "", VERY, FULL), "…while the other stages still render");
    }
}
