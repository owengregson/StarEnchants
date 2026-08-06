package engine.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import compile.model.Ability;
import compile.model.ChanceRebate;
import compile.model.CompiledCondition;
import compile.model.cond.Cond;
import compile.model.cond.NumExpr;
import engine.interact.SoulSpender;
import engine.stores.CooldownStore;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import schema.diag.Source;
import schema.grammar.expr.FlowKind;
import testfx.Abilities;

/**
 * Gate 8's three-way split (ADR-0076 part E). The contract that matters most is that naming the blocked band
 * did not MOVE it: a declared {@code chance-rebate:} activates on exactly the rolls the shipped
 * {@code chance: "B - r"} subtraction activated on, so migrating a file cannot re-price it.
 */
class ChanceRebateTest {

    private static final UUID ACTOR = UUID.randomUUID();

    private final CooldownStore cooldowns = new CooldownStore();
    private final ActivationPipeline pipeline = new ActivationPipeline(cooldowns, ALWAYS_PAYS);

    @Test
    void aDeclaredRebateActivatesOnExactlyTheRollsTheSubtractionDid() {
        // The headline: sweep the whole roll space against both spellings of one rate. A divergence anywhere
        // means a migrated file's live proc rate moved, which is the one thing S6 promises it does not.
        Ability declared = rebated(40.0, points(12.5));
        Ability subtracted = plain(27.5);
        for (int bp = 0; bp < 10_000; bp++) {
            double roll = bp / 100.0;
            boolean declaredFired = pipeline.evaluate(declared, act(roll)).activated();
            boolean subtractedFired = pipeline.evaluate(subtracted, act(roll)).activated();
            assertEquals(subtractedFired, declaredFired, "roll " + roll + " diverged");
        }
    }

    @Test
    void oneDrawDecidesAllThreeArms() {
        // Splitting the verdict must not cost a second draw: two would let the rebate roll independently of the
        // proc, which is both a different distribution and a different feel (a rebate could then eat a miss).
        AtomicInteger draws = new AtomicInteger();
        Activation activation = Activation.builder(ACTOR, 3, 0, 100L)
                .chanceRoll(() -> {
                    draws.incrementAndGet();
                    return 30.0;
                }).build();
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(rebated(40.0, points(12.5)), activation));
        assertEquals(1, draws.get());
    }

    @Test
    void theRebatedBandIsExactlyBetweenTheEffectiveAndUnrebatedChances() {
        Ability a = rebated(40.0, points(12.5)); // effective 27.5, unrebated 40
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a, act(27.4)));
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(a, act(27.5)));       // the band's closed lower edge
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(a, act(39.9)));
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a, act(40.0))); // its open upper edge
    }

    @Test
    void aZeroRebateNeverProducesTheVerdict() {
        // A rebate whose expression reads 0 — a victim wearing none, which is the overwhelming case — must be
        // indistinguishable from no rebate at all, or the corpus would print blocked lines at every miss.
        Ability a = rebated(40.0, points(0.0));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a, act(39.9)));
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a, act(40.0)));
    }

    @Test
    void aRebateLargerThanTheChanceClampsInsteadOfInverting() {
        // Snare's -7 pp a level reaches -28 against its 9 % rung: the effective rate floors at 0 (real
        // immunity) and the band is the whole authored chance — never a negative that flips the comparison.
        Ability a = rebated(9.0, points(28.0));
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(a, act(0.0)));
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(a, act(8.99)));
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a, act(9.0)));
    }

    @Test
    void theScaleFormPricesOffTheBaseChance() {
        // Polymorphic Metaphysical vetoes 20 % OF THE PROC per level, not 20 percentage points — the one shape
        // points cannot say. 0.4 of a 7.5 % rung is 3 pp, so the effective rate is 4.5 %.
        Ability a = scaled(7.5, 0.4);
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(a, act(4.49)));
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(a, act(4.5)));
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(a, act(7.49)));
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(a, act(7.5)));
    }

    @Test
    void aScaleOutsideZeroToOneClampsRatherThanInvertingOrOvershooting() {
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(scaled(40.0, 3.0), act(0.0)));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(scaled(40.0, -1.0), act(39.9)));
    }

    @TestFactory
    Stream<DynamicTest> theFlooredShapeFiresOnExactlyTheRollsTheMaxSpellingDid() {
        // The algebra S6's floored files migrate on, held as a spec over a test-owned fixture rather than a
        // re-typed catalogue value: max(F, B - r) == B - min(B - F, r). Trap and Titan Trap keep a 1 % window
        // under every rebate, and getting the cap wrong would either delete that floor or freeze the rate.
        double base = 4.0;
        double floor = 1.0;
        return DoubleStream.of(0.0, 0.5, 2.5, 3.0, 5.0, 12.5).boxed()
                .map(r -> DynamicTest.dynamicTest("rebate " + r, () -> {
                    Ability declared = Abilities.ability().triggerMask(1).chance(base)
                            .chanceRebate(new ChanceRebate(
                                    new NumExpr.Fn(NumExpr.FnKind.MIN,
                                            List.of(new NumExpr.Lit(base - floor), new NumExpr.Lit(r))),
                                    null, null, false, -1, false))
                            .build();
                    Ability flooredSpelling = Abilities.ability().triggerMask(1)
                            .chanceExpr(new NumExpr.Fn(NumExpr.FnKind.MAX,
                                    List.of(new NumExpr.Lit(floor),
                                            new NumExpr.Bin(new NumExpr.Lit(base), NumExpr.Op.SUBTRACT,
                                                    new NumExpr.Lit(r)))))
                            .build();
                    for (int bp = 0; bp < 1_000; bp++) {
                        double roll = bp / 100.0;
                        assertEquals(pipeline.evaluate(flooredSpelling, act(roll)).activated(),
                                pipeline.evaluate(declared, act(roll)).activated(), "roll " + roll);
                    }
                }));
    }

    @Test
    void rebateSpendsCooldownBurnsTheWindowOnTheRebatedArmOnly() {
        // Guided Rocket Escape: a SABOTAGED escape burns its 15 s window. An ordinary miss must NOT, or every
        // failed roll on a proc enchant would arm a cooldown it never earned.
        Ability burns = windowed(40.0, points(12.5), true);
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(burns, act(30.0)));
        assertEquals(GateOutcome.ON_COOLDOWN, pipeline.evaluate(burns, act(1.0)), "the window stood");
    }

    @Test
    void anOrdinaryMissStillReleasesTheWindowEvenWithTheKnobOn() {
        Ability burns = windowed(40.0, points(12.5), true);
        assertEquals(GateOutcome.CHANCE_FAILED, pipeline.evaluate(burns, act(90.0)));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(burns, act(1.0)), "the window was released");
    }

    @Test
    void withoutTheKnobEvenARebatedRollReleasesTheWindow() {
        Ability releases = windowed(40.0, points(12.5), false);
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(releases, act(30.0)));
        assertEquals(GateOutcome.ACTIVATED, pipeline.evaluate(releases, act(1.0)));
    }

    @Test
    void theVerdictIsRecordedEvenWithNoFeedbackAuthored() {
        // The line is optional; the verdict is not. An operator asking /se why must see REBATED whether or not
        // the content chose to tell the player about it.
        Ability silent = Abilities.ability().triggerMask(1).chance(40.0)
                .chanceRebate(new ChanceRebate(points(12.5), null, null, false, -1, false)).build();
        assertEquals(GateOutcome.REBATED, pipeline.evaluate(silent, act(30.0)));
    }

    @Test
    void aForcedConditionSkipsTheRollAndTheRebateWithIt() {
        // FORCE means "do not roll"; a rebate biting there would let a defender veto a proc the content
        // declared unconditional, and would draw from a supplier the gate promises never to touch.
        Ability a = Abilities.ability().triggerMask(1).chance(0.0)
                .condition(new CompiledCondition(new Cond.BoolLit(true), FlowKind.FORCE, FlowKind.FORCE, 0.0,
                        Source.UNKNOWN))
                .chanceRebate(new ChanceRebate(points(100.0), null, "blocked", false, -1, false)).build();
        Activation forced = Activation.builder(ACTOR, 3, 0, 100L).chanceRoll(() -> {
            throw new AssertionError("a forced flow must not draw");
        }).build();
        assertTrue(pipeline.evaluate(a, forced).activated());
    }

    private Activation act(double roll) {
        return Activation.builder(ACTOR, 3, 0, 100L).chanceRoll(() -> roll).build();
    }

    private static NumExpr points(double value) {
        return new NumExpr.Lit(value);
    }

    private static Ability plain(double chance) {
        return Abilities.ability().triggerMask(1).chance(chance).build();
    }

    /** No cooldown: the verdict rows re-evaluate on one tick, and a reservation would answer ON_COOLDOWN. */
    private static Ability rebated(double chance, NumExpr term) {
        return Abilities.ability().triggerMask(1).chance(chance)
                .chanceRebate(new ChanceRebate(term, null, "blocked", false, -1, false)).build();
    }

    private static Ability scaled(double chance, double fraction) {
        return Abilities.ability().triggerMask(1).chance(chance)
                .chanceRebate(new ChanceRebate(null, points(fraction), "blocked", false, -1, false)).build();
    }

    /** Carries a cooldown scope, so gate 6's reservation arms and the release-vs-burn split is observable. */
    private static Ability windowed(double chance, NumExpr term, boolean spendsCooldown) {
        return Abilities.ability().triggerMask(1).chance(chance).cooldown(20).cooldownScope(9, -1, -1)
                .chanceRebate(new ChanceRebate(term, null, "blocked", false, -1, spendsCooldown)).build();
    }

    private static final SoulSpender ALWAYS_PAYS = new SoulSpender() {
        @Override public boolean trySpend(UUID player, int cost) {
            return true;
        }

        @Override public boolean trySpendCarried(UUID player, int cost) {
            return true;
        }
    };
}
