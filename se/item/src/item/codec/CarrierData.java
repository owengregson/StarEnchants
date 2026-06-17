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
 *                     success when applied to gear is {@code clamp(base + successBonus)}.
 *                     {@code 0} for a freshly-minted carrier, a dust, or a scroll. Non-negative.
 * @param baseSuccess  an explicit base success chance that OVERRIDES the def-derived base when
 *                     {@code >= 0} (docs/v3-directives.md §I): an unopened book mints its output book
 *                     with a random success, and a randomizer scroll rerolls a book's success in full
 *                     range — both need a per-item base that the {@code ItemDef} cannot supply.
 *                     {@code -1} means "use the def's success chance" (the common case).
 */
public record CarrierData(String itemKey, String grantKey, int grantLevel, int successBonus, int baseSuccess) {

    public CarrierData {
        Objects.requireNonNull(itemKey, "itemKey");
        Objects.requireNonNull(grantKey, "grantKey");
        grantLevel = Math.max(0, grantLevel);
        successBonus = Math.max(0, successBonus);
        baseSuccess = baseSuccess < 0 ? -1 : Math.min(100, baseSuccess);
    }

    /** A carrier with no accumulated success bonus (the common case — a freshly-minted book/scroll/dust). */
    public CarrierData(String itemKey, String grantKey, int grantLevel) {
        this(itemKey, grantKey, grantLevel, 0, -1);
    }

    /** A carrier with an accumulated success bonus but the def-derived base (the pre-§I 4-field form). */
    public CarrierData(String itemKey, String grantKey, int grantLevel, int successBonus) {
        this(itemKey, grantKey, grantLevel, successBonus, -1);
    }

    /** Whether this carrier confers a content grant (an enchant/crystal/set), vs a pure-mechanic scroll. */
    public boolean grants() {
        return !grantKey.isEmpty();
    }

    /** Whether this carrier carries an explicit base-success override (vs deferring to its def). */
    public boolean hasBaseSuccess() {
        return baseSuccess >= 0;
    }

    /** This carrier with its accumulated success bonus replaced (used when a dust combines onto a book). */
    public CarrierData withSuccessBonus(int bonus) {
        return new CarrierData(itemKey, grantKey, grantLevel, bonus, baseSuccess);
    }

    /** This carrier with an explicit base success and no accumulated bonus (a randomizer-scroll reroll). */
    public CarrierData withBaseSuccess(int base) {
        return new CarrierData(itemKey, grantKey, grantLevel, 0, base);
    }
}
