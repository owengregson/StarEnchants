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
}
