package platform.text;

import java.util.Locale;

/**
 * Number rendering for CHAT READOUTS — the one convention every player-visible number substituted into a
 * message follows ({@code REFLECT}/{@code DAMAGE_CAP} feedback, the {@code MESSAGE} {@code tokens} bindings).
 * At most two decimals, trailing zeros trimmed, locale-independent: {@code 5} stays {@code 5},
 * {@code 2.505} reads {@code 2.51}, {@code 12.30} reads {@code 12.3}.
 *
 * <p>{@link #chat} deliberately carries NO grouping separator: it lands inside colour-coded combat lines
 * where a comma reads as punctuation. {@link #grouped} is the counterpart for a standing READOUT — an item's
 * own lore, where a five-digit total is read rather than glanced at.
 */
public final class Numbers {

    private Numbers() {
    }

    /**
     * {@code value} as a grouped readout: thousands separated by commas, at most two decimals, trailing zeros
     * trimmed ({@code 56250} reads {@code 56,250}; {@code 1.25} reads {@code 1.25}; {@code 3.0} reads
     * {@code 3}). This is the {@code DecimalFormat("#,###.##")} convention the pet economy's numbers are
     * recorded in (R-QC65) — built on {@link #chat} rather than a {@code DecimalFormat} so the two can never
     * disagree about a decimal, and locale-independent for the same reason {@code chat} is.
     */
    public static String grouped(double value) {
        String text = chat(value);
        int dot = text.indexOf('.');
        int end = dot < 0 ? text.length() : dot;
        int start = text.startsWith("-") ? 1 : 0;
        StringBuilder out = new StringBuilder(text);
        for (int at = end - 3; at > start; at -= 3) {
            out.insert(at, ',');
        }
        return out.toString();
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
