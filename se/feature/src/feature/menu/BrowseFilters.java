package feature.menu;

import compile.load.EnchantDef;
import java.util.ArrayList;
import java.util.List;

/**
 * Catalog tier-grouping for the browse menus (docs/v3-directives.md §K — "tier → enchant"). Server-free so
 * the bucketing / null-tier fallback is unit-tested. The {@code Library} catalog is a flat list with no
 * per-tier index, so the browser groups on demand here.
 */
public final class BrowseFilters {

    private BrowseFilters() {
    }

    /** The effective tier of {@code def}: its declared tier, or {@code defaultTier} when it has none. */
    public static String tierOf(EnchantDef def, String defaultTier) {
        String tier = def.tier();
        return tier == null || tier.isBlank() ? defaultTier : tier;
    }

    /** The enchants whose effective tier equals {@code tier} (case-insensitive), in catalog order. */
    public static List<EnchantDef> enchantsOfTier(List<EnchantDef> catalog, String tier, String defaultTier) {
        List<EnchantDef> out = new ArrayList<>();
        for (EnchantDef def : catalog) {
            if (tierOf(def, defaultTier).equalsIgnoreCase(tier)) {
                out.add(def);
            }
        }
        return out;
    }

    /**
     * The tier names (from {@code tierOrder}, the registry's declared order) that have at least one enchant
     * in {@code catalog}, so the tier-list view shows no empty buckets. An enchant whose tier is not in
     * {@code tierOrder} is bucketed under {@code defaultTier} (defensive — an authored typo never vanishes).
     */
    public static List<String> populatedTiers(List<EnchantDef> catalog, List<String> tierOrder, String defaultTier) {
        List<String> out = new ArrayList<>();
        for (String tier : tierOrder) {
            if (!enchantsOfTier(catalog, tier, defaultTier).isEmpty()) {
                out.add(tier);
            }
        }
        // If the default tier isn't itself a declared tier but caught the untiered enchants, surface it last
        // so a catalog authored without explicit tiers (all bucketed under the default) still shows a bucket.
        boolean defaultListed = tierOrder.stream().anyMatch(t -> t.equalsIgnoreCase(defaultTier));
        if (defaultTier != null && !defaultListed && !enchantsOfTier(catalog, defaultTier, defaultTier).isEmpty()) {
            out.add(defaultTier);
        }
        return out;
    }
}
