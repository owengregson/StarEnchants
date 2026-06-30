package engine.sink;

import java.util.Locale;
import java.util.Set;
import org.bukkit.potion.PotionEffectType;

/**
 * Canonical potion-effect category sets for selective {@code CURE} ({@code Sink.cureByCategory}). Classification
 * is by the effect's CANONICAL NAME — deliberately NOT {@code PotionEffectType.getCategory()}, which is absent on
 * the 1.17.1 API floor the modern overlay compiles against. Both the pre- and post-1.20.5 spellings are listed
 * (SLOW/SLOWNESS, HARM/INSTANT_DAMAGE, CONFUSION/NAUSEA, SLOW_DIGGING/MINING_FATIGUE) so a live type matches on
 * any version in the range; a name in neither set is NEUTRAL (e.g. GLOWING). Lives in {@code engine.sink} so the
 * Sink + DispatchSink reach it without an {@code engine.effect.kind → engine.sink} package cycle.
 */
public final class PotionCategories {

    private PotionCategories() {
    }

    /** Category codes shared by {@code CURE}'s param lowering and {@code Sink.cureByCategory}. */
    public static final int ALL = 0;
    public static final int HARMFUL = 1;
    public static final int BENEFICIAL = 2;
    public static final int NEUTRAL = 3;

    private static final Set<String> HARMFUL_NAMES = Set.of(
            "SLOW", "SLOWNESS", "SLOW_DIGGING", "MINING_FATIGUE", "HARM", "INSTANT_DAMAGE",
            "CONFUSION", "NAUSEA", "BLINDNESS", "HUNGER", "WEAKNESS", "POISON", "WITHER",
            "LEVITATION", "UNLUCK", "DARKNESS", "BAD_OMEN", "TRIAL_OMEN", "RAID_OMEN",
            "WIND_CHARGED", "WEAVING", "OOZING", "INFESTED");

    private static final Set<String> BENEFICIAL_NAMES = Set.of(
            "SPEED", "FAST_DIGGING", "HASTE", "INCREASE_DAMAGE", "STRENGTH", "HEAL", "INSTANT_HEALTH",
            "JUMP", "JUMP_BOOST", "REGENERATION", "DAMAGE_RESISTANCE", "RESISTANCE", "FIRE_RESISTANCE",
            "WATER_BREATHING", "INVISIBILITY", "NIGHT_VISION", "HEALTH_BOOST", "ABSORPTION",
            "SATURATION", "LUCK", "SLOW_FALLING", "CONDUIT_POWER", "DOLPHINS_GRACE", "HERO_OF_THE_VILLAGE");

    /** The category code of a live potion type by its canonical name (HARMFUL / BENEFICIAL / else NEUTRAL). */
    @SuppressWarnings("deprecation") // getName(): deprecated-not-removed; the one name accessor stable across the range.
    public static int categoryOf(PotionEffectType type) {
        if (type == null) {
            return NEUTRAL;
        }
        String name = type.getName().toUpperCase(Locale.ROOT);
        if (HARMFUL_NAMES.contains(name)) {
            return HARMFUL;
        }
        if (BENEFICIAL_NAMES.contains(name)) {
            return BENEFICIAL;
        }
        return NEUTRAL;
    }

    /** Whether a requested cure category ({@link #ALL} matches everything) applies to a live potion type. */
    public static boolean matches(int category, PotionEffectType type) {
        return category == ALL || categoryOf(type) == category;
    }
}
