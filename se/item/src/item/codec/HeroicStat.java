package item.codec;

/**
 * Heroic-armour stats (§5.5, §6; §F; ADR-0021): a bounded <em>multiplicative</em> stage applied
 * AFTER the additive damage fold (ADR-0012). {@code percentDamage} scales attacker outgoing,
 * {@code percentReduction} scales defender incoming, {@code durability} is the per-item probability
 * of cancelling an item-damage event.
 *
 * <p>Stored per item in {@link CombatState}; percents are pre-summed across worn pieces at equip so
 * the hit reads one struct, but durability is read from the SPECIFIC item taking damage, not the sum.
 *
 * @param percentDamage    outgoing-damage multiplier contribution as a fraction (e.g. {@code 0.10} = +10%)
 * @param percentReduction incoming-damage reduction as a fraction (e.g. {@code 0.10} = −10%)
 * @param durability       per-item probability {@code [0,1]} of cancelling an item-damage event
 * @param flatDamage       flat OUTGOING damage added on the attack side (§F diamond-equivalence: a weak display
 *                         weapon hits like diamond); folded through the additive flat stage (§6.1)
 * @param flatReduction    flat INCOMING reduction on the defense side (§F: weak display armour resists like
 *                         diamond); subtracted last in the fold (§6.1)
 */
public record HeroicStat(double percentDamage, double percentReduction, double durability,
                         double flatDamage, double flatReduction) {

    public static final HeroicStat NONE = new HeroicStat(0.0, 0.0, 0.0, 0.0, 0.0);

    /** Back-compat constructor for the three original percent/durability stats (no diamond flat deltas). */
    public HeroicStat(double percentDamage, double percentReduction, double durability) {
        this(percentDamage, percentReduction, durability, 0.0, 0.0);
    }

    public boolean isZero() {
        return percentDamage == 0.0 && percentReduction == 0.0 && durability == 0.0
                && flatDamage == 0.0 && flatReduction == 0.0;
    }

    public HeroicStat plus(HeroicStat other) {
        return new HeroicStat(percentDamage + other.percentDamage,
                percentReduction + other.percentReduction,
                durability + other.durability,
                flatDamage + other.flatDamage,
                flatReduction + other.flatReduction);
    }
}
