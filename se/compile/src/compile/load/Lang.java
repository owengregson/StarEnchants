package compile.load;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import schema.diag.Diagnostic;

/**
 * Compiled snapshot of {@code lang.yml} (§L) — player-facing messages keyed by dotted key, swapped in the
 * same atomic {@code /se reload} transaction as content. {@code &}→{@code §} translation happens at the
 * Bukkit send boundary, not here. A missing key renders as a visible {@code &c<key>?} marker (never an
 * exception, never a silent blank) so a typo surfaces in-game.
 *
 * <p>{@link #defaults()} is the ONE canonical catalogue: it parses the bundled {@code /lang.yml} resource
 * ({@code se/compile/resources/lang.yml}) — there is no second copy hand-maintained in Java. A user's on-disk
 * {@code lang.yml} overlays those defaults ({@link LangLoader}), so an omitted key falls back to the built-in
 * value; a partial file is valid.
 */
public record Lang(Map<String, String> singles, Map<String, List<String>> lists, List<Diagnostic> diagnostics) {

    public Lang {
        singles = Map.copyOf(singles);
        lists = deepCopy(lists);
        diagnostics = List.copyOf(diagnostics);
    }

    /** An empty catalogue (every lookup is the missing-key marker) — the degenerate fallback. */
    public static Lang empty() {
        return new Lang(Map.of(), Map.of(), List.of());
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::blocking);
    }

    /**
     * The template for {@code key} with {@code {TOKEN}} placeholders substituted from {@code kv} (alternating
     * {@code "TOKEN", value, …}). An unknown key returns the {@code &c<key>?} marker.
     */
    public String format(String key, Object... kv) {
        String template = singles.get(key);
        if (template == null) {
            return "&c" + key + "?";
        }
        return substitute(template, kv);
    }

    /** The multi-line block for {@code key} with placeholders substituted; an unknown key is a one-line marker. */
    public List<String> lines(String key, Object... kv) {
        List<String> template = lists.get(key);
        if (template == null) {
            return List.of("&c" + key + "?");
        }
        List<String> out = new java.util.ArrayList<>(template.size());
        for (String line : template) {
            out.add(substitute(line, kv));
        }
        return out;
    }

    /** Whether {@code key} is a known single-line message. */
    public boolean has(String key) {
        return singles.containsKey(key);
    }

    private static String substitute(String template, Object... kv) {
        if (kv.length == 0 || template.indexOf('{') < 0) {
            return template;
        }
        String out = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return out;
    }

    private static Map<String, List<String>> deepCopy(Map<String, List<String>> in) {
        Map<String, List<String>> out = new LinkedHashMap<>(in.size());
        in.forEach((k, v) -> out.put(k, List.copyOf(v)));
        return Map.copyOf(out);
    }

    /**
     * The built-in English catalogue — the bundled {@code /lang.yml} resource, parsed once. This is the single
     * source of truth for every default message; the shipped resource is also written to the data folder on
     * first boot for owners to edit. Cached (a lazy holder), so the parse runs once, on first use.
     */
    public static Lang defaults() {
        return Defaults.CATALOGUE;
    }

    /** Lazy holder: the bundled resource is parsed on first {@link #defaults()} call, then reused. */
    private static final class Defaults {
        static final Lang CATALOGUE = LangLoader.loadBundledDefaults();
    }
}
