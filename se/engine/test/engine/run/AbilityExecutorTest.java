package engine.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import compile.model.Ability;
import compile.model.Affinity;
import compile.model.CompiledEffect;
import compile.model.CompiledSelector;
import compile.model.ScopeKinds;
import compile.model.StableKeyIndex;
import engine.effect.EffectRegistry;
import engine.effect.kind.IgniteEffect;
import engine.interact.SoulSpender;
import engine.pipeline.Activation;
import engine.pipeline.ActivationPipeline;
import engine.pipeline.GateOutcome;
import engine.selector.SelectorRegistry;
import engine.selector.kind.SelfSelector;
import engine.selector.kind.VictimSelector;
import engine.sink.ModernDispatchSink;
import engine.sink.SinkReadback;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import engine.stores.WhyRecorder;
import java.util.ArrayList;
import java.util.List;
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

    /** R-QC25b: a rebounded proc IS an activation an addon can observe — the gateless path must not swallow it. */
    @Test
    void runForcedNotifiesTheActivationListenerToo() {
        Player reflector = mock(Player.class);
        java.util.List<String> seen = new java.util.ArrayList<>();
        ActivationListener listener = (key, ability, ctx) -> seen.add(key);
        AbilityExecutor observed = new AbilityExecutor(
                EffectRegistry.builder().register(new IgniteEffect()).build(),
                SelectorRegistry.builder().register(new SelfSelector()).register(new VictimSelector()).build(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE, listener);
        Ability[] abilities = {ignite("SELF", 80, Affinity.TARGET_ENTITY)};
        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());

        observed.runForced(abilities, new int[] {0}, activation(), context(reflector, null), sink, KEYS);

        assertEquals(java.util.List.of("enchants/test"), seen, "the same base key the gated path reports");
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

    // ── Gate-verdict feedback: the dispatch layer emits off a BLOCKED verdict (wave 1d.3) ────────

    @Test
    void aBlockingTimedSuppressionWindowEmitsItsAuthoredFeedback() {
        // The verdict is the pipeline's; the emit is the dispatch layer's, because the pipeline is Bukkit-free
        // and holds no player handle. Both parties named on the SUPPRESS line get their own line.
        UUID suppressor = UUID.randomUUID();
        SuppressionStore suppression = new SuppressionStore();
        suppression.suppress(ACTOR, CooldownStore.key(ScopeKinds.ENCHANT, 5), 0L, 100, 88,
                new SuppressionStore.Feedback(suppressor, "you blocked it", "you are silenced", -1));
        AbilityExecutor gated = executorWith(suppression, SoulSpender.NONE);

        Player actor = mock(Player.class);
        SinkReadback sink = mock(SinkReadback.class);
        Ability suppressed = Abilities.ability().trigger(TRIGGER).cooldownScope(5, -1, -1)
                .effects(igniteEffect("SELF", 60, Affinity.TARGET_ENTITY)).build();

        assertEquals(0, gated.run(new Ability[] {suppressed}, new int[] {0}, activation(),
                context(actor, null), sink, KEYS));

        verify(sink).messageTo(suppressor, "you blocked it");
        verify(sink).message(actor, "you are silenced");
    }

    @Test
    void aSilentSuppressionWindowEmitsNothing() {
        // The overwhelmingly common case: no cue authored, so the read-back finds the null it stored and the
        // blocked walk stays as quiet as it was before this mechanism existed.
        SuppressionStore suppression = new SuppressionStore();
        suppression.suppress(ACTOR, CooldownStore.key(ScopeKinds.ENCHANT, 5), 0L, 100, 88);
        AbilityExecutor gated = executorWith(suppression, SoulSpender.NONE);

        SinkReadback sink = mock(SinkReadback.class);
        Ability suppressed = Abilities.ability().trigger(TRIGGER).cooldownScope(5, -1, -1)
                .effects(igniteEffect("SELF", 60, Affinity.TARGET_ENTITY)).build();

        gated.run(new Ability[] {suppressed}, new int[] {0}, activation(), context(mock(Player.class), null),
                sink, KEYS);

        verifyNoInteractions(sink);
    }

    @Test
    void anAbortedSoulSpendEmitsTheAbilitysOwnNoSoulsLine() {
        AbilityExecutor gated = executorWith(new SuppressionStore(), (player, cost) -> false);
        Player actor = mock(Player.class);
        SinkReadback sink = mock(SinkReadback.class);
        Ability costly = Abilities.ability().trigger(TRIGGER).soulCost(5).noSoulsMessage("out of souls")
                .effects(igniteEffect("SELF", 60, Affinity.TARGET_ENTITY)).build();
        Ability silent = Abilities.ability().trigger(TRIGGER).soulCost(5)
                .effects(igniteEffect("SELF", 60, Affinity.TARGET_ENTITY)).build();
        Activation inSoulMode = Activation.builder(ACTOR, 0, TRIGGER, 0L).soulMode(UUID.randomUUID()).build();

        assertEquals(0, gated.run(new Ability[] {costly, silent}, new int[] {0, 1}, inSoulMode,
                context(actor, null), sink, KEYS));

        // The throttle lives in the sink, not here: only the ability that authored a line emits at all.
        verify(sink).outOfSoulsNotice(actor, "out of souls", -1, -1);
        verifyNoMoreInteractions(sink);
    }

    @Test
    void anAbortedSoulSpendCarriesTheAbilitysOwnCueAlongsideItsLine() {
        AbilityExecutor gated = executorWith(new SuppressionStore(), (player, cost) -> false);
        Player actor = mock(Player.class);
        SinkReadback sink = mock(SinkReadback.class);
        // Cue-only, no line: the notice must still reach the dispatch layer, or a silent-by-design ability
        // (a set bonus with no chat spam) would have no way to report an empty pool at all.
        Ability cueOnly = Abilities.ability().trigger(TRIGGER).soulCost(5).noSoulsSound(7).noSoulsParticle(3)
                .effects(igniteEffect("SELF", 60, Affinity.TARGET_ENTITY)).build();
        Activation inSoulMode = Activation.builder(ACTOR, 0, TRIGGER, 0L).soulMode(UUID.randomUUID()).build();

        assertEquals(0, gated.run(new Ability[] {cueOnly}, new int[] {0}, inSoulMode,
                context(actor, null), sink, KEYS));

        verify(sink).outOfSoulsNotice(actor, null, 7, 3);
        verifyNoMoreInteractions(sink);
    }

    // ── SUPPRESS_INCOMING per TARGET APPLICATION (owner ruling R-v) ──────────────────────────────
    //
    // Gate 5 adjudicates the activation's primary victim; everything an effect ALSO resolves onto — the
    // chain hops — is adjudicated one body at a time here, and a block drops that body only.

    @Test
    void aDefendedChainHopIsSkippedWhileTheOtherHopsStillTakeIt() {
        // The gap this closes: keyed on the activation alone, a Necromancer-mask wearer is only skipped when
        // the swing was aimed at THEM. Standing two blocks away, the chain reached them anyway.
        LivingEntity first = target();
        LivingEntity masked = target();
        LivingEntity last = target();
        SuppressionStore suppression = new SuppressionStore();
        suppression.defend(masked.getUniqueId(), CooldownStore.key(ScopeKinds.ENCHANT, 5), 0L, 100, 100, -1, null);
        AbilityExecutor chained = chainExecutor(suppression, WhyRecorder.NONE, first, masked, last);
        ModernDispatchSink sink = sink();

        int activated = chained.run(new Ability[] {chainAbility()}, new int[] {0}, activation(),
                context(mock(Player.class), null), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        verify(first).setFireTicks(60);
        verify(last).setFireTicks(60);
        verify(masked, never()).setFireTicks(anyInt());
    }

    @Test
    void everyChainHopDefendedStillLeavesTheAbilityActivated() {
        // The semantic call: a per-target veto can only remove bodies. Gates 6/10 already armed the cooldown
        // and spent the souls, and /se why must keep reporting what actually happened — ACTIVATED, not a
        // retroactive SUPPRESSED, which would also contradict the cooldown the walk really did arm.
        LivingEntity one = target();
        LivingEntity two = target();
        SuppressionStore suppression = new SuppressionStore();
        for (LivingEntity blocked : List.of(one, two)) {
            suppression.defend(blocked.getUniqueId(), CooldownStore.key(ScopeKinds.ENCHANT, 5), 0L, 100, 100,
                    -1, null);
        }
        List<Integer> verdicts = new ArrayList<>();
        WhyRecorder recorder = (actor, now, trigger, defId, verdict, pA, pB) -> verdicts.add(verdict);
        AbilityExecutor chained = chainExecutor(suppression, recorder, one, two);
        ModernDispatchSink sink = sink();

        int activated = chained.run(new Ability[] {chainAbility()}, new int[] {0}, activation(),
                context(mock(Player.class), null), sink, KEYS);
        sink.flush();

        assertEquals(1, activated);
        assertEquals(List.of(GateOutcome.ACTIVATED.ordinal()), verdicts);
        verify(one, never()).setFireTicks(anyInt());
        verify(two, never()).setFireTicks(anyInt());
    }

    @Test
    void theActivatorIsNeverFilteredOutOfTheirOwnAbility() {
        // A defender window says what OTHERS may aim at its holder. Consulting it for the activator would cut
        // a mask wearer out of their own who=@Self lifesteal heal — the same key, the same scope, their gear.
        Player actor = mock(Player.class);
        when(actor.getUniqueId()).thenReturn(ACTOR);
        SuppressionStore suppression = new SuppressionStore();
        suppression.defend(ACTOR, CooldownStore.key(ScopeKinds.ENCHANT, 5), 0L, 100, 100, -1, null);
        AbilityExecutor gated = executorWith(suppression, SoulSpender.NONE);
        Ability selfHeal = Abilities.ability().trigger(TRIGGER).cooldownScope(5, -1, -1)
                .effects(igniteEffect("SELF", 60, Affinity.TARGET_ENTITY)).build();

        ModernDispatchSink sink = new ModernDispatchSink(handles, Envs.sink().build());
        assertEquals(1, gated.run(new Ability[] {selfHeal}, new int[] {0}, activation(), context(actor, null),
                sink, KEYS));
        sink.flush();

        verify(actor).setFireTicks(60);
    }

    @Test
    void thePrimaryVictimIsAdjudicatedOnceAtGateFiveAndNeverRolledAgain() {
        // Two consults against one window would square a partial mask's block rate. The third draw here WOULD
        // block (0.0 < 50), so re-consulting the victim is directly observable.
        LivingEntity victim = target();
        SuppressionStore suppression = new SuppressionStore();
        suppression.defend(victim.getUniqueId(), CooldownStore.key(ScopeKinds.ENCHANT, 5), 0L, 100, 50, -1, null);
        AbilityExecutor chained = chainExecutor(suppression, WhyRecorder.NONE, victim);
        List<Double> draws = List.of(90.0, 0.0, 0.0); // gate 5b lets it through, gate 8 passes, then nothing
        int[] next = {0};
        Activation act = Activation.builder(ACTOR, 0, TRIGGER, 0L)
                .victimId(victim.getUniqueId())
                .chanceRoll(() -> draws.get(next[0]++)).build();
        ModernDispatchSink sink = sink();

        assertEquals(1, chained.run(new Ability[] {chainAbility()}, new int[] {0}, act,
                context(mock(Player.class), victim), sink, KEYS));
        sink.flush();

        verify(victim).setFireTicks(60);
        assertEquals(2, next[0], "one draw at gate 5b, one at gate 8 — the target loop must not draw again");
    }

    @Test
    void withNoDefenderWindowAnywhereTheTargetLoopIsNeverEntered() {
        // The hoist (owner ruling R-v: keep the cost profile). A server with nobody wearing SUPPRESS_INCOMING
        // must pay what it paid before this existed: the chance gate's single draw and nothing else.
        LivingEntity one = target();
        LivingEntity two = target();
        AbilityExecutor chained = chainExecutor(new SuppressionStore(), WhyRecorder.NONE, one, two);
        int[] draws = {0};
        Activation act = Activation.builder(ACTOR, 0, TRIGGER, 0L).chanceRoll(() -> {
            draws[0]++;
            return 0.0;
        }).build();
        ModernDispatchSink sink = sink();

        assertEquals(1, chained.run(new Ability[] {chainAbility()}, new int[] {0}, act,
                context(mock(Player.class), null), sink, KEYS));
        sink.flush();

        verify(one).setFireTicks(60);
        verify(two).setFireTicks(60);
        assertEquals(1, draws[0], "gate 8 only — neither gate 5b nor the per-target consult read the store");
    }

    /** The chain fixture's ability: scoped so a defender window keyed to ENCHANT id 5 matches it. */
    private static Ability chainAbility() {
        return Abilities.ability().trigger(TRIGGER).cooldownScope(5, -1, -1)
                .effects(igniteEffect(FixedTargets.HEAD, 60, Affinity.TARGET_ENTITY)).build();
    }

    private AbilityExecutor chainExecutor(SuppressionStore suppression, WhyRecorder recorder,
                                          LivingEntity... chain) {
        return new AbilityExecutor(
                EffectRegistry.builder().register(new IgniteEffect()).build(),
                SelectorRegistry.builder().register(new SelfSelector()).register(new VictimSelector())
                        .register(new FixedTargets(chain)).build(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE, suppression,
                        ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW, recorder),
                AreaScan.NONE);
    }

    private ModernDispatchSink sink() {
        return new ModernDispatchSink(handles, Envs.sink().build());
    }

    /** A distinct body the executor can identify; the sink applies IGNITE straight onto it. */
    private static LivingEntity target() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
        return entity;
    }

    /**
     * Test-owned multi-target selector: the executor's per-target filter is what is under test, so the chain
     * is handed in directly rather than mocked out of an area scan. Its list is IMMUTABLE, which also pins
     * that the filter never writes back into the selector's own result.
     */
    private static final class FixedTargets implements engine.selector.SelectorKind {

        static final String HEAD = "FIXTURE_CHAIN";

        private static final engine.spec.SelectorSpec SPEC = engine.spec.SelectorSpec.of(HEAD)
                .doc("test fixture").example("@FixtureChain").build();

        private final List<LivingEntity> chain;

        FixedTargets(LivingEntity... chain) {
            this.chain = List.of(chain);
        }

        @Override
        public engine.spec.SelectorSpec spec() {
            return SPEC;
        }

        @Override
        public List<LivingEntity> resolve(engine.selector.SelectorCtx ctx) {
            return chain;
        }
    }

    private AbilityExecutor executorWith(SuppressionStore suppression, SoulSpender spender) {
        return new AbilityExecutor(
                EffectRegistry.builder().register(new IgniteEffect()).build(),
                SelectorRegistry.builder().register(new SelfSelector()).register(new VictimSelector()).build(),
                new ActivationPipeline(new CooldownStore(), spender, suppression,
                        ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW),
                AreaScan.NONE);
    }

    /**
     * The ability's three int identities reach the effect ctx in their OWN slots (ADR-0074 adds the third).
     * {@code level}, {@code defId} and {@code cdScopeGroup} are adjacent ints in one constructor call, so a
     * transposition compiles silently — and would scope a landing by a level or attribute a diagnostic to a
     * group. Every value here is distinct precisely so that fails.
     */
    @Test
    void theAbilitysLevelDefIdAndGroupReachTheEffectCtxInTheirOwnSlots() {
        int[] seen = new int[3];
        EffectRegistry probes = EffectRegistry.builder().register(new IgniteEffect()).register(
                new engine.effect.EffectKind() {
                    @Override
                    public engine.spec.EffectSpec spec() {
                        return engine.spec.EffectSpec.of("PROBE_IDS").affinity(Affinity.CONTEXT_LOCAL)
                                .doc("test double").example("{ PROBE_IDS: { } }").build();
                    }

                    @Override
                    public void run(engine.effect.EffectCtx ctx, engine.sink.Sink sink) {
                        seen[0] = ctx.level();
                        seen[1] = ctx.sourceDefId();
                        seen[2] = ctx.sourceGroup();
                    }
                }).build();
        AbilityExecutor probing = new AbilityExecutor(probes,
                SelectorRegistry.builder().register(new SelfSelector()).register(new VictimSelector()).build(),
                new ActivationPipeline(new CooldownStore(), SoulSpender.NONE), AreaScan.NONE);
        Ability ability = Abilities.ability().trigger(TRIGGER).level(4).defId(77).cooldownScope(-1, 12, -1)
                .affinity(Affinity.CONTEXT_LOCAL)
                .effects(new CompiledEffect("PROBE_IDS", Args.empty(),
                        new CompiledSelector("SELF", Args.empty()), 0, Affinity.CONTEXT_LOCAL))
                .build();

        probing.run(new Ability[] {ability}, new int[] {0}, activation(), context(mock(Player.class), null),
                new ModernDispatchSink(handles, Envs.sink().build()), KEYS);

        assertEquals(4, seen[0], "level");
        assertEquals(77, seen[1], "sourceDefId");
        assertEquals(12, seen[2], "sourceGroup");
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
