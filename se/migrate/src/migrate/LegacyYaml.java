package migrate;

import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Null-tolerant typed accessors over a SnakeYAML-parsed legacy document. Uses the version-stable
 * {@code Yaml.load} surface, not Bukkit's {@code YamlConfiguration} whose internal SnakeYAML calls differ
 * across versions, so the importer runs on whatever SnakeYAML the server bundles.
 */
final class LegacyYaml {

    private LegacyYaml() {
    }

    static Map<?, ?> parse(String yaml) {
        Object root = newYaml().load(yaml == null ? "" : yaml);
        return root instanceof Map<?, ?> map ? map : Map.of();
    }

    /**
     * A SnakeYAML reader, version-tolerant: the 2.x {@code LoaderOptions} form when present, else the no-arg
     * {@code Yaml} on a 1.8-era server whose bundled SnakeYAML lacks it (a {@link LinkageError}). The legacy
     * fork uses the server's SnakeYAML (cross-version). Legacy EE/EA/AE configs are sloppy — duplicate keys
     * parse last-wins on every SnakeYAML (setting it explicitly makes 2.x match the 1.x default the migrator
     * always relied on); a 64-alias cap bounds alias bombs. This spec is kept in lockstep with
     * {@code compile.load.YamlNode.newYaml} via {@code testfx.YamlAcceptance}.
     */
    private static Yaml newYaml() {
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(true);   // last wins on every SnakeYAML, like 1.x
            capAliases(options);                   // alias-bomb cap; inner-guarded (younger API than LoaderOptions)
            return new Yaml(options);
        } catch (LinkageError oldSnakeYaml) {
            return new Yaml();                     // 1.8-era: no LoaderOptions; natively dup-tolerant, no cap
        }
    }

    private static void capAliases(LoaderOptions options) {
        try {
            options.setMaxAliasesForCollections(64);
        } catch (LinkageError preCapSnakeYaml) {
            // 1.18–1.25-era SnakeYAML: dup-key control exists but the cap doesn't — keep the dup-key tolerance
        }
    }

    static Map<?, ?> map(Map<?, ?> parent, String key) {
        return parent != null && parent.get(key) instanceof Map<?, ?> m ? m : null;
    }

    static String string(Map<?, ?> parent, String key, String fallback) {
        Object v = parent == null ? null : parent.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    static boolean has(Map<?, ?> parent, String key) {
        return parent != null && parent.get(key) != null;
    }

    static Integer intOrNull(Map<?, ?> parent, String key) {
        Object v = parent == null ? null : parent.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    static int intOr(Map<?, ?> parent, String key, int fallback) {
        Integer v = intOrNull(parent, key);
        return v == null ? fallback : v;
    }

    static Double doubleOrNull(Map<?, ?> parent, String key) {
        Object v = parent == null ? null : parent.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** A string list (joining a scalar into a one-element list); empty if absent or not a list/scalar. */
    static List<String> stringList(Map<?, ?> parent, String key) {
        Object v = parent == null ? null : parent.get(key);
        if (v instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        return v == null ? List.of() : List.of(String.valueOf(v));
    }
}
