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
import engine.stores.SuppressionStore;
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
import schema.spec.PotionLoadout;
import testfx.Abilities;
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
            "PERIODIC_DAMAGE: { amount: 6, period: 20, duration: 120, replace: WITHER, "
                    + "tick-sound: ENTITY_ZOMBIFIED_PIGLIN_ANGRY, tick-volume: 0.6, tick-pitch: 0.8, "
                    + "tick-particle: FLAME, tick-particle-count: 20, who: \"@Victim\" }",
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

        run("absolute", "MODIFY_FOOD: { mode: absolute, factor: 1.75, duration: 80, who: \"@Self\" }",
                stores, actor, null);
        assertEquals(1.75, stores.foodWindows().absoluteFactor(ACTOR, 0L));
        assertEquals(2.5, stores.foodWindows().gainFactor(ACTOR, 0L),
                "absolute is its own slot, not a re-arm of scale-gain");
    }

    @Test
    void theSuppressConsumeFeedbackReachesTheWindowItArms() throws Exception {
        // Wave 1d.3. The lines are authored on SUPPRESS but read at the BLOCK, so they have to survive the
        // whole compile AND the sink's arm — a drop anywhere is a window that silently blocks in silence.
        EngineStores stores = EngineStores.fresh();
        Player actor = mock(Player.class);
        Player victim = mock(Player.class);
        UUID victimId = UUID.randomUUID();
        when(actor.getUniqueId()).thenReturn(ACTOR);
        when(victim.getUniqueId()).thenReturn(victimId);

        run("silence", """
                SUPPRESS: { scope: GROUP, key: lifesteal, duration: 200, \
                consumed-message-actor: "silenced them", consumed-message-victim: "you are silenced", \
                who: "@Victim" }""", stores, actor, victim);

        SuppressionStore.Feedback feedback = feedbackFor(stores, victimId);
        assertNotNull(feedback, "the authored lines reached the armed window");
        assertEquals("silenced them", feedback.actorMessage());
        assertEquals("you are silenced", feedback.victimMessage());
        assertEquals(ACTOR, feedback.by(), "the window remembers who armed it, so 'actor' can be told");
    }

    /**
     * The feedback on the group window {@code victim} now holds. The interned scope id is a compile-time
     * detail, so probe the small id space rather than re-typing a number the interner chose.
     */
    private static SuppressionStore.Feedback feedbackFor(EngineStores stores, UUID victim) {
        for (int id = 0; id < 64; id++) {
            SuppressionStore.Feedback f = stores.suppression().blockedFeedback(
                    Abilities.ability().trigger(0).cooldownScope(-1, id, -1).build(), victim, 0L);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    @Test
    void aPayloadSpawnCompilesToTheSameKindTheRuntimeIndexes() throws Exception {
        // The payload params ride SPAWN_ENTITY rather than a new head, so the whole feature is invisible to a
        // spec test: only a compile-to-registry walk proves the stamped dense id still indexes SPAWN_ENTITY
        // (a mis-stamp runs a neighbouring kind with its own args and stays green at both ends).
        Snapshot snap = load("selfdestruct", """
                SPAWN_ENTITY: { type: PRIMED_TNT, count: 4, ttl: 40, owner: activator, scatter: 3, \
                payload-phase: detonate, payload-radius: 2, payload-height: 2, payload-filter: ENEMIES, \
                payload-max-targets: 0, who: "@Self" }""");
        CompiledEffect effect = snap.byStableKey("enchants/selfdestruct/1").effects()[0];
        assertEquals("SPAWN_ENTITY",
                BuiltinEffects.registry().kindsById()[effect.kindId()].spec().head());
        assertEquals("detonate", effect.args().str("payload-phase"), "the phase survives the whole compile");
        assertEquals(3L, effect.args().lng("scatter"));
    }

    @Test
    void aBurnsTickCuesAndReplacedDotSurviveTheWholeCompile() throws Exception {
        // The cues and the replaced DoT are read ONLY at pulse time, long after the walk that armed them, so a
        // stage that drops an optional handle or the replace list leaves an enchant that burns in silence (or
        // one whose conversion never happens) with nothing red at either end.
        Snapshot snap = load("immolation", """
                PERIODIC_DAMAGE: { amount: 6, period: 20, duration: 120, replace: WITHER, \
                tick-sound: ENTITY_ZOMBIFIED_PIGLIN_ANGRY, tick-volume: 0.6, tick-pitch: 0.8, \
                tick-particle: FLAME, tick-particle-count: 20, who: "@Victim" }""");
        CompiledEffect effect = snap.byStableKey("enchants/immolation/1").effects()[0];

        assertEquals(1, effect.args().ids("replace").size(), "the converted DoT reaches the runtime");
        assertTrue(effect.args().has("tick-sound") && effect.args().has("tick-particle"),
                "both optional cue handles interned rather than being dropped as absent");
        assertEquals(0.6, effect.args().dbl("tick-volume"));
        assertEquals(0.8, effect.args().dbl("tick-pitch"));
        assertEquals(20L, effect.args().lng("tick-particle-count"));
    }

    @Test
    void aSummonLoadoutKeepsItsPerEntryLevelThroughTheWholeCompile() throws Exception {
        // Undead Ruse's minions carry leveled self-buffs; the level rides INSIDE the interned id, so a stage
        // that re-resolves or re-packs it drops every buff back to level 1 with no diagnostic.
        Snapshot snap = load("undeadruse", """
                SPAWN_SWARM: { type: ZOMBIE, count: 6, name: "&cRuse", effects: "SPEED*3, INCREASE_DAMAGE" }""");
        List<Integer> loadout = snap.byStableKey("enchants/undeadruse/1").effects()[0].args().ids("effects");
        assertEquals(2, loadout.size());
        assertEquals(2, PotionLoadout.amp(loadout.get(0)), "level 3 survives as amplifier 2");
        assertEquals(0, PotionLoadout.amp(loadout.get(1)), "a bare entry stays level 1");
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
