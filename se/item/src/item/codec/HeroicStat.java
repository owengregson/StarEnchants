package item.codec;

/**
 * The heroic-armour stats, treated as a first-class combat source (docs/architecture.md §5.5, §6;
 * docs/v3-directives.md §F; ADR-0021). Heroic is a PERCENT modifier applied as a distinct bounded
 * <em>multiplicative</em> stage AFTER the additive damage fold (ADR-0012 stays additive): the
 * attacker's {@code percentDamage} scales outgoing damage and the defender's {@code percentReduction}
 * scales incoming damage. {@code durability} is the per-item probability ({@code 0..1}) of cancelling
 * an item-damage event (the heroic "barely-wears" property).
 *
 * <p>All three are stored per item (in {@link CombatState}); the percents are pre-summed across worn
 * pieces at equip time so the hit reads one struct, while durability is read from the SPECIFIC item
 * taking damage (not the sum).
 *
 * @param percentDamage    outgoing-damage multiplier contribution as a fraction (e.g. {@code 0.10} = +10%)
 * @param percentReduction incoming-damage reduction as a fraction (e.g. {@code 0.10} = −10%)
 * @param durability       per-item probability {@code [0,1]} of cancelling an item-damage event
 */
public record HeroicStat(double percentDamage, double percentReduction, double durability) {

    /** No heroic contribution. */
    public static final HeroicStat NONE = new HeroicStat(0.0, 0.0, 0.0);

    /** Whether all three stats are zero (the common no-heroic case). */
    public boolean isZero() {
        return percentDamage == 0.0 && percentReduction == 0.0 && durability == 0.0;
    }

    /** This stat plus {@code other}, component-wise (used to pre-sum across worn pieces). */
    public HeroicStat plus(HeroicStat other) {
        return new HeroicStat(percentDamage + other.percentDamage,
                percentReduction + other.percentReduction,
                durability + other.durability);
    }
}
