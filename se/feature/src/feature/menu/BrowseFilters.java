package feature.menu;

import compile.load.EnchantDef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/** Tier-grouping for the browse menus (§K); server-free so the bucketing / null-tier fallback is unit-tested. */
public final class BrowseFilters {

    private BrowseFilters() {
    }

    /**
     * Order enchants by their tier WEIGHT ascending (least-weight tier first), then by key for a stable
     * tie-break — the §K flat-list ordering, so a rarity climb reads top-to-bottom instead of by filename.
     * {@code tierWeight} maps a tier name to its weight (an unregistered tier sorts last via the caller).
     */
    public static Comparator<EnchantDef> byTierWeight(String defaultTier, ToIntFunction<String> tierWeight) {
        return Comparator
                .comparingInt((EnchantDef d) -> tierWeight.applyAsInt(tierOf(d, defaultTier)))
                .thenComparing(WITHIN_TIER);
    }

    /**
     * Ordering WITHIN one rarity tier: group enchants by their applies-kind, then alphabetical by display name —
     * so every {@code applies: sword} enchant sits together, A→Z, mirroring how an item's lore groups them. The
     * applies-key joins the (sorted) applies-to list so identical apply-sets bucket together; the name-key strips
     * legacy colour codes so the sort is on the visible text, not the {@code &7} prefixes.
     */
    public static final Comparator<EnchantDef> WITHIN_TIER =
            Comparator.comparing(BrowseFilters::appliesKey).thenComparing(BrowseFilters::nameKey);

    private static String appliesKey(EnchantDef def) {
        return def.appliesTo().isEmpty() ? ""
                : def.appliesTo().stream().map(s -> s.toLowerCase(Locale.ROOT)).sorted().collect(Collectors.joining(","));
    }

    private static String nameKey(EnchantDef def) {
        String name = def.display() == null || def.display().isBlank() ? def.key() : def.display();
        return name.replaceAll("(?i)&[0-9a-fk-or]", "").toLowerCase(Locale.ROOT);
    }

    /** The effective tier of {@code def}: its declared tier, or {@code defaultTier} when it has none. */
    public static String tierOf(EnchantDef def, String defaultTier) {
        String tier = def.tier();
        return tier == null || tier.isBlank() ? defaultTier : tier;
    }

    /** The enchants whose effective tier equals {@code tier} (case-insensitive), grouped by applies-kind then
     *  alphabetical ({@link #WITHIN_TIER}) — the tier is fixed here, so its weight is uniform. */
    public static List<EnchantDef> enchantsOfTier(List<EnchantDef> catalog, String tier, String defaultTier) {
        List<EnchantDef> out = new ArrayList<>();
        for (EnchantDef def : catalog) {
            if (tierOf(def, defaultTier).equalsIgnoreCase(tier)) {
                out.add(def);
            }
        }
        out.sort(WITHIN_TIER);
        return out;
    }

    /**
     * The {@code tierOrder} tiers with at least one enchant, so the tier-list shows no empty buckets. An
     * enchant whose tier is absent from {@code tierOrder} falls under {@code defaultTier} (no typo vanishes).
     */
    public static List<String> populatedTiers(List<EnchantDef> catalog, List<String> tierOrder, String defaultTier) {
        List<String> out = new ArrayList<>();
        for (String tier : tierOrder) {
            if (!enchantsOfTier(catalog, tier, defaultTier).isEmpty()) {
                out.add(tier);
            }
        }
        // Surface the default tier last when it's undeclared yet caught the untiered enchants.
        boolean defaultListed = tierOrder.stream().anyMatch(t -> t.equalsIgnoreCase(defaultTier));
        if (defaultTier != null && !defaultListed && !enchantsOfTier(catalog, defaultTier, defaultTier).isEmpty()) {
            out.add(defaultTier);
        }
        return out;
    }
}
