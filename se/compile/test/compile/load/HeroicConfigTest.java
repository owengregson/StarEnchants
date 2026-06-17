package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pure tests for {@link HeroicConfig} (§F) — the success-range clamp/normalisation, the case-insensitive
 * material-upgrade lookup, and reduction-scope normalisation. No Bukkit.
 */
final class HeroicConfigTest {

    private static HeroicConfig of(int min, int max, String scope, Map<String, String> upgrades) {
        return new HeroicConfig("NETHERITE_SCRAP", "&6Heroic", List.of(), min, max, 0.1, 0.1, 0.25,
                upgrades, scope, "", "");
    }

    @Test
    void successRangeIsClampedAndOrdered() {
        HeroicConfig swapped = of(40, 10, "ENTITY", Map.of());
        assertEquals(10, swapped.successMin(), "min/max reorder when given backwards");
        assertEquals(40, swapped.successMax());

        HeroicConfig outOfRange = of(-5, 200, "ENTITY", Map.of());
        assertEquals(0, outOfRange.successMin());
        assertEquals(100, outOfRange.successMax());
    }

    @Test
    void materialUpgradeLookupIsCaseInsensitive() {
        HeroicConfig cfg = of(5, 15, "entity", Map.of("DIAMOND_SWORD", "NETHERITE_SWORD"));
        assertEquals("NETHERITE_SWORD", cfg.upgradeFor("diamond_sword"));
        assertEquals("NETHERITE_SWORD", cfg.upgradeFor("DIAMOND_SWORD"));
        assertNull(cfg.upgradeFor("DIAMOND_HELMET"), "unmapped material → no upgrade");
        assertNull(cfg.upgradeFor(null));
    }

    @Test
    void reductionScopeIsUppercased() {
        assertEquals("ENTITY", of(5, 15, "entity", Map.of()).reductionScope());
        assertEquals("ALL", of(5, 15, "all", Map.of()).reductionScope());
        assertEquals("ENTITY", of(5, 15, null, Map.of()).reductionScope(), "null → ENTITY default");
    }
}
