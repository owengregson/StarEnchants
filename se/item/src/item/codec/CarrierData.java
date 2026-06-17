package item.codec;

import java.util.Objects;

/**
 * A carrier item's on-item state (ADR-0016; item-data-model "identity record") — an item (book / scroll
 * / dust / gem) that <em>applies</em> something to OTHER gear. Stored under {@link ItemKeys#carrier()},
 * separate from the {@link CombatState} blob so a carrier never decodes on the combat hot path.
 *
 * @param itemKey    the {@code ItemDef} key it was minted from (e.g. {@code items/book/thunder-book}) —
 *                   the source of the apply mechanics (success/destroy/protect, kind); never {@code null}
 * @param grantKey   the RESOLVED content key it confers (e.g. {@code enchants/thunderstrike}), resolved
 *                   at mint time (so a random-pool book fixes its roll on creation); {@code ""} for a
 *                   carrier that grants no content (e.g. a protect scroll); never {@code null}
 * @param grantLevel the enchant level to grant ({@code 0} for crystals/sets/scrolls)
 */
public record CarrierData(String itemKey, String grantKey, int grantLevel) {

    public CarrierData {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(grantKey, "grantKey");
        grantLevel = Math.max(0, grantLevel);
    }

    /** Whether this carrier confers a content grant (an enchant/crystal/set), vs a pure-mechanic scroll. */
    public boolean grants() {
        return !grantKey.isEmpty();
    }
}
