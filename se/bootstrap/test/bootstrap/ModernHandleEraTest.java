package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import schema.diag.Diagnostic;
import schema.spec.HandleCategory;

/**
 * The MODERN-era twin of {@link SignaturePackValidationTest}: every shipped content library must also
 * resolve its sound/particle tokens on the post-1.20.5 enum flattening, not only the 1.17.1 floor the
 * compile classpath provides. The 1.20.5 rename wave (ENCHANTMENT_TABLE→ENCHANT, TOTEM→TOTEM_OF_UNDYING,
 * …) once shipped 79 authored particle lines that loaded on the floor but died as {@code E_UNKNOWN_HANDLE}
 * on 1.21.x — resolution here runs through the production {@link HandleResolver} + {@link Aliases} against
 * committed per-era constant lists (javap'd from the reference-cache paper-api jars,
 * {@code test-fixtures/handles/}), so a stale token or a missing alias fails {@code ./gradlew build}
 * instead of a live {@code /se pack apply}. Sounds/particles only: the other handle categories are
 * floor-validated by the twin and live-validated per matrix version by the tester's CatalogSuite.
 */
class ModernHandleEraTest {

    @ParameterizedTest
    @ValueSource(strings = {"1.21.11", "26.1.2"})
    void signaturePackResolvesOnModernEra(String era) {
        compileClean(Path.of("packs-src/signature-pack/content"), era, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.21.11", "26.1.2"})
    void defaultCatalogResolvesOnModernEra(String era) {
        compileClean(Path.of("resources/content"), era, 60);
    }

    /**
     * Every SOUND alias TARGET must name a constant the modern era really has. The shipped-content sweeps
     * above only reach rows some config happens to author, so a mistyped target on a legacy-sweep row would
     * ship dark: the legacy spelling would resolve to nothing forever, on every era.
     */
    @ParameterizedTest
    @ValueSource(strings = {"1.21.11", "26.1.2"})
    void everySoundAliasTargetExistsOnModernEra(String era) {
        Set<String> sounds = constants("sounds-" + era + ".txt");
        String missing = Aliases.forCategory(HandleCategory.SOUND).entrySet().stream()
                .filter(e -> !sounds.contains(e.getValue()))
                .map(e -> e.getKey() + " -> " + e.getValue())
                .sorted()
                .collect(Collectors.joining(", "));
        assertTrue(missing.isEmpty(), () -> "sound alias targets absent on " + era + ": " + missing);
    }

    private static void compileClean(Path content, String era, int minAbilities) {
        assertTrue(Files.isDirectory(content), "content not found from " + Path.of("").toAbsolutePath());
        Compiler compiler = ContentCompiler.production(eraResolvers(era));
        Library library = LibraryLoader.load(content, compiler, 0);
        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(),
                () -> content + " has blocking diagnostics on " + era + ":\n  " + blocking);
        assertTrue(library.snapshot().abilityCount() > minAbilities,
                () -> content + " under-loaded on " + era + ": " + library.snapshot().abilityCount() + " abilities");
    }

    /** Strict sound/particle resolution against the era's committed constant list; the rest permissive. */
    private static PlatformResolvers eraResolvers(String era) {
        Set<String> sounds = constants("sounds-" + era + ".txt");
        Set<String> particles = constants("particles-" + era + ".txt");
        return new PlatformResolvers() {
            @Override public OptionalInt material(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt sound(String t) { return strict(HandleCategory.SOUND, t, sounds::contains); }
            @Override public OptionalInt particle(String t) { return strict(HandleCategory.PARTICLE, t, particles::contains); }
            @Override public OptionalInt entityType(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt attribute(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt potionEffect(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt enchantment(String t) { return OptionalInt.of(0); }
        };
    }

    private static OptionalInt strict(HandleCategory category, String token, Predicate<String> exists) {
        return HandleResolver.resolve(token, Aliases.forCategory(category), exists).isPresent()
                ? OptionalInt.of(0)
                : OptionalInt.empty();
    }

    private static Set<String> constants(String file) {
        Path path = Path.of("test-fixtures/handles").resolve(file);
        try {
            Set<String> names = Files.readAllLines(path).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
            assertTrue(names.size() > 50, "suspiciously small constant list: " + path);
            return names;
        } catch (IOException e) {
            throw new UncheckedIOException("missing committed era constants: " + path, e);
        }
    }
}
