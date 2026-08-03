package feature.combat;

import java.util.Locale;
import platform.text.Tokens;

/**
 * The {@code REFLECT} per-hit feedback line: fills {@code {damage}} with the health the reflect actually
 * returned. Numbers render locale-independently and at most two decimals with trailing zeros trimmed — a chat
 * readout, not a locale-formatted one, so {@code 5} stays {@code 5} and {@code 2.505} reads {@code 2.51}.
 */
final class ReflectFeedback {

    private ReflectFeedback() {
    }

    static String fill(String template, double damage) {
        return Tokens.sub(template, "damage", number(damage));
    }

    private static String number(double value) {
        double rounded = Math.round(value * 100.0) / 100.0;
        if (rounded == Math.rint(rounded)) {
            return Long.toString((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.2f", rounded);
        return text.endsWith("0") ? text.substring(0, text.length() - 1) : text;
    }
}
