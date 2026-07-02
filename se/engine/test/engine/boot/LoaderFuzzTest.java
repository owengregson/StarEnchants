package engine.boot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.diag.DiagCode;
import schema.diag.Diagnostic;
import testfx.PermissiveResolvers;

/**
 * The YAML-surface half of the fuzz gate: generated valid enchant files load clean and clamped, and a fixed
 * pathological corpus (deep nesting, alias bombs, duplicate/unicode keys, terse effects, malformed shapes)
 * always loads without throwing, every fault a closed-set {@link DiagCode} (ADR-0014, ADR-0042).
 */
class LoaderFuzzTest {

    private static final Compiler COMPILER = ContentCompiler.production(PermissiveResolvers.INSTANCE);
    private static final ContentFuzz.Vocab VOCAB = ContentFuzz.Vocab.live();
    static final int VALID_FILES = 25;

    /** The corpus entries whose blocking code is deterministic for every server's SnakeYAML. */
    private static final Map<String, DiagCode> PINS = Map.of(
            "deep-nesting.yml", DiagCode.E_LOAD_YAML,
            "alias-bomb.yml", DiagCode.E_LOAD_YAML,
            "tabs-and-garbage.yml", DiagCode.E_LOAD_YAML,
            "terse.yml", DiagCode.E_TERSE_EFFECT,
            "scalar-root.yml", DiagCode.E_LOAD_ENCHANT,
            "bad-level.yml", DiagCode.E_LOAD_ENCHANT_LEVEL,
            "no-trigger.yml", DiagCode.E_LOAD_ENCHANT_TRIGGER,
            "bad-chance.yml", DiagCode.E_LOAD_CHANCE);

    @TempDir
    Path root;

    @Test
    void generatedValidFilesLoadCleanAndClamped() throws Exception {
        Path enchants = Files.createDirectories(root.resolve("content/enchants"));
        List<ContentFuzz.YamlSample> samples = new ArrayList<>();
        int totalLevels = 0;
        for (int i = 0; i < VALID_FILES; i++) {
            ContentFuzz.YamlSample sample = ContentFuzz.yamlValid(i, VOCAB);
            samples.add(sample);
            Files.writeString(enchants.resolve(sample.fileName()), sample.yaml(), StandardCharsets.UTF_8);
            totalLevels += sample.levelCount();
        }
        Library lib = assertDoesNotThrow(() -> LibraryLoader.load(root.resolve("content"), COMPILER, 0));

        assertFalse(lib.hasErrors(), blockingSupplier(lib, samples));
        assertEquals(totalLevels, lib.snapshot().abilityCount()); // every file and level was read
        assertEquals(VALID_FILES, lib.catalog().size());
        for (Ability a : lib.snapshot().abilities()) {
            assertTrue(a.baseChance() >= 0.0 && a.baseChance() <= 100.0);
        }
        CompilerFuzzTest.assertClosedCodes(lib.diagnostics());
        CompilerFuzzTest.assertStructurallySound(lib.snapshot());
    }

    @Test
    void adversarialFilesNeverThrowAndDiagnoseClosedCodes() throws Exception {
        for (Map.Entry<String, String> entry : ContentFuzz.yamlAdversarial()) {
            String name = entry.getKey();
            Path dir = Files.createDirectories(root.resolve(name + "-root/content/enchants"));
            Files.writeString(dir.resolve(name), entry.getValue(), StandardCharsets.UTF_8);
            Path content = root.resolve(name + "-root/content");

            Library lib = assertDoesNotThrow(() -> LibraryLoader.load(content, COMPILER, 0), name);
            CompilerFuzzTest.assertClosedCodes(lib.diagnostics());
            for (Ability a : lib.snapshot().abilities()) {
                assertTrue(a.baseChance() >= 0.0 && a.baseChance() <= 100.0); // loader clamp holds everywhere
            }
            DiagCode pin = PINS.get(name);
            if (pin != null) {
                assertTrue(lib.diagnostics().stream().anyMatch(d -> d.is(pin)),
                        () -> name + " expected " + pin + "\n" + entry.getValue() + "\n" + render(lib));
            }
        }
    }

    @Test
    void loadIsDeterministicAcrossRuns() throws Exception {
        Path enchants = Files.createDirectories(root.resolve("content/enchants"));
        for (int i = 0; i < VALID_FILES; i++) {
            ContentFuzz.YamlSample sample = ContentFuzz.yamlValid(i, VOCAB);
            Files.writeString(enchants.resolve(sample.fileName()), sample.yaml(), StandardCharsets.UTF_8);
        }
        Library a = LibraryLoader.load(root.resolve("content"), COMPILER, 0);
        Library b = LibraryLoader.load(root.resolve("content"), COMPILER, 0);

        assertEquals(a.snapshot().abilityCount(), b.snapshot().abilityCount());
        for (int i = 0; i < a.snapshot().abilityCount(); i++) {
            assertEquals(a.snapshot().stableKeys().keyOf(i), b.snapshot().stableKeys().keyOf(i));
        }
        assertEquals(codes(a), codes(b)); // the sorted-relative-path file ordering contract
    }

    private static List<String> codes(Library lib) {
        return lib.diagnostics().stream().map(Diagnostic::code).toList();
    }

    private static String render(Library lib) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : lib.diagnostics()) {
            sb.append(d.render()).append('\n');
        }
        return sb.toString();
    }

    private static Supplier<String> blockingSupplier(Library lib, List<ContentFuzz.YamlSample> samples) {
        return () -> {
            StringBuilder sb = new StringBuilder("blocking diagnostics:\n");
            Set<String> files = new HashSet<>();
            for (Diagnostic d : lib.diagnostics()) {
                if (d.blocking()) {
                    sb.append("  ").append(d.render()).append('\n');
                    files.add(d.source().file());
                }
            }
            for (ContentFuzz.YamlSample sample : samples) {
                if (files.stream().anyMatch(f -> f.contains(sample.fileName()))) {
                    sb.append("--- ").append(sample.fileName()).append(" ---\n").append(sample.yaml());
                }
            }
            return sb.toString();
        };
    }
}
