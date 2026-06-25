package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.SourceKind;
import compile.model.StableKeyIndex;
import engine.effect.EffectRegistry;
import engine.effect.kind.IgniteEffect;
import engine.interact.SoulLedger;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.selector.kind.VictimSelector;
import engine.sink.DispatchSink;
import engine.sink.RecordingSchedulerBackend;
import engine.sink.SyncSchedulerBackend;
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

/**
 * {@link AbilityExecutor} — the gate-walk + effect-execution glue (docs/architecture.md §3.3, §3.5). Wires
 * REAL engine components (registries, pipeline, real kinds, a real {@link DispatchSink}) and mocks only the
 * Bukkit entities, exercising selector→context→effect→sink end to end without a server. Why no live matrix
 * run: the executor adds no Bukkit/version/thread surface of its own — dispatcher routing is matrix-verified,
 * selectors are pure, the pipeline is unit-tested elsewhere.
 */
class AbilityExecutorTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final int TRIGGER = 0;
    // Maps dense id 0 to its per-level key; the executor reduces that to the BASE key (enchants/test) for
    // the ActivationListener (§13 seam), since the fixtures all build level-1 abilities.
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
        ActivationPipeline pipeline = new ActivationPipeline(new CooldownStore(), new SoulLedger());
        executor = new AbilityExecutor(effects, selectors, pipeline, AreaScan.NONE);
    }

    @Test
    void activatedAbilityRunsItsEffectOnTheResolvedTarget() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability[] abilities = {ignite("VICTIM", 60, Affinity.TARGET_ENTITY)};
        DispatchSink sink = new DispatchSink(handles);

        int activated = executor.run(abilities, new int[] {0}, activation(), context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verify(victim).setFireTicks(60);
    }

    @Test
    void nonMatchingTriggerDoesNotActivate() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability onOtherTrigger = new Ability(0, 0, SourceKind.ENCHANT, 1 << 5, 1, 100.0, 0, 0, 0L, null,
                new CompiledEffect[] {igniteEffect("VICTIM", 60, Affinity.TARGET_ENTITY)},
                0, Affinity.TARGET_ENTITY, -1, -1, -1, -1, 0);
        DispatchSink sink = new DispatchSink(handles);

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
        DispatchSink sink = new DispatchSink(handles);

        executor.run(abilities, new int[] {0}, activation(), context(null, victim), sink, KEYS);
        sink.flush();
        verify(victim).setFireTicks(40);
    }

    @Test
    void selfSelectorResolvesToTheActor() {
        Player actor = mock(Player.class);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        DispatchSink sink = new DispatchSink(handles);

        executor.run(abilities, new int[] {0}, activation(), context(actor, null), sink, KEYS);
        sink.flush();

        verify(actor).setFireTicks(80);
    }

    /**
     * Listener (public-event seam, §13) fires once per ACTIVATED ability with the BASE key: resolve the
     * per-level key against the run's OWN index, then strip {@code /<level>} — never re-resolve a dense id
     * against the live (possibly swapped) snapshot, which a reload could mismatch.
     */
    @Test
    void notifiesTheActivationListenerWithTheBaseStableKey() {
        Player actor = mock(Player.class);
        java.util.List<String> seen = new java.util.ArrayList<>();
        ActivationListener listener = (key, ability, ctx) -> seen.add(key);
        AbilityExecutor observed = new AbilityExecutor(
                EffectRegistry.builder().register(new IgniteEffect()).build(),
                SelectorRegistry.builder().register(new SelfSelector()).register(new VictimSelector()).build(),
                new ActivationPipeline(new CooldownStore(), new SoulLedger()), AreaScan.NONE, listener);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        DispatchSink sink = new DispatchSink(handles);

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
                new ActivationPipeline(new CooldownStore(), new SoulLedger()), AreaScan.NONE, listener);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        DispatchSink sink = new DispatchSink(handles);

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
        Ability ability = new Ability(0, 0, SourceKind.ENCHANT, 1 << TRIGGER, 1, 100.0, 0, 0, 0L, null,
                new CompiledEffect[] {missing, good}, 0, Affinity.TARGET_ENTITY, -1, -1, -1, -1, 0);
        DispatchSink sink = new DispatchSink(handles);

        int activated = executor.run(new Ability[] {ability}, new int[] {0}, activation(),
                context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verify(victim).setFireTicks(60);
    }

    @Test
    void outOfRangeCandidateIdsAreSkipped() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability[] abilities = {ignite("VICTIM", 60, Affinity.TARGET_ENTITY)};
        DispatchSink sink = new DispatchSink(handles);

        int activated = executor.run(abilities, new int[] {-1, 7, 0}, activation(), context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated); // only id 0 is valid
        verify(victim).setFireTicks(60);
    }

    /**
     * WAIT (§3.6): an effect with accumulated {@code cumulativeWaitTicks} is routed by the executor into
     * the sink's delay tier, so it neither runs in the gate pass nor on flush — only when its timer fires.
     * Proves the executor→sink wiring (the deferral mechanism itself is pinned in {@code DispatchSinkWaitTest}).
     */
    @Test
    void waitDefersTheEffectUntilItsTimerFires() {
        RecordingSchedulerBackend recording = new RecordingSchedulerBackend();
        Scheduling.install(recording);
        LivingEntity victim = mock(LivingEntity.class);
        CompiledEffect delayed = new CompiledEffect("IGNITE", Args.empty().with("duration", 60L),
                new CompiledSelector("VICTIM", Args.empty()), 40, Affinity.TARGET_ENTITY);
        Ability ability = new Ability(0, 0, SourceKind.ENCHANT, 1 << TRIGGER, 1, 100.0, 0, 0, 0L, null,
                new CompiledEffect[] {delayed}, 0, Affinity.TARGET_ENTITY, -1, -1, -1, -1, 0);
        DispatchSink sink = new DispatchSink(handles);

        int activated = executor.run(new Ability[] {ability}, new int[] {0}, activation(),
                context(null, victim), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verifyNoInteractions(victim);                          // WAIT:40 — nothing yet
        assertEquals(1, recording.delayed.size(), "one delayed batch scheduled");
        assertEquals(40L, recording.delayed.get(0).delayTicks());

        recording.runDelayed();
        verify(victim).setFireTicks(60);                       // ignites after the delay
    }

    private static Activation activation() {
        return Activation.builder(ACTOR, 0, TRIGGER, 0L).build(); // world 0, trigger 0, tick 0
    }

    private static ActivationContext context(Player actor, LivingEntity victim) {
        return new ActivationContext(actor, victim, null, null);
    }

    private static Ability ignite(String selectorHead, int duration, Affinity affinity) {
        return new Ability(0, 0, SourceKind.ENCHANT, 1 << TRIGGER, 1, 100.0, 0, 0, 0L, null,
                new CompiledEffect[] {igniteEffect(selectorHead, duration, affinity)},
                0, affinity, -1, -1, -1, -1, 0);
    }

    private static CompiledEffect igniteEffect(String selectorHead, int duration, Affinity affinity) {
        return new CompiledEffect("IGNITE", Args.empty().with("duration", (long) duration),
                new CompiledSelector(selectorHead, Args.empty()), 0, affinity);
    }
}
