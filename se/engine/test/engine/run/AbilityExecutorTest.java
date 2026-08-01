package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.StableKeyIndex;
import engine.effect.EffectRegistry;
import engine.effect.kind.IgniteEffect;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.selector.kind.VictimSelector;
import engine.sink.ModernDispatchSink;
import engine.stores.CooldownStore;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import platform.resolve.RegistryResolvers;
import platform.resolve.RuntimeHandles;
import platform.sched.Scheduling;
import schema.spec.Args;
import testfx.Abilities;
import testfx.Envs;
import testfx.RecordingSchedulerBackend;
import testfx.SyncSchedulerBackend;

/**
 * Wires REAL engine components (registries, pipeline, kinds, a real {@link ModernDispatchSink}), mocking only the
 * Bukkit entities. No live matrix run: the executor adds no Bukkit/version/thread surface of its own —
 * dispatcher routing is matrix-verified, selectors are pure, the pipeline is unit-tested elsewhere.
 */
class AbilityExecutorTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final int TRIGGER = 0;
    // The executor reduces the per-level key to its BASE for the ActivationListener (§13); fixtures are all level-1.
    private static final StableKeyIndex KEYS = new StableKeyIndex(java.util.List.of("enchants/test/1"));

    private RuntimeHandles handles;
    private AbilityExecutor executor;

    @BeforeEach
    void setUp() {
        handles = new RuntimeHandles(new RegistryResolvers());
        Scheduling.install(new SyncSchedulerBackend());
        EffectRegistry effects = EffectRegistry.builder().register(new IgniteEffect()).build();
        SelectorRegistry selectors = SelectorRegistry.builder()
                .register(new VictimSelector())
                .register(new SelfSelector())
                .build();
        ActivationPipeline pipeline = new ActivationPipeline(new CooldownStore(), SoulSpender.NONE);
        executor = new AbilityExecutor(effects, selectors, pipeline, AreaScan.NONE);
    }

    @Test
    void activatedAbilityRunsItsEffectOnTheResolvedTarget() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability[] abilities = {ignite("VICTIM", 60, Affinity.TARGET_ENTITY)};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(abilities, new int[] {0}, activation(), context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verify(victim).setFireTicks(60);
    }

    @Test
    void noSoulsRunsOnlyTheAuthoredFailureEffects() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.TARGET_ENTITY)
                .soulCost(2)
                .effects(igniteEffect("VICTIM", 60, Affinity.TARGET_ENTITY))
                .noSoulEffects(igniteEffect("VICTIM", 20, Affinity.TARGET_ENTITY))
                .build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(new Ability[] {ability}, new int[] {0}, activation(),
                context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(0, activated);
        verify(victim).setFireTicks(20);
    }

    @Test
    void nonMatchingTriggerDoesNotActivate() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability onOtherTrigger = Abilities.ability().trigger(5).affinity(Affinity.TARGET_ENTITY)
                .effects(igniteEffect("VICTIM", 60, Affinity.TARGET_ENTITY)).build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(new Ability[] {onOtherTrigger}, new int[] {0}, activation(),
                context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(0, activated);
        verifyNoInteractions(victim);
    }

    /** Affinity is not a Sink routing key: an effect applies on flush regardless of its declared affinity. */
    @Test
    void effectAppliesOnFlushRegardlessOfAffinity() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability[] abilities = {ignite("VICTIM", 40, Affinity.CONTEXT_LOCAL)};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        executor.run(abilities, new int[] {0}, activation(), context(null, victim), sink, KEYS);
        sink.flush();
        verify(victim).setFireTicks(40);
    }

    @Test
    void selfSelectorResolvesToTheActor() {
        Player actor = mock(Player.class);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        executor.run(abilities, new int[] {0}, activation(), context(actor, null), sink, KEYS);
        sink.flush();

        verify(actor).setFireTicks(80);
    }

    /** Resolves the BASE key against the run's OWN index (§13), never the live snapshot — a reload could swap it and mismatch. */
    @Test
    void notifiesTheActivationListenerWithTheBaseStableKey() {
        Player actor = mock(Player.class);
        java.util.List<String> seen = new java.util.ArrayList<>();
        ActivationListener listener = (key, ability, ctx) -> seen.add(key);
        AbilityExecutor observed = new AbilityExecutor(
                EffectRegistry.builder().register(new IgniteEffect()).build(),
                SelectorRegistry.builder().register(new SelfSelector()).register(new VictimSelector()).build(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE, listener);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        observed.run(abilities, new int[] {0}, activation(), context(actor, null), sink, KEYS);

        assertEquals(java.util.List.of("enchants/test"), seen); // per-level enchants/test/1 → base enchants/test
    }

    /** A dense id with no entry in the run's index resolves to a {@code null} key, never a crash (§5.3). */
    @Test
    void resolvesNullKeyWhenTheIndexDoesNotCoverTheAbilityId() {
        Player actor = mock(Player.class);
        java.util.List<String> seen = new java.util.ArrayList<>();
        ActivationListener listener = (key, ability, ctx) -> seen.add(key);
        AbilityExecutor observed = new AbilityExecutor(
                EffectRegistry.builder().register(new IgniteEffect()).build(),
                SelectorRegistry.builder().register(new SelfSelector()).register(new VictimSelector()).build(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE, listener);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        // An empty index (e.g. resolving an id from a different/reloaded snapshot) → null, not IOOBE.
        observed.run(abilities, new int[] {0}, activation(), context(actor, null), sink,
                new StableKeyIndex(java.util.List.of()));

        assertEquals(java.util.Collections.singletonList(null), seen);
    }

    /** Per-effect isolation: an unresolvable effect head is skipped, not propagated to abort its siblings. */
    @Test
    void aFailingEffectDoesNotAbortTheOthers() {
        LivingEntity victim = mock(LivingEntity.class);
        CompiledEffect missing = new CompiledEffect("NO_SUCH_KIND", Args.empty(),
                new CompiledSelector("VICTIM", Args.empty()), 0, Affinity.TARGET_ENTITY);
        CompiledEffect good = igniteEffect("VICTIM", 60, Affinity.TARGET_ENTITY);
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.TARGET_ENTITY)
                .effects(missing, good).build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(new Ability[] {ability}, new int[] {0}, activation(),
                context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verify(victim).setFireTicks(60);
    }

    /** ADR-0039: an effect stamped with its dense kind id dispatches via the array index (the production fast path). */
    @Test
    void stampedKindIdDispatchesThroughTheFastPath() {
        EffectRegistry effects = EffectRegistry.builder().register(new IgniteEffect()).build();
        SelectorRegistry selectors = SelectorRegistry.builder()
                .register(new VictimSelector()).register(new SelfSelector()).build();
        AbilityExecutor stamped = new AbilityExecutor(effects, selectors,
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        LivingEntity victim = mock(LivingEntity.class);
        CompiledEffect ignite = new CompiledEffect("IGNITE", Args.empty().with("duration", 60L),
                new CompiledSelector("VICTIM", Args.empty(), selectors.idOf("VICTIM")), 0,
                Affinity.TARGET_ENTITY, effects.idOf("IGNITE"));
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.TARGET_ENTITY)
                .effects(ignite).build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = stamped.run(new Ability[] {ability}, new int[] {0}, activation(),
                context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verify(victim).setFireTicks(60);
    }

    @Test
    void outOfRangeCandidateIdsAreSkipped() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability[] abilities = {ignite("VICTIM", 60, Affinity.TARGET_ENTITY)};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(abilities, new int[] {-1, 7, 0}, activation(), context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated); // only id 0 is valid
        verify(victim).setFireTicks(60);
    }

    /** WAIT (§3.6): the executor routes a delayed effect into the sink's delay tier so it runs only on its timer, not on flush. */
    @Test
    void waitDefersTheEffectUntilItsTimerFires() {
        RecordingSchedulerBackend recording = new RecordingSchedulerBackend();
        Scheduling.install(recording);
        LivingEntity victim = mock(LivingEntity.class);
        CompiledEffect delayed = new CompiledEffect("IGNITE", Args.empty().with("duration", 60L),
                new CompiledSelector("VICTIM", Args.empty()), 40, Affinity.TARGET_ENTITY);
        Ability ability = Abilities.ability().trigger(TRIGGER).affinity(Affinity.TARGET_ENTITY)
                .effects(delayed).build();
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        int activated = executor.run(new Ability[] {ability}, new int[] {0}, activation(),
                context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verifyNoInteractions(victim);
        assertEquals(1, recording.delayed.size(), "one delayed batch scheduled");
        assertEquals(40L, recording.delayed.get(0).delayTicks());

        recording.runDelayed();
        verify(victim).setFireTicks(60);
    }

    /** §3.6 cold use-item path: a candidate activates once, then its armed cooldown reports the remaining ticks. */
    @Test
    void runUseActivatesThenReportsTheRemainingCooldownOnReuse() {
        Player actor = mock(Player.class);
        Ability withCooldown = Abilities.ability().trigger(TRIGGER).affinity(Affinity.TARGET_ENTITY)
                .cooldown(200).cooldownScope(0, -1, -1) // arm the enchant scope so gate 6 blocks the reuse
                .effects(igniteEffect("SELF", 80, Affinity.TARGET_ENTITY)).build();
        Ability[] abilities = {withCooldown};

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        UseAttempt first = executor.runUse(abilities, new int[] {0}, activation(), context(actor, null), sink, KEYS);
        sink.flush(); // the effect intent applies on flush; the cooldown armed at gate 11 regardless
        assertTrue(first.activated());
        verify(actor).setFireTicks(80);

        UseAttempt second = executor.runUse(abilities, new int[] {0}, activation(), context(actor, null),
                new ModernDispatchSink(handles, Envs.sink().build()), KEYS); // same tick, cooldown still hot
        assertFalse(second.activated());
        assertTrue(second.onCooldown());
        assertEquals(200L, second.cooldownRemainingTicks());
    }

    /** The collapse distinguishes a chance miss from a block, and runs no effect (§3.6). */
    @Test
    void runUseCollapsesAZeroChanceToTheChanceSignal() {
        Player actor = mock(Player.class);
        Ability neverRolls = Abilities.ability().trigger(TRIGGER).affinity(Affinity.TARGET_ENTITY)
                .chance(0).effects(igniteEffect("SELF", 80, Affinity.TARGET_ENTITY)).build();

        UseAttempt attempt = executor.runUse(new Ability[] {neverRolls}, new int[] {0}, activation(),
                context(actor, null), new ModernDispatchSink(handles, Envs.sink().build()), KEYS);

        assertFalse(attempt.activated());
        assertTrue(attempt.chanceFailed());
        assertEquals(-1, attempt.conditionCandidateIndex());
        verifyNoInteractions(actor);
    }

    private static Activation activation() {
        return Activation.builder(ACTOR, 0, TRIGGER, 0L).build();
    }

    private static ActivationContext context(Player actor, LivingEntity victim) {
        return new ActivationContext(actor, victim, null, null);
    }

    private static Ability ignite(String selectorHead, int duration, Affinity affinity) {
        return Abilities.ability().trigger(TRIGGER).affinity(affinity)
                .effects(igniteEffect(selectorHead, duration, affinity)).build();
    }

    private static CompiledEffect igniteEffect(String selectorHead, int duration, Affinity affinity) {
        return new CompiledEffect("IGNITE", Args.empty().with("duration", (long) duration),
                new CompiledSelector(selectorHead, Args.empty()), 0, affinity);
    }
}
