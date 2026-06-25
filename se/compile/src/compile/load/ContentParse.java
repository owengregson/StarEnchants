package compile.load;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import schema.diag.Diagnostics;
import schema.diag.Source;
import schema.grammar.EffectLine;

/**
 * Shared field-parsing helpers for the content readers (ADR-0014): turn raw {@link YamlNode} text into
 * validated values. Every fault is a {@code file:line:col} diagnostic, never an exception, so one bad
 * field is warned-and-skipped and the rest of the file still loads (§7, §10).
 */
final class ContentParse {

    private ContentParse() {
    }

    /** A {@code [0,100]} activation chance; NaN/out-of-range is a diagnostic, then clamped. */
    static double clampChance(double chance, Source source, Diagnostics diags) {
        // NaN passes a naive range check (NaN < 0 and NaN > 100 are both false); guard it explicitly.
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

    /** Parse {@code raw} as an integer, or {@code fallback} when absent/non-integer. */
    static int parseIntOr(String raw, int fallback) {
        Integer value = parseInt(raw == null ? "" : raw);
        return value == null ? fallback : value;
    }

    /** {@code null} for an absent or all-whitespace value, else the value verbatim. */
    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * Warn ({@code W_UNKNOWN_KEY}, ADR-0016 §5) for each of {@code node}'s own keys not in {@code known},
     * so a typo ({@code triggers:}, {@code max_level:}, {@code cooldwon:}) is diagnosed rather than
     * silently dropped. Recognised-but-misplaced keys still load; only the unknown ones are flagged.
     */
    static void warnUnknownKeys(YamlNode node, Set<String> known, Diagnostics diags) {
        for (YamlNode.Entry entry : node.entries()) {
            if (!known.contains(entry.key())) {
                diags.warning("W_UNKNOWN_KEY", "unknown key '" + entry.key() + "' (ignored)",
                        node.sourceOf(entry.key()));
            }
        }
    }

    /**
     * Resolve a def's tier (ADR-0016 §2): the in-file {@code tier:} wins; otherwise the folder-derived
     * {@code folderTier}. When both are present and differ, the in-file value wins and a
     * {@code W_TIER_FOLDER_MISMATCH} warning names both. May return {@code null} when neither is set.
     */
    static String resolveTier(String folderTier, YamlNode root, Diagnostics diags) {
        String inFile = blankToNull(root.string("tier"));
        if (inFile != null && folderTier != null && !inFile.equals(folderTier)) {
            diags.warning("W_TIER_FOLDER_MISMATCH",
                    "in-file tier '" + inFile + "' differs from the folder tier '" + folderTier
                            + "'; using '" + inFile + "'", root.sourceOf("tier"));
        }
        return inFile != null ? inFile : folderTier;
    }

    /**
     * The description as one string (ADR-0016): a scalar verbatim, OR a list of strings joined with
     * {@code \n} (so multi-line lore reads naturally in YAML), or {@code null} when absent/blank.
     */
    static String descriptionOf(YamlNode root) {
        String scalar = root.string("description");
        if (scalar != null) {
            return blankToNull(scalar);
        }
        List<String> lines = root.stringList("description");
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    /**
     * The effects under {@code key} of {@code node} as {@link EffectLine}s: each item is a terse
     * {@code "HEAD:arg"} string OR a verbose {@code HEAD: { param: value, who:, wait: }} map. A
     * {@code wait:} desugars to a preceding {@code WAIT} line. (A literal {@code $} in a value is
     * preserved verbatim — there is no scale/token grammar.)
     */
    static List<EffectLine> effectItems(YamlNode node, String key, Diagnostics diags) {
        List<EffectLine> out = new ArrayList<>();
        for (YamlNode item : node.items(key)) {
            if (item.isScalar()) {
                out.add(EffectLine.parse(item.scalar(), item.source()));
            } else {
                appendVerbose(item, out, diags);
            }
        }
        return out;
    }

    /** Lower one verbose effect map ({@code HEAD: { ... }}) into its {@link EffectLine}(s). */
    private static void appendVerbose(YamlNode item, List<EffectLine> out, Diagnostics diags) {
        List<YamlNode.Entry> head = item.entries();
        if (head.size() != 1) {
            diags.error("E_EFFECT", "a verbose effect must be a single-key map 'HEAD: { ... }'", item.source());
            return;
        }
        String effectHead = head.get(0).key();
        YamlNode body = head.get(0).value();

        if (effectHead.equalsIgnoreCase("WAIT")) {
            String ticks = body.isScalar() ? body.scalar() : null;
            if (ticks == null) {
                diags.error("E_EFFECT", "WAIT must be written 'WAIT: <ticks>'", item.source());
            } else {
                out.add(EffectLine.parse("WAIT:" + ticks, item.source()));
            }
            return;
        }
        if (body.isScalar()) {
            diags.error("E_EFFECT", "verbose effect '" + effectHead + "' must be a map of named parameters,"
                    + " e.g. " + effectHead + ": { ... }", item.source());
            return;
        }

        Map<String, String> named = new LinkedHashMap<>();
        String who = null;
        Integer wait = null;
        for (YamlNode.Entry param : body.entries()) {
            String name = param.key();
            if (!param.value().isScalar()) {
                diags.error("E_EFFECT", "parameter '" + name + "' of '" + effectHead + "' must be a scalar",
                        param.value().source());
                continue;
            }
            String value = param.value().scalar();
            switch (name) {
                case "who" -> who = value;
                case "wait" -> {
                    wait = parseInt(value);
                    if (wait == null || wait < 0) {
                        diags.error("E_EFFECT", "'wait' of '" + effectHead + "' must be a non-negative integer,"
                                + " got '" + value + "'", param.value().source());
                        wait = null;
                    }
                }
                default -> named.put(name, value);
            }
        }
        if (wait != null && wait > 0) {
            out.add(EffectLine.parse("WAIT:" + wait, body.source()));
        }
        out.add(EffectLine.verbose(effectHead, 1, named, who, item.source()));
    }

    /** A {@code [0,100]} chance knob (scalar); {@code 100} when absent. */
    static double resolveChance(YamlNode node, String key, Diagnostics diags) {
        String raw = node.has(key) ? node.string(key) : null;
        if (raw == null) {
            return 100.0;
        }
        double chance;
        try {
            chance = Double.parseDouble(raw.trim());
        } catch (NumberFormatException bad) {
            diags.error("load.double", "'" + key + "' must be a number, got '" + raw + "'", node.sourceOf(key));
            return 100.0;
        }
        return clampChance(chance, node.sourceOf(key), diags);
    }

    /** An integer knob (scalar); else {@code fallback}. */
    static int resolveInt(YamlNode node, String key, int fallback, Diagnostics diags) {
        String raw = node.has(key) ? node.string(key) : null;
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

    /** A string-valued knob (scalar); {@code null} when absent. */
    static String resolveString(YamlNode node, String key, Diagnostics diags) {
        return node.has(key) ? node.string(key) : null;
    }
}
