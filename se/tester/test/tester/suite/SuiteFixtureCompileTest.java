package tester.suite;

import static org.junit.jupiter.api.Assertions.assertFalse;

import compile.load.Library;
import compile.load.LibraryLoader;
import engine.boot.ContentCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles the live suites' inline content fixtures against the PRODUCTION registry, in {@code ./gradlew
 * build}, where a typo costs a second instead of a whole matrix run.
 *
 * <p>The failure this exists for is disproportionate and silent-looking. A suite writes its fixture to a temp
 * dir and compiles it at boot; a fixture naming an effect the real registry does not have (the unit-test
 * registries are FAKES — {@code MapSpecRegistry.of(ParamSpec.of("HEAL")…)} is a compile-test double, not a
 * shipped kind) yields a library with errors, the scenario bails before staging anything, and every guard it
 * declared reports "unresolved (timed out after 400 ticks)". Five targets, one typo, and the report points at
 * the timeouts rather than at the cause.
 *
 * <p>Compiled through the PRODUCTION kind registry but with permissive resolvers: whether a version really has
 * a {@code REGENERATION} potion token is a per-version fact only the live {@code CatalogSuite} can answer, and
 * a unit JVM has no server to ask. The kind and param surface — which is where this class of typo lives — is
 * fully checkable here.
 *
 * <p>A suite that adds an inline fixture makes the constant package-private and adds a row here. This asserts
 * only that the content COMPILES — what it then does on a booted server is the suite's own business.
 */
class SuiteFixtureCompileTest {

    /** Every inline fixture, by the name its suite gives it. */
    private static Map<String, String> fixtures() {
        return Map.of(
                "SetSuite.YETI", SetSuite.YETI,
                "SetSuite.WRAITH", SetSuite.WRAITH);
    }

    @Test
    void everyInlineSuiteFixtureCompilesAgainstTheProductionRegistry(@TempDir Path root) throws IOException {
        for (Map.Entry<String, String> fixture : fixtures().entrySet()) {
            Path file = root.resolve(fixture.getKey()).resolve("sets/fixture.yml");
            Files.createDirectories(file.getParent());
            Files.writeString(file, fixture.getValue(), StandardCharsets.UTF_8);
            Library library = LibraryLoader.load(file.getParent().getParent(),
                    ContentCompiler.production(testfx.PermissiveResolvers.INSTANCE), 0);
            assertFalse(library.hasErrors(),
                    () -> fixture.getKey() + " does not compile: " + library.diagnostics());
        }
    }
}
