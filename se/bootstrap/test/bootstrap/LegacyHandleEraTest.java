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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pack.PackManifest;
import platform.caps.Capabilities;
import platform.resolve.Aliases;
import platform.resolve.HandleResolver;
import platform.resolve.LegacyFallbacks;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import schema.spec.HandleCategory;

/**
 * The LEGACY-era twin of {@link ModernHandleEraTest} (R-QC11): a shipped pack must either resolve its SOUND
 * tokens against the real 1.8.8 {@code org.bukkit.Sound} enum (committed at
 * {@code test-fixtures/handles/sounds-1.8.8.txt}) or DECLARE itself modern-only in its {@code pack.yml}. Both
 * outcomes are green; what fails is a pack that quietly stops resolving without saying so. The declaration is
 * read from the manifest, not re-typed here, so the pack file is the single source and
 * {@code /se pack apply}'s refusal (PackGate) and this gate can never disagree.
 *
 * <p>Sounds only. 1.8 has no {@code org.bukkit.Particle}/{@code Attribute} type at all (the legacy sink resolves
 * those to NMS by name), so a committed constant list would be an invention rather than a javap fact.
 */
class LegacyHandleEraTest {

    /** The oldest era the mega-jar ships for; the legacy overlay's compile target. */
    private static final Capabilities LEGACY = Capabilities.probe("1.8.9-R0.1-SNAPSHOT", false);

    @ParameterizedTest
    @CsvSource({"cosmic-pack, 1000", "signature-pack, 400"})
    void shippedPackEitherResolvesOnLegacyOrDeclaresItselfModernOnly(String pack, int minAbilities) {
        PackManifest manifest = manifest(pack);
        Path content = Path.of("packs-src", pack, "content");
        if (!PackGate.meetsFloor(manifest, LEGACY)) {
            // Declared modern-only, so the 1.8.9 lane never loads it and PackGate refuses the apply with one
            // E_PACK_ERA. The declaration still has to EARN itself: a pack that resolves clean here should drop
            // its floor, not keep an exclusion nobody re-checks.
            assertTrue(unresolvableHandles(content) > 0,
                    () -> pack + " declares min-server " + manifest.minServer()
                            + " but resolves clean on 1.8.8 — drop the floor instead of excluding it");
            return;
        }
        compileClean(content, minAbilities);
    }

    /** The claim cosmic-pack's config header makes on the engine's behalf: the bundled defaults are legacy-capable. */
    @Test
    void defaultCatalogResolvesOnTheLegacyEra() {
        compileClean(Path.of("resources/content"), 60);
    }

    private static long unresolvableHandles(Path content) {
        return LibraryLoader.load(content, ContentCompiler.production(legacyResolvers()), 0)
                .diagnostics().stream()
                .filter(d -> d.is(DiagCode.E_UNKNOWN_HANDLE))
                .count();
    }

    private static void compileClean(Path content, int minAbilities) {
        assertTrue(Files.isDirectory(content), "content not found from " + Path.of("").toAbsolutePath());
        Compiler compiler = ContentCompiler.production(legacyResolvers());
        Library library = LibraryLoader.load(content, compiler, 0);
        String blocking = library.diagnostics().stream()
                .filter(Diagnostic::blocking)
                .map(Diagnostic::toString)
                .collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(),
                () -> content + " has blocking diagnostics on 1.8.8:\n  " + blocking);
        assertTrue(library.snapshot().abilityCount() > minAbilities,
                () -> content + " under-loaded on 1.8.8: " + library.snapshot().abilityCount() + " abilities");
    }

    /** Strict SOUND resolution against the committed 1.8.8 constant list; every other category permissive. */
    private static PlatformResolvers legacyResolvers() {
        Set<String> sounds = constants();
        return new PlatformResolvers() {
            @Override public OptionalInt material(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt sound(String t) {
                // The same table RenameResolvers builds on the legacy lane: renames + the 1.8 degradations.
                Map<String, String> table = Aliases.mergedWith(
                        HandleCategory.SOUND, LegacyFallbacks.forCategory(HandleCategory.SOUND));
                return HandleResolver.resolve(t, table, sounds::contains).isPresent()
                        ? OptionalInt.of(0) : OptionalInt.empty();
            }
            @Override public OptionalInt particle(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt entityType(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt attribute(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt potionEffect(String t) { return OptionalInt.of(0); }
            @Override public OptionalInt enchantment(String t) { return OptionalInt.of(0); }
        };
    }

    private static PackManifest manifest(String pack) {
        Path packYml = Path.of("packs-src", pack, PackManifest.ENTRY);
        try {
            return PackManifest.fromYaml(Files.readString(packYml, StandardCharsets.UTF_8), pack);
        } catch (IOException e) {
            throw new UncheckedIOException("missing " + packYml, e);
        }
    }

    private static Set<String> constants() {
        Path path = Path.of("test-fixtures/handles/sounds-1.8.8.txt");
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
