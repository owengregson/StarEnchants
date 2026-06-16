package compile.load;

import java.util.ArrayList;
import java.util.List;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Shared field-parsing helpers for the content readers (ADR-0014) — the typed, diagnostic-emitting
 * primitives every source reader ({@link EnchantDefReader}, {@link CrystalDefReader}, and the
 * later set/heroic readers) uses to turn raw {@link YamlNode} text into validated values. Pure;
 * every fault is a {@code file:line:col} diagnostic, never an exception, so one bad field is
 * warned-and-skipped and the rest of the file still loads (§7, §10).
 */
final class ContentParse {

    private ContentParse() {
    }

    /** A {@code [0,100]} activation chance; NaN/out-of-range is a diagnostic, then clamped. */
    static double clampChance(double chance, Source source, Diagnostics diags) {
        // NaN slips past a naive range check (NaN < 0 and NaN > 100 are both false), so guard it
        // explicitly — a NaN activation chance must never reach the runtime.
        if (Double.isNaN(chance) || chance < 0.0 || chance > 100.0) {
            diags.error("load.chance", "chance must be a number in [0,100], got " + chance, source);
            return Double.isNaN(chance) ? 0.0 : Math.max(0.0, Math.min(100.0, chance));
        }
        return chance;
    }

    /** An optional integer field; a non-integer value is a diagnostic and yields {@code fallback}. */
    static int optInt(YamlNode node, String key, int fallback, Diagnostics diags) {
        String raw = node.string(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException bad) {
            diags.error("load.int", "'" + key + "' must be an integer, got '" + raw + "'", node.sourceOf(key));
            return fallback;
        }
    }

    /** An optional double field; a non-number value is a diagnostic and yields {@code fallback}. */
    static double optDouble(YamlNode node, String key, double fallback, Diagnostics diags) {
        String raw = node.string(key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException bad) {
            diags.error("load.double", "'" + key + "' must be a number, got '" + raw + "'", node.sourceOf(key));
            return fallback;
        }
    }

    /** Parse a positive-or-any integer from a raw string, or {@code null} if it is not an integer. */
    static Integer parseInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** {@code null} for an absent or all-whitespace value, else the value verbatim. */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * The {@code effects:} list of {@code node} as lexed {@link EffectLine}s; an empty list is a
     * warning ({@code label} names the owning ability, e.g. {@code level 2 of 'enchants/x'}).
     */
    static List<EffectLine> effectLines(YamlNode node, String label, Diagnostics diags) {
        List<String> raw = node.stringList("effects");
        Source source = node.sourceOf("effects");
        if (raw.isEmpty()) {
            diags.warning("load.effects", label + " declares no effects", source);
        }
        List<EffectLine> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(EffectLine.parse(line, source));
        }
        return out;
    }
}
