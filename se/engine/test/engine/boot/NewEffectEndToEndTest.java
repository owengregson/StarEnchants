package engine.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import compile.Compiler;
import compile.load.Library;
import compile.load.LibraryLoader;
import compile.model.Ability;
import compile.model.CompiledEffect;
import compile.model.Snapshot;
import engine.effect.EffectRegistry;
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
import engine.stores.DotAmplifyStore;
import engine.stores.EngineStores;
import engine.stores.HeadTrophyStore;
import engine.stores.OutgoingDebuffStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
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
 * The wave-1d.2/1d.3 surface end to end: authored YAML → loader → compiler → snapshot → a real pipeline run on
 * the FULL production registries. Effect dispatch is by DENSE kind id stamped at compile (ADR-0039), so a kind
 * whose head the compiler cannot resolve, or whose id the executor indexes differently, lowers to an ability
 * that silently runs the wrong effect or none — with a green spec test at one end and a green sink test at the
 * other. Only a compile-to-run walk over the same registry the runtime stamps against catches it.
 */
class NewEffectEndToEndTest {

    private static final Compiler COMPILER = ContentCompiler.production(PermissiveResolvers.INSTANCE);
    private static final UUID ACTOR = UUID.randomUUID();

    /** The eight kinds this wave adds, with a minimal authored form of each. */
    private static final List<String> WAVE_1D2 = List.of(
            "PERIODIC_DAMAGE: { amount: 6, period: 20, duration: 120, who: \"@Victim\" }",
            "DOT_AMPLIFY_MARK: { causes: dot, factor: 3, duration: 60, who: \"@Victim\" }",
            "OUTGOING_DEBUFF: { percent: 50, duration: 80, cause: projectile, who: \"@Victim\" }",
            "DESPAWN: { who: \"@Victim\" }",
            "VIEWER_HIDE: { duration: 60, viewer: all, who: \"@Self\" }",
            "PROJECTILE_DRESSING: { type: COW, ttl: 200 }",
            "HEAD_TROPHY: { name: \"Skull of {VICTIM}\", who: \"@Victim\" }",
            "SUMMON_REBIND: { type: IRON_GOLEM, ttl: 600, who: \"@Victim\" }");

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
    Stream<DynamicTest> everyNewKindCompilesToItsOwnRegistryId() {
        EffectRegistry registry = BuiltinEffects.registry();
        return WAVE_1D2.stream().map(authored -> {
            String head = authored.substring(0, authored.indexOf(':'));
            return DynamicTest.dynamicTest(head, () -> {
                Snapshot snap = load(head.toLowerCase(Locale.ROOT).replace('_', '-'), authored);
                Ability ability = snap.byStableKey("enchants/" + head.toLowerCase(Locale.ROOT).replace('_', '-') + "/1");
                assertNotNull(ability, "the authored level compiled to an ability");
                assertEquals(1, ability.effects().length);
                CompiledEffect effect = ability.effects()[0];
                // The stamped dense id must index the SAME kind the head names, in the production registry.
                assertEquals(head, registry.kindsById()[effect.kindId()].spec().head(),
                        "the compiled kindId must index this kind in the runtime's own registry");
            });
        });
    }

    @Test
    void everyNewKindReachesItsSinkIntentOnARealRun() throws Exception {
        // One walk per store-writing kind: an ability that compiles but never reaches its intent is the dense-id
        // failure this file exists for, and only the store state proves the intent actually landed.
        EngineStores stores = EngineStores.fresh();
        Player actor = mock(Player.class);
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(ACTOR);
        when(victim.getUniqueId()).thenReturn(victimId);

        run("marker", """
                DOT_AMPLIFY_MARK: { causes: poison, factor: 4, duration: 60, who: "@Victim" }
                """.trim(), stores, actor, victim);
        assertEquals(4.0, stores.dotAmplify().factor(victimId, 0L, DotAmplifyStore.CAUSE_POISON));
        assertEquals(1.0, stores.dotAmplify().factor(victimId, 0L, DotAmplifyStore.CAUSE_WITHER),
                "the authored cause filter survives the whole compile");

        run("nerf", """
                OUTGOING_DEBUFF: { percent: 50, duration: 80, cause: projectile, feedback: "hit", who: "@Victim" }
                """.trim(), stores, actor, victim);
        OutgoingDebuffStore.Debuff debuff = stores.outgoingDebuff().active(victimId, 0L);
        assertEquals(50.0, debuff.percent());
        assertEquals("hit", debuff.feedback());
        assertFalse(debuff.covers(OutgoingDebuffStore.CAUSE_MELEE));

        run("trophy", """
                HEAD_TROPHY: { name: "Skull of {VICTIM}", lore: "a|b", who: "@Victim" }
                """.trim(), stores, actor, victim);
        HeadTrophyStore.Trophy trophy = stores.headTrophies().consume(victimId);
        assertNotNull(trophy, "the trophy reached the store through the real dispatch");
        assertEquals("Skull of {VICTIM}", trophy.name());
        assertNull(stores.headTrophies().consume(victimId));
    }

    @Test
    void theFoodWindowModesReachTheirStoreOnARealRun() throws Exception {
        // Wave 1d.3. MODIFY_FOOD's window modes emit nothing observable at activation — their whole contract
        // is the flag a LATER FoodLevelChangeEvent reads, so a mis-wired mode is an enchant with no symptom
        // until someone eats. Both modes on one head, so a swapped wire code shows up as the wrong window.
        EngineStores stores = EngineStores.fresh();
        Player actor = mock(Player.class);
        when(actor.getUniqueId()).thenReturn(ACTOR);

        run("scale", "MODIFY_FOOD: { mode: scale-gain, factor: 2.5, duration: 60, who: \"@Self\" }",
                stores, actor, null);
        assertEquals(2.5, stores.foodWindows().gainFactor(ACTOR, 0L));
        assertFalse(stores.foodWindows().cancelsDrain(ACTOR, 0L), "scale-gain must not arm the drain window");

        run("nodrain", "MODIFY_FOOD: { mode: cancel-drain, duration: 40, who: \"@Self\" }",
                stores, actor, null);
        assertTrue(stores.foodWindows().cancelsDrain(ACTOR, 0L));
        assertEquals(2.5, stores.foodWindows().gainFactor(ACTOR, 0L), "the two windows are independent");
        assertFalse(stores.foodWindows().cancelsDrain(ACTOR, 40L), "half-open: the expiry tick is free");
    }

    @Test
    void theBoreSelectorCompilesToItsOwnSelectorRegistryId() throws Exception {
        // Wave 1d.3. Selector dispatch is a dense id too, so a new selector can compile cleanly and still
        // resolve through whichever kind happens to sit at that index.
        Snapshot snap = load("bore", "BREAK_BLOCK: { who: \"@Bore{half-width=1, depth=2}\" }");
        CompiledEffect effect = snap.byStableKey("enchants/bore/1").effects()[0];
        assertEquals("BORE",
                BuiltinSelectors.registry().selectorsById()[effect.target().kindId()].spec().head());
    }

    @Test
    void projectileDressingReachesTheBowReadBack() throws Exception {
        // PROJECTILE_DRESSING emits nothing into the world: its whole contract is the read-back the bow
        // dispatcher consumes, so an unset read-back is an inert enchant with no other symptom.
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        assertNull(sink.projectileDressing(), "a fresh sink dresses nothing");
        runOn(load("dressing", "PROJECTILE_DRESSING: { type: COW, ttl: 150, invulnerable: 40, no-pickup: true }"),
                "dressing", sink, mock(Player.class), null);
        assertNotNull(sink.projectileDressing());
        assertEquals(150, sink.projectileDressing().ttlTicks());
        assertEquals(40, sink.projectileDressing().invulnerableTicks());
        assertTrue(sink.projectileDressing().noPickup());
    }

    private void run(String key, String authored, EngineStores stores, Player actor, Player victim)
            throws Exception {
        ModernDispatchSink sink = new ModernDispatchSink(handles,
                Envs.sink().stores(stores).nowTicks(() -> 0L).build());
        runOn(load(key, authored), key, sink, actor, victim);
    }

    private void runOn(Snapshot snap, String key, ModernDispatchSink sink, Player actor, Player victim) {
        Ability ability = snap.byStableKey("enchants/" + key + "/1");
        assertNotNull(ability, "the authored level compiled to an ability");
        int activated = executor.run(snap.abilities(), new int[] {ability.id()},
                Activation.builder(ACTOR, 0, 0, 0L).build(),
                new ActivationContext(actor, victim, null, null), sink, snap.stableKeys());
        sink.flush();
        assertEquals(1, activated, "the ability actually walks");
    }

    private Snapshot load(String key, String effect) throws Exception {
        Path enchants = Files.createDirectories(root.resolve("content/enchants"));
        Files.writeString(enchants.resolve(key + ".yml"), """
                display: "Probe"
                trigger: "ATTACK"
                levels:
                  1: { effects: [{ %s }] }
                """.formatted(effect), StandardCharsets.UTF_8);
        Library lib = LibraryLoader.load(root.resolve("content"), COMPILER, 0);
        assertFalse(lib.hasErrors(), () -> lib.diagnostics().toString());
        return lib.snapshot();
    }
}
