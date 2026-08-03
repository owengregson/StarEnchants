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
 * The batch gate for the in-progress {@code cosmic-pack} port: every content batch must leave the pack
 * compiling clean through the real loaders and {@link FloorStrictResolvers}, the same bar
 * {@link SignaturePackValidationTest} holds the shipped pack to. No corpus-size floor while the pack fills
 * batch by batch — that (and the ship wiring: zip task, {@code packs/index.txt}, fingerprint stamp) lands
 * with the port's polish PR, which is also when a half-built pack first becomes applyable.
 */
class CosmicPackValidationTest {

    private static final Path PACK = Path.of("packs-src/cosmic-pack");

    @Test
    void cosmicPackContentCompilesClean() {
        Path content = PACK.resolve("content");
        assertTrue(Files.isDirectory(content), "Cosmic pack content not found from " + Path.of("").toAbsolutePath());

        Compiler compiler = ContentCompiler.production(FloorStrictResolvers.INSTANCE);
        Library library = LibraryLoader.load(content, compiler, 0);

        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "Cosmic pack content has blocking diagnostics:\n  " + blocking);
    }

    @Test
    void cosmicPackItemsLoadClean() {
        Path items = PACK.resolve("items");
        assertTrue(Files.isDirectory(items), "Cosmic pack items not found");
        ItemsConfig config = ItemsLoader.load(items);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Cosmic pack items have blocking diagnostics:\n  " + errors);
    }

    @Test
    void cosmicPackMenusLoadClean() {
        Path menus = PACK.resolve("menus");
        assertTrue(Files.isDirectory(menus), "Cosmic pack menus not found");
        MenusConfig config = MenusLoader.load(menus);
        String errors = config.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(config.hasErrors(), () -> "Cosmic pack menus have blocking diagnostics:\n  " + errors);
    }

    @Test
    void cosmicPackMasterConfigLoadsClean() {
        Path configFile = PACK.resolve("config.yml");
        assertTrue(Files.isRegularFile(configFile), "Cosmic pack config.yml not found");
        MasterConfig master = MasterConfigLoader.load(configFile);
        String errors = master.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(master.hasErrors(), () -> "Cosmic pack config.yml has blocking diagnostics:\n  " + errors);
    }

    // pack.yml is the ADR-0023 descriptor; the rest are the captured surface roots (pack.PackSurface FILES+DIRS).
    private static final Set<String> ALLOWED_TOP_LEVEL = Set.of(
            "pack.yml", "config.yml", "lang.yml", "content", "items", "menus");

    @Test
    void cosmicPackHasOnlySurfaceRootsAtTopLevel() throws Exception {
        assertTrue(Files.isDirectory(PACK), "Cosmic pack source tree not found from " + Path.of("").toAbsolutePath());
        try (Stream<Path> top = Files.list(PACK)) {
            List<String> stray = top.map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .filter(name -> !ALLOWED_TOP_LEVEL.contains(name))
                    .sorted()
                    .toList();
            assertTrue(stray.isEmpty(),
                    () -> "cosmic-pack has top-level entries outside pack.yml + the surface roots: " + stray);
        }
    }
}
