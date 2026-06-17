package migrate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.SpecRegistry;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.resolve.PlatformResolvers;
import engine.boot.ContentCompiler;
import engine.effect.kind.BuiltinEffects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import schema.diag.Diagnostic;
import schema.spec.ParamSpec;

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

    /** The real effect-head → ParamSpec lookup (engine vocabulary) used to emit verbose v2 effects. */
    private static final Function<String, ParamSpec> SPECS;
    static {
        SpecRegistry reg = BuiltinEffects.registry().specRegistry();
        SPECS = head -> reg.lookup(head).orElse(null);
    }

    /** An AdvancedEnchantments enchantments.yml fixture — the REAL modern form: enchants at the document
     *  root, a compound {@code type}, and {@code @Victim}/{@code @Self} (capital-@) space-separated targets. */
    private static final String AE = """
            venomaura:
              display: '%group-color%Venom Aura'
              description: 'Poison the foe you strike.'
              applies-to: 'Swords'
              type: ATTACK;ATTACK_MOB
              group: ULTIMATE
              applies:
                - ALL_SWORD
              levels:
                '1':
                  chance: 25
                  effects:
                    - 'DAMAGE:4 @Victim'
                    - 'POTION:POISON:0:60 @Victim'
                    - 'MESSAGE:&aVenom courses through them'
                '2':
                  chance: 40
                  effects:
                    - 'ADD_HEALTH:2 @Self'
                    - 'STEAL_MONEY:100 @Victim'
            """;

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

    @Test
    void migratesAdvancedEnchantmentsToCompilableContent(@TempDir Path dir) throws IOException {
        Migrator.Result result = Migrator.advancedEnchantments(AE, SPECS);
        Library library = compile(result.files(), dir);

        String blocking = library.diagnostics().stream().filter(Diagnostic::blocking)
                .map(Diagnostic::toString).collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "migrated AE output should compile clean:\n  " + blocking);
        assertEquals(2, library.snapshot().abilityCount(), "two levels ⇒ two compiled abilities");
        // STEAL_MONEY has no verified equivalent → flagged, not silently dropped.
        String venom = result.files().get("enchants/venomaura.yml");
        assertTrue(venom.contains("# TODO port manually: STEAL_MONEY:100 @Victim"),
                "expected a TODO for the unmapped AE effect");
        assertFalse(result.diagnostics().hasErrors(), "an unmapped AE effect is a warning, never an error");
    }

    @Test
    void translatesVerifiedAeEffectsFaithfully() {
        // The real modern AE form: capital-@ space-separated targets.
        assertEquals("DAMAGE:4:@Victim", Mappings.aeEffect("DAMAGE:4 @Victim").se());
        assertEquals("POTION:POISON:0:60:@Attacker", Mappings.aeEffect("POTION:POISON:0:60 @Attacker").se());
        assertEquals("HEAL:2:@Self", Mappings.aeEffect("ADD_HEALTH:2 @Self").se()); // AE add-health → HEAL
        assertEquals("DAMAGE:6:@Victim", Mappings.aeEffect("DAMAGE:6:@Victim").se()); // colon-attached selector
        // Legacy %victim% form still maps; large money values survive (not int-capped).
        assertEquals("GIVE_MONEY:5000000000:@Self", Mappings.aeEffect("ADD_MONEY:5000000000 @Self").se());
        assertFalse(Mappings.aeEffect("STEAL_MONEY:100 @Victim").mapped(), "no verified equivalent → TODO");
        assertFalse(Mappings.aeEffect("DAMAGE:4 @Aoe{r=5}").mapped(), "an arg-bearing AE area selector → TODO");
    }

    @Test
    void aeCompoundTypeMapsToTheFirstRecognisedTrigger() {
        assertEquals("ATTACK", Mappings.aeTrigger("ATTACK;ATTACK_MOB;SHOOT;SHOOT_MOB"));
        assertEquals("DEFENSE", Mappings.aeTrigger("DEFENSE;DEFENSE_MOB;DEFENSE_PROJECTILE"));
        assertEquals("MINE", Mappings.aeTrigger("EXPLOSION;MINING")); // EXPLOSION has no v1 trigger → first RECOGNISED (MINING) wins
        org.junit.jupiter.api.Assertions.assertNull(Mappings.aeTrigger("ELYTRA_FLY;EXPLOSION"), "no v1 trigger → null");
    }

    @Test
    void emitsVerboseV2EffectsWhenSpecsSupplied(@TempDir Path dir) throws IOException {
        Migrator.Result result = Migrator.eliteEnchantments(EE, SPECS);
        String venom = result.files().get("enchants/venomstrike.yml");
        // The mapped DAMAGE:6:@Victim is written in the verbose v2 form, not as a terse string.
        assertTrue(venom.contains("{ DAMAGE: { amount: 6, who: \"@Victim\" } }"),
                () -> "expected verbose v2 DAMAGE effect, got:\n" + venom);
        // And the verbose output still compiles clean through the real loader.
        Library library = compile(result.files(), dir);
        assertFalse(library.hasErrors(), "verbose migrated output should compile clean");
        assertEquals(2, library.snapshot().abilityCount());
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
