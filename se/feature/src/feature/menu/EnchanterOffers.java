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
     * The book price for a tier in XP levels: the tier's explicit {@code cost:} when set (configurable per
     * group/tier in {@code tiers.yml}), else derived from its {@code weight} via {@link #cost(int)}.
     */
    public static int priceFor(TierRegistry.Tier tier) {
        return tier.cost() >= 0 ? tier.cost() : cost(tier.weight());
    }

    /** EXP-level cost from a tier weight (rarer = pricier), at least one level — the fallback when no {@code cost:}. */
    public static int cost(int weight) {
        return Math.max(1, weight / 5);
    }
}
