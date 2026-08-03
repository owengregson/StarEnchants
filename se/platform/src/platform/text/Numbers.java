package platform.text;

import java.util.Locale;

/**
 * Number rendering for CHAT READOUTS — the one convention every player-visible number substituted into a
 * message follows ({@code REFLECT}/{@code DAMAGE_CAP} feedback, the {@code MESSAGE} {@code tokens} bindings).
 * At most two decimals, trailing zeros trimmed, locale-independent: {@code 5} stays {@code 5},
 * {@code 2.505} reads {@code 2.51}, {@code 12.30} reads {@code 12.3}.
 *
 * <p>Not a general formatter — there is deliberately no grouping separator, because these land inside
 * colour-coded combat lines where a comma reads as punctuation.
 */
public final class Numbers {

    private Numbers() {
    }

    /** {@code value} as a chat readout: whole numbers render without a decimal point, the rest to at most 2dp. */
    public static String chat(double value) {
        if (!Double.isFinite(value)) {
            return "0"; // a NaN/∞ from a missing fact degrades to 0, never leaks "NaN" into a player's chat
        }
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.2f", rounded);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }
}
