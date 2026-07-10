package tester.suite;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import schema.diag.Diagnostic;
import tester.harness.Harness;

/**
 * Compiles the shipped content live with the real {@link RegistryResolvers} (ADR-0014; §10) — the only place
 * a handle token that does not resolve on THIS server version is caught, run on every matrix target. TWO
 * libraries compile here: the default catalog AND the bundled cosmic pack — the pack is the bigger handle
 * surface, and validating only the default once let the 1.20.5 particle rename wave ship 79 dead cue lines
 * that every matrix target greenlit.
 */
public final class CatalogSuite implements Harness.Scenario {

    private final Plugin plugin;

    public CatalogSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("catalog.compilesCleanWithRealHandles");
        h.expect("catalog.cosmicPackCompilesCleanWithRealHandles");
        h.guard("catalog.compilesCleanWithRealHandles",
                () -> compileClean("content", 60, "default catalog"));
        h.guard("catalog.cosmicPackCompilesCleanWithRealHandles",
                () -> compileClean("pack-cosmic", 400, "cosmic pack"));
    }

    private void compileClean(String bundleRoot, int minAbilities, String label) {
        Path content;
        try {
            content = extract(bundleRoot);
        } catch (IOException e) {
            throw new IllegalStateException("could not extract bundled " + label + ": " + e, e);
        }
        Compiler compiler = ContentCompiler.production(new RegistryResolvers());
        Library library = LibraryLoader.load(content, compiler, 0);
        long errors = library.diagnostics().stream().filter(Diagnostic::blocking).count();
        if (errors > 0) {
            String detail = library.diagnostics().stream().filter(Diagnostic::blocking).limit(8)
                    .map(Diagnostic::toString).collect(Collectors.joining(" | "));
            throw new IllegalStateException(errors + " blocking diagnostic(s) in the " + label + ": " + detail);
        }
        if (library.snapshot().abilityCount() < minAbilities) {
            throw new IllegalStateException(label + " too small — only " + library.snapshot().abilityCount()
                    + " abilities (did the content bundle?)");
        }
        plugin.getLogger().info("[catalog-suite] " + label + " clean: " + library.snapshot().abilityCount()
                + " abilities, " + library.catalog().size() + " enchants, " + library.crystals().size()
                + " crystals, " + library.sets().size() + " sets");
    }

    /** Extract the bundled content set rooted at {@code bundleRoot} ({@code <root>/index.txt} lists the files). */
    private Path extract(String bundleRoot) throws IOException {
        Path root = Files.createTempDirectory("se-catalog-suite").resolve("content");
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream index = loader.getResourceAsStream(bundleRoot + "/index.txt")) {
            if (index == null) {
                throw new IOException(bundleRoot + "/index.txt is not bundled in the tester jar");
            }
            List<String> paths = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))
                    .lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            for (String relative : paths) {
                Path target = root.resolve(relative);
                Files.createDirectories(target.getParent());
                String resource = bundleRoot.equals("content")
                        ? "content/" + relative                 // the default catalog bundles flat
                        : bundleRoot + "/content/" + relative;  // packs bundle under <root>/content/
                try (InputStream file = loader.getResourceAsStream(resource)) {
                    if (file == null) {
                        throw new IOException("missing bundled resource " + resource);
                    }
                    Files.copy(file, target);
                }
            }
        }
        return root;
    }
}
