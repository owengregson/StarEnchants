package migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.diag.Diagnostic;

/**
 * The importer's contract (docs/architecture.md §10): the structure migrates and the verified core
 * effects translate to valid StarEnchants tokens — proven by compiling the importer's OWN output
 * through the real production compiler — while unmapped effects are flagged (a warning + a {@code # TODO}
 * line) without failing the migration. Handle tokens resolve permissively here (no server); the live
 * CatalogSuite owns cross-version handle existence.
 */
class MigratorTest {

    private static final PlatformResolvers PERMISSIVE = new PlatformResolvers() {
        @Override public OptionalInt material(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt sound(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt potionEffect(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt particle(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt enchantment(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt entityType(String token) { return OptionalInt.of(0); }
        @Override public OptionalInt attribute(String token) { return OptionalInt.of(0); }
    };

    private static final String EE = """
            Enchants:
              venomstrike:
                name: "Venom Strike"
                description:
                  - "Poison and burn your foe."
                group: "ELITE"
                applies: "SWORDS"
                type: "ATTACK"
                levels:
                  1:
                    chance: 25
                    cooldown: 5
                    effects:
                      - 'DAMAGE:1:6:TARGET'
                      - 'FLAME:3:TARGET'
                      - 'MESSAGE:&aStruck!:PLAYER'
                  2:
                    chance: 40
                    effects:
                      - 'FEED:4'
                      - 'DROP_HEAD:TARGET'
            """;

    @Test
    void migratesEliteEnchantmentsToCompilableContent(@TempDir Path dir) throws IOException {
        Migrator.Result result = Migrator.eliteEnchantments(EE);
        Library library = compile(result.files(), dir);

        String blocking = library.diagnostics().stream().filter(Diagnostic::blocking)
                .map(Diagnostic::toString).collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "migrated output should compile clean:\n  " + blocking);
        assertEquals(2, library.snapshot().abilityCount(), "two levels ⇒ two compiled abilities");
    }

    @Test
    void translatesVerifiedCoreEffectsFaithfully() {
        assertEquals("DAMAGE:6:@Victim", Mappings.effect("DAMAGE:1:6:TARGET").se()); // random range → max
        assertEquals("IGNITE:60:@Victim", Mappings.effect("FLAME:3:TARGET").se()); // seconds → ticks (x20)
        assertEquals("GIVE_EXP:30", Mappings.effect("EXP:30").se());
        assertEquals("EXTINGUISH:@Self", Mappings.effect("EXTINGUISH:PLAYER").se());
        assertEquals("MESSAGE:&aStruck!", Mappings.effect("MESSAGE:&aStruck!:PLAYER").se());
    }

    @Test
    void messageStripsTheTrailingTargetButNeverTruncatesAColonBody() {
        assertEquals("MESSAGE:&aStruck!", Mappings.effect("MESSAGE:&aStruck!:PLAYER").se());
        assertEquals("MESSAGE:hello", Mappings.effect("MESSAGE:hello").se()); // no target
        // A body that itself contains ':' would be split by the effect lexer — demote to a TODO, never emit a
        // silently-truncated MESSAGE.
        assertFalse(Mappings.effect("MESSAGE:Time left 5:00:PLAYER").mapped(),
                "a colon-bearing message body must be a TODO, not a truncated token");
    }

    @Test
    void skipsEnchantsWithAPathUnsafeId() {
        String malicious = """
                Enchants:
                  "../../../evil":
                    name: "Evil"
                    type: "ATTACK"
                    applies: "SWORDS"
                    levels:
                      1: { chance: 100, effects: ['FEED:1'] }
                """;
        Migrator.Result result = Migrator.eliteEnchantments(malicious);
        assertTrue(result.files().isEmpty(), "an id with path separators must be skipped, not written");
        assertTrue(result.diagnostics().all().stream().anyMatch(d -> d.code().equals("migrate.id")),
                "expected a migrate.id warning for the unsafe id");
    }

    @Test
    void flagsUnmappedEffectsWithoutFailing() {
        Migrator.Result result = Migrator.eliteEnchantments(EE);
        // DROP_HEAD has no equivalent: a warning is recorded and a TODO line is emitted, but the file
        // still migrates (its other effects translate) and compiles.
        assertTrue(result.diagnostics().all().stream()
                        .anyMatch(d -> d.code().equals("migrate.effect") && d.message().contains("DROP_HEAD")),
                "expected a migrate.effect warning for DROP_HEAD");
        String venom = result.files().get("enchants/venomstrike.yml");
        assertTrue(venom.contains("# TODO port manually: DROP_HEAD"), "expected a TODO line for DROP_HEAD");
        assertFalse(result.diagnostics().hasErrors(), "an unmapped effect is a warning, never an error");
    }

    @Test
    void migratesEliteArmorSetToCompilableContent(@TempDir Path dir) throws IOException {
        String ea = """
                Required-Items: 4
                Effects:
                  - 'REDUCTION:15'
                  - 'DAMAGE:35'
                  - 'BLESS'
                """;
        Migrator.Result result = Migrator.eliteArmorSet("ancient", ea);
        String set = result.files().get("sets/ancient.yml");
        assertTrue(set.contains("REDUCE_DAMAGE:15"), "REDUCTION:15 should map to REDUCE_DAMAGE:15");
        assertTrue(set.contains("# TODO port manually: DAMAGE:35"), "attack-direction DAMAGE should be a TODO");

        Library library = compile(result.files(), dir);
        assertFalse(library.hasErrors(), "migrated set should compile clean");
        assertEquals(1, library.snapshot().abilityCount(), "one set ⇒ one ability");
    }

    @Test
    void writeToEmitsFilesAndNeverClobbersExisting(@TempDir Path dir) throws IOException {
        Migrator.Result result = Migrator.eliteEnchantments(EE);
        int first = result.writeTo(dir);
        assertEquals(1, first, "one enchant file written");
        assertTrue(Files.exists(dir.resolve("enchants/venomstrike.yml")));

        // A re-run must not overwrite an operator's edited copy.
        Files.writeString(dir.resolve("enchants/venomstrike.yml"), "edited", StandardCharsets.UTF_8);
        int second = result.writeTo(dir);
        assertEquals(0, second, "existing file is not clobbered");
        assertEquals("edited", Files.readString(dir.resolve("enchants/venomstrike.yml")));
    }

    /** Write the migrated files under {@code dir} (preserving relative paths) and compile them. */
    private static Library compile(Map<String, String> files, Path dir) throws IOException {
        for (Map.Entry<String, String> file : files.entrySet()) {
            Path out = dir.resolve(file.getKey());
            Files.createDirectories(out.getParent());
            Files.writeString(out, file.getValue(), StandardCharsets.UTF_8);
        }
        Compiler compiler = ContentCompiler.production(PERMISSIVE);
        return LibraryLoader.load(dir, compiler, 0);
    }
}
