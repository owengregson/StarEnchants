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
 */
public record HeroicStat(double percentDamage, double percentReduction, double durability) {

    public static final HeroicStat NONE = new HeroicStat(0.0, 0.0, 0.0);

    public boolean isZero() {
        return percentDamage == 0.0 && percentReduction == 0.0 && durability == 0.0;
    }

    public HeroicStat plus(HeroicStat other) {
        return new HeroicStat(percentDamage + other.percentDamage,
                percentReduction + other.percentReduction,
                durability + other.durability);
    }
}
