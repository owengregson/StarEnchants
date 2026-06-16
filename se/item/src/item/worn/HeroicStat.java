package item.worn;

/**
 * The heroic-armor flat stats, treated as a first-class combat source
 * (docs/architecture.md §5.5, §6). Flat damage and flat reduction feed the
 * {@code DamageFold} (§6.1); durability is a passive item property. All three are
 * pre-summed across worn pieces at equip time, so the hit reads one struct.
 *
 * @param flatDamage    flat damage added on the attack side
 * @param flatReduction flat damage absorbed on the defense side
 * @param durability    flat durability bonus
 */
public record HeroicStat(double flatDamage, double flatReduction, double durability) {

    /** No heroic contribution. */
    public static final HeroicStat NONE = new HeroicStat(0.0, 0.0, 0.0);
}
