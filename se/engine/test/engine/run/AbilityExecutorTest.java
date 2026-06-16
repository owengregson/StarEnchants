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
import engine.effect.EffectRegistry;
import engine.effect.kind.IgniteEffect;
import engine.interact.SoulLedger;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.selector.kind.VictimSelector;
import engine.sink.DispatchSink;
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

        int activated = executor.run(abilities, new int[] {0}, activation(), context(null, victim), sink);
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
                context(null, victim), sink);
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

        executor.run(abilities, new int[] {0}, activation(), context(null, victim), sink);
        sink.flush();
        verify(victim).setFireTicks(40);
    }

    /** @Self resolves to the actor, so the effect targets the firing player. */
    @Test
    void selfSelectorResolvesToTheActor() {
        Player actor = mock(Player.class);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        DispatchSink sink = new DispatchSink(handles);

        executor.run(abilities, new int[] {0}, activation(), context(actor, null), sink);
        sink.flush();

        verify(actor).setFireTicks(80);
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
                context(null, victim), sink);
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

        int activated = executor.run(abilities, new int[] {-1, 7, 0}, activation(), context(null, victim), sink);
        sink.flush();

        assertEquals(1, activated); // only id 0 is valid
        verify(victim).setFireTicks(60);
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
