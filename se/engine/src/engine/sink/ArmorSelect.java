package engine.sink;

import java.util.Locale;

/**
 * Which worn armour piece(s) a {@code DURABILITY target: armor} op addresses — the codes the {@code DURABILITY}
 * kind maps its {@code select} enum to and {@code Sink.repairArmor}/{@code Sink.damageArmor} read back. The slot
 * codes ARE indices into Bukkit's {@code EntityEquipment.getArmorContents()} order (boots first), so the sink
 * addresses a slot without a second mapping table; every other selector is a negative sentinel.
 */
public final class ArmorSelect {

    private ArmorSelect() {
    }

    /** Boots — index 0 of {@code getArmorContents()}. */
    public static final int BOOTS = 0;

    /** Leggings — index 1 of {@code getArmorContents()}. */
    public static final int LEGGINGS = 1;

    /** Chestplate — index 2 of {@code getArmorContents()}. */
    public static final int CHESTPLATE = 2;

    /** Helmet — index 3 of {@code getArmorContents()}. */
    public static final int HELMET = 3;

    /** Every worn piece — the pre-{@code select} behaviour, and the default. */
    public static final int WHOLE_SET = -1;

    /** The single worn piece with the most durability damage. */
    public static final int MOST_DAMAGED = -2;

    /** The single worn piece with the least durability damage. */
    public static final int LEAST_DAMAGED = -3;

    /** One uniformly-drawn worn piece. */
    public static final int RANDOM_PIECE = -4;

    /** No piece qualified — {@link #pick} returns this when the eligibility filter left no candidate. */
    public static final int NONE = -5;

    /** The code for an authored {@code select} token; an unrecognised token falls back to {@link #WHOLE_SET}. */
    public static int of(String token) {
        return switch (token == null ? "" : token.toLowerCase(Locale.ROOT)) {
            case "slot:helmet" -> HELMET;
            case "slot:chestplate" -> CHESTPLATE;
            case "slot:leggings" -> LEGGINGS;
            case "slot:boots" -> BOOTS;
            case "most-damaged" -> MOST_DAMAGED;
            case "least-damaged" -> LEAST_DAMAGED;
            case "random-piece" -> RANDOM_PIECE;
            default -> WHOLE_SET;
        };
    }

    /**
     * The one armour index {@code select} addresses, or {@link #NONE}. {@code damage} carries each slot's current
     * durability damage in {@code getArmorContents()} order, {@code -1} for a slot that is empty or does not wear.
     * {@code skipUndamaged} drops pristine pieces from the CANDIDATE set, so a scatter pick never lands on gear the
     * op would then leave untouched. {@code roll} is a uniform {@code [0,1)} draw, read only by {@link #RANDOM_PIECE}.
     *
     * <p>{@link #WHOLE_SET} is the caller's fast path and is returned unchanged.
     */
    public static int pick(int select, int[] damage, boolean skipUndamaged, double roll) {
        if (select == WHOLE_SET) {
            return WHOLE_SET;
        }
        if (select >= 0) {
            return eligible(damage, select, skipUndamaged) ? select : NONE;
        }
        if (select == RANDOM_PIECE) {
            int candidates = 0;
            for (int i = 0; i < damage.length; i++) {
                if (eligible(damage, i, skipUndamaged)) {
                    candidates++;
                }
            }
            if (candidates == 0) {
                return NONE;
            }
            int nth = Math.min(candidates - 1, (int) (roll * candidates));
            for (int i = 0; i < damage.length; i++) {
                if (eligible(damage, i, skipUndamaged) && nth-- == 0) {
                    return i;
                }
            }
            return NONE;
        }
        int best = NONE;
        for (int i = 0; i < damage.length; i++) {
            if (!eligible(damage, i, skipUndamaged)) {
                continue;
            }
            boolean better = best == NONE
                    || (select == MOST_DAMAGED ? damage[i] > damage[best] : damage[i] < damage[best]);
            if (better) {
                best = i;
            }
        }
        return best;
    }

    /** Whether slot {@code i} can be addressed at all: it wears, and it is damaged when the filter demands it. */
    private static boolean eligible(int[] damage, int i, boolean skipUndamaged) {
        return i < damage.length && damage[i] >= 0 && (!skipUndamaged || damage[i] > 0);
    }
}
