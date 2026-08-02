package engine.sink;

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Whether an active potion effect is PERMANENT — something its holder carries by choice rather than a debuff
 * someone landed on them. A cleanse ({@code CURE category: HARMFUL}) never strips one: a helmet granting
 * permanent mining fatigue is the wearer's own trade-off, and the passive-potion driver would re-apply it on
 * its next refresh anyway, so removing it would buy nothing but a flicker.
 *
 * <p>Two independent tests, deliberately overlapping:
 * <ul>
 *   <li>{@link #maintains} — SE's own permanent-while-worn grants, re-derived from the holder's live worn
 *       state; {@link #NONE} answers "none of mine" and is the default at every non-root construction site;</li>
 *   <li>{@link #permanentDuration(int)} — an effectively infinite remaining duration, which catches another
 *       plugin's permanent grant that SE knows nothing about, and holds even with {@link #NONE} wired.</li>
 * </ul>
 *
 * <p>Rides {@link SinkEnv} as instance wiring rather than a mutable static installer (the
 * {@code movementExemption} rule, ADR-0047 G2-c).
 */
@FunctionalInterface
public interface PermanentPotions {

    /** SE claims nothing — the duration test stands alone (tests, tester suites, any non-root site). */
    PermanentPotions NONE = (target, type) -> false;

    /**
     * At or above this remaining duration an effect counts as permanent. Well above the longest real debuff —
     * vanilla Bad Omen runs 100 minutes (120 000 ticks) and stays cleansable — and well below the 1 000 000
     * ticks the passive driver applies, so a worn grant reads as permanent however long it has been since the
     * driver last refreshed it.
     */
    int PERMANENT_FLOOR_TICKS = 20 * 60 * 60 * 4; // 4 hours

    /** Whether SE maintains {@code type} on {@code target} as a permanent-while-worn grant. */
    boolean maintains(LivingEntity target, PotionEffectType type);

    /**
     * Whether {@code durationTicks} is effectively permanent: the 1.19.4+ infinite marker (negative) or a
     * remaining span past {@link #PERMANENT_FLOOR_TICKS}.
     */
    static boolean permanentDuration(int durationTicks) {
        return durationTicks < 0 || durationTicks >= PERMANENT_FLOOR_TICKS;
    }

    /** Whether {@code effect} on {@code target} must survive a cleanse (a faulty bridge degrades to "cleansable"). */
    default boolean spares(LivingEntity target, PotionEffect effect) {
        if (effect == null) {
            return false;
        }
        if (permanentDuration(effect.getDuration())) {
            return true;
        }
        if (target == null) {
            return false;
        }
        try {
            return maintains(target, effect.getType());
        } catch (RuntimeException faultyBridge) {
            return false;
        }
    }
}
