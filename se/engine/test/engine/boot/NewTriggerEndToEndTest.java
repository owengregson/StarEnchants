package engine.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.Snapshot;
import engine.condition.BuiltinVars;
import engine.effect.kind.BuiltinEffects;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
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
import java.util.stream.Stream;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import testfx.Envs;
import testfx.PermissiveResolvers;
import testfx.SyncSchedulerBackend;

/**
 * The wave-1c triggers end to end: authored YAML → loader → compiler → snapshot → a real pipeline run on the
 * FULL production registries. A trigger name that the compiler fails to intern lowers to an ability nothing
 * ever routes — no diagnostic, no failing unit test at either end, just an enchant that never fires. Only a
 * compile-to-run walk over the same registry the runtime stamps dense ids against catches that.
 */
class NewTriggerEndToEndTest {

    private static final Compiler COMPILER = ContentCompiler.production(PermissiveResolvers.INSTANCE);
    private static final UUID ACTOR = UUID.randomUUID();

    /** The four triggers this wave adds — each must survive the same compile-and-route walk. */
    private static final List<String> WAVE_1C = List.of("HURT");

    @TempDir
    Path root;

    private RuntimeHandles handles;
    private AbilityExecutor executor;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
        executor = new AbilityExecutor(BuiltinEffects.registry(), BuiltinSelectors.registry(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
    }

    @TestFactory
    Stream<DynamicTest> everyNewTriggerCompilesAndRoutesToItsRegistryId() {
        return WAVE_1C.stream().map(trigger -> DynamicTest.dynamicTest(trigger, () -> {
            String key = trigger.toLowerCase(java.util.Locale.ROOT);
            Snapshot snap = load(key, """
                    display: "Probe"
                    trigger: "%s"
                    levels:
                      1: { effects: [{ IGNITE: { duration: 60, who: "@Self" } }] }
                    """.formatted(trigger));
            Ability ability = snap.byStableKey("enchants/" + key + "/1");
            assertNotNull(ability, "the authored level compiled to an ability");
            int id = BuiltinTriggers.registry().idOf(trigger).orElseThrow();
            assertTrue(ability.firesOn(id), "the compiled triggerMask must name the runtime's own id");

            Player actor = mock(Player.class);
            ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
            int activated = executor.run(snap.abilities(), new int[] {ability.id()},
                    Activation.builder(ACTOR, 0, id, 0L).build(),
                    new ActivationContext(actor, null, null, null), sink, snap.stableKeys());
            sink.flush();

            assertEquals(1, activated, "the ability actually walks on the trigger it authored");
            verify(actor).setFireTicks(60);
        }));
    }

    @Test
    void hurtGatesOnTheDamageCauseThroughTheWholeCompile() throws Exception {
        // HURT's whole point is that the author, not the trigger, picks the causes — so %damagecause% has to
        // reach the runtime record's fact mask, or the populator skips the slot and the condition reads "".
        Snapshot snap = load("inversion", """
                display: "Inversion"
                trigger: "HURT"
                levels:
                  1:
                    condition: "%damagecause% == \\"POISON\\""
                    effects: [{ IGNITE: { duration: 60, who: "@Self" } }]
                """);
        Ability ability = snap.byStableKey("enchants/inversion/1");
        assertNotNull(ability.condition(), "the condition survives to the runtime record");
        assertTrue(ability.factMask().readsStr(BuiltinVars.vocabulary().bindings().get("damagecause").slot()));
    }

    private Snapshot load(String key, String yaml) throws Exception {
        Path enchants = Files.createDirectories(root.resolve("content/enchants"));
        Files.writeString(enchants.resolve(key + ".yml"), yaml, StandardCharsets.UTF_8);
        Library lib = LibraryLoader.load(root.resolve("content"), COMPILER, 0);
        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        return lib.snapshot();
    }
}
