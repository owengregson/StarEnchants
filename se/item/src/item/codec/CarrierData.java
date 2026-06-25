package item.codec;

import java.util.Objects;

/**
 * A carrier item's on-item state (ADR-0016; ADR-0019; item-data-model "identity record") — an item
 * (book / scroll / dust / gem) that <em>applies</em> something to OTHER gear. Stored under
 * {@link ItemKeys#carrier()}, separate from the {@link CombatState} blob so a carrier never decodes on
 * the combat hot path.
 *
 * @param itemKey      the stable carrier-kind key — one of the {@code CarrierService} sentinels
 *                     {@code "book"} / {@code "dust"} / {@code "white-scroll"} — naming what kind of carrier
 *                     this is (and so its apply mechanics); never {@code null}
 * @param grantKey     the RESOLVED content key it confers (e.g. {@code enchants/thunderstrike}),
 *                     resolved at mint time (so a random-pool book fixes its roll on creation);
 *                     {@code ""} for a carrier that grants no content (a dust / white scroll); never
 *                     {@code null}
 * @param grantLevel   the enchant level to grant ({@code 0} for a dust / white scroll)
 * @param successBonus dual-use, never negative: on an enchant BOOK it is the accumulated dust bonus
 *                     (ADR-0019) — the book's effective success on gear is {@code clamp(base + successBonus)};
 *                     on a DUST it is the FIXED bonus the dust confers ({@code 0} = roll the configured
 *                     {@code [min, max]} range at apply). {@code 0} for a freshly-minted book or white scroll.
 * @param baseSuccess  an explicit base success chance that OVERRIDES the default 100 when {@code >= 0}
 *                     (docs/v3-directives.md §I): an unopened book mints its output with a random success,
 *                     and a randomizer scroll rerolls a book's success in full range — both need a per-item
 *                     base. {@code -1} means "use the default success" (the common case).
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

    public boolean grants() {
        return !grantKey.isEmpty();
    }

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
