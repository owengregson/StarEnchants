package migrate;

import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Minimal typed accessors over a SnakeYAML-parsed legacy document (docs/architecture.md §10). Parsing
 * uses the version-stable {@code Yaml.load → Map/List/scalar} surface (not Bukkit's
 * {@code YamlConfiguration}, whose internal SnakeYAML calls differ across versions), so the importer
 * runs on whatever SnakeYAML the server bundles. All accessors are null-tolerant and never throw on a
 * missing/mistyped key — a legacy config is untrusted input.
 */
final class LegacyYaml {

    private LegacyYaml() {
    }

    /** Parse a YAML document into its root mapping, or an empty map if it is not a mapping. */
    static Map<?, ?> parse(String yaml) {
        LoaderOptions options = new LoaderOptions();
        options.setMaxAliasesForCollections(64);
        Object root = new Yaml(options).load(yaml == null ? "" : yaml);
        return root instanceof Map<?, ?> map ? map : Map.of();
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
