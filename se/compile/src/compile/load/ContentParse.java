package compile.load;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
     * The effects under {@code key} of {@code node} as {@link EffectLine}s (ADR-0016 §2–3): each item
     * is a terse {@code "HEAD:arg"} string OR a verbose {@code HEAD: { param: value, who:, wait: }} map.
     * {@code $token} / {@code ${token}} scale references are substituted at {@code level}; a {@code wait:}
     * desugars to a preceding {@code WAIT} line. Both forms produce the same downstream shape.
     */
    static List<EffectLine> effectItems(YamlNode node, String key, int level, ScaleEnv scale, Diagnostics diags) {
        List<EffectLine> out = new ArrayList<>();
        for (YamlNode item : node.items(key)) {
            if (item.isScalar()) {
                out.add(EffectLine.parse(substitute(item.scalar(), level, scale, item.source(), diags), item.source()));
            } else {
                appendVerbose(item, level, scale, out, diags);
            }
        }
        return out;
    }

    /** Lower one verbose effect map ({@code HEAD: { ... }}) into its {@link EffectLine}(s). */
    private static void appendVerbose(YamlNode item, int level, ScaleEnv scale, List<EffectLine> out,
                                      Diagnostics diags) {
        List<YamlNode.Entry> head = item.entries();
        if (head.size() != 1) {
            diags.error("E_EFFECT", "a verbose effect must be a single-key map 'HEAD: { ... }'", item.source());
            return;
        }
        String effectHead = head.get(0).key();
        YamlNode body = head.get(0).value();

        if (effectHead.equalsIgnoreCase("WAIT")) {
            // `- WAIT: 20` — the verbose spelling of a `"WAIT:20"` timing directive.
            String ticks = body.isScalar() ? substitute(body.scalar(), level, scale, body.source(), diags) : null;
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
            String value = substitute(param.value().scalar(), level, scale, param.value().source(), diags);
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

    /**
     * Substitute {@code $token} / {@code ${token}} scale references in {@code raw} at {@code level}
     * (ADR-0016 §3). Backward-compatible with v1 strings that contain a literal {@code $} (money
     * amounts, other plugins' {@code ${...}} placeholders): {@code $$} is an escaped literal {@code $};
     * a {@code $} not followed by an identifier start is a literal {@code $}; and a {@code $name} that
     * is not a declared scale token is only an error when this def actually USES scaling (a non-empty
     * {@code scale:} block) — otherwise it is left verbatim. Returns {@code raw} unchanged when it
     * holds no {@code $}.
     */
    static String substitute(String raw, int level, ScaleEnv scale, Source source, Diagnostics diags) {
        if (raw == null || raw.indexOf('$') < 0) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        int i = 0;
        int n = raw.length();
        while (i < n) {
            char c = raw.charAt(i);
            if (c != '$') {
                out.append(c);
                i++;
                continue;
            }
            if (i + 1 < n && raw.charAt(i + 1) == '$') {
                out.append('$'); // $$ → an escaped literal $
                i += 2;
                continue;
            }
            boolean braced = i + 1 < n && raw.charAt(i + 1) == '{';
            int nameStart = i + (braced ? 2 : 1);
            if (nameStart >= n || !(Character.isLetter(raw.charAt(nameStart)) || raw.charAt(nameStart) == '_')) {
                out.append('$'); // a bare $ (e.g. "$500") — a literal, not a token reference
                i++;
                continue;
            }
            int j = nameStart;
            while (j < n && (Character.isLetterOrDigit(raw.charAt(j)) || raw.charAt(j) == '_' || raw.charAt(j) == '-')) {
                j++;
            }
            String name = raw.substring(nameStart, j);
            int end = braced && j < n && raw.charAt(j) == '}' ? j + 1 : j;
            if (scale.has(name)) {
                String value = scale.resolve(name, level);
                out.append(value == null ? "" : value);
            } else if (scale.active()) {
                // The def declares a scale: block, so $name is meant as a token — an unknown one is a typo.
                diags.error("E_SCALE", "unknown scale token '$" + name + "'", source,
                        "declare '" + name + "' under scale:");
                out.append(raw, i, end);
            } else {
                out.append(raw, i, end); // no scale block → $name is a literal, left verbatim
            }
            i = end;
        }
        return out.toString();
    }

    /** A {@code [0,100]} chance knob that may be a literal, a {@code $token}, or an inline level-map. */
    static double resolveChance(YamlNode node, String key, int level, ScaleEnv scale, Diagnostics diags) {
        String raw = knobValue(node, key, level, scale, diags);
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

    /** An integer knob that may be a literal, a {@code $token}, or an inline level-map; else {@code fallback}. */
    static int resolveInt(YamlNode node, String key, int fallback, int level, ScaleEnv scale, Diagnostics diags) {
        String raw = knobValue(node, key, level, scale, diags);
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

    /** The raw value of a knob at {@code level}: a scalar (with {@code $token} substituted) or an inline level-map. */
    private static String knobValue(YamlNode node, String key, int level, ScaleEnv scale, Diagnostics diags) {
        if (!node.has(key)) {
            return null;
        }
        String scalar = node.string(key);
        if (scalar != null) {
            return substitute(scalar, level, scale, node.sourceOf(key), diags);
        }
        TreeMap<Integer, String> byLevel = new TreeMap<>();
        for (YamlNode.Entry entry : node.entries(key)) {
            Integer lvl = parseInt(entry.key());
            if (lvl == null || !entry.value().isScalar()) {
                diags.error("E_SCALE", "'" + key + "' level-map entry must be '<level>: <value>', got '"
                        + entry.key() + "'", entry.value().source());
                continue;
            }
            byLevel.put(lvl, entry.value().scalar());
        }
        if (byLevel.isEmpty()) {
            return null;
        }
        Map.Entry<Integer, String> floor = byLevel.floorEntry(level);
        return floor != null ? floor.getValue() : byLevel.firstEntry().getValue();
    }
}
