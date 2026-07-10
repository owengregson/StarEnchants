package feature.menu;

import compile.load.TierRegistry;
import java.util.ArrayList;
import java.util.List;

/** Default {@link EnchanterMenu} offers — one mystery-book per tier, priced off tier weight; §L placeholder. */
public final class EnchanterOffers {

    public record Offer(String tier, int costLevels) {
    }

    private EnchanterOffers() {
    }

    /** One offer per tier, in tier-declaration order. */
    public static List<Offer> defaults(TierRegistry tiers) {
        List<Offer> out = new ArrayList<>();
        for (TierRegistry.Tier tier : tiers.tiers()) {
            out.add(new Offer(tier.name(), priceFor(tier)));
        }
        return out;
    }

    /**
     * The book price for a tier in XP levels — delegates to {@link TierRegistry.Tier#bookCostLevels()} so the
     * Enchanter's charge and the Tinkerer's salvage-refund cap read the identical rule from {@code tiers.yml}.
     */
    public static int priceFor(TierRegistry.Tier tier) {
        return tier.bookCostLevels();
    }

    /**
     * The Enchanter's white/black scroll price in XP levels: the {@code legendary} tier's live book price
     * (the Cosmic Enchants-style shop prices both scrolls level with a legendary book), or the priciest
     * tier's book price for a pack without a {@code legendary} tier. §L placeholder like the book offers.
     */
    public static int scrollPriceLevels(TierRegistry tiers) {
        return scrollPriceLevels(tiers.tier("legendary"), tiers.tiers());
    }

    /** The scroll-price rule on plain tiers (pure — unit-tested without a registry). */
    public static int scrollPriceLevels(TierRegistry.Tier legendary, java.util.Collection<TierRegistry.Tier> all) {
        if (legendary != null) {
            return legendary.bookCostLevels();
        }
        int max = 1;
        for (TierRegistry.Tier tier : all) {
            max = Math.max(max, tier.bookCostLevels());
        }
        return max;
    }
}
