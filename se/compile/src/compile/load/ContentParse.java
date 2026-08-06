package compile.load;

import compile.def.RebateKnobs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Severity;
import schema.diag.Source;
import schema.spec.ParamType;
import schema.grammar.EffectLine;
import schema.spec.Ranges;

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
            diags.error(DiagCode.E_LOAD_CHANCE, "chance must be a number in [0,100], got " + chance, source);
            return Double.isNaN(chance) ? 0.0 : Ranges.clampPercent(chance);
        }
        return chance;
    }

    /**
     * ADR-0042 numeric/boolean policy, stated once: CONTENT numbers BLOCK (strict entries pass
     * Severity.ERROR + E_LOAD_INT/E_LOAD_DOUBLE); config.yml / items/ / menus/ numbers WARN-AND-FALL-BACK
     * (their loaders pass Severity.WARNING + W_CONFIG_NUM/W_ITEM_NUM/W_MENU_NUM). Booleans use ONE
     * truthy/falsy vocabulary (true/yes/on/1 | false/no/off/0) and warn on anything else. Every loader
     * parses through this family — no private NumberFormatException catch anywhere else in compile.load.
     * An absent (null) value silently yields the fallback; a PRESENT non-parsing value is diagnosed.
     */
    static int intOr(String raw, int fallback, String what, Severity severity, DiagCode code, Source source,
                     Diagnostics diags) {
        if (raw == null) {
            return fallback;
        }
        Integer value = parseInt(raw);
        if (value == null) {
            diags.add(severity, code, label(what) + " must be an integer, got '" + raw + "'"
                    + (severity == Severity.ERROR ? "" : ", using " + fallback), source);
            return fallback;
        }
        return value;
    }

    static double doubleOr(String raw, double fallback, String what, Severity severity, DiagCode code, Source source,
                           Diagnostics diags) {
        if (raw == null) {
            return fallback;
        }
        Double value = parseDouble(raw);
        if (value == null) {
            diags.add(severity, code, label(what) + " must be a number, got '" + raw + "'"
                    + (severity == Severity.ERROR ? "" : ", using " + fallback), source);
            return fallback;
        }
        return value;
    }

    static boolean boolOr(String raw, boolean fallback, String what, DiagCode code, Source source, Diagnostics diags) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String v = raw.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("yes") || v.equals("on") || v.equals("1")) {
            return true;
        }
        if (v.equals("false") || v.equals("no") || v.equals("off") || v.equals("0")) {
            return false;
        }
        diags.add(Severity.WARNING, code, label(what) + " is not a boolean (true/yes/on/1 or false/no/off/0), got '"
                + raw + "', using " + fallback, source);
        return fallback;
    }

    private static String label(String what) {
        return what == null ? "value" : "'" + what + "'";
    }

    /** An optional integer field; a non-integer value is a blocking diagnostic and yields {@code fallback}. */
    static int optInt(YamlNode node, String key, int fallback, Diagnostics diags) {
        return intOr(node.string(key), fallback, key, Severity.ERROR, DiagCode.E_LOAD_INT, node.sourceOf(key), diags);
    }

    /** An optional double field; a non-number value is a blocking diagnostic and yields {@code fallback}. */
    static double optDouble(YamlNode node, String key, double fallback, Diagnostics diags) {
        return doubleOr(node.string(key), fallback, key, Severity.ERROR, DiagCode.E_LOAD_DOUBLE, node.sourceOf(key),
                diags);
    }

    /** Parse a positive-or-any integer from a raw string, or {@code null} if it is not an integer. */
    static Integer parseInt(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException bad) {
            return null;
        }
    }

    /** Parse a number from a raw string, or {@code null} if it is not a number. */
    static Double parseDouble(String raw) {
        try {
            return Double.valueOf(raw.trim());
        } catch (NumberFormatException bad) {
            return null;
        }
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
                diags.warning(DiagCode.W_UNKNOWN_KEY, "unknown key '" + entry.key() + "' (ignored)",
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
            diags.warning(DiagCode.W_TIER_FOLDER_MISMATCH,
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
     * The effects under {@code key} of {@code node} as {@link EffectLine}s: each item is a block
     * {@code HEAD: { param: value, who:, wait: }} map. A {@code wait:} desugars to a preceding
     * {@code WAIT} line. The legacy terse {@code "HEAD:arg:@Selector"} string form is no longer a
     * supported authoring shape — a scalar item is a {@code E_TERSE_EFFECT} diagnostic. (Terse is still
     * read by the migrator when importing AE/EE/EA configs; it is just not authorable in content.)
     */
    static List<EffectLine> effectItems(YamlNode node, String key, Diagnostics diags) {
        List<EffectLine> out = new ArrayList<>();
        for (YamlNode item : node.items(key)) {
            if (item.isScalar()) {
                diags.error(DiagCode.E_TERSE_EFFECT,
                        "terse effect strings are no longer supported; write a block map, e.g."
                                + " - { HEAD: { param: value, who: \"@Selector\" } } — got '" + item.scalar() + "'",
                        item.source());
                continue;
            }
            appendVerbose(item, out, diags);
        }
        return out;
    }

    /** Lower one verbose effect map ({@code HEAD: { ... }}) into its {@link EffectLine}(s). */
    private static void appendVerbose(YamlNode item, List<EffectLine> out, Diagnostics diags) {
        List<YamlNode.Entry> head = item.entries();
        if (head.size() != 1) {
            diags.error(DiagCode.E_EFFECT, "a verbose effect must be a single-key map 'HEAD: { ... }'", item.source());
            return;
        }
        String effectHead = head.get(0).key();
        YamlNode body = head.get(0).value();

        if (effectHead.equalsIgnoreCase("WAIT")) {
            String ticks = body.isScalar() ? body.scalar() : null;
            if (ticks == null) {
                diags.error(DiagCode.E_EFFECT, "WAIT must be written 'WAIT: <ticks>'", item.source());
            } else {
                out.add(EffectLine.waitLine(ticks, item.source()));
            }
            return;
        }
        if (body.isScalar()) {
            diags.error(DiagCode.E_EFFECT, "verbose effect '" + effectHead + "' must be a map of named parameters,"
                    + " e.g. " + effectHead + ": { ... }", item.source());
            return;
        }

        Map<String, String> named = new LinkedHashMap<>();
        String who = null;
        Integer wait = null;
        for (YamlNode.Entry param : body.entries()) {
            String name = param.key();
            String value;
            if (param.value().isScalar()) {
                value = param.value().scalar();
            } else {
                // A nested map is the authored form of an EXPR_MAP param ({ tokens: { souls: "%actor.souls%" } });
                // it flattens to the equivalent flat scalar the param type also accepts. Any other non-scalar
                // (a sequence, a map of maps) is still the plain shape error.
                value = flattenBindings(param.value());
                if (value == null) {
                    diags.error(DiagCode.E_EFFECT, "parameter '" + name + "' of '" + effectHead
                            + "' must be a scalar or a map of name: expression bindings", param.value().source());
                    continue;
                }
            }
            switch (name) {
                case "who" -> who = value;
                case "wait" -> {
                    wait = parseInt(value);
                    if (wait == null || wait < 0) {
                        diags.error(DiagCode.E_EFFECT, "'wait' of '" + effectHead + "' must be a non-negative integer,"
                                + " got '" + value + "'", param.value().source());
                        wait = null;
                    }
                }
                default -> named.put(name, value);
            }
        }
        if (wait != null && wait > 0) {
            out.add(EffectLine.waitLine(String.valueOf(wait), body.source()));
        }
        out.add(EffectLine.verbose(effectHead, 1, named, who, item.source()));
    }

    /**
     * Flatten a {@code { name: expression, … }} block into the flat {@code name=expression; …} token an
     * {@code EXPR_MAP} param also accepts, so the two authored forms are one value by the time typechecking
     * sees them. {@code null} when the node is not a mapping of scalars — the caller reports that shape.
     */
    private static String flattenBindings(YamlNode node) {
        if (!node.isMapping()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (YamlNode.Entry binding : node.entries()) {
            if (!binding.value().isScalar()) {
                return null;
            }
            if (out.length() > 0) {
                out.append(ParamType.EXPR_MAP_ENTRY_SEPARATOR).append(' ');
            }
            out.append(binding.key()).append(ParamType.EXPR_MAP_BINDING_SEPARATOR).append(binding.value().scalar());
        }
        return out.toString();
    }

    /**
     * A chance knob: a constant clamped to {@code [0,100]}, or an expression carried as raw text for the
     * lower stage to compile (evaluated and clamped per activation). {@code 100} when absent.
     */
    record Chance(double constant, String expr) {
        static Chance of(double constant) {
            return new Chance(constant, null);
        }
    }

    /** A {@code [0,100]} chance knob (scalar); {@code 100} when absent. Constants only — see {@link #resolveChanceValue}. */
    static double resolveChance(YamlNode node, String key, Diagnostics diags) {
        return resolveChanceValue(node, key, diags).constant();
    }

    static Chance resolveChanceValue(YamlNode node, String key, Diagnostics diags) {
        String raw = node.has(key) ? node.string(key) : null;
        // An expression can't be range-checked at load, so it skips the clamp here and is clamped at the gate.
        if (raw != null && !raw.isBlank() && ParamType.isExpression(raw.trim())) {
            return new Chance(100.0, raw.trim());
        }
        double chance = doubleOr(raw, 100.0, key, Severity.ERROR,
                DiagCode.E_LOAD_DOUBLE, node.sourceOf(key), diags);
        return Chance.of(clampChance(chance, node.sourceOf(key), diags));
    }

    /** An integer knob (scalar); else {@code fallback}. */
    static int resolveInt(YamlNode node, String key, int fallback, Diagnostics diags) {
        return intOr(node.has(key) ? node.string(key) : null, fallback, key, Severity.ERROR, DiagCode.E_LOAD_INT,
                node.sourceOf(key), diags);
    }

    /** A double knob (scalar); else {@code fallback}. */
    static double resolveDouble(YamlNode node, String key, double fallback, Diagnostics diags) {
        return doubleOr(node.has(key) ? node.string(key) : null, fallback, key, Severity.ERROR,
                DiagCode.E_LOAD_DOUBLE, node.sourceOf(key), diags);
    }

    /** A string-valued knob (scalar); {@code null} when absent. */
    static String resolveString(YamlNode node, String key, Diagnostics diags) {
        return node.has(key) ? node.string(key) : null;
    }

    /** The names of {@link SoulKnobs}' keys, for a reader's allowed-key set. */
    static final Set<String> SOUL_KNOB_KEYS = Set.of("soul-cost-carried", "no-souls-sound", "no-souls-particle");

    private static final String COOLDOWN_SCOPE = "cooldown-scope";

    private static final String COOLDOWN_PER_VICTIM = "cooldown-per-victim";

    private static final String SUPPRESS_TYPE = "suppress-type";

    /** The one RESERVED {@code cooldown-scope} value — the opt-out; anything else is a shared bucket name. */
    private static final String COOLDOWN_SCOPE_NONE = "none";

    /**
     * The ability's ENCHANT cooldown scope. Three forms:
     *
     * <ul>
     *   <li>absent &rarr; {@code defaultScope}, this enchant's own key: its blocks share one bucket;</li>
     *   <li>{@code none} &rarr; {@code null}, which erases to {@code -1} and gate 6 skips outright, so the
     *       ability neither blocks on nor arms the bucket its siblings share (Rocket Escape's FALL
     *       companion, starved by its own launch);</li>
     *   <li>any other name &rarr; a SHARED bucket two enchants can both point at (R-QC57). The scope table is
     *       a free-form intern, and gate 6 keys on the interned id, so two files writing the same name pace
     *       each other — which is the only way to express the measured "one bucket across a grade pair".
     *       Each ability keeps its OWN {@code cooldown:}, so the asymmetric thresholds the matrix records
     *       (15 s on one grade, 30 s on the other) survive the sharing.</li>
     * </ul>
     *
     * <p>A shared name is deliberately NOT namespaced or validated against a registry: there is no scope
     * catalogue to validate against, and a typo produces a private bucket rather than a wrong one — the same
     * failure an un-shared ability already has, and strictly safer than silently joining the wrong pace.
     */
    static String resolveCooldownScope(YamlNode node, String defaultScope, Diagnostics diags) {
        String raw = blankToNull(resolveString(node, COOLDOWN_SCOPE, diags));
        if (raw == null) {
            return defaultScope;
        }
        String trimmed = raw.trim();
        return COOLDOWN_SCOPE_NONE.equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    /**
     * The ability's TYPE suppression scope — the key a {@code SUPPRESS { scope: TYPE }} window matches it by
     * (R-QC3, ADR-0075). Absent is the normal case and returns {@code null}: the erase stage then stamps the
     * ability's combat direction ({@code DEFENSE}/{@code ATTACK}), which is what every defensive file in a
     * library wants without authoring a word. Author it only to put an ability in a type NOTHING about its
     * trigger implies — and note it REPLACES the implicit stamp rather than adding to it, since one interned
     * slot names one type.
     */
    static String resolveSuppressType(YamlNode node, Diagnostics diags) {
        return blankToNull(resolveString(node, SUPPRESS_TYPE, diags));
    }

    /**
     * Whether gate 6 keys this ability's cooldown on the VICTIM instead of the coarse player/mob target bucket
     * — the opt-in a per-target throttle (Thundering Blow) needs, since the coarse bucket lets the first mob in
     * a pack lock out every other. Absent or unparseable is {@code false}, today's shared bucket.
     */
    static boolean resolveCooldownPerVictim(YamlNode node, Diagnostics diags) {
        return boolOr(resolveString(node, COOLDOWN_PER_VICTIM, diags), false, COOLDOWN_PER_VICTIM,
                DiagCode.W_LOAD_BOOL, node.sourceOf(COOLDOWN_PER_VICTIM), diags);
    }

    /**
     * The soul-cost envelope knobs beyond {@code soul-cost}/{@code no-souls-message}: whether the cost may be
     * charged against CARRIED gems outside soul mode, and the cue that rides the "out of souls" notice.
     * Grouped so the seven def readers share one read, not twenty-one.
     */
    record SoulKnobs(boolean carried, String sound, String particle) {

        static final SoulKnobs NONE = new SoulKnobs(false, null, null);
    }

    /** {@link SoulKnobs} read off one node; every knob absent is {@link SoulKnobs#NONE}. */
    static SoulKnobs resolveSoulKnobs(YamlNode node, Diagnostics diags) {
        return resolveSoulKnobs(node, node, node, diags);
    }

    /**
     * {@link SoulKnobs} where each knob resolves against its OWN node — the shape a reader with scoped
     * knob inheritance needs, so an inner {@code soul-cost-carried: false} still beats an outer {@code true}.
     */
    static SoulKnobs resolveSoulKnobs(YamlNode carried, YamlNode sound, YamlNode particle, Diagnostics diags) {
        return new SoulKnobs(
                boolOr(resolveString(carried, "soul-cost-carried", diags), false, "soul-cost-carried",
                        DiagCode.W_LOAD_BOOL, carried.sourceOf("soul-cost-carried"), diags),
                blankToNull(resolveString(sound, "no-souls-sound", diags)),
                blankToNull(resolveString(particle, "no-souls-particle", diags)));
    }

    /** {@code keys} plus the soul envelope (accepted wherever {@code no-souls-message} is) and the cooldown knobs. */
    static Set<String> withEnvelopeKnobs(String... keys) {
        List<String> all = new ArrayList<>(List.of(keys));
        all.addAll(SOUL_KNOB_KEYS);
        return withCooldownScope(all.toArray(new String[0]));
    }

    /**
     * {@code keys} plus the scope-envelope knobs ({@code cooldown-scope}, {@code cooldown-per-victim},
     * {@code suppress-type}) and the chance-rebate envelope — accepted by every reader, soul envelope or not.
     */
    static Set<String> withCooldownScope(String... keys) {
        List<String> all = new ArrayList<>(List.of(keys));
        all.add(COOLDOWN_SCOPE);
        all.add(COOLDOWN_PER_VICTIM);
        all.add(SUPPRESS_TYPE);
        all.addAll(REBATE_KNOB_KEYS);
        return Set.copyOf(all);
    }

    private static final String CHANCE_REBATE = "chance-rebate";

    private static final String CHANCE_REBATE_SCALE = "chance-rebate-scale";

    private static final String BLOCKED_MESSAGE = "blocked-message";

    private static final String BLOCKED_MESSAGE_WHO = "blocked-message-who";

    private static final String BLOCKED_SOUND = "blocked-sound";

    private static final String REBATE_SPENDS_COOLDOWN = "rebate-spends-cooldown";

    /** The names of the {@link RebateKnobs} keys, for a reader's allowed-key set. */
    static final Set<String> REBATE_KNOB_KEYS = Set.of(CHANCE_REBATE, CHANCE_REBATE_SCALE, BLOCKED_MESSAGE,
            BLOCKED_MESSAGE_WHO, BLOCKED_SOUND, REBATE_SPENDS_COOLDOWN);

    /** {@code blocked-message-who}'s two values; anything else is a diagnostic rather than a silent default. */
    private static final String WHO_ACTOR = "actor";

    private static final String WHO_VICTIM = "victim";

    /**
     * The chance-rebate envelope (ADR-0076 part E) read off ONE node — the seven def readers share one read.
     *
     * <p>Two shapes are rejected rather than tolerated, because both ship as content that quietly does nothing:
     * declaring BOTH terms (which of the two prices the roll would be an arbitrary tie-break), and declaring
     * feedback with no term at all (the verdict that would carry it can never be reached).
     */
    static RebateKnobs resolveRebateKnobs(YamlNode node, Diagnostics diags) {
        return resolveRebateKnobs(node, node, node, node, node, node, diags);
    }

    /**
     * {@link RebateKnobs} where each knob resolves against its OWN node — the shape a reader with scoped knob
     * inheritance needs, so an inner {@code rebate-spends-cooldown: false} still beats an outer {@code true}.
     */
    static RebateKnobs resolveRebateKnobs(YamlNode points, YamlNode scale, YamlNode message, YamlNode who,
                                          YamlNode sound, YamlNode spends, Diagnostics diags) {
        String pointsExpr = blankToNull(resolveString(points, CHANCE_REBATE, diags));
        String scaleExpr = blankToNull(resolveString(scale, CHANCE_REBATE_SCALE, diags));
        if (pointsExpr != null && scaleExpr != null) {
            diags.error(DiagCode.E_LOAD_REBATE,
                    "'" + CHANCE_REBATE + "' and '" + CHANCE_REBATE_SCALE + "' are mutually exclusive",
                    points.sourceOf(CHANCE_REBATE),
                    "a rebate is either percentage points off the base chance or a fraction of it, not both");
            scaleExpr = null;
        }
        String line = blankToNull(resolveString(message, BLOCKED_MESSAGE, diags));
        String cue = blankToNull(resolveString(sound, BLOCKED_SOUND, diags));
        boolean spendsCooldown = boolOr(resolveString(spends, REBATE_SPENDS_COOLDOWN, diags), false,
                REBATE_SPENDS_COOLDOWN, DiagCode.W_LOAD_BOOL, spends.sourceOf(REBATE_SPENDS_COOLDOWN), diags);
        RebateKnobs knobs = new RebateKnobs(pointsExpr, scaleExpr, line, rebateMessageToActor(who, diags), cue,
                spendsCooldown);
        if (knobs.authored() && !knobs.hasTerm()) {
            diags.error(DiagCode.E_LOAD_REBATE,
                    "the chance-rebate feedback knobs need a '" + CHANCE_REBATE + "' or '"
                            + CHANCE_REBATE_SCALE + "' to report on", message.sourceOf(BLOCKED_MESSAGE),
                    "gate 8 can only name a blocked roll when the rebate is a declared term");
            return RebateKnobs.NONE;
        }
        return knobs;
    }

    /** {@code blocked-message-who}: the VICTIM by default, since the party a rebate protects is the one told. */
    private static boolean rebateMessageToActor(YamlNode node, Diagnostics diags) {
        String raw = blankToNull(resolveString(node, BLOCKED_MESSAGE_WHO, diags));
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        if (WHO_ACTOR.equalsIgnoreCase(trimmed)) {
            return true;
        }
        if (!WHO_VICTIM.equalsIgnoreCase(trimmed)) {
            diags.error(DiagCode.E_LOAD_REBATE, "unknown '" + BLOCKED_MESSAGE_WHO + "' value '" + trimmed + "'",
                    node.sourceOf(BLOCKED_MESSAGE_WHO), "use '" + WHO_VICTIM + "' or '" + WHO_ACTOR + "'");
        }
        return false;
    }
}
