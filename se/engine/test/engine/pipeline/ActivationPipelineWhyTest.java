package engine.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import compile.model.CompiledCondition;
import compile.model.ScopeKinds;
import compile.model.cond.Cond;
import engine.interact.SoulSpender;
import engine.interact.SuppressionSet;
import engine.stores.CooldownStore;
import engine.stores.SuppressionStore;
import engine.stores.WhyRecorder;
import engine.stores.WhyRing;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import schema.diag.Source;
import schema.grammar.expr.FlowKind;
import testfx.Abilities;

/**
 * The {@link ActivationPipeline} reports one packed record per {@code evaluate} to the {@link WhyRecorder}
 * (ADR-0045): this pins the verdict + per-gate payload captured at each failing gate, and that a pipeline
 * built via the old (NONE-recorder) constructors gates byte-identically.
 */
class ActivationPipelineWhyTest {

    private static final UUID ACTOR = UUID.randomUUID();

    /** Records every attempt into a list (tests may allocate). */
    private static final class Capture implements WhyRecorder {
        record Rec(UUID actor, long tick, int trigger, int defId, int verdict, int pA, int pB) {
        }

        final List<Rec> recs = new ArrayList<>();

        @Override
        public void record(UUID actor, long nowTicks, int triggerId, int defId, int verdict, int pA, int pB) {
            recs.add(new Rec(actor, nowTicks, triggerId, defId, verdict, pA, pB));
        }

        Rec only() {
            assertEquals(1, recs.size(), "exactly one record per evaluate");
            return recs.get(0);
        }

        Rec last() {
            return recs.get(recs.size() - 1);
        }
    }

    private final Capture cap = new Capture();

    private ActivationPipeline pipeline(SuppressionStore suppression, ActivationPipeline.Guard protection,
                                        ActivationPipeline.Guard preActivate, SoulSpender spender) {
        return new ActivationPipeline(new CooldownStore(), spender, suppression, protection, preActivate, cap);
    }

    private ActivationPipeline plain() {
        return pipeline(new SuppressionStore(), ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW,
                SoulSpender.NONE);
    }

    private static Ability ab() {
        return Abilities.ability().defId(77).trigger(0).build();
    }

    private static Activation.Builder act(long tick, int trigger) {
        return Activation.builder(ACTOR, 3, trigger, tick); // world 3
    }

    private static int ordinal(GateOutcome out) {
        return out.ordinal();
    }

    @Test
    void blockedWorldCapturesTheWorldId() {
        Ability a = Abilities.ability().defId(77).trigger(0).worldBlacklist(1L << 3).build();
        assertEquals(GateOutcome.BLOCKED_WORLD, plain().evaluate(a, act(100L, 0).build()));
        Capture.Rec r = cap.only();
        assertEquals(ordinal(GateOutcome.BLOCKED_WORLD), r.verdict());
        assertEquals(3, r.pA());
        // The record identity plumbs actor/tick/trigger/defId through unchanged.
        assertEquals(ACTOR, r.actor());
        assertEquals(100L, r.tick());
        assertEquals(0, r.trigger());
        assertEquals(77, r.defId());
    }

    @Test
    void protectionDenialCaptured() {
        ActivationPipeline p = pipeline(new SuppressionStore(), (ab, act) -> false,
                ActivationPipeline.Guard.ALLOW, SoulSpender.NONE);
        assertEquals(GateOutcome.BLOCKED_PROTECTION, p.evaluate(ab(), act(100L, 0).build()));
        Capture.Rec r = cap.only();
        assertEquals(ordinal(GateOutcome.BLOCKED_PROTECTION), r.verdict());
        assertEquals(0, r.pA());
        assertEquals(0, r.pB());
    }

    @Test
    void wrongTriggerCaptured() {
        assertEquals(GateOutcome.WRONG_TRIGGER, plain().evaluate(ab(), act(100L, 1).build())); // fires on 0, event is 1
        assertEquals(ordinal(GateOutcome.WRONG_TRIGGER), cap.only().verdict());
    }

    @Test
    void transientSuppressionCapturesFlavorZeroAndTheSuppressKey() {
        Ability a = Abilities.ability().defId(77).trigger(0).suppressKey(7).build();
        SuppressionSet set = new SuppressionSet();
        set.add(7);
        assertEquals(GateOutcome.SUPPRESSED, plain().evaluate(a, act(100L, 0).suppression(set).build()));
        Capture.Rec r = cap.only();
        assertEquals(ordinal(GateOutcome.SUPPRESSED), r.verdict());
        assertEquals(0, WhyRing.scopeFlavor(r.pA()));
        assertEquals(7, WhyRing.scopeId(r.pA()));
        assertEquals(-1, r.pB());
    }

    @Test
    void timedSuppressionCapturesFlavorOneTheArmedScopeAndTheSuppressorDefId() {
        SuppressionStore store = new SuppressionStore();
        store.suppress(ACTOR, CooldownStore.key(ScopeKinds.GROUP, 5), 100L, 40, 42); // DISABLE_GROUP id 5 by defId 42
        Ability a = Abilities.ability().defId(77).trigger(0).cooldownScope(-1, 5, -1).build();

        ActivationPipeline p = pipeline(store, ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW,
                SoulSpender.NONE);
        assertEquals(GateOutcome.SUPPRESSED, p.evaluate(a, act(100L, 0).build()));
        Capture.Rec r = cap.only();
        assertEquals(ordinal(GateOutcome.SUPPRESSED), r.verdict());
        assertEquals(1, WhyRing.scopeFlavor(r.pA()));
        assertEquals(ScopeKinds.GROUP, WhyRing.scopeKind(r.pA()));
        assertEquals(5, WhyRing.scopeId(r.pA()));
        assertEquals(42, r.pB());
    }

    @Test
    void cooldownCapturesTheScopeRemainingAndRoutesByBucket() {
        CooldownStore cd = new CooldownStore();
        ActivationPipeline p = new ActivationPipeline(cd, SoulSpender.NONE, new SuppressionStore(),
                ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW, cap);
        Ability a = Abilities.ability().defId(77).trigger(0).cooldownScope(5, -1, -1).cooldown(100).build();

        assertEquals(GateOutcome.ACTIVATED, p.evaluate(a, act(100L, 0).build())); // arms enchant scope 5 @100 dur 100
        assertEquals(GateOutcome.ON_COOLDOWN, p.evaluate(a, act(168L, 0).build())); // remaining 200-168 = 32
        Capture.Rec r = cap.last();
        assertEquals(ordinal(GateOutcome.ON_COOLDOWN), r.verdict());
        assertEquals(0, WhyRing.scopeFlavor(r.pA()));
        assertEquals(ScopeKinds.ENCHANT, WhyRing.scopeKind(r.pA()));
        assertEquals(5, WhyRing.scopeId(r.pA()));
        assertEquals(32, r.pB());

        // Cooldowns route by target bucket: the same ability on the other bucket is ready.
        assertEquals(GateOutcome.ACTIVATED, p.evaluate(a, act(168L, 0).targetBucket(1).build()));
    }

    @Test
    void conditionStopCaptured() {
        Ability a = Abilities.ability().defId(77).trigger(0)
                .condition(CompiledCondition.gate(new Cond.BoolLit(false), Source.UNKNOWN)).build();
        assertEquals(GateOutcome.CONDITION_FAILED, plain().evaluate(a, act(100L, 0).build()));
        assertEquals(ordinal(GateOutcome.CONDITION_FAILED), cap.only().verdict());
    }

    @Test
    void chanceFailCapturesRollAndChanceBasisPoints() {
        Ability a = Abilities.ability().defId(77).trigger(0).chance(25.0).build();
        assertEquals(GateOutcome.CHANCE_FAILED, plain().evaluate(a, act(100L, 0).chanceRoll(() -> 87.2).build()));
        Capture.Rec r = cap.only();
        assertEquals(ordinal(GateOutcome.CHANCE_FAILED), r.verdict());
        assertEquals(8720, r.pA());
        assertEquals(2500, r.pB());
    }

    @Test
    void forcedFlowActivatesWithNoRoll() {
        Ability a = Abilities.ability().defId(77).trigger(0).chance(25.0)
                .condition(new CompiledCondition(new Cond.BoolLit(true), FlowKind.FORCE, FlowKind.STOP, 0.0,
                        Source.UNKNOWN))
                .build();
        assertEquals(GateOutcome.ACTIVATED, plain().evaluate(a, act(100L, 0).chanceRoll(() -> 99.0).build()));
        Capture.Rec r = cap.only();
        assertEquals(ordinal(GateOutcome.ACTIVATED), r.verdict());
        assertEquals(-1, r.pA()); // no roll was taken
    }

    @Test
    void preActivateCancelCaptured() {
        ActivationPipeline p = pipeline(new SuppressionStore(), ActivationPipeline.Guard.ALLOW,
                (ab, act) -> false, SoulSpender.NONE);
        assertEquals(GateOutcome.CANCELLED, p.evaluate(ab(), act(100L, 0).build()));
        assertEquals(ordinal(GateOutcome.CANCELLED), cap.only().verdict());
    }

    @Test
    void noSoulsCapturesTheFailModeAndCost() {
        Ability a = Abilities.ability().defId(77).trigger(0).soulCost(5).build();

        // No active gem → fail code 0.
        assertEquals(GateOutcome.NO_SOULS, plain().evaluate(a, act(100L, 0).build()));
        Capture.Rec noGem = cap.only();
        assertEquals(ordinal(GateOutcome.NO_SOULS), noGem.verdict());
        assertEquals(0, noGem.pA());
        assertEquals(5, noGem.pB());

        // Gem active but the pool refuses → fail code 1.
        Capture cap2 = new Capture();
        ActivationPipeline p = new ActivationPipeline(new CooldownStore(), (player, cost) -> false,
                new SuppressionStore(), ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW, cap2);
        assertEquals(GateOutcome.NO_SOULS, p.evaluate(a, act(100L, 0).soulMode(ACTOR).build()));
        assertEquals(1, cap2.only().pA());
    }

    @Test
    void activatedCapturesTheRollAndChance() {
        Ability a = Abilities.ability().defId(77).trigger(0).chance(25.0).build();
        assertEquals(GateOutcome.ACTIVATED, plain().evaluate(a, act(100L, 0).chanceRoll(() -> 12.34).build()));
        Capture.Rec r = cap.only();
        assertEquals(ordinal(GateOutcome.ACTIVATED), r.verdict());
        assertEquals(1234, r.pA());
        assertEquals(2500, r.pB());
    }

    @Test
    void oldConstructorRecordsNothingAndGatesIdentically() {
        // The 5-arg ctor injects WhyRecorder.NONE; each case must gate exactly as the recording pipeline does.
        record Case(String name, Ability ability, Activation activation, GateOutcome expected) {
        }
        List<Case> cases = List.of(
                new Case("world", Abilities.ability().trigger(0).worldBlacklist(1L << 3).build(),
                        act(100L, 0).build(), GateOutcome.BLOCKED_WORLD),
                new Case("trigger", ab(), act(100L, 1).build(), GateOutcome.WRONG_TRIGGER),
                new Case("condition", Abilities.ability().trigger(0)
                        .condition(CompiledCondition.gate(new Cond.BoolLit(false), Source.UNKNOWN)).build(),
                        act(100L, 0).build(), GateOutcome.CONDITION_FAILED),
                new Case("chance", Abilities.ability().trigger(0).chance(25.0).build(),
                        act(100L, 0).chanceRoll(() -> 87.2).build(), GateOutcome.CHANCE_FAILED),
                new Case("activated", ab(), act(100L, 0).build(), GateOutcome.ACTIVATED));

        for (Case c : cases) {
            ActivationPipeline none = new ActivationPipeline(new CooldownStore(), SoulSpender.NONE);
            Capture recording = new Capture();
            ActivationPipeline recorded = new ActivationPipeline(new CooldownStore(), SoulSpender.NONE,
                    new SuppressionStore(), ActivationPipeline.Guard.ALLOW, ActivationPipeline.Guard.ALLOW, recording);
            assertEquals(c.expected(), none.evaluate(c.ability(), c.activation()), c.name());
            assertEquals(c.expected(), recorded.evaluate(c.ability(), c.activation()), c.name());
            assertTrue(recording.recs.size() == 1, c.name() + ": exactly one record");
        }
    }
}
