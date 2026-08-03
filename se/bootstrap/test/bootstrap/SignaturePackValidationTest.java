package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.ItemsConfig;
import compile.load.ItemsLoader;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.load.MasterConfig;
import compile.load.MasterConfigLoader;
import compile.load.MenusConfig;
import compile.load.MenusLoader;
import engine.boot.ContentCompiler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import schema.diag.Diagnostic;

/**
 * The shipped {@code signature-pack} config pack (ADR-0023) must compile clean through the real
 * registries, like {@link CatalogValidationTest} guards the default catalog — so a broken pack port can
 * never ship.
 *
 * <p>Unlike the default-catalog test, handle tokens here resolve <em>strictly</em>, through
 * {@link FloorStrictResolvers}: this is what turns "the port loaded a token no server has" (e.g. the
 * {@code BLEED} particle, the pre-flattening {@code ENDERDRAGON_GROWL} sound) from a silent runtime
 * {@code E_UNKNOWN_HANDLE} on every enchant into an offline build failure.
 */
class SignaturePackValidationTest {

    private static final Path PACK = Path.of("packs-src/signature-pack");

    @Test
    void signaturePackContentCompilesClean() {
        Path content = PACK.resolve("content");
        assertTrue(Files.isDirectory(content), "Signature pack content not found from " + Path.of("").toAbsolutePath());

        Compiler compiler = ContentCompiler.production(FloorStrictResolvers.INSTANCE);
        Library library = LibraryLoader.load(content, compiler, 0);

        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "Signature pack content has blocking diagnostics:\n  " + blocking);
        // 122 enchants × multiple levels — guard against a silent empty/partial load.
        assertTrue(library.snapshot().abilityCount() > 400,
                () -> "expected the full EE catalog, got " + library.snapshot().abilityCount() + " abilities");
    }

    @Test
    void signaturePackItemsLoadClean() {
        Path items = PACK.resolve("items");
        assertTrue(Files.isDirectory(items), "Signature pack items not found");
        ItemsConfig config = ItemsLoader.load(items);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Signature pack items have blocking diagnostics:\n  " + errors);
        assertTrue(config.soulGem().isPresent(), "the Signature pack should carry a soul-gem likeness");
    }

    @Test
    void signaturePackMenusLoadClean() {
        Path menus = PACK.resolve("menus");
        assertTrue(Files.isDirectory(menus), "Signature pack menus not found");
        MenusConfig config = MenusLoader.load(menus);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Signature pack menus have blocking diagnostics:\n  " + errors);
    }

    @Test
    void signaturePackMasterConfigLoadsClean() {
        Path configFile = PACK.resolve("config.yml");
        assertTrue(Files.isRegularFile(configFile), "Signature pack config.yml not found");
        MasterConfig master = MasterConfigLoader.load(configFile);
        String errors = master.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(master.hasErrors(), () -> "Signature pack config.yml has blocking diagnostics:\n  " + errors);
    }

    // pack.yml is the ADR-0023 descriptor; the rest are the captured surface roots (pack.PackSurface FILES+DIRS).
    // signature-pack.zip is a BUILD output (se/bootstrap/build.gradle.kts packSignaturePack), never a source entry.
    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of(
            "pack.yml", "config.yml", "lang.yml", "content", "items", "menus");

    @Test
    void signaturePackHasOnlySurfaceRootsAtTopLevel() throws Exception {
        assertTrue(Files.isDirectory(PACK), "Signature pack source tree not found from " + Path.of("").toAbsolutePath());
        try (Stream<Path> top = Files.list(PACK)) {
            List<String> stray = top.map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .filter(name -> !ALLOWED_TOP_LEVEL.contains(name))
                    .sorted()
                    .toList();
            assertTrue(stray.isEmpty(),
                    () -> "signature-pack has top-level entries outside pack.yml + the surface roots: " + stray);
        }
    }
}
