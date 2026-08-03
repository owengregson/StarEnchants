package compile.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.Compiler;
import compile.MapSpecRegistry;
import compile.def.AbilityDef;
import compile.model.Ability;
import compile.model.Snapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import schema.spec.D;
import schema.spec.ParamSpec;
import org.junit.jupiter.api.Test;
import schema.diag.DiagCode;
import schema.diag.Diagnostics;
import schema.diag.Source;
import testfx.PermissiveResolvers;

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
    void outOfRangeChanceIsReported() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1: { chance: 150, effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_CHANCE);
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

    @Test
    void noSoulsMessageThreadsFromYamlToTheCompiledAbility() {
        // The same producer seam as suppress-immune: the gate-10 line is authored per ability, so a stage that
        // rebuilds the record field-by-field drops it with no diagnostic and no other failing test.
        Diagnostics diags = new Diagnostics();
        IntSupplier ids = counter();
        String message = "&cNot enough souls, mortal.";
        String costlyYaml = """
            trigger: ATTACK
            levels:
              1: { soul-cost: 2, no-souls-message: "%s", effects: [{ HEAL: { amount: 2 } }] }
            """.formatted(message);
        String silentYaml = """
            trigger: ATTACK
            levels:
              1: { soul-cost: 2, effects: [{ HEAL: { amount: 2 } }] }
            """;
        List<AbilityDef> defs = new ArrayList<>();
        defs.addAll(EnchantDefReader.read("enchants/costly", root(costlyYaml, diags), ids, diags).abilities());
        defs.addAll(EnchantDefReader.read("enchants/silent", root(silentYaml, diags), ids, diags).abilities());

        Snapshot snap = Compiler.of(MapSpecRegistry.of(heal())).compile(defs, 1, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(message, snap.byStableKey("enchants/costly/1").noSoulsMessage(),
                "no-souls-message must survive reader → lower → resolve → erase");
        assertNull(snap.byStableKey("enchants/silent/1").noSoulsMessage());
    }

    @Test
    void theSoulEnvelopeKnobsThreadFromYamlAndInheritPerKey() {
        // The producer seam for the three knobs, plus the scope rule they share with soul-cost: a knob
        // declared at the root is the level's default, and a level that declares it AGAIN wins. Reading the
        // three off one node instead of three would quietly break that per-key override.
        Diagnostics diags = new Diagnostics();
        IntSupplier ids = counter();
        String yaml = """
            trigger: ATTACK
            soul-cost: 2
            soul-cost-carried: true
            no-souls-sound: ENTITY_VILLAGER_NO
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
              2: { soul-cost-carried: false, effects: [{ HEAL: { amount: 2 } }] }
            """;
        List<AbilityDef> defs =
                new ArrayList<>(EnchantDefReader.read("enchants/wallet", root(yaml, diags), ids, diags).abilities());

        Snapshot snap = Compiler.of(MapSpecRegistry.of(heal()), head -> compile.model.Affinity.CONTEXT_LOCAL,
                        MapSpecRegistry.of(), head -> null, PermissiveResolvers.INSTANCE).compile(defs, 1, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        Ability inherited = snap.byStableKey("enchants/wallet/1");
        assertTrue(inherited.soulCostCarried(), "the root knob is the level's default");
        assertTrue(inherited.noSoulsSound() >= 0, "the sound token survives to an interned id");
        assertFalse(snap.byStableKey("enchants/wallet/2").soulCostCarried(),
                "a level re-declaring the knob overrides the root — including back to false");
        assertTrue(snap.byStableKey("enchants/wallet/2").noSoulsSound() >= 0,
                "overriding ONE knob must not drop the siblings it never mentioned");
    }

    @Test
    void escalatingSoulCostKnobsThreadFromYamlToTheCompiledAbility() {
        // The same producer seam as no-souls-message, for the three knobs only gate 10 reads: a stage that
        // rebuilds the record field-by-field drops them with no diagnostic. Three distinct non-default values
        // so a transposition (growth read as cap, cap as period) fails here.
        Diagnostics diags = new Diagnostics();
        IntSupplier ids = counter();
        String levelYaml = """
            trigger: ATTACK
            levels:
              1: { soul-cost: 500, soul-cost-growth: 2.0, soul-cost-cap: 8000, soul-cost-decay-period: 12000,
                   effects: [{ HEAL: { amount: 2 } }] }
            """;
        // The knobs must ride the same block → level → root cascade every other per-level knob does.
        String rootYaml = """
            trigger: ATTACK
            soul-cost: 500
            soul-cost-growth: 2.0
            soul-cost-cap: 8000
            soul-cost-decay-period: 12000
            levels:
              1: { effects: [{ HEAL: { amount: 2 } }] }
            """;
        String plainYaml = """
            trigger: ATTACK
            levels:
              1: { soul-cost: 500, effects: [{ HEAL: { amount: 2 } }] }
            """;
        List<AbilityDef> defs = new ArrayList<>();
        defs.addAll(EnchantDefReader.read("enchants/escalating", root(levelYaml, diags), ids, diags).abilities());
        defs.addAll(EnchantDefReader.read("enchants/inherited", root(rootYaml, diags), ids, diags).abilities());
        defs.addAll(EnchantDefReader.read("enchants/static", root(plainYaml, diags), ids, diags).abilities());

        Snapshot snap = Compiler.of(MapSpecRegistry.of(heal())).compile(defs, 1, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        for (String key : List.of("enchants/escalating/1", "enchants/inherited/1")) {
            Ability escalating = snap.byStableKey(key);
            assertEquals(2.0, escalating.soulCostGrowth(), 1e-9, key);
            assertEquals(8000, escalating.soulCostCap(), key);
            assertEquals(12000, escalating.soulCostDecayPeriod(), key);
        }
        // Unauthored = today's static price, uncapped, never decaying.
        Ability plain = snap.byStableKey("enchants/static/1");
        assertEquals(1.0, plain.soulCostGrowth(), 1e-9);
        assertEquals(0, plain.soulCostCap());
        assertEquals(0, plain.soulCostDecayPeriod());
    }

    // ── Multi-ability levels: a level may fan into N ability blocks, keyed like every other multi-ability
    // source (crystal/mask/reforge/pet) — first block keeps the bare key, the rest take /a1, /a2, … dense.

    private static final String TWO_BLOCK_YAML = """
        trigger: ATTACK
        group: combat
        chance: 100
        levels:
          2:
            abilities:
              - { chance: 40, cooldown: 60, condition: "%sneaking% == true",
                  effects: [{ HEAL: { amount: 2 } }] }
              - { trigger: DEFENSE, soul-cost: 3, repeat: 20,
                  effects: [{ HEAL: { amount: 9 } }] }
        """;

    @Test
    void aTwoBlockLevelFansIntoTwoAbilitiesWithDenseKeys() {
        Diagnostics diags = new Diagnostics();
        List<AbilityDef> abilities =
                EnchantDefReader.read("enchants/phoenix", root(TWO_BLOCK_YAML, diags), counter(), diags).abilities();

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(2, abilities.size());
        assertEquals(List.of("enchants/phoenix/2", "enchants/phoenix/2/a1"),
                abilities.stream().map(AbilityDef::stableKey).toList());
    }

    @Test
    void eachBlockCarriesItsOwnKnobsAndInheritsTheRest() {
        Diagnostics diags = new Diagnostics();
        List<AbilityDef> abilities =
                EnchantDefReader.read("enchants/phoenix", root(TWO_BLOCK_YAML, diags), counter(), diags).abilities();
        AbilityDef first = abilities.get(0);
        AbilityDef second = abilities.get(1);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals(40.0, first.baseChance(), 1e-9);
        assertEquals(60, first.cooldownTicks());
        assertEquals("%sneaking% == true", first.conditionExpr());
        assertEquals(List.of("ATTACK"), first.triggers());
        assertEquals("HEAL", first.effects().get(0).head());

        assertEquals(List.of("DEFENSE"), second.triggers(), "a block may override the enchant's trigger");
        assertEquals(3, second.soulCost());
        assertEquals(20, second.repeatTicks());
        assertEquals(100.0, second.baseChance(), 1e-9, "an undeclared knob still falls back to the root");
        assertEquals(0, second.cooldownTicks(), "the sibling block's cooldown must not leak across");

        // Both blocks are the SAME enchant: one suppression key, one cooldown scope, one level.
        for (AbilityDef ability : abilities) {
            assertEquals("enchants/phoenix", ability.suppressKey());
            assertEquals("enchants/phoenix", ability.cdScopeEnchant());
            assertEquals("combat", ability.cdScopeGroup());
            assertEquals(2, ability.level());
        }
    }

    @Test
    void aSingleBlockLevelKeepsTheBareKeyAndMatchesTheLegacyShape() {
        // Back-compat is byte-stable: existing items store enchants/<name>/<level>, so a one-block
        // abilities: list must produce the same key the direct condition/effects shape does — no /a0.
        Diagnostics diags = new Diagnostics();
        String legacy = """
            trigger: ATTACK
            levels:
              1: { chance: 25, cooldown: 40, condition: "%sneaking%", effects: [{ HEAL: { amount: 2 } }] }
            """;
        String listed = """
            trigger: ATTACK
            levels:
              1:
                abilities:
                  - { chance: 25, cooldown: 40, condition: "%sneaking%", effects: [{ HEAL: { amount: 2 } }] }
            """;
        AbilityDef fromLegacy = EnchantDefReader.read("enchants/x", root(legacy, diags), counter(), diags)
                .abilities().get(0);
        AbilityDef fromList = EnchantDefReader.read("enchants/x", root(listed, diags), counter(), diags)
                .abilities().get(0);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertEquals("enchants/x/1", fromLegacy.stableKey());
        // Whole-record equality, so a field the fan-out forgets to carry over fails here rather than slipping
        // past a hand-listed subset. Position-derived parts (defId, Source) are normalised away — the two
        // shapes legitimately sit at different lines; everything that decides BEHAVIOUR must match.
        assertEquals(normalise(fromLegacy), normalise(fromList));
        assertEquals(effectShape(fromLegacy), effectShape(fromList));
    }

    /** The def with its position-derived fields dropped, so two authoring shapes of one ability compare whole. */
    private static AbilityDef normalise(AbilityDef d) {
        // Every behaviour-deciding field is carried explicitly: a back-compat ctor here would default the
        // tail fields on BOTH sides and silently hide a fan-out that forgot to carry one.
        return new AbilityDef(d.sourceKind(), d.stableKey(), 0, d.level(), d.baseChance(), d.cooldownTicks(),
                d.soulCost(), d.triggers(), d.worldBlacklist(), d.conditionExpr(), List.of(), d.suppressKey(),
                d.cdScopeEnchant(), d.cdScopeGroup(), d.cdScopeType(), d.repeatTicks(),
                Source.ofFile("normalised.yml"), d.setPieces(), d.suppressImmune(), d.chanceExpr(),
                d.noSoulsMessage(), d.soulCostCarried(), d.noSoulsSound(), d.noSoulsParticle(),
                d.soulCostGrowth(), d.soulCostCap(), d.soulCostDecayPeriod());
    }

    /** Effect lines by head + named args; their embedded Source tracks the line they were written on. */
    private static List<String> effectShape(AbilityDef d) {
        return d.effects().stream().map(e -> e.head() + e.named()).toList();
    }

    @Test
    void abilitiesBesideASiblingEffectsListIsAmbiguousAndBlocks() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1:
                effects: [{ HEAL: { amount: 1 } }]
                abilities:
                  - { effects: [{ HEAL: { amount: 2 } }] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_ENCHANT_LEVEL);
    }

    @Test
    void anEmptyAbilitiesListBlocks() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1: { abilities: [] }
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_ENCHANT_LEVEL);
    }

    @Test
    void aNonMappingAbilityEntryIsReportedNotThrown() {
        Diagnostics diags = new Diagnostics();
        String yaml = """
            trigger: ATTACK
            levels:
              1:
                abilities:
                  - "just a string"
            """;
        EnchantDefReader.read("enchants/x", root(yaml, diags), counter(), diags);
        assertCode(diags, DiagCode.E_LOAD_ENCHANT_LEVEL);
    }

    @Test
    void multiAbilityKeysSurviveTheWholeCompile() {
        Diagnostics diags = new Diagnostics();
        Snapshot snap = Compiler.of(MapSpecRegistry.of(heal())).compile(
                EnchantDefReader.read("enchants/phoenix", root(TWO_BLOCK_YAML, diags), counter(), diags).abilities(),
                1, diags);

        assertFalse(diags.hasErrors(), () -> diags.all().toString());
        assertNotNull(snap.byStableKey("enchants/phoenix/2"));
        assertNotNull(snap.byStableKey("enchants/phoenix/2/a1"));
    }

    private static ParamSpec heal() {
        return ParamSpec.of("HEAL").param("amount", D.DOUBLE.min(0)).build();
    }
}
