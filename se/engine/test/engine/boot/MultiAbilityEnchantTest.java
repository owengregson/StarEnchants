package engine.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import engine.condition.BuiltinVars;
import engine.condition.FactBuffer;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.pipeline.GateOutcome;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.selector.kind.BuiltinSelectors;
import engine.sink.ModernDispatchSink;
import engine.stores.CooldownStore;
import engine.trigger.BuiltinTriggers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.PermissiveResolvers;
import testfx.SyncSchedulerBackend;

/**
 * The multi-ability enchant level end to end: authored YAML → loader → compiler → snapshot → a real
 * pipeline run. The contract a unit test cannot reach is that the SECOND block is a first-class ability —
 * it gets its own dense id and its own effects actually execute, rather than compiling into a key nothing
 * ever activates.
 */
class MultiAbilityEnchantTest {

    private static final Compiler COMPILER = ContentCompiler.production(PermissiveResolvers.INSTANCE);
    private static final UUID ACTOR = UUID.randomUUID();

    /** Two blocks on one level, distinguished by their ignite duration so the sink tells them apart. */
    private static final String TWO_BLOCK = """
        display: "Phoenix"
        trigger: "%s"
        levels:
          2:
            abilities:
              - { effects: [{ IGNITE: { duration: 60, who: "@Victim" } }] }
              - { effects: [{ IGNITE: { duration: 120, who: "@Self" } }] }
        """;

    @TempDir
    Path root;

    private RuntimeHandles handles;
    private AbilityExecutor executor;
    private int triggerId;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
        // The SAME registries ContentCompiler.production stamps dense effect/selector kind ids against
        // (ADR-0039). A hand-built subset registry would renumber those ids and silently mis-dispatch —
        // an end-to-end test has to run the production vocabulary for the stamps to mean anything.
        executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        triggerId = BuiltinTriggers.registry().idOf("ATTACK").orElseThrow();
    }

    private Snapshot load(String yaml) throws Exception {
        Path enchants = Files.createDirectories(root.resolve("content/enchants"));
        Files.writeString(enchants.resolve("phoenix.yml"), yaml, StandardCharsets.UTF_8);
        Library lib = LibraryLoader.load(root.resolve("content"), COMPILER, 0);
        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        return lib.snapshot();
    }

    @Test
    void bothBlocksBecomeDistinctAbilitiesWithDenseKeys() throws Exception {
        Snapshot snap = load(TWO_BLOCK.formatted("ATTACK"));

        Ability first = snap.byStableKey("enchants/phoenix/2");
        Ability second = snap.byStableKey("enchants/phoenix/2/a1");
        assertNotNull(first, "the first block keeps the bare per-level key items already store");
        assertNotNull(second, "the second block takes the dense /a1 suffix");
        assertNotEquals(first.id(), second.id());
        assertEquals(2, first.level());
        assertEquals(2, second.level(), "both blocks belong to the same enchant level");
    }

    @Test
    void theSecondBlockActuallyExecutesItsOwnEffects() throws Exception {
        Snapshot snap = load(TWO_BLOCK.formatted("ATTACK"));
        Player actor = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        int[] candidates = {
                snap.byStableKey("enchants/phoenix/2").id(),
                snap.byStableKey("enchants/phoenix/2/a1").id()};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(snap.abilities(), candidates,
                Activation.builder(ACTOR, 0, triggerId, 0L).build(),
                new ActivationContext(actor, victim, null, null), sink, snap.stableKeys());
        sink.flush();

        assertEquals(2, activated, "both blocks of the level activate");
        verify(victim).setFireTicks(60);   // block 1 — @Victim
        verify(actor).setFireTicks(120);   // block 2 — @Self, its OWN selector and its OWN duration
    }

    @Test
    void aSingleBlockLevelStillResolvesFromItsBareKeyOnly() throws Exception {
        // The shape every shipped enchant uses: no /a1 exists, and the bare key an item's PDC stores is
        // unchanged. This is the back-compat invariant the fan-out must not disturb.
        Snapshot snap = load("""
                display: "Phoenix"
                trigger: "ATTACK"
                levels:
                  2: { effects: [{ IGNITE: { duration: 60, who: "@Victim" } }] }
                """);

        assertNotNull(snap.byStableKey("enchants/phoenix/2"));
        assertEquals(1, snap.abilityCount());
        assertEquals(List.of("enchants/phoenix/2"), keysOf(snap.stableKeys(), snap.abilityCount()));
    }

    private static List<String> keysOf(StableKeyIndex keys, int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(keys::keyOf).toList();
    }

    @Test
    void anExpressionChanceCompilesAndGatesTheActivation() throws Exception {
        // EXPR_CHANCE end to end on the production vocabulary: %recentattackers% is a real fact slot, so the
        // erase stage must have unioned it into the ability's FactMask for the roll to see anything but 0.
        Snapshot snap = load("""
                display: "Phoenix"
                trigger: "ATTACK"
                levels:
                  1:
                    chance: "min(50, %recentattackers% * 10)"
                    effects: [{ IGNITE: { duration: 60, who: "@Victim" } }]
                """);
        Ability ability = snap.byStableKey("enchants/phoenix/1");
        assertNotNull(ability.chanceExpr(), "an expression chance survives to the runtime record");

        FactBuffer facts = new FactBuffer(64, 0, 0);
        int slot = BuiltinVars.vocabulary().bindings().get("recentattackers").slot();
        assertTrue(ability.factMask().readsNum(slot), "the populator must be told to compute the roll's fact");

        facts.setNumber(slot, 2.0); // → 20% chance
        assertEquals(GateOutcome.ACTIVATED, gate(ability, facts, 19.0));
        assertEquals(GateOutcome.CHANCE_FAILED, gate(ability, facts, 21.0));

        facts.setNumber(slot, 8.0); // → 80 raw, held at 50 by the author's own min()
        assertEquals(GateOutcome.ACTIVATED, gate(ability, facts, 49.0));
        assertEquals(GateOutcome.CHANCE_FAILED, gate(ability, facts, 51.0));
    }

    @Test
    void theRelationFactsSurviveTheWholeCompileAndReachTheMask() throws Exception {
        // Every new fact gets end-to-end coverage through the FULL registry path, not just a populator pair:
        // a stage that rebuilds a record can drop a field with no diagnostic (the chanceExpr bug), and only a
        // compile-to-runtime walk sees it. A missing mask bit here means the populator would skip the scan.
        Snapshot snap = load("""
                display: "Phoenix"
                trigger: "ATTACK"
                levels:
                  1:
                    condition: "%victim.relation% == \\"ALLY\\" && %nearbyallies% > 1"
                    effects: [{ IGNITE: { duration: 60, who: "@Victim" } }]
                """);
        Ability ability = snap.byStableKey("enchants/phoenix/1");
        assertNotNull(ability.condition(), "the condition survives to the runtime record");

        var vocab = BuiltinVars.vocabulary().bindings();
        assertTrue(ability.factMask().readsStr(vocab.get("victim.relation").slot()));
        assertTrue(ability.factMask().readsNum(vocab.get("nearbyallies").slot()));
    }

    @Test
    void theWave1b3FactsSurviveTheWholeCompileAndReachTheMask() throws Exception {
        Snapshot snap = load("""
                display: "Phoenix"
                trigger: "DEFENSE"
                levels:
                  1:
                    condition: "%posthit.health% <= 0 && !%victim.fromspawner% && %heldticks% > 20 \
                                && %actor.souls% > 0 && %victim.souls% > 0 \
                                && %impactheight% > 1 && %projectilekind% == \\"ARROW\\""
                    effects: [{ IGNITE: { duration: 60, who: "@Victim" } }]
                """);
        Ability ability = snap.byStableKey("enchants/phoenix/1");
        assertNotNull(ability.condition(), "the condition survives to the runtime record");

        var vocab = BuiltinVars.vocabulary().bindings();
        assertTrue(ability.factMask().readsNum(vocab.get("posthit.health").slot()));
        assertTrue(ability.factMask().readsFlag(vocab.get("victim.fromspawner").slot()));
        assertTrue(ability.factMask().readsNum(vocab.get("heldticks").slot()));
        assertTrue(ability.factMask().readsNum(vocab.get("actor.souls").slot()));
        assertTrue(ability.factMask().readsNum(vocab.get("victim.souls").slot()));
        assertTrue(ability.factMask().readsNum(vocab.get("impactheight").slot()));
        assertTrue(ability.factMask().readsStr(vocab.get("projectilekind").slot()));
    }

    private GateOutcome gate(Ability ability, FactBuffer facts, double roll) {
        return new ActivationPipeline(new CooldownStore(), SoulSpender.NONE).evaluate(ability,
                Activation.builder(ACTOR, 0, triggerId, 0L).facts(facts).chanceRoll(() -> roll).build());
    }
}
