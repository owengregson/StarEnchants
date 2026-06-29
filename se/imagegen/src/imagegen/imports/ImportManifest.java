package imagegen.imports;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Declares which real plugin items {@code renderImages} should import and render, and from where — the
 * data-driven, path-selectable surface that replaces hand-transcribed fixtures. Read from a YAML file
 * (default {@code se/imagegen/imports.yml}, override {@code -Dse.imagegen.imports=}); a missing or malformed
 * file yields an empty manifest so the hardcoded fixtures still render.
 *
 * <pre>
 * sources:
 *   - root: se/bootstrap/packs-src/cosmic-pack/content   # a content/ tree (enchants/, sets/, tiers.yml)
 *     sets: [spooky, clarity, koth]                       # explicit keys, or "*" for every set in the root
 * </pre>
 *
 * @param sources the content roots to compile, each with its set selection (in declared order)
 */
public record ImportManifest(List<Source> sources) {

    public ImportManifest {
        sources = List.copyOf(sources);
    }

    /**
     * One content tree and which of its sets to render.
     *
     * @param root    a content/ directory, resolved against the {@code renderImages} working dir (repo root)
     * @param allSets {@code true} when {@code sets: "*"} — render every set the root compiles
     * @param setKeys explicit set keys to render when {@code allSets} is false (ignored otherwise)
     */
    public record Source(String root, boolean allSets, List<String> setKeys) {
        public Source {
            setKeys = List.copyOf(setKeys);
        }
    }

    private static final System.Logger LOG = System.getLogger("StarEnchants.ImageGen");

    /** An empty manifest — the safe fallback when no imports file is present. */
    public static ImportManifest empty() {
        return new ImportManifest(List.of());
    }

    /**
     * Parse {@code file}, or return {@link #empty()} (logging why) when it is absent or unreadable. Unknown
     * keys are ignored and a source with no {@code root} is skipped, so a typo degrades to fewer renders
     * rather than failing the run.
     */
    public static ImportManifest load(Path file) {
        if (!Files.isRegularFile(file)) {
            LOG.log(System.Logger.Level.INFO, "no imports manifest at " + file + " — rendering hardcoded fixtures only");
            return empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(new Yaml().load(reader));
        } catch (IOException | RuntimeException bad) {
            LOG.log(System.Logger.Level.WARNING, "failed to read imports manifest " + file + ": " + bad.getMessage());
            return empty();
        }
    }

    private static ImportManifest parse(Object loaded) {
        if (!(loaded instanceof Map<?, ?> root)) {
            return empty();
        }
        List<Source> sources = new ArrayList<>();
        if (root.get("sources") instanceof List<?> rawSources) {
            for (Object rawSource : rawSources) {
                if (rawSource instanceof Map<?, ?> source) {
                    Source parsed = source(source);
                    if (parsed != null) {
                        sources.add(parsed);
                    }
                }
            }
        }
        return new ImportManifest(sources);
    }

    private static Source source(Map<?, ?> source) {
        Object root = source.get("root");
        if (!(root instanceof String rootPath) || rootPath.isBlank()) {
            LOG.log(System.Logger.Level.WARNING, "imports source missing a 'root' — skipping");
            return null;
        }
        Object sets = source.get("sets");
        if (sets instanceof String wildcard && wildcard.trim().equals("*")) {
            return new Source(rootPath.trim(), true, List.of());
        }
        List<String> keys = new ArrayList<>();
        if (sets instanceof List<?> rawKeys) {
            for (Object key : rawKeys) {
                if (key instanceof String s && !s.isBlank()) {
                    keys.add(s.trim());
                }
            }
        }
        return new Source(rootPath.trim(), false, keys);
    }
}
