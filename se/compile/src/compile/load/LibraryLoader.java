package compile.load;

import compile.Compiler;
import compile.def.AbilityDef;
import compile.model.Snapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import schema.diag.Diagnostics;
import schema.diag.Source;

/**
 * Loads a whole {@code content/} tree into a {@link Library} (ADR-0014): walk each source-typed
 * directory, read every {@code .yml} into its {@code AbilityDef}s + metadata, then run the injected
 * {@link Compiler} once over all the defs to produce the immutable {@link Snapshot}. Pure — file I/O
 * + SnakeYAML + the existing grammar/compiler — so it is reused verbatim by {@code validateContent}
 * and is fully unit-testable with zero server.
 *
 * <p>Files are visited in sorted order so dense ability ids are assigned deterministically. Every
 * fault (unreadable file, malformed YAML, bad field) is a {@code file:line:col} diagnostic; the load
 * never throws on content, only on a genuine I/O failure walking the tree. The caller inspects
 * {@link Library#hasErrors()} before publishing (transactional reload, §10).
 *
 * <p>This cycle reads {@code enchants/} and {@code crystals/}; {@code sets/}/{@code weapons/}/
 * {@code heroic/} register their own readers when those sources are authored (ADR-0014).
 */
public final class LibraryLoader {

    private static final String YML = ".yml";
    private static final String YAML = ".yaml";

    private LibraryLoader() {
    }

    /**
     * Load {@code contentRoot} into a {@link Library} using {@code compiler}, stamping {@code generation}.
     *
     * @param contentRoot the directory holding {@code enchants/}, …; may not exist (→ an empty library)
     * @param compiler    the wired compiler (effect specs + selectors + resolvers + trigger vocabulary)
     * @param generation  the build counter to stamp into the snapshot (§5.2)
     */
    public static Library load(Path contentRoot, Compiler compiler, int generation) {
        Diagnostics diags = new Diagnostics();
        List<EnchantDef> catalog = new ArrayList<>();
        List<CrystalDef> crystals = new ArrayList<>();
        List<AbilityDef> defs = new ArrayList<>();
        int[] nextDefId = {0};

        // Enchants (per-level abilities) then crystals (one ability each). Each source directory is
        // walked the same way; the reader chosen per directory decides the def + metadata shape.
        for (Path file : sourceFiles(contentRoot, "enchants")) {
            String baseKey = baseKeyOf(contentRoot, file, diags);
            if (baseKey == null) {
                continue; // no name stem (already reported) — don't waste a read/parse on it
            }
            YamlNode root = composeOf(contentRoot, file, diags);
            if (root == null) {
                continue;
            }
            EnchantDefReader.Parsed parsed = EnchantDefReader.read(baseKey, root, () -> nextDefId[0]++, diags);
            if (parsed.def() != null) {
                catalog.add(parsed.def());
            }
            defs.addAll(parsed.abilities());
        }
        for (Path file : sourceFiles(contentRoot, "crystals")) {
            String baseKey = baseKeyOf(contentRoot, file, diags);
            if (baseKey == null) {
                continue;
            }
            YamlNode root = composeOf(contentRoot, file, diags);
            if (root == null) {
                continue;
            }
            CrystalDefReader.Parsed parsed = CrystalDefReader.read(baseKey, root, () -> nextDefId[0]++, diags);
            if (parsed.def() != null) {
                crystals.add(parsed.def());
            }
            defs.addAll(parsed.abilities());
        }

        Snapshot snapshot = compiler.compile(defs, generation, diags);
        return new Library(snapshot, catalog, crystals, diags.all());
    }

    /** The content files under {@code contentRoot/<dir>} in deterministic order, or empty if absent. */
    private static List<Path> sourceFiles(Path contentRoot, String dir) {
        Path sourceDir = contentRoot.resolve(dir);
        return Files.isDirectory(sourceDir) ? listContentFiles(contentRoot, sourceDir) : List.of();
    }

    /** The path-derived base key for a content file, or {@code null} (with a diagnostic) if it has none. */
    private static String baseKeyOf(Path contentRoot, Path file, Diagnostics diags) {
        String label = relativePath(contentRoot, file);   // e.g. enchants/lifesteal.yml
        String baseKey = stripExtension(label);            // e.g. enchants/lifesteal
        if (baseKey.isEmpty() || baseKey.endsWith("/")) {
            diags.error("load.key", "content file has no name stem: " + label, Source.ofFile(label));
            return null;
        }
        return baseKey;
    }

    /** Read + compose a content file into a {@link YamlNode}, or {@code null} (with a diagnostic) on I/O fault. */
    private static YamlNode composeOf(Path contentRoot, Path file, Diagnostics diags) {
        String label = relativePath(contentRoot, file);
        String yaml = readFile(file, label, diags);
        return yaml == null ? null : YamlNode.compose(label, yaml, diags);
    }

    /** The path under {@code contentRoot}, slash-normalised so keys/labels are stable cross-OS. */
    private static String relativePath(Path contentRoot, Path file) {
        return contentRoot.relativize(file).toString().replace('\\', '/');
    }

    /** Strip the exact {@code .yml}/{@code .yaml} suffix (not just the last dot) to get the base key. */
    private static String stripExtension(String relativePath) {
        if (relativePath.endsWith(YAML)) {
            return relativePath.substring(0, relativePath.length() - YAML.length());
        }
        if (relativePath.endsWith(YML)) {
            return relativePath.substring(0, relativePath.length() - YML.length());
        }
        return relativePath;
    }

    private static List<Path> listContentFiles(Path contentRoot, Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .filter(LibraryLoader::isContentFile)
                    // Sort by the normalised relative-path STRING, not Path natural order: Path
                    // ordering is case-sensitivity-dependent (Linux vs macOS/Windows), which would
                    // assign dense ids differently in dev than on a Linux prod server.
                    .sorted(Comparator.comparing(p -> relativePath(contentRoot, p)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("walking content directory " + dir, e);
        }
    }

    private static boolean isContentFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(YML) || name.endsWith(YAML);
    }

    private static String readFile(Path file, String label, Diagnostics diags) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            diags.error("load.io", "could not read content file: " + e.getMessage(), Source.ofFile(label));
            return null;
        }
    }
}
