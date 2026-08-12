package bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.junit.jupiter.params.provider.ValueSource;
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
 * <p>Content is swept for sounds only; a pack's {@code items/} tree adds MATERIALS, whose 1.8 vocabulary is a
 * javap fact too ({@code materials-1.8.8.txt}). Particles are in neither sweep: 1.8 has no
 * {@code org.bukkit.Particle}/{@code Attribute} type at all, and production's 1.8 particle vocabulary is a
 * name set inside the legacy overlay's {@code LegacyHandleLookup}, which this lane cannot see — committing a
 * copy of it would be a second place to edit rather than a javap fact.
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

    /**
     * The {@code items/} half of the same claim, which the compile sweep above cannot reach. A pack's physical
     * items fail SILENTLY on the wrong era — an unminted material drops to the caller's generic fallback and a
     * cue plays nothing — so unlike content there is no {@code E_UNKNOWN_HANDLE} to catch. Materials resolve by
     * {@code ItemFactory}'s own rule (the era's spelling, else the registered degradation for a minted token),
     * cues through the shared alias table {@code feature.compat.Sounds} uses.
     *
     * <p>A pack that declares itself modern-only is skipped rather than asserted against: the content twin
     * above already makes that declaration earn itself, and re-proving it here would only pin which of its
     * tokens happens to be the 1.8-less one.
     */
    @ParameterizedTest
    @ValueSource(strings = {"cosmic-pack", "signature-pack"})
    void shippedPackItemsResolveOnLegacy(String pack) {
        assumeTrue(PackGate.meetsFloor(manifest(pack), LEGACY), () -> pack + " is declared modern-only");
        ItemHandles handles = ItemHandles.of(Path.of("packs-src", pack, "items"));
        String materials = handles.unresolvableMaterials(constants("materials-1.8.8.txt"));
        String sounds = handles.unresolvableSounds(constants("sounds-1.8.8.txt"));
        assertTrue(materials.isEmpty(),
                () -> pack + " items name materials with no 1.8.8 spelling — each mints as the caller's generic"
                        + " fallback there, with no diagnostic: " + materials);
        assertTrue(sounds.isEmpty(),
                () -> pack + " items name sounds with no 1.8.8 spelling — each gesture is mute there, with no"
                        + " diagnostic: " + sounds);
    }

    /**
     * The legacy half of {@code ModernHandleEraTest.everySoundAliasTargetExistsOnModernEra}: an alias row is a
     * PAIR, and a key misspelt on the 1.8 side is invisible to every other gate — the token still resolves
     * modern (a direct hit), so the row just never fires and the cue goes mute on the one era it was added for.
     */
    @Test
    void everySoundAliasKeyIsAReal18Constant() {
        Set<String> sounds = constants("sounds-1.8.8.txt");
        String missing = Aliases.forCategory(HandleCategory.SOUND).entrySet().stream()
                .filter(e -> !sounds.contains(e.getKey()))
                .map(e -> e.getKey() + " -> " + e.getValue())
                .sorted()
                .collect(Collectors.joining(", "));
        assertTrue(missing.isEmpty(), () -> "sound alias keys no 1.8.8 constant spells: " + missing);
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
        Set<String> sounds = constants("sounds-1.8.8.txt");
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
