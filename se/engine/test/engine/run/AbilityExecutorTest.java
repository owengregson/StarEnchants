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
 * Tests for the {@link AbilityExecutor} — the gate-walk + effect-execution glue (docs/architecture.md
 * §3.3, §3.5). These wire the REAL engine components (effect/selector registries, the gate pipeline,
 * the {@code IgniteEffect}/{@code VictimSelector}/{@code SelfSelector} kinds, and a real
 * {@link DispatchSink}) — only the Bukkit entities are mocked — so the selector→context→effect→sink
 * wiring is exercised end to end without a server. No live matrix run is needed: the executor adds no
 * Bukkit/version/thread surface of its own (the dispatcher's routing is already matrix-verified, the
 * selectors are pure, and the pipeline is unit-tested elsewhere).
 */
class AbilityExecutorTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final int TRIGGER = 0;
    // This test only ever activates ability id 0; the index maps that dense id to its compiled per-level
    // stable key, which the executor reduces to the BASE key (enchants/test) for the ActivationListener
    // since the fixtures build level-1 abilities (the §13 api seam).
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

    /** IGNITE:@Victim — the activated ability resolves the victim and ignites it through the sink. */
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

    /** An ability that does not fire on this trigger never runs its effect. */
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

    /** Affinity no longer routes the Sink: an effect applies on flush regardless of its declared affinity. */
    @Test
    void effectAppliesOnFlushRegardlessOfAffinity() {
        LivingEntity victim = mock(LivingEntity.class);
        Ability[] abilities = {ignite("VICTIM", 40, Affinity.CONTEXT_LOCAL)};
        DispatchSink sink = new DispatchSink(handles);

        executor.run(abilities, new int[] {0}, activation(), context(null, victim), sink, KEYS);
        sink.flush();
        verify(victim).setFireTicks(40);
    }

    /** @Self resolves to the actor, so the effect targets the firing player. */
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
     * The activation listener is invoked once per ACTIVATED ability (the public-event seam, §13) with the
     * BASE content key: the executor resolves the ability's compiled per-level key against the run's own
     * index, then strips the {@code /<level>} suffix — so a level-1 {@code enchants/test/1} surfaces as
     * {@code enchants/test}, never a dense id re-resolved against a possibly-swapped live snapshot.
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

    /** A bad effect head is skipped; the good effect on the same ability still runs (per-effect isolation). */
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

    /** Out-of-range candidate ids are skipped defensively rather than throwing. */
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

    // ── fixtures ─────────────────────────────────────────────────────────────────────────────────

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
