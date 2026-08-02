package engine.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.Snapshot;
import compile.model.StableKeyIndex;
import engine.effect.EffectRegistry;
import engine.effect.kind.IgniteEffect;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.run.AbilityExecutor;
import engine.run.ActivationContext;
import engine.run.AreaScan;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.selector.kind.VictimSelector;
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
        executor = new AbilityExecutor(
                EffectRegistry.builder().register(new IgniteEffect()).build(),
                SelectorRegistry.builder().register(new VictimSelector()).register(new SelfSelector()).build(),
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
}
