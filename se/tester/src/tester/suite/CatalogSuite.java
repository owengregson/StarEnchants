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
 * Validates the SHIPPED content catalog live (ADR-0014; §10): the whole {@code content/} tree is
 * extracted from the bundled {@code index.txt} manifest and compiled through the production compiler
 * with the REAL cross-version {@link RegistryResolvers}. Structural errors are caught at build time
 * by the bootstrap's {@code CatalogValidationTest}; this is the only place a handle-name typo (a
 * potion/particle/sound/entity token that does not resolve on THIS server version) is caught — and
 * it runs on every matrix target, so a token valid on one version but renamed on another is surfaced.
 */
public final class CatalogSuite implements Harness.Scenario {

    private final Plugin plugin;

    public CatalogSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("catalog.compilesCleanWithRealHandles");
        h.guard("catalog.compilesCleanWithRealHandles", () -> {
            Path content;
            try {
                content = extractCatalog();
            } catch (IOException e) {
                throw new IllegalStateException("could not extract bundled catalog: " + e, e);
            }
            Compiler compiler = ContentCompiler.production(new RegistryResolvers());
            Library library = LibraryLoader.load(content, compiler, 0);
            long errors = library.diagnostics().stream().filter(Diagnostic::blocking).count();
            if (errors > 0) {
                String detail = library.diagnostics().stream().filter(Diagnostic::blocking).limit(8)
                        .map(Diagnostic::toString).collect(Collectors.joining(" | "));
                throw new IllegalStateException(errors + " blocking diagnostic(s) in the catalog: " + detail);
            }
            if (library.snapshot().abilityCount() < 60) {
                throw new IllegalStateException("catalog too small — only " + library.snapshot().abilityCount()
                        + " abilities (did the content bundle?)");
            }
            plugin.getLogger().info("[catalog-suite] clean: " + library.snapshot().abilityCount() + " abilities, "
                    + library.catalog().size() + " enchants, " + library.crystals().size() + " crystals, "
                    + library.sets().size() + " sets");
        });
    }

    /** Extract the bundled {@code content/} resources (per index.txt) to a temp dir; returns the content root. */
    private Path extractCatalog() throws IOException {
        Path root = Files.createTempDirectory("se-catalog-suite").resolve("content");
        ClassLoader loader = getClass().getClassLoader();
        try (InputStream index = loader.getResourceAsStream("content/index.txt")) {
            if (index == null) {
                throw new IOException("content/index.txt is not bundled in the tester jar");
            }
            List<String> paths = new BufferedReader(new InputStreamReader(index, StandardCharsets.UTF_8))
                    .lines().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            for (String relative : paths) {
                Path target = root.resolve(relative);
                Files.createDirectories(target.getParent());
                try (InputStream file = loader.getResourceAsStream("content/" + relative)) {
                    if (file == null) {
                        throw new IOException("missing bundled resource content/" + relative);
                    }
                    Files.copy(file, target);
                }
            }
        }
        return root;
    }
}
