package feature.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.load.EnchantDef;
import java.util.List;
import org.junit.jupiter.api.Test;
import schema.diag.Source;

/**
 * Pure unit tests for {@link BrowseFilters} — the tier bucketing behind the enchants browser, exercised
 * without a server. There is no per-tier index in the {@code Library}, so the browser groups on demand;
 * these pin the null-tier fallback, case-insensitivity and the empty-bucket suppression.
 */
class BrowseFiltersTest {

    private static EnchantDef enchant(String key, String tier) {
        return new EnchantDef(key, "&b" + key, "", tier, List.of("SWORD"), 1,
                List.of(), List.of(), false, Source.UNKNOWN);
    }

    private static final List<EnchantDef> CATALOG = List.of(
            enchant("enchants/a", "rare"),
            enchant("enchants/b", "rare"),
            enchant("enchants/c", "mythic"),
            enchant("enchants/d", null),     // no tier → effective default
            enchant("enchants/e", ""));      // blank tier → effective default

    private static final List<String> ORDER = List.of("common", "uncommon", "rare", "epic", "legendary", "mythic");

    @Test
    void tierOfFallsBackToDefaultWhenAbsentOrBlank() {
        assertEquals("rare", BrowseFilters.tierOf(enchant("x", "rare"), "common"));
        assertEquals("common", BrowseFilters.tierOf(enchant("x", null), "common"));
        assertEquals("common", BrowseFilters.tierOf(enchant("x", ""), "common"));
    }

    @Test
    void enchantsOfTierFiltersByEffectiveTierCaseInsensitively() {
        assertEquals(2, BrowseFilters.enchantsOfTier(CATALOG, "rare", "common").size());
        assertEquals(2, BrowseFilters.enchantsOfTier(CATALOG, "RARE", "common").size()); // case-insensitive
        assertEquals(1, BrowseFilters.enchantsOfTier(CATALOG, "mythic", "common").size());
        // The two untiered enchants bucket under the default tier.
        assertEquals(2, BrowseFilters.enchantsOfTier(CATALOG, "common", "common").size());
        assertTrue(BrowseFilters.enchantsOfTier(CATALOG, "epic", "common").isEmpty());
    }

    @Test
    void populatedTiersDropEmptyBucketsAndKeepDeclaredOrder() {
        // Only common (the default-bucketed pair), rare and mythic have entries — in declared order.
        assertEquals(List.of("common", "rare", "mythic"),
                BrowseFilters.populatedTiers(CATALOG, ORDER, "common"));
    }

    @Test
    void aDefaultTierNotItselfDeclaredStillSurfacesItsBucket() {
        // tiers.yml lists only rare/mythic, but the catalog has untiered enchants → bucket them under default.
        List<String> sparseOrder = List.of("rare", "mythic");
        assertEquals(List.of("rare", "mythic", "common"),
                BrowseFilters.populatedTiers(CATALOG, sparseOrder, "common"));
    }
}
