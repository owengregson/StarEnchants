package compile.load;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Parses the message catalogue. {@link #loadBundledDefaults()} reads the bundled {@code /lang.yml} resource —
 * the ONE canonical source (see {@link Lang#defaults()}); {@link #load(Path)} overlays a user's on-disk
 * {@code lang.yml} on those defaults, so any key present overrides its default and a partial file is valid.
 * Never throws: an absent/unreadable file yields the defaults, a malformed file a diagnostic.
 */
public final class LangLoader {

    /** Classpath location of the shipped catalogue; jar-root, contributed by {@code se/compile/resources/}. */
    private static final String BUNDLED_RESOURCE = "/lang.yml";

    private LangLoader() {
    }

    /**
     * The built-in catalogue: parse the bundled {@code /lang.yml} resource with no overlay. A can't-happen
     * absent/unreadable resource degrades to {@link Lang#empty()} (visible {@code &c<key>?} markers, never a
     * crash) — {@code LangCatalogueDriftTest} asserts the shipped resource is present and complete so this
     * never ships.
     */
    static Lang loadBundledDefaults() {
        try (InputStream in = LangLoader.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) {
                return Lang.empty();
            }
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse("lang.yml", yaml, Map.of(), Map.of());
        } catch (IOException unreadable) {
            return Lang.empty();
        }
    }

    /** A user's on-disk {@code lang.yml} overlaid on {@link Lang#defaults()}; absent/unreadable → the defaults. */
    public static Lang load(Path langFile) {
        Lang defaults = Lang.defaults();
        if (langFile == null || !Files.isRegularFile(langFile)) {
            return defaults;
        }
        String yaml;
        try {
            yaml = Files.readString(langFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Diagnostics diags = new Diagnostics();
            diags.error(DiagCode.E_LANG_IO, "could not read lang.yml: " + e.getMessage(), Source.ofFile("lang.yml"));
            return new Lang(defaults.singles(), defaults.lists(), diags.all());
        }
        return parse("lang.yml", yaml, defaults.singles(), defaults.lists());
    }

    /**
     * Parse {@code yaml} into a {@link Lang}, starting from {@code baseSingles}/{@code baseLists} (empty for the
     * bundled defaults, the defaults for a user overlay). A non-mapping document is a diagnostic; the parse
     * never throws.
     */
    private static Lang parse(String file, String yaml, Map<String, String> baseSingles,
                              Map<String, List<String>> baseLists) {
        Diagnostics diags = new Diagnostics();
        Map<String, String> singles = new LinkedHashMap<>(baseSingles);
        Map<String, List<String>> lists = new LinkedHashMap<>(baseLists);
        YamlNode root = YamlNode.compose(file, yaml, diags);
        if (!root.isMapping()) {
            diags.error(DiagCode.E_LANG_SHAPE, file + " is not a YAML mapping", Source.ofFile(file));
            return new Lang(singles, lists, diags.all());
        }
        walk(root, "", singles, lists);
        return new Lang(singles, lists, diags.all());
    }

    /** Walk {@code node}'s entries, dotting keys under {@code prefix}; scalars → singles, sequences → lists. */
    private static void walk(YamlNode node, String prefix, Map<String, String> singles,
                             Map<String, List<String>> lists) {
        for (YamlNode.Entry entry : node.entries()) {
            String key = prefix + entry.key();
            YamlNode child = entry.value();
            if (child.isMapping()) {
                walk(child, key + ".", singles, lists);
            } else if (child.isScalar()) {
                singles.put(key, child.scalar());
            } else {
                // Multi-line block: read through the parent + raw key (stringList wants the raw key, not dotted).
                List<String> list = node.stringList(entry.key());
                if (!list.isEmpty()) {
                    lists.put(key, list);
                }
            }
        }
    }
}
