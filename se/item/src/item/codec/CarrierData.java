package item.codec;

import java.util.Objects;

/**
 * A carrier item's on-item state (ADR-0016; ADR-0019; item-data-model "identity record") — an item
 * (book / scroll / dust / gem) that <em>applies</em> something to OTHER gear. Stored under
 * {@link ItemKeys#carrier()}, separate from the {@link CombatState} blob so a carrier never decodes on
 * the combat hot path.
 *
 * @param itemKey      the {@code ItemDef} key it was minted from (e.g. {@code items/book/thunder-book})
 *                     — the source of the apply mechanics (success/destroy/protect, kind); never
 *                     {@code null}
 * @param grantKey     the RESOLVED content key it confers (e.g. {@code enchants/thunderstrike}),
 *                     resolved at mint time (so a random-pool book fixes its roll on creation);
 *                     {@code ""} for a carrier that grants no content (e.g. a protect scroll); never
 *                     {@code null}
 * @param grantLevel   the enchant level to grant ({@code 0} for crystals/sets/scrolls)
 * @param successBonus the success-chance bonus accumulated on THIS carrier (ADR-0019): combining a dust
 *                     onto an enchant book raises the book's stored bonus, and the book's effective
 *                     success when applied to gear is {@code clamp(def.successChance + successBonus)}.
 *                     {@code 0} for a freshly-minted carrier, a dust, or a scroll. Non-negative.
 */
public record CarrierData(String itemKey, String grantKey, int grantLevel, int successBonus) {

    public CarrierData {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(grantKey, "grantKey");
        grantLevel = Math.max(0, grantLevel);
        successBonus = Math.max(0, successBonus);
    }

    /** A carrier with no accumulated success bonus (the common case — a freshly-minted book/scroll/dust). */
    public CarrierData(String itemKey, String grantKey, int grantLevel) {
        this(itemKey, grantKey, grantLevel, 0);
    }

    /** Whether this carrier confers a content grant (an enchant/crystal/set), vs a pure-mechanic scroll. */
    public boolean grants() {
        return !grantKey.isEmpty();
    }

    /** This carrier with its accumulated success bonus replaced (used when a dust combines onto a book). */
    public CarrierData withSuccessBonus(int bonus) {
        return new CarrierData(itemKey, grantKey, grantLevel, bonus);
    }
}
