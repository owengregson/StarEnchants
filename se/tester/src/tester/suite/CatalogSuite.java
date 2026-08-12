package tester.suite;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.bukkit.plugin.Plugin;
import platform.resolve.RegistryResolvers;
import schema.diag.Diagnostic;
import tester.harness.Harness;

/**
 * Compiles the shipped content live with the real {@link RegistryResolvers} (ADR-0014; §10) — the only place
 * a handle token that does not resolve on THIS server version is caught, run on every matrix target. TWO
 * libraries compile here: the default catalog AND both bundled packs — a pack is the bigger handle surface,
 * and validating only the default once let the 1.20.5 particle rename wave ship 79 dead cue lines that every
 * matrix target greenlit. cosmic-pack is the sharpest case: the unit-side era gates pin only its sounds and
 * particles against committed constant lists, so its material/entity/attribute/potion/enchantment tokens are
 * resolved for the first time right here, per version.
 */
public final class CatalogSuite implements Harness.Scenario {

    private final Plugin plugin;

    public CatalogSuite(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void accept(Harness h) {
        h.expect("catalog.compilesCleanWithRealHandles");
        h.expect("catalog.signaturePackCompilesCleanWithRealHandles");
        h.expect("catalog.cosmicPackCompilesCleanWithRealHandles");
        h.guard("catalog.compilesCleanWithRealHandles",
                () -> compileClean("content", 60, "default catalog"));
        h.guard("catalog.signaturePackCompilesCleanWithRealHandles",
                () -> compileClean("pack-signature", 400, "signature pack"));
        h.guard("catalog.cosmicPackCompilesCleanWithRealHandles",
                () -> compileClean("pack-cosmic", 1000, "cosmic pack"));
    }

    private void compileClean(String bundleRoot, int minAbilities, String label) {
        Path content;
        try {
            content = BundledContent.extract(bundleRoot);
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
}
