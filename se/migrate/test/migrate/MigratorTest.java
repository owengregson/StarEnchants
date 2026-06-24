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
import migrate.model.MigratedCondition;
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

    /** An AE enchant with a per-level {@code conditions:} list: one mappable allow-gate + one unmappable. */
    private static final String AE_COND = """
            sneakstrike:
              display: 'Sneak Strike'
              type: ATTACK
              group: ELITE
              applies:
                - ALL_SWORD
              levels:
                '1':
                  conditions:
                    - '%player is sneaking% = true : %allow%'
                    - '%block type% contains ORE : %allow%'
                  chance: 50
                  effects:
                    - 'DAMAGE:4 @Victim'
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
        assertEquals("MODIFY_EXP:30:give", Mappings.effect("EXP:30").se());
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
    void translatesTheExpandedEeEffectVocabulary() {
        // Combat (ATTACK direction): the foe is @Victim.
        assertEquals("POTION:CONFUSION:1:60:@Victim", Mappings.effect("POTION:CONFUSION:1:TARGET:3").se()); // sec→ticks
        assertEquals("POTION:FAST_DIGGING:1:200:@Self", Mappings.effect("POTION:FAST_DIGGING:1:PLAYER").se()); // no-dur default
        assertEquals("MODIFY_HEALTH:2:give:@Self", Mappings.effect("HEAL:ADD:1:2").se());        // range → max
        assertEquals("MODIFY_HEALTH:50:give:@Self", Mappings.effect("HEAL:ADD:40:50:PLAYER").se()); // trailing target ignored
        assertEquals("DAMAGE_MOD:defense:add:1.85", Mappings.effect("REDUCTION:1.85").se());     // decimal kept
        assertEquals("DAMAGE_MOD:defense:add:100", Mappings.effect("REDUCTION:150").se());       // clamped to 100
        assertEquals("DAMAGE_MOD:attack:add:25", Mappings.effect("DAMAGE_INCREASE:25").se());
        assertFalse(Mappings.effect("DAMAGE_INCREASE:-50").mapped());                            // negative self-nerf → TODO
        assertEquals("REMOVE_POTION:POISON:@Self", Mappings.effect("CURE:POISON:true").se());
        assertEquals("SOUND:ENTITY_GENERIC_EXPLODE:2:5", Mappings.effect("SOUND:ENTITY_GENERIC_EXPLODE:2:5").se());
        assertEquals("PARTICLE:FLAME", Mappings.effect("PARTICLE:FLAME").se());
        assertEquals("DURABILITY:10:item:restore", Mappings.effect("ADD_DURABILITY:10").se());
        assertEquals("DURABILITY:2:armor:damage:@Victim", Mappings.effect("DAMAGE_ARMOR:2").se());
        assertEquals("DAMAGE:3.25:@Aoe", Mappings.effect("DAMAGE_ARC:3.25").se());               // decimal arc damage
        assertEquals("LIGHTNING:5:@Victim", Mappings.effect("LIGHTNING:TARGET:REAL").se());
        assertEquals("EXPLODE:1:false:@Victim", Mappings.effect("EXPLODE:1:false:TARGET").se());
        assertEquals("VELOCITY:add:0:5:0:@Self", Mappings.effect("THROW:0:5:0:PLAYER").se());
        assertEquals("SPAWN_ENTITY:PRIMED_TNT:2", Mappings.effect("TNT:2:PLAYER").se());
        assertEquals("SPAWN_ENTITY:IRON_GOLEM:1:400:0:activator",
                Mappings.effect("SPAWN:IRON_GOLEM:20:1:10:&b&l%player%").se());
        assertEquals("SUPPRESS:GROUP:common:200:@Victim", Mappings.effect("DISABLE_ENCHANTMENT_GROUP:COMMON:10").se());
        assertEquals("SUPPRESS:ENCHANT:enchants/immortal:80:@Victim",
                Mappings.effect("DISABLE_ENCHANTMENT:immortal:4").se());
        assertEquals("SUPPRESS:TYPE:DEFENSE:100:@Victim", Mappings.effect("DISABLE_ENCHANTMENT_TYPE:DEFENSE:5").se());
        assertEquals("DAMAGE_MOD:defense:add:100", Mappings.effect("DAMAGE_CANCEL").se()); // ≈ cancel the hit
        assertEquals("IGNORE_ARMOR", Mappings.effect("ARMOR_CANCEL").se());
    }

    @Test
    void eeEffectsAreDirectionAwareOnDefense() {
        // DEFENSE direction: the foe is @Attacker (the entity that struck the wielder); the wielder is @Self.
        assertEquals("DAMAGE:6:@Attacker", Mappings.effect("DAMAGE:1:6:TARGET", true).se());
        assertEquals("POTION:WITHER:1:80:@Attacker", Mappings.effect("POTION:WITHER:1:TARGET:4", true).se());
        assertEquals("MODIFY_HEALTH:3:give:@Self", Mappings.effect("HEAL:ADD:2:3", true).se()); // self-heal both ways
        assertEquals("SUPPRESS:GROUP:rare:200:@Attacker",
                Mappings.effect("DISABLE_ENCHANTMENT_GROUP:RARE:10", true).se());
    }

    @Test
    void eeDefenseCompoundEffectPortsTheInnerEffect() {
        // ender-walker: "DEFENSE;<factor>;HEAL:ADD:1:2" → the inner HEAL maps; the threshold factor is dropped.
        var compound = Mappings.effect("DEFENSE;2.5;HEAL:ADD:1:3", true);
        assertTrue(compound.mapped());
        assertEquals("MODIFY_HEALTH:3:give:@Self", compound.se());
    }

    @Test
    void eeConditionsMapToFactsOrTodo() {
        assertEquals("%actor.health% <= 5", Mappings.eeCondition("isPlayerHealth <= 5").expr());
        assertEquals("%victim.health% <= 5", Mappings.eeCondition("isTargetHealth <= 5").expr());
        assertEquals("%blocking% == true", Mappings.eeCondition("isPlayerBlocking").expr());
        // isTargetHolding checks an item GROUP — no SE fact → TODO (never emitted as an invalid raw gate).
        assertFalse(Mappings.eeCondition("isTargetHolding SWORD").mapped());
    }

    @Test
    void eeRepeatingTypeMapsToTheRepeatTrigger() {
        assertEquals("REPEATING", Mappings.trigger("REPEATING-5"));
        assertEquals(100, Mappings.repeatTicks("REPEATING-5")); // 5s × 20
        assertEquals(0, Mappings.repeatTicks("ATTACK"));
    }

    @Test
    void migratesAnEeRepeatingEnchantWithItsPeriod(@TempDir Path dir) throws IOException {
        String ee = """
                Enchants:
                  implants:
                    name: "Implants"
                    group: "EPIC"
                    applies: "HELMET"
                    type: "REPEATING-5"
                    levels:
                      1: { chance: 25, effects: ['HEAL:ADD:1', 'FEED:2'] }
                """;
        Migrator.Result result = Migrator.eliteEnchantments(ee, SPECS);
        String yml = result.files().get("enchants/implants.yml");
        assertTrue(yml.contains("trigger: REPEATING"), () -> yml);
        assertTrue(yml.contains("repeat: 100"), () -> yml);
        Library library = compile(result.files(), dir);
        assertFalse(library.hasErrors(), "migrated repeating enchant should compile clean");
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
        assertTrue(set.contains("DAMAGE_MOD:defense:add:15"), "REDUCTION:15 should map to DAMAGE_MOD:defense:add:15");
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
        // STEAL_MONEY now maps to the canonical MODIFY_MONEY transfer mode (§C collapse) — no longer a TODO.
        String venom = result.files().get("enchants/venomaura.yml");
        assertTrue(venom.contains("MODIFY_MONEY") && venom.contains("transfer"),
                "expected STEAL_MONEY migrated to MODIFY_MONEY (transfer): " + venom);
        assertFalse(venom.contains("# TODO port manually: STEAL_MONEY"),
                "STEAL_MONEY now has a verified equivalent — no manual TODO");
        assertFalse(result.diagnostics().hasErrors(), "an unmapped AE effect is a warning, never an error");
    }

    @Test
    void translatesVerifiedAeEffectsFaithfully() {
        // The real modern AE form: capital-@ space-separated targets. On an ATTACK enchant (the default
        // direction here) AE @Attacker is the WIELDER → StarEnchants @Self (NOT @Attacker, which is empty
        // on the attack side); AE @Victim is the foe → @Victim.
        assertEquals("DAMAGE:4:@Victim", Mappings.aeEffect("DAMAGE:4 @Victim").se());
        assertEquals("POTION:POISON:1:60:@Self", Mappings.aeEffect("POTION:POISON:0:60 @Attacker").se()); // §C: AE amplifier 0 → SE level 1
        assertEquals("MODIFY_HEALTH:2:give:@Self", Mappings.aeEffect("ADD_HEALTH:2 @Self").se()); // AE add-health → MODIFY_HEALTH (give)
        assertEquals("DAMAGE:6:@Victim", Mappings.aeEffect("DAMAGE:6:@Victim").se()); // colon-attached selector
        // Legacy %victim% form still maps; large money values survive (not int-capped). Money collapsed to
        // the canonical MODIFY_MONEY (§C): add→give, remove→take, and STEAL now maps to transfer mode.
        assertEquals("MODIFY_MONEY:5000000000:give:@Self", Mappings.aeEffect("ADD_MONEY:5000000000 @Self").se());
        assertEquals("MODIFY_MONEY:100:transfer:@Victim", Mappings.aeEffect("STEAL_MONEY:100 @Victim").se());
        // §C: STEAL_MONEY_PERCENT now maps to the steal_percent mode (a % of the target's balance).
        assertEquals("MODIFY_MONEY:25:steal_percent:@Victim",
                Mappings.aeEffect("STEAL_MONEY_PERCENT:25 @Victim").se());
    }

    @Test
    void translatesAdditionalVerifiedAeEffects() {
        assertEquals("MODIFY_FOOD:4:give:@Self", Mappings.aeEffect("ADD_FOOD:4 @Self").se()); // AE add-food → MODIFY_FOOD (give)
        assertEquals("MODIFY_EXP:30:give", Mappings.aeEffect("EXP:30").se());            // AE EXP → MODIFY_EXP (give)
        assertEquals("IGNITE:60:@Victim", Mappings.aeEffect("BURN:3 @Victim").se());     // BURN seconds → IGNITE ticks (x20)
        assertEquals("DURABILITY:-1:item", Mappings.aeEffect("REPAIR").se());
        assertEquals("KILL:@Victim", Mappings.aeEffect("KILL @Victim").se());
        assertEquals("EXTINGUISH:@Self", Mappings.aeEffect("EXTINGUISH @Self").se());
        assertEquals("DISARM:@Victim", Mappings.aeEffect("DISARM @Victim").se());
        assertEquals("LIGHTNING:@Victim", Mappings.aeEffect("LIGHTNING @Victim").se());  // visual lightning (no damage)
        assertEquals("RUN_COMMAND:eco give %player% 100",
                Mappings.aeEffect("CONSOLE_COMMAND:eco give %player% 100").se());
        // Not faithfully mappable → TODO, never silently wrong.
        assertFalse(Mappings.aeEffect("LIGHTNING:true @Victim").mapped(), "real-damage lightning → TODO");
        assertFalse(Mappings.aeEffect("PLAYER_COMMAND:spawn").mapped(), "run-as-player command → TODO");
        assertFalse(Mappings.aeEffect("CONSOLE_COMMAND:title %p% times 5:10:5").mapped(), "colon command body → TODO");
    }

    @Test
    void aeAreaAndMiningSelectorsAreNotMapped() {
        // StarEnchants @Aoe still differs from AE's (default radius 4 vs 1, no entity cap vs AE's always-20),
        // so AoE remains TODO'd rather than retargeted; the mining/block selectors await a location-target seam.
        assertFalse(Mappings.aeEffect("DAMAGE:4 @Aoe{r=5}").mapped(), "AoE has no faithful @Aoe equivalent → TODO");
        assertFalse(Mappings.aeEffect("DAMAGE:4 @Aoe").mapped(), "bare AoE (radius differs) → TODO");
        assertFalse(Mappings.aeEffect("DAMAGE:4 @Trench{...}").mapped(), "mining selectors need a location target → TODO");
    }

    @Test
    void aeNearestPlayerMapsToTheNewSelector() {
        // v3.1 §A: StarEnchants now has a faithful @NearestPlayer using the same {r=N} syntax.
        var mapped = Mappings.aeEffect("DAMAGE:4 @NearestPlayer{r=5}");
        assertTrue(mapped.mapped(), "@NearestPlayer is now a real selector → mapped, not TODO");
        assertTrue(mapped.se().endsWith("@NearestPlayer{r=5}"), () -> "selector preserved: " + mapped.se());
    }

    @Test
    void aeSelectorsAreTriggerDirectionAware() {
        // ATTACK direction: AE @Attacker = the wielder → @Self; AE @Victim = the foe → @Victim.
        assertEquals("MODIFY_HEALTH:4:give:@Self", Mappings.aeEffect("ADD_HEALTH:4 @Attacker", false).se());
        assertEquals("DAMAGE:4:@Victim", Mappings.aeEffect("DAMAGE:4 @Victim", false).se());
        // DEFENSE direction: AE @Victim = the wielder → @Self; AE @Attacker = the foe → @Attacker.
        assertEquals("MODIFY_HEALTH:4:give:@Self", Mappings.aeEffect("ADD_HEALTH:4 @Victim", true).se());
        assertEquals("DAMAGE:4:@Attacker", Mappings.aeEffect("DAMAGE:4 @Attacker", true).se());
    }

    @Test
    void aeMessageWithAnEmbeddedSelectorIsNotRetargeted() {
        // A non-selector "@word" is text and maps to the actor; a real @selector target → TODO (recipient differs).
        assertEquals("MESSAGE:contact @admin", Mappings.aeEffect("MESSAGE:contact @admin").se());
        assertFalse(Mappings.aeEffect("MESSAGE:Visit @Self now").mapped(), "an embedded @selector → TODO");
        assertFalse(Mappings.aeEffect("MESSAGE:hello @Victim").mapped(), "a trailing target selector → TODO");
        // ACTIONBAR collapses onto the canonical MESSAGE via the actionbar channel (§C); MESSAGE stays chat.
        assertEquals("MESSAGE:Charged:actionbar", Mappings.aeEffect("ACTIONBAR:Charged").se());
    }

    @Test
    void translatesAeConditionsToAllowGates() {
        assertEquals("!(%victim.health% > 5)", Mappings.aeCondition("%victim health% > 5 : %stop%").expr());
        assertEquals("%sneaking% == true", Mappings.aeCondition("%player is sneaking% = true : %allow%").expr());
        assertEquals("%combo% > 0 && %combo% < 5",
                Mappings.aeCondition("%combo% > 0 && %combo% < 5 : %continue%").expr());
        // Flow/chance results now map to clause forms (v3.1 §A) — flagged as clauses so the reader does not &&-join them.
        MigratedCondition force = Mappings.aeCondition("%victim health% > 5 : %force%");
        assertEquals("%victim.health% > 5 : %force%", force.expr());
        assertTrue(force.clauseForm(), "a %force% result is a clause, not a joinable gate");
        MigratedCondition chance = Mappings.aeCondition("%victim health% > 5 : +50 %chance%");
        assertEquals("%victim.health% > 5 : +50 %chance%", chance.expr());
        assertTrue(chance.clauseForm());
        assertEquals("%victim.health% < 3 : -25 %chance%",
                Mappings.aeCondition("%victim health% < 3 : -25 %chance%").expr());
        // No StarEnchants equivalent → TODO, never a silently-wrong gate.
        assertFalse(Mappings.aeCondition("%block type% contains ORE : %allow%").mapped(), "contains operator → TODO");
        assertFalse(Mappings.aeCondition("%victim food% > 5 : %allow%").mapped(), "no 'food' fact → TODO");
        assertFalse(Mappings.aeCondition("%victim health% > 5").mapped(), "missing ' : <result>' → TODO");
    }

    @Test
    void migratesAeConditionsToACompilableAllowGate(@TempDir Path dir) throws IOException {
        Migrator.Result result = Migrator.advancedEnchantments(AE_COND, SPECS);
        String yml = result.files().get("enchants/sneakstrike.yml");
        // The mappable line becomes a StarEnchants allow-gate; the unmappable one is a TODO, not dropped.
        assertTrue(yml.contains("condition: \"%sneaking% == true\""),
                () -> "expected a mapped allow-gate condition, got:\n" + yml);
        assertTrue(yml.contains("# TODO condition (port manually):"),
                () -> "expected a TODO for the unmappable contains-condition, got:\n" + yml);
        // And the mapped condition compiles clean through the real loader (the gate is valid Expr).
        Library library = compile(result.files(), dir);
        String blocking = library.diagnostics().stream().filter(Diagnostic::blocking)
                .map(Diagnostic::toString).collect(Collectors.joining("\n  "));
        assertFalse(library.hasErrors(), () -> "migrated AE condition output should compile clean:\n  " + blocking);
        assertEquals(1, library.snapshot().abilityCount());
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
