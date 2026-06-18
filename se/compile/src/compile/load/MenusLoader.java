package compile.load;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads the top-level {@code menus/} folder into an immutable {@link MenusConfig} (docs/v3-directives.md §L) —
 * one {@code .yml} per GUI, the menu name taken from the file stem ({@code menus/enchanter.yml} →
 * {@code enchanter}). Mirrors {@link ItemsLoader}'s per-file pattern over the package-private {@link YamlNode};
 * reads only the surfaceable layout fields and leaves unset fields absent (the framework falls back to the
 * programmatic default per field). Never throws — a missing folder yields {@link MenusConfig#empty()}, an
 * unreadable/malformed file yields a diagnostic and is skipped.
 */
public final class MenusLoader {

    private MenusLoader() {
    }

    /** Load {@code menus/} (or an empty config when the folder is absent). */
    public static MenusConfig load(Path menusRoot) {
        Diagnostics diags = new Diagnostics();
        if (menusRoot == null || !Files.isDirectory(menusRoot)) {
            return MenusConfig.empty();
        }
        Map<String, MenuLayoutConfig> byMenu = new LinkedHashMap<>();
        for (Path file : configFiles(menusRoot)) {
            String stem = stripExtension(file.getFileName().toString()).toLowerCase(Locale.ROOT);
            String name = "menus/" + file.getFileName();
            String yaml;
            try {
                yaml = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                diags.error("E_MENU_IO", "could not read " + name + ": " + e.getMessage(), Source.ofFile(name));
                continue;
            }
            YamlNode root = YamlNode.compose(name, yaml, diags);
            if (!root.isMapping()) {
                diags.error("E_MENU_SHAPE", name + " is not a YAML mapping", Source.ofFile(name));
                continue;
            }
            if (byMenu.containsKey(stem)) {
                diags.warning("W_MENU_DUP", "more than one menus/ file for '" + stem + "' (" + name
                        + "); keeping the first", root.source());
                continue;
            }
            byMenu.put(stem, new MenuLayoutConfig(
                    optInt(root.string("rows"), name, diags),
                    Optional.ofNullable(blankToNull(root.string("title"))),
                    root.has("filler") ? Optional.of(orEmpty(root.string("filler"))) : Optional.empty(),
                    optInt(root.string("prev-slot"), name, diags),
                    optInt(root.string("next-slot"), name, diags),
                    optInt(root.string("back-slot"), name, diags),
                    optInt(root.string("close-slot"), name, diags)));
        }
        return new MenusConfig(byMenu, diags.all());
    }

    private static OptionalInt optInt(String raw, String file, Diagnostics diags) {
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            diags.warning("W_MENU_NUM", "invalid number '" + raw + "' in " + file, Source.ofFile(file));
            return OptionalInt.empty();
        }
    }

    private static List<Path> configFiles(Path root) {
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }

    /** A filler token; blank/empty is preserved as "" (operator explicitly disabling filler), not nulled. */
    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }
}
