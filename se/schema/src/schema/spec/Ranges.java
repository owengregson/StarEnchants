package schema.spec;

/** The [0,100] percent/chance value domain (§I success rates, activation chances): the clamp and the
 *  clamp-then-order pair rule, stated once instead of hand-spelled per config record. */
public final class Ranges {
    private Ranges() { }
    /** value clamped into [0,100]. */
    public static int clampPercent(int value) { return Math.max(0, Math.min(100, value)); }
    /** value clamped into [0,100]. NaN propagates — callers with NaN inputs guard first (ContentParse). */
    public static double clampPercent(double value) { return Math.max(0.0, Math.min(100.0, value)); }
    /** Both bounds clamped to [0,100], then ordered so min <= max — the config-record pair rule. */
    public static IntRange percentRange(int min, int max) {
        int lo = clampPercent(min);
        int hi = clampPercent(max);
        return new IntRange(Math.min(lo, hi), Math.max(lo, hi));
    }
    public record IntRange(int min, int max) { }
}
