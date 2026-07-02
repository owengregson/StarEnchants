package compile.load;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads the top-level {@code menus/} folder into an immutable {@link MenusConfig} — one {@code .yml} per GUI,
 * menu name from the file stem ({@code menus/enchanter.yml} → {@code enchanter}). Unset fields are left absent
 * (the framework falls back per field). Never throws: a missing/unreadable/malformed input is empty or skipped.
 */
public final class MenusLoader {

    private MenusLoader() {
    }

    public static MenusConfig load(Path menusRoot) {
        Diagnostics diags = new Diagnostics();
        if (menusRoot == null || !Files.isDirectory(menusRoot)) {
            return MenusConfig.empty();
        }
        Map<String, MenuLayoutConfig> byMenu = new LinkedHashMap<>();
        for (Path file : configFiles(menusRoot, diags)) {
            String stem = stripExtension(file.getFileName().toString()).toLowerCase(Locale.ROOT);
            String name = "menus/" + file.getFileName();
            String yaml;
            try {
                yaml = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                diags.error(DiagCode.E_MENU_IO, "could not read " + name + ": " + e.getMessage(), Source.ofFile(name));
                continue;
            }
            YamlNode root = YamlNode.compose(name, yaml, diags);
            if (!root.isMapping()) {
                diags.error(DiagCode.E_MENU_SHAPE, name + " is not a YAML mapping", Source.ofFile(name));
                continue;
            }
            if (byMenu.containsKey(stem)) {
                diags.warning(DiagCode.W_MENU_DUP, "more than one menus/ file for '" + stem + "' (" + name
                        + "); keeping the first", root.source());
                continue;
            }
            byMenu.put(stem, new MenuLayoutConfig(
                    optInt(root.string("rows"), name, diags),
                    optStr(root.string("title")),
                    root.has("filler") ? Optional.of(orEmpty(root.string("filler"))) : Optional.empty(),
                    optStr(root.string("frame")),
                    optInt(root.string("prev-slot"), name, diags),
                    optInt(root.string("next-slot"), name, diags),
                    optInt(root.string("back-slot"), name, diags),
                    optInt(root.string("close-slot"), name, diags),
                    optStr(root.string("prev-material")), optStr(root.string("prev-name")),
                    optStr(root.string("next-material")), optStr(root.string("next-name")),
                    optStr(root.string("back-material")), optStr(root.string("back-name")),
                    optStr(root.string("close-material")), optStr(root.string("close-name")),
                    optStr(root.string("info-material")), optStr(root.string("info-name")),
                    optInt(root.string("info-slot"), name, diags)));
        }
        return new MenusConfig(byMenu, diags.all());
    }

    private static OptionalInt optInt(String raw, String file, Diagnostics diags) {
        // Keep the OptionalInt shape (empty-on-malformed is load-bearing: the framework falls back per field),
        // but single-source the parse through ContentParse.
        if (raw == null || raw.isBlank()) {
            return OptionalInt.empty();
        }
        Integer v = ContentParse.parseInt(raw);
        if (v == null) {
            diags.warning(DiagCode.W_MENU_NUM, "invalid number '" + raw + "' in " + file, Source.ofFile(file));
            return OptionalInt.empty();
        }
        return OptionalInt.of(v);
    }

    private static List<Path> configFiles(Path root, Diagnostics diags) {
        try (Stream<Path> stream = Files.list(root)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.endsWith(".yml") || n.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            diags.error(DiagCode.E_MENU_IO, "could not list menus/: " + e.getMessage(), Source.ofFile("menus"));
            return List.of();
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** A non-blank string value as an {@link Optional}, else empty (an omitted/blank key keeps the code default). */
    private static Optional<String> optStr(String v) {
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v);
    }

    /** Preserves blank/empty as "" (operator explicitly disabling filler), not nulled. */
    private static String orEmpty(String v) {
        return v == null ? "" : v;
    }
}
