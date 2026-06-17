package compile.load;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * The per-level value tables of a {@code scale:} block (ADR-0016 §3) — the anti-copy-paste mechanism.
 * Each named token resolves to a value at a given level, so the shared effect lines are written once
 * and only the varying numbers are tabulated. A token value is one of:
 *
 * <ul>
 *   <li>a <strong>constant</strong> scalar — the same value at every level (e.g. {@code burn: 80});</li>
 *   <li>an explicit <strong>level-map</strong> — {@code { 1: 2, 2: 4, 3: 6 }} (non-linear allowed;
 *       a level past the last entry clamps to the nearest lower one);</li>
 *   <li>a <strong>linear</strong> form — {@code { from: 2, step: 2 }} → value at level <i>L</i> is
 *       {@code from + (L-1)*step} (integer-exact, no rounding, no expression grammar).</li>
 * </ul>
 *
 * <p>There is deliberately NO arithmetic/formula language: the project's {@code Expr} grammar has no
 * arithmetic, and inventing one was rejected (ADR-0016). Tokens are substituted into effect/knob
 * values as {@code $token} / {@code ${token}} at compile time (see {@link ContentParse#substitute}).
 */
final class ScaleEnv {

    /** A token's value at a given level. */
    private interface Token extends IntFunction<String> {
    }

    static final ScaleEnv EMPTY = new ScaleEnv(Map.of());

    private final Map<String, Token> tokens;

    private ScaleEnv(Map<String, Token> tokens) {
        this.tokens = tokens;
    }

    boolean has(String name) {
        return tokens.containsKey(name);
    }

    /** Whether this def declares any scale tokens (so a {@code $name} is meant as a reference, not a literal). */
    boolean active() {
        return !tokens.isEmpty();
    }

    /** The token's value at {@code level}, or {@code null} if no such token is declared. */
    String resolve(String name, int level) {
        Token token = tokens.get(name);
        return token == null ? null : token.apply(level);
    }

    /** Read the {@code scale:} mapping of {@code root} (absent → {@link #EMPTY}). */
    static ScaleEnv read(YamlNode root, Diagnostics diags) {
        if (!root.has("scale")) {
            return EMPTY;
        }
        Map<String, Token> tokens = new LinkedHashMap<>();
        for (YamlNode.Entry entry : root.entries("scale")) {
            tokens.put(entry.key(), tokenFor(entry.key(), entry.value(), diags));
        }
        return new ScaleEnv(tokens);
    }

    private static Token tokenFor(String name, YamlNode value, Diagnostics diags) {
        if (value.isScalar()) {
            String constant = value.scalar();
            return level -> constant; // same value at every level
        }
        List<YamlNode.Entry> entries = value.entries();
        boolean linear = entries.stream().anyMatch(e -> e.key().equals("from") || e.key().equals("step"));
        if (linear) {
            Integer from = ContentParse.parseInt(orEmpty(value.string("from")));
            Integer step = ContentParse.parseInt(orEmpty(value.string("step")));
            if (from == null) {
                diags.error("E_SCALE", "scale '" + name + "' linear form needs an integer 'from'", value.source());
                from = 0;
            }
            int base = from;
            int by = step == null ? 0 : step;
            return level -> Integer.toString(base + (level - 1) * by);
        }
        return levelMap(name, entries, value.source(), diags);
    }

    private static Token levelMap(String name, List<YamlNode.Entry> entries, Source source, Diagnostics diags) {
        TreeMap<Integer, String> byLevel = new TreeMap<>();
        for (YamlNode.Entry entry : entries) {
            Integer level = ContentParse.parseInt(entry.key());
            if (level == null || !entry.value().isScalar()) {
                diags.error("E_SCALE",
                        "scale '" + name + "' entry must be '<level>: <value>', got '" + entry.key() + "'",
                        entry.value().source());
                continue;
            }
            byLevel.put(level, entry.value().scalar());
        }
        if (byLevel.isEmpty()) {
            diags.error("E_SCALE", "scale '" + name + "' declares no level values", source);
            return level -> "0";
        }
        return level -> {
            Map.Entry<Integer, String> floor = byLevel.floorEntry(level);
            return floor != null ? floor.getValue() : byLevel.firstEntry().getValue();
        };
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
