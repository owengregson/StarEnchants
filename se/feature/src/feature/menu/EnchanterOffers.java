package feature.menu;

import compile.load.TierRegistry;
import java.util.ArrayList;
import java.util.List;

/**
 * The pure default offer list for the {@link EnchanterMenu} shop — one "mystery book" offer per rarity tier,
 * priced in EXP levels off the tier's GUI {@code weight} (so rarer tiers cost more). Kept server-free and
 * deterministic so the pricing/ordering is unit-tested; the §L {@code menus/} config will later replace this
 * default with authored offers (price, currency, reward) without touching the menu.
 */
public final class EnchanterOffers {

    /** One shop offer: buy a {@code tier} mystery (unopened) book for {@code costLevels} experience levels. */
    public record Offer(String tier, int costLevels) {
    }

    private EnchanterOffers() {
    }

    /** Default offers in tier-declaration order: cost = {@code max(1, weight/5)} EXP levels. */
    public static List<Offer> defaults(TierRegistry tiers) {
        List<Offer> out = new ArrayList<>();
        for (TierRegistry.Tier tier : tiers.tiers()) {
            out.add(new Offer(tier.name(), cost(tier.weight())));
        }
        return out;
    }

    /** EXP-level cost from a tier weight (rarer = pricier), at least one level. */
    public static int cost(int weight) {
        return Math.max(1, weight / 5);
    }
}
