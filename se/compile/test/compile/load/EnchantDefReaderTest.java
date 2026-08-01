package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.def.AbilityDef;
import compile.model.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import schema.spec.D;
import schema.spec.ParamSpec;
import org.junit.jupiter.api.Test;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;

/** Unit tests for the enchant reader (ADR-0014): malformed input is a diagnostic, never an exception. */
class EnchantDefReaderTest {

    private static YamlNode root(String yaml, Diagnostics diags) {
        return YamlNode.compose("test.yml", yaml, diags);
    }

    private static IntSupplier counter() {
        int[] id = {0};
        return () -> id[0]++;
    }

    /** Assert a diagnostic with the given code was emitted — the contract, not just "something failed". */
    private static void assertCode(Diagnostics diags, DiagCode code) {
        assertTrue(diags.all().stream().anyMatch(d -> d.is(code)), () -> diags.all().toString());
    }

    @Test
    void validEnchantExpandsToOneAbilityPerLevel() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: "&cLifesteal"
            description: "Heal on hit."
            trigger: ATTACK
            applies-to: [SWORD, AXE]
            group: combat
            levels:
              1: { chance: 10, cooldown: 40, effects: [{ HEAL: { amount: 2, who: "@Self" } }] }
              2: { chance: 15, effects: [{ HEAL: { amount: 4, who: "@Self" } }, { MESSAGE: { text: hi, who: "@Self" } }] }
              3: { chance: 20, effects: [{ HEAL: { amount: 6, who: "@Self" } }] }
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/lifesteal", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("enchants/lifesteal", parsed.def().key());
        assertEquals("&cLifesteal", parsed.def().display());
        assertEquals(List.of("SWORD", "AXE"), parsed.def().appliesTo());
        assertEquals(3, parsed.def().maxLevel());

        List<AbilityDef> abilities = parsed.abilities();
        assertEquals(3, abilities.size());
        AbilityDef level2 = abilities.get(1);
        assertEquals("enchants/lifesteal/2", level2.stableKey());
        assertEquals(2, level2.level());
        assertEquals(15.0, level2.baseChance(), 1e-9);
        assertEquals(List.of("ATTACK"), level2.triggers());
        assertEquals("enchants/lifesteal", level2.suppressKey());
        assertEquals("enchants/lifesteal", level2.cdScopeEnchant());
        assertEquals("combat", level2.cdScopeGroup());
        assertEquals(2, level2.effects().size());
        assertEquals("HEAL", level2.effects().get(0).head());
        assertEquals(40, abilities.get(0).cooldownTicks());
    }

    @Test
    void levelAbilitiesCarryIndependentHooksAndStableKeys() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            display: Drunk
            applies-to: [HELMET]
            stacking: HIGHEST
            chance: 11
            levels:
              1:
                abilities:
                  - trigger: PASSIVE
                    effects: [{ POTION: { effect: SLOW, level: 1, duration: 200 } }]
                  - trigger: ATTACK
                    chance: 25
                    effects: [{ HEAL: { amount: 2 } }]
            """;

        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/drunk", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(EnchantDef.Stacking.HIGHEST, parsed.def().stacking());
        assertEquals(2, parsed.abilities().size());
        assertEquals("enchants/drunk/1", parsed.abilities().get(0).stableKey());
        assertEquals(List.of("PASSIVE"), parsed.abilities().get(0).triggers());
        assertEquals(11.0, parsed.abilities().get(0).baseChance(), 1e-9,
                "an ability with no chance inherits the root through the level");
        assertEquals("enchants/drunk/1/a1", parsed.abilities().get(1).stableKey());
        assertEquals(List.of("ATTACK"), parsed.abilities().get(1).triggers());
        assertEquals(25.0, parsed.abilities().get(1).baseChance(), 1e-9);
    }

    @Test
    void noSoulsEffectsInheritAndCanBeOverriddenPerAbility() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: DEFENSE
            soul-cost: 2
            no-souls-effects: [{ MESSAGE: { text: root-fail } }]
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
              2:
                abilities:
                  - effects: [{ HEAL: { amount: 3 } }]
                  - no-souls-effects: [{ MESSAGE: { text: nested-fail } }]
                    effects: [{ HEAL: { amount: 4 } }]
            """;

        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/soulful", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(3, parsed.abilities().size());
        assertEquals("root-fail", parsed.abilities().get(0).noSoulEffects().get(0).named().get("text"));
        assertEquals("root-fail", parsed.abilities().get(1).noSoulEffects().get(0).named().get("text"));
        assertEquals("nested-fail", parsed.abilities().get(2).noSoulEffects().get(0).named().get("text"));
    }

    @Test
    void invalidStackingModeIsBlocking() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            stacking: sometimes
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_ENCHANT);
    }

    @Test
    void disabledEnvironmentsComposeWithRootAndNestedAbilityConditions() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            disabled-environments: [THE_END]
            condition: '%damage% > 2'
            levels:
              1:
                abilities:
                  - effects: [{ HEAL: { amount: 2 } }]
                  - disabled-environments: [NETHER, THE_END]
                    condition: '%damage% < 10'
                    effects: [{ HEAL: { amount: 3 } }]
            """;

        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("(%damage% > 2) && %actor.environment% != \"THE_END\"",
                parsed.abilities().get(0).conditionExpr());
        assertEquals("(%damage% < 10) && %actor.environment% != \"NETHER\""
                        + " && %actor.environment% != \"THE_END\"",
                parsed.abilities().get(1).conditionExpr());
    }

    @Test
    void invalidDisabledEnvironmentIsBlocking() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            disabled-environments: ['THE END']
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
            """;

        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);

        assertCode(diags, DiagCode.E_LOAD_ENCHANT);
    }

    @Test
    void missingTriggerIsAnError() {
        Diagnostics diags = new Diagnostics();
        String yaml = "levels:\n  1: { chance: 10, effects: [{ HEAL: { amount: 2 } }] }\n";
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_ENCHANT_TRIGGER);
    }

    @Test
    void missingLevelsIsAnError() {
        Diagnostics diags = new Diagnostics();
        EnchantDefReader.read("enchants/x", root("trigger: ATTACK\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_ENCHANT_LEVELS);
    }

    @Test
    void negativeChanceIsReported() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: -1, effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_CHANCE);
    }

    @Test
    void chanceAboveOneHundredIsPreservedAsGuaranteedThreshold() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: 102, effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(102.0, parsed.abilities().get(0).baseChance(), 1e-9);
    }

    @Test
    void duplicateKeysParseAsLastWins() {
        Diagnostics diags = new Diagnostics();
        // SnakeYAML 2.x rejects dup keys by default, 1.x does not; loader forces
        // allow-duplicate-keys so a file behaves identically on every server's SnakeYAML
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: 10, chance: 25, effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(25.0, parsed.abilities().get(0).baseChance(), 1e-9);
    }

    @Test
    void nanChanceIsReported() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: NaN, effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_CHANCE);
    }

    @Test
    void nonMappingFileIsAnError() {
        Diagnostics diags = new Diagnostics();
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root("- just\n- a\n- list\n", diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_ENCHANT);
        assertTrue(parsed.abilities().isEmpty());
    }

    @Test
    void booleanFlagsAcceptTheOneTruthyVocabulary() {
        // ADR-0042: every loader boolean parses the same vocabulary — `yes`/`on` must not silently read false.
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            requires: [enchants/base]
            removes-required: yes
            suppress-immune: on
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertTrue(parsed.def().removesRequired());
        assertTrue(parsed.abilities().get(0).suppressImmune());
    }

    @Test
    void aGarbageBooleanWarnsAndFallsBackFalse() {
        // The silent-typo case: `ture` must surface in /se problems, not compile to an unprotected buff.
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            suppress-immune: ture
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertFalse(parsed.abilities().get(0).suppressImmune());
        assertCode(diags, DiagCode.W_LOAD_BOOL);
    }

    @Test
    void repeatingInitialDelayThreadsFromYamlToCompiledAbility() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: REPEATING
            repeat: 160
            initial-delay: 20
            levels:
              1:
                abilities:
                  - effects: [{ HEAL: { amount: 2 } }]
                  - repeat: 40
                    effects: [{ HEAL: { amount: 3 } }]
                  - repeat: 80
                    initial-delay: 5
                    effects: [{ HEAL: { amount: 4 } }]
            """;
        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/commander", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(160, parsed.abilities().get(0).repeatTicks());
        assertEquals(20, parsed.abilities().get(0).repeatInitialDelayTicks());
        assertEquals(40, parsed.abilities().get(1).repeatTicks());
        assertEquals(40, parsed.abilities().get(1).repeatInitialDelayTicks(),
                "overriding repeat without initial-delay defaults first run to the new period");
        assertEquals(80, parsed.abilities().get(2).repeatTicks());
        assertEquals(5, parsed.abilities().get(2).repeatInitialDelayTicks());

        Snapshot snapshot = Compiler.of(MapSpecRegistry.of(heal())).compile(parsed.abilities(), 1, diags);
        assertEquals(20, snapshot.byStableKey("enchants/commander/1").repeatInitialDelayTicks(),
                "initial-delay must survive reader through erase into runtime Ability");
    }

    @Test
    void levelRepeatAndInitialDelayOverrideRootAndFlowIntoNestedAbilities() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: REPEATING
            repeat: 160
            initial-delay: 20
            levels:
              1:
                repeat: 120
                effects: [{ HEAL: { amount: 1 } }]
              2:
                repeat: 80
                initial-delay: 5
                abilities:
                  - effects: [{ HEAL: { amount: 2 } }]
                  - repeat: 40
                    effects: [{ HEAL: { amount: 3 } }]
              3:
                effects: [{ HEAL: { amount: 4 } }]
            """;

        EnchantDefReader.Parsed parsed =
                EnchantDefReader.read("enchants/pulses", root(yaml, diags), counter(), diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(120, parsed.abilities().get(0).repeatTicks());
        assertEquals(120, parsed.abilities().get(0).repeatInitialDelayTicks(),
                "a level repeat override defaults its first run to that level period");
        assertEquals(80, parsed.abilities().get(1).repeatTicks());
        assertEquals(5, parsed.abilities().get(1).repeatInitialDelayTicks(),
                "nested abilities inherit both level timing knobs");
        assertEquals(40, parsed.abilities().get(2).repeatTicks());
        assertEquals(40, parsed.abilities().get(2).repeatInitialDelayTicks(),
                "an ability repeat override defaults its own first run to its period");
        assertEquals(160, parsed.abilities().get(3).repeatTicks());
        assertEquals(20, parsed.abilities().get(3).repeatInitialDelayTicks(),
                "levels without timing overrides inherit the root timing");
    }

    @Test
    void suppressImmuneThreadsFromYamlToTheCompiledAbility() {
        // The full producer seam (f9): reader → AbilityDef → lower → resolve → erase → Ability. A stage
        // quietly reverting to a back-compat ctor (defaulting the flag) is the regression class that shipped
        // the dormant SUPPRESS no-op; the two Mockito-stubbed runtime tests cannot see it.
        Diagnostics diags = new Diagnostics();
        IntSupplier ids = counter();
        String immuneYaml = """
            trigger: ATTACK
            suppress-immune: true
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
            """;
        String plainYaml = """
            trigger: ATTACK
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
            """;
        List<AbilityDef> defs = new ArrayList<>();
        defs.addAll(EnchantDefReader.read("enchants/immune", root(immuneYaml, diags), ids, diags).abilities());
        defs.addAll(EnchantDefReader.read("enchants/plain", root(plainYaml, diags), ids, diags).abilities());

        Snapshot snap = Compiler.of(MapSpecRegistry.of(heal())).compile(defs, 1, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertTrue(snap.byStableKey("enchants/immune/1").suppressImmune(),
                "suppress-immune: true must survive reader → lower → resolve → erase");
        assertFalse(snap.byStableKey("enchants/plain/1").suppressImmune());
    }

    private static ParamSpec heal() {
        return ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build();
    }
}
