package item.codec;

/**
 * The heroic-armour flat stats, treated as a first-class combat source (docs/architecture.md §5.5,
 * §6). Flat damage and flat reduction feed the {@code DamageFold} (§6.1); durability is a passive
 * item property. All three are stored per item (in {@link CombatState}) and pre-summed across worn
 * pieces at equip time, so the hit reads one struct.
 *
 * @param flatDamage    flat damage added on the attack side
 * @param flatReduction flat damage absorbed on the defense side
 * @param durability    flat durability bonus (passive; not a combat-fold term)
 */
public record HeroicStat(double flatDamage, double flatReduction, double durability) {

    /** No heroic contribution. */
    public static final HeroicStat NONE = new HeroicStat(0.0, 0.0, 0.0);

    /** Whether all three stats are zero (the common no-heroic case). */
    public boolean isZero() {
        return flatDamage == 0.0 && flatReduction == 0.0 && durability == 0.0;
    }

    /** This stat plus {@code other}, component-wise (used to pre-sum across worn pieces). */
    public HeroicStat plus(HeroicStat other) {
        return new HeroicStat(flatDamage + other.flatDamage,
                flatReduction + other.flatReduction,
                durability + other.durability);
    }
}
