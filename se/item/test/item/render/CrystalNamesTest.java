package item.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link CrystalNames} (§E name join, ADR-0034) — the single source of the crystal item name
 * shared by the mint and the gear renderer. Feeds a TEST-OWNED template + name map and asserts the produced
 * string, so the literals are the test's own input, not a production constant (writing-tests Rule 1). The
 * contract under test is the merged-name colour join: names are separated by the template's LEADING format run
 * + {@code ", "}, so each crystal reads in its own colour.
 */
final class CrystalNamesTest {

    private static final Function<String, String> NAMES = key -> Map.of(
            "flame", "&c&lFlame",
            "chaos", "&4&lChaos",
            "light", "&e&lLight").get(key);

    @Test
    void singleRendersItsOwnStyledName() {
        assertEquals("&6&lArmor Crystal (&c&lFlame&6&l)",
                CrystalNames.render("&6&lArmor Crystal ({CRYSTAL}&6&l)", List.of("flame"), NAMES));
    }

    @Test
    void mergeSeparatesWithTheTemplatesLeadingRunSoEachNameKeepsItsColour() {
        // Template opens "&6&l" → separator "&6&l, "; then Chaos supplies &4&l and Light supplies &e&l.
        assertEquals("&6&lArmor Crystal (&4&lChaos&6&l, &e&lLight&6&l)",
                CrystalNames.render("&6&lArmor Crystal ({CRYSTAL}&6&l)", List.of("chaos", "light"), NAMES));
    }

    @Test
    void plainTextTemplateSeparatesWithABareComma() {
        // No leading format codes → separator "" + ", ".
        assertEquals("&4&lChaos, &e&lLight &7Crystal",
                CrystalNames.render("{CRYSTAL} &7Crystal", List.of("chaos", "light"), NAMES));
    }

    @Test
    void unknownKeyFallsBackToTheRawKey() {
        assertEquals("&6&lArmor Crystal (mystery&6&l)",
                CrystalNames.render("&6&lArmor Crystal ({CRYSTAL}&6&l)", List.of("mystery"), NAMES));
    }
}
