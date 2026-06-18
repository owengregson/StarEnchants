package compile.load;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads {@code lang.yml} into an immutable {@link Lang} (docs/v3-directives.md §L). One FILE (like
 * {@link MasterConfigLoader}, unlike the {@code items/} folder): the document is composed with the
 * package-private {@link YamlNode} and walked recursively, flattening nested mappings to dotted keys
 * ({@code command:\n  give:\n    book: "…"} → {@code command.give.book}) and reading sequences into the
 * multi-line block map. The shipped {@link Lang#defaults()} are the base; any key present in the file
 * overrides its default, so a partial {@code lang.yml} is valid (every unset message keeps its default).
 * Never throws — an absent/unreadable file yields {@link Lang#defaults()}, a malformed file a diagnostic.
 */
public final class LangLoader {

    private LangLoader() {
    }

    /** Load {@code lang.yml} overlaid on {@link Lang#defaults()} (or pure defaults if the file is absent). */
    public static Lang load(Path langFile) {
        Diagnostics diags = new Diagnostics();
        Lang defaults = Lang.defaults();
        if (langFile == null || !Files.isRegularFile(langFile)) {
            return defaults;
        }
        String yaml;
        try {
            yaml = Files.readString(langFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diags.error("E_LANG_IO", "could not read lang.yml: " + e.getMessage(), Source.ofFile("lang.yml"));
            return new Lang(defaults.singles(), defaults.lists(), diags.all());
        }
        YamlNode root = YamlNode.compose("lang.yml", yaml, diags);
        if (!root.isMapping()) {
            diags.error("E_LANG_SHAPE", "lang.yml is not a YAML mapping", Source.ofFile("lang.yml"));
            return new Lang(defaults.singles(), defaults.lists(), diags.all());
        }
        Map<String, String> singles = new LinkedHashMap<>(defaults.singles());
        Map<String, List<String>> lists = new LinkedHashMap<>(defaults.lists());
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
                // A sequence (a multi-line block) — read it through the parent + raw key.
                List<String> list = node.stringList(entry.key());
                if (!list.isEmpty()) {
                    lists.put(key, list);
                }
            }
        }
    }
}
